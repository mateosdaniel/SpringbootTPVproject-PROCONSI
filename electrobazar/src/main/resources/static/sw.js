const CACHE_NAME = 'tpv-v2';
const ASSETS = [
    '/tpv',
    '/css/tpv/tpv-main.css',
    '/js/tpv/tpv-main.js',
    '/icons/favicon.svg',
    '/icons/favicon-light.svg',
    'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css',
    'https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js',
    'https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css',
    'https://fonts.googleapis.com/css2?family=Barlow:wght@400;500;600;700&family=Barlow+Condensed:wght@600;700&display=swap'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            return cache.addAll(ASSETS);
        })
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys => {
            return Promise.all(
                keys.filter(key => key !== CACHE_NAME)
                    .map(key => caches.delete(key))
            );
        })
    );
});

self.addEventListener('fetch', event => {
    // Only cache GET requests
    if (event.request.method !== 'GET') return;

    const url = new URL(event.request.url);

    // Strategy: Network First for the main navigation (/tpv) and API calls
    if (url.pathname === '/tpv' || url.pathname === '/admin' || url.pathname.includes('/api/') || event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request)
                .then(response => {
                    // Only cache successful GET responses
                    if (response.ok && response.status === 200 && event.request.method === 'GET') {
                        // Check if it's the admin page and potentially clear its cache first
                        if (url.pathname === '/admin') {
                            // Optionally skip caching for admin if it's too problematic
                            // For now, let's try to cache only if we think it's complete
                        }
                        
                        const copy = response.clone();
                        caches.open(CACHE_NAME).then(cache => {
                            // We use a trial put and catch error
                            cache.put(event.request, copy).catch(err => {
                                console.warn('SW: Cache put failed (possibly incomplete):', err.message);
                            });
                        });
                    }
                    return response;
                })
                .catch(() => caches.match(event.request))
        );
        return;
    }

    // Strategy: Cache First for other static assets (CSS, JS, Fonts, etc.)
    event.respondWith(
        caches.match(event.request).then(response => {
            return response || fetch(event.request).then(networkResponse => {
                if (networkResponse.ok && (url.origin === location.origin)) {
                    const copy = networkResponse.clone();
                    caches.open(CACHE_NAME).then(cache => {
                        cache.put(event.request, copy).catch(err => {
                            console.warn('SW: Cache put (static) failed:', err.message);
                        });
                    });
                }
                return networkResponse;
            });
        })
    );
});
