(function () {
    const accentColors = [
        { name: 'Monocromo', value: '#ffffff', hover: '#e0e0e0' },
        { name: 'Naranja', value: '#f5a623', hover: '#e09400' },
        { name: 'Azul', value: '#3b82f6', hover: '#2563eb' },
        { name: 'Verde', value: '#22c55e', hover: '#16a34a' },
        { name: 'Rojo', value: '#ef4444', hover: '#dc2626' },
        { name: 'Morado', value: '#a855f7', hover: '#9333ea' },
        { name: 'Rosa', value: '#ec4899', hover: '#db2777' },
        { name: 'Cian', value: '#06b6d4', hover: '#0891b2' }
    ];

    const darkP = [
        { name: 'Medianoche', primary: '#151525', secondary: '#1e1e35', surface: '#252545', border: '#2c2c4d', muted: '#8892a4', text: '#e8eaf0' },
        { name: 'Negro total', primary: '#000000', secondary: '#0c0c0c', surface: '#161616', border: '#222222', muted: '#777777', text: '#e0e0e0' },
        { name: 'Carbon', primary: '#121212', secondary: '#1a1a1a', surface: '#242424', border: '#2d2d2d', muted: '#888888', text: '#e8e8e8' },
    ];

    const lightP = [
        { name: 'Blanco Puro', primary: '#ffffff', secondary: '#f8fafc', surface: '#f1f5f9' },
        { name: 'Arena', primary: '#f5edc5', secondary: '#faf3e0', surface: '#f0e6c8' },
        { name: 'Ceniza', primary: '#eef2f7', secondary: '#e4e9f0', surface: '#d8e0ea' },
    ];

    const lightFixed = {
        border: '#dae1e7',
        muted: '#64748b',
        text: '#0f172a'
    };

    const defaultPrefs = {
        mode: 'dark',
        lightAccent: 1, lightPrimaryIdx: 0,
        darkAccent: 7, darkPrimaryIdx: 0
    };

    let prefs = JSON.parse(localStorage.getItem('tpv-prefs') || 'null');
    if (prefs && prefs.darkPrimaryIdx >= darkP.length) prefs.darkPrimaryIdx = 0;
    if (prefs && prefs.lightPrimaryIdx >= lightP.length) prefs.lightPrimaryIdx = 0;

    if (!prefs || prefs.accent !== undefined) {
        prefs = { ...defaultPrefs, mode: prefs ? prefs.mode : 'dark' };
    }
    if (prefs.darkAccent >= accentColors.length) prefs.darkAccent = 0;
    if (prefs.lightAccent >= accentColors.length) prefs.lightAccent = 0;

    const flash = document.getElementById('themeFlash');

    function triggerFlash() {
        if (!flash) return;
        flash.classList.add('active');
        setTimeout(() => flash.classList.remove('active'), 400);
    }

    function apply(animate) {
        const isDark = prefs.mode === 'dark';
        let currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;
        if (currentAccentIdx >= accentColors.length) currentAccentIdx = 0;
        const currentPrimaryIdx = isDark ? prefs.darkPrimaryIdx : prefs.lightPrimaryIdx;

        const accent = accentColors[currentAccentIdx] || accentColors[isDark ? 7 : 1];
        let accentValue = accent.value;
        let accentHover = accent.hover;

        const palette = isDark ? darkP : lightP;
        const p = palette[currentPrimaryIdx] || palette[0];
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

        if (accent.name === 'Monocromo') {
            accentValue = isDark ? '#ffffff' : '#000000';
            accentHover = isDark ? '#e0e0e0' : '#333333';
            if (isDark && p.primary === '#000000') root.setProperty('--border', '#222222');
            if (!isDark && p.primary === '#ffffff') root.setProperty('--border', '#e0e0e0');
        }

        function hexToRgb(hex) {
            let r = 0, g = 0, b = 0;
            if (hex.length === 7) {
                r = parseInt(hex.slice(1, 3), 16);
                g = parseInt(hex.slice(3, 5), 16);
                b = parseInt(hex.slice(5, 7), 16);
            }
            return r + ", " + g + ", " + b;
        }

        root.setProperty('--accent', accentValue);
        root.setProperty('--accent-hover', accentHover);
        root.setProperty('--accent-rgb', hexToRgb(accentValue));

        let logoFile = isDark ? '/icons/favicon.svg' : '/icons/favicon-light.svg';
        document.querySelectorAll('img[alt="Logo"]').forEach(img => img.src = logoFile);
        const faviconLink = document.querySelector('link[rel="icon"]');
        if (faviconLink) faviconLink.href = logoFile;

        document.getElementById('modeDark').classList.toggle('active', isDark);
        document.getElementById('modeLight').classList.toggle('active', !isDark);

        localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
        if (animate) triggerFlash();
        renderSwatches();
    }

    function renderSwatches() {
        const accentGrid = document.getElementById('accentSwatches');
        if (!accentGrid) return;
        accentGrid.innerHTML = '';
        const isDark = prefs.mode === 'dark';
        const currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;

        accentColors.forEach((c, i) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'swatch-wrapper';
            const el = document.createElement('div');
            el.className = 'swatch' + (currentAccentIdx === i ? ' selected' : '');
            let swatchColor = c.value;
            if (c.name === 'Monocromo') swatchColor = isDark ? '#ffffff' : '#000000';
            el.style.background = swatchColor;
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

        const palette = isDark ? darkP : lightP;
        const currentPrimaryIdx = isDark ? prefs.darkPrimaryIdx : prefs.lightPrimaryIdx;
        const primaryGrid = document.getElementById('primarySwatches');
        if (!primaryGrid) return;
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

    apply(false);
})();