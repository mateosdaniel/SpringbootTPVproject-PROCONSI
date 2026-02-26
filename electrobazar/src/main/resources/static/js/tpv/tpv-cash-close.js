        const theoryAmountText = document.getElementById('theoryAmount').textContent.replace('â‚¬', '').trim();
        // El formato espaÃ±ol usa punto como separador de miles y coma como decimal (ej: 1.234,56)
        // Primero eliminamos los puntos de miles, luego reemplazamos la coma decimal por punto
        const theoryAmount = parseFloat(theoryAmountText.replace(/\./g, '').replace(',', '.')) || 0;
        const closingBalanceInput = document.getElementById('closingBalance');

        function updateDifference() {
            const realAmount = parseFloat(closingBalanceInput.value.replace(',', '.')) || 0;
            const difference = realAmount - theoryAmount;

            document.getElementById('realAmount').textContent = realAmount.toFixed(2) + 'â‚¬';
            const diffAmountSpan = document.getElementById('diffAmount');

            // Cambiar color y texto segÃºn si hay diferencia
            if (Math.abs(difference) < 0.01) {
                diffAmountSpan.textContent = '0.00â‚¬';
                diffAmountSpan.style.color = 'var(--success)';
            } else if (difference > 0) {
                diffAmountSpan.textContent = '+' + difference.toFixed(2) + 'â‚¬';
                diffAmountSpan.style.color = 'var(--success)';
            } else {
                diffAmountSpan.textContent = difference.toFixed(2) + 'â‚¬';
                diffAmountSpan.style.color = 'var(--danger)';
            }
        }

        closingBalanceInput.addEventListener('input', updateDifference);
        closingBalanceInput.addEventListener('change', updateDifference);
        closingBalanceInput.addEventListener('keyup', updateDifference);

        // â”€â”€ Apply saved theme â”€â”€
        (function () {
            const accentColors = [
                { value: '#f5a623', hover: '#e09400' },
                { value: '#3b82f6', hover: '#2563eb' },
                { value: '#22c55e', hover: '#16a34a' },
                { value: '#ef4444', hover: '#dc2626' },
                { value: '#a855f7', hover: '#9333ea' },
                { value: '#ec4899', hover: '#db2777' },
                { value: '#06b6d4', hover: '#0891b2' }
            ];
            const darkP = [
                { primary: '#1a1a2e', secondary: '#16213e', surface: '#0f3460', border: '#1e2d45', muted: '#8892a4', text: '#e8eaf0' },
                { primary: '#000000', secondary: '#111111', surface: '#0a0a0a', border: '#222222', muted: '#777777', text: '#e0e0e0' },
                { primary: '#1c1c1c', secondary: '#2a2a2a', surface: '#222222', border: '#3a3a3a', muted: '#888888', text: '#e8e8e8' },
            ];
            const lightP = [
                { primary: '#f6f6f6', secondary: '#ffffff', surface: '#e2e8f0' },
                { primary: '#fbf9f6', secondary: '#fdfdfc', surface: '#e9e7e2' },
                { primary: '#f0f4f8', secondary: '#ffffff', surface: '#dae3ed' },
            ];
            const lightFixed = { border: '#cbd5e1', muted: '#636b7a', text: '#0f172a' };

            const defaultPrefs = {
                mode: 'dark',
                lightAccent: 0, lightPrimaryIdx: 0,
                darkAccent: 6, darkPrimaryIdx: 0
            };

            let prefs = JSON.parse(localStorage.getItem('tpv-prefs') || 'null');
            if (prefs && prefs.darkPrimaryIdx >= darkP.length) prefs.darkPrimaryIdx = 0;
            if (prefs && prefs.lightPrimaryIdx >= lightP.length) prefs.lightPrimaryIdx = 0;

            if (!prefs || prefs.accent !== undefined) {
                prefs = { ...defaultPrefs, mode: prefs ? prefs.mode : 'dark' };
            }

            const isDark = prefs.mode === 'dark';
            let currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;
            if (currentAccentIdx >= accentColors.length) currentAccentIdx = 0;
            const currentPrimaryIdx = isDark ? prefs.darkPrimaryIdx : prefs.lightPrimaryIdx;

            const accent = accentColors[currentAccentIdx] || accentColors[isDark ? 6 : 0];
            const palette = isDark ? darkP : lightP;
            const p = palette[currentPrimaryIdx] || palette[isDark ? 0 : 1];

            const r = document.documentElement.style;
            r.setProperty('--primary', p.primary);
            r.setProperty('--secondary', p.secondary);
            r.setProperty('--surface', p.surface);
            if (isDark) {
                r.setProperty('--border', p.border);
                r.setProperty('--text-muted', p.muted);
                r.setProperty('--text-main', p.text);
            } else {
                r.setProperty('--border', lightFixed.border);
                r.setProperty('--text-muted', lightFixed.muted);
                r.setProperty('--text-main', lightFixed.text);
            }
            r.setProperty('--accent', accent.value);
            r.setProperty('--accent-hover', accent.hover);

            const logo = isDark ? '/favicon.svg' : '/favicon-light.svg';
            document.querySelectorAll('img[alt="Logo"], img[alt="Logo ElectroBazar"]').forEach(function (i) { i.src = logo; });
            const fl = document.querySelector('link[rel="icon"]'); if (fl) fl.href = logo;
        })();
