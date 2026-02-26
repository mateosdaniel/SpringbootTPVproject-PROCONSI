        (function () {
            // â”€â”€ Color Palettes â”€â”€
            const accentColors = [
                { name: 'Naranja', value: '#f5a623', hover: '#e09400' },
                { name: 'Azul', value: '#3b82f6', hover: '#2563eb' },
                { name: 'Verde', value: '#22c55e', hover: '#16a34a' },
                { name: 'Rojo', value: '#ef4444', hover: '#dc2626' },
                { name: 'Violeta', value: '#a855f7', hover: '#9333ea' },
                { name: 'Rosa', value: '#ec4899', hover: '#db2777' },
                { name: 'Cian', value: '#06b6d4', hover: '#0891b2' }
            ];

            const darkPrimaries = [
                { name: 'Medianoche', primary: '#1a1a2e', secondary: '#16213e', surface: '#0f3460', border: '#1e2d45', muted: '#8892a4', text: '#e8eaf0' },
                { name: 'Negro total', primary: '#000000', secondary: '#111111', surface: '#0a0a0a', border: '#222222', muted: '#777777', text: '#e0e0e0' },
                { name: 'CarbÃ³n', primary: '#1c1c1c', secondary: '#2a2a2a', surface: '#222222', border: '#3a3a3a', muted: '#888888', text: '#e8e8e8' },
            ];

            const lightPrimaries = [
                { name: 'Blanco total', primary: '#f6f6f6', secondary: '#ffffff', surface: '#e2e8f0' },
                { name: 'Blanco hueso', primary: '#fbf9f6', secondary: '#fdfdfc', surface: '#e9e7e2' },
                { name: 'Gris perla', primary: '#f0f4f8', secondary: '#ffffff', surface: '#dae3ed' },
            ];

            const lightFixed = {
                border: '#cbd5e1',
                muted: '#636b7a',
                text: '#0f172a'
            };

            // â”€â”€ State â”€â”€
            const defaultPrefs = {
                mode: 'dark',
                lightAccent: 0, lightPrimaryIdx: 0,
                darkAccent: 6, darkPrimaryIdx: 0
            };

            let prefs = JSON.parse(localStorage.getItem('tpv-prefs') || 'null');
            // If the user's previously saved index is now out of bounds due to array shrinkage, reset to 0
            if (prefs && prefs.darkPrimaryIdx >= darkPrimaries.length) prefs.darkPrimaryIdx = 0;
            if (prefs && prefs.lightPrimaryIdx >= lightPrimaries.length) prefs.lightPrimaryIdx = 0;

            if (!prefs || prefs.accent !== undefined) {
                // Initialize or migrate from old
                prefs = { ...defaultPrefs, mode: prefs ? prefs.mode : 'dark' };
            }
            // Migrate off out-of-bounds accents
            if (prefs.darkAccent >= accentColors.length) prefs.darkAccent = 0;
            if (prefs.lightAccent >= accentColors.length) prefs.lightAccent = 0;

            const flash = document.getElementById('themeFlash');

            function triggerFlash() {
                flash.classList.add('active');
                setTimeout(() => flash.classList.remove('active'), 400);
            }

            // â”€â”€ Apply Theme â”€â”€
            function apply(animate) {
                const isDark = prefs.mode === 'dark';

                let currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;
                if (currentAccentIdx >= accentColors.length) currentAccentIdx = 0;

                const currentPrimaryIdx = isDark ? prefs.darkPrimaryIdx : prefs.lightPrimaryIdx;

                const accent = accentColors[currentAccentIdx] || accentColors[isDark ? 6 : 0];
                const palette = isDark ? darkPrimaries : lightPrimaries;
                const p = palette[currentPrimaryIdx] || palette[isDark ? 0 : 1];
                const root = document.documentElement.style;

                root.setProperty('--primary', p.primary);
                root.setProperty('--secondary', p.secondary);
                root.setProperty('--surface', p.surface);
                if (isDark) {
                    root.setProperty('--border', p.border);
                    root.setProperty('--text-muted', p.muted);
                    root.setProperty('--text-main', p.text);
                } else {
                    root.setProperty('--border', lightFixed.border);
                    root.setProperty('--text-muted', lightFixed.muted);
                    root.setProperty('--text-main', lightFixed.text);
                }
                root.setProperty('--accent', accent.value);
                root.setProperty('--accent-hover', accent.hover);

                // Swap logo images
                const logoFile = isDark ? '/favicon.svg' : '/favicon-light.svg';
                document.querySelectorAll('img[alt="Logo"]').forEach(img => img.src = logoFile);
                const faviconLink = document.querySelector('link[rel="icon"]');
                if (faviconLink) faviconLink.href = logoFile;

                // Mode buttons
                document.getElementById('modeDark').classList.toggle('active', isDark);
                document.getElementById('modeLight').classList.toggle('active', !isDark);

                localStorage.setItem('tpv-prefs', JSON.stringify(prefs));

                if (animate) triggerFlash();

                renderSwatches();
            }

            // â”€â”€ Render Accent Swatches â”€â”€
            function renderSwatches() {
                const accentGrid = document.getElementById('accentSwatches');
                accentGrid.innerHTML = '';
                const isDark = prefs.mode === 'dark';
                const currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;

                accentColors.forEach((c, i) => {

                    const wrapper = document.createElement('div');
                    wrapper.className = 'swatch-wrapper';

                    const el = document.createElement('div');
                    el.className = 'swatch' + (currentAccentIdx === i ? ' selected' : '');
                    el.style.background = c.value;
                    el.title = c.name;
                    el.addEventListener('click', () => {
                        if (isDark) prefs.darkAccent = i; else prefs.lightAccent = i;
                        apply(true);
                    });

                    const label = document.createElement('span');
                    label.className = 'swatch-name';
                    label.textContent = c.name;

                    wrapper.appendChild(el);
                    wrapper.appendChild(label);
                    accentGrid.appendChild(wrapper);
                });

                // Primary tones
                const palette = isDark ? darkPrimaries : lightPrimaries;
                const currentPrimaryIdx = isDark ? prefs.darkPrimaryIdx : prefs.lightPrimaryIdx;
                const primaryGrid = document.getElementById('primarySwatches');
                primaryGrid.innerHTML = '';
                palette.forEach((p, i) => {
                    const el = document.createElement('div');
                    el.className = 'primary-swatch' + (currentPrimaryIdx === i ? ' selected' : '');
                    el.style.background = p.primary;
                    el.style.borderColor = currentPrimaryIdx === i ? 'var(--accent)' : (p.border || lightFixed.border);

                    const dot = document.createElement('div');
                    dot.className = 'preview-dot';
                    dot.style.background = p.secondary;
                    dot.style.border = '1px solid ' + (p.border || lightFixed.border);

                    const label = document.createElement('span');
                    label.className = 'preview-label';
                    label.style.color = p.text || lightFixed.text;
                    label.textContent = p.name;

                    el.appendChild(dot);
                    el.appendChild(label);
                    el.addEventListener('click', () => {
                        if (isDark) prefs.darkPrimaryIdx = i; else prefs.lightPrimaryIdx = i;
                        apply(true);
                    });
                    primaryGrid.appendChild(el);
                });
            }

            // â”€â”€ Mode Toggle Events â”€â”€
            document.getElementById('modeDark').addEventListener('click', () => {
                if (prefs.mode !== 'dark') {
                    prefs.mode = 'dark';
                    apply(true);
                }
            });

            document.getElementById('modeLight').addEventListener('click', () => {
                if (prefs.mode !== 'light') {
                    prefs.mode = 'light';
                    apply(true);
                }
            });

            // â”€â”€ Init â”€â”€
            apply(false);
        })();
