        // Apply saved theme mode if any
        (function () {
            const prefs = JSON.parse(localStorage.getItem('tpv-prefs') || 'null');
            if (prefs && prefs.mode === 'light') {
                const r = document.documentElement.style;
                // Simplified light mode for login
                r.setProperty('--primary', '#f6f6f6');
                r.setProperty('--secondary', '#ffffff');
                r.setProperty('--text-main', '#0f172a');
                r.setProperty('--text-muted', '#636b7a');
                r.setProperty('--border', '#cbd5e1');
                document.querySelectorAll('img[alt="Logo"]').forEach(img => img.src = '/favicon-light.svg');
            }
        })();
