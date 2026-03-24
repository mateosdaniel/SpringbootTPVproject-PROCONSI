(function () {
    const i18n = Object.assign({
        monochrome: 'Monochrome', orange: 'Orange', blue: 'Blue', green: 'Green', red: 'Red', purple: 'Purple', pink: 'Pink', cyan: 'Cyan',
        midnight: 'Midnight', totalblack: 'Total Black', carbon: 'Carbon',
        purewhite: 'Pure White', sand: 'Sand', ash: 'Ash', custom: 'Custom'
    }, window.prefsI18n || {});

    const accentColors = [
        { name: i18n.monochrome, value: '#ffffff', hover: '#e0e0e0' },
        { name: i18n.orange, value: '#f5a623', hover: '#e09400' },
        { name: i18n.blue, value: '#3b82f6', hover: '#2563eb' },
        { name: i18n.green, value: '#22c55e', hover: '#16a34a' },
        { name: i18n.red, value: '#ef4444', hover: '#dc2626' },
        { name: i18n.purple, value: '#a855f7', hover: '#9333ea' },
        { name: i18n.pink, value: '#ec4899', hover: '#db2777' },
        { name: i18n.cyan, value: '#06b6d4', hover: '#0891b2' }
    ];

    const darkP = [
        { name: i18n.midnight, primary: '#151525', secondary: '#1e1e35', surface: '#252545', border: '#2c2c4d', muted: '#8892a4', text: '#e8eaf0' },
        { name: i18n.totalblack, primary: '#000000', secondary: '#0c0c0c', surface: '#161616', border: '#222222', muted: '#777777', text: '#e0e0e0' },
        { name: i18n.carbon, primary: '#121212', secondary: '#1a1a1a', surface: '#242424', border: '#2d2d2d', muted: '#888888', text: '#e8e8e8' },
    ];

    const lightP = [
        { name: i18n.purewhite, primary: '#ffffff', secondary: '#f8fafc', surface: '#f1f5f9' },
        { name: i18n.sand, primary: '#f5edc5', secondary: '#faf3e0', surface: '#f0e6c8' },
        { name: i18n.ash, primary: '#eef2f7', secondary: '#e4e9f0', surface: '#d8e0ea' },
    ];

    const lightFixed = {
        border: '#dae1e7',
        muted: '#64748b',
        text: '#0f172a'
    };

    const defaultPrefs = {
        mode: 'dark',
        language: 'es',
        lightAccent: 1, lightPrimaryIdx: 0,
        darkAccent: 7, darkPrimaryIdx: 0
    };

    let prefs = JSON.parse(localStorage.getItem('tpv-prefs') || 'null');
    
    // Sync language with URL if present (Spring standard)
    const urlParams = new URLSearchParams(window.location.search);
    const langParam = urlParams.get('lang');
    if (langParam) {
        if (!prefs) prefs = { ...defaultPrefs };
        const newLang = langParam.toLowerCase();
        if (prefs.language !== newLang) {
            prefs.language = newLang;
            localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
        }
    }

    if (!prefs) prefs = { ...defaultPrefs };
    if (prefs.darkPrimaryIdx === undefined || prefs.darkPrimaryIdx >= darkP.length) prefs.darkPrimaryIdx = 0;
    if (prefs.lightPrimaryIdx === undefined || prefs.lightPrimaryIdx >= lightP.length) prefs.lightPrimaryIdx = 0;

    if (prefs.accent !== undefined) {
        prefs = { ...defaultPrefs, mode: prefs.mode || 'dark' };
    }
    if (!prefs.darkAccent || prefs.darkAccent >= accentColors.length) prefs.darkAccent = 0;
    if (!prefs.lightAccent || prefs.lightAccent >= accentColors.length) prefs.lightAccent = 0;
    if (!prefs.language) prefs.language = 'es';

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

        const palette = isDark ? darkP : lightP;
        const p = palette[currentPrimaryIdx] || palette[0];
        const root = document.documentElement.style;

        let accentValue, accentHover;
        if (prefs.customAccent) {
            accentValue = prefs.customAccent;
            accentHover = accentValue;
        } else {
            const accent = accentColors[currentAccentIdx] || accentColors[isDark ? 7 : 1];
            accentValue = accent.value;
            accentHover = accent.hover;
            if (accent.name === i18n.monochrome) {
                accentValue = isDark ? '#ffffff' : '#000000';
                accentHover = isDark ? '#e0e0e0' : '#333333';
                if (isDark && p.primary === '#000000') root.setProperty('--border', '#222222');
                if (!isDark && p.primary === '#ffffff') root.setProperty('--border', '#e0e0e0');
            }
        }

        root.setProperty('--primary', p.primary);
        root.setProperty('--secondary', p.secondary);
        root.setProperty('--surface', p.surface);
        root.setProperty('--main-font-size', prefs.fontSize || '16px');
        root.setProperty('--main-font-family', prefs.fontFamily || "'Barlow', sans-serif");

        // Root font size for rem/em scaling
        document.documentElement.style.fontSize = prefs.fontSize || '16px';
        // Body font size as fallback
        document.body.style.fontSize = prefs.fontSize || '16px';
        document.body.style.fontFamily = prefs.fontFamily || "'Barlow', sans-serif";

        if (isDark) {
            root.setProperty('--border', p.border);
            root.setProperty('--text-muted', p.muted);
            root.setProperty('--text-main', p.text);
        } else {
            root.setProperty('--border', lightFixed.border);
            root.setProperty('--text-muted', lightFixed.muted);
            root.setProperty('--text-main', lightFixed.text);
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
        document.querySelectorAll('link[rel="icon"]').forEach(link => {
            link.removeAttribute('media');
            link.href = logoFile;
        });

        const mDark = document.getElementById('modeDark');
        const mLight = document.getElementById('modeLight');
        if (mDark) mDark.classList.toggle('active', isDark);
        if (mLight) mLight.classList.toggle('active', !isDark);

        // Update Language UI
        const langEs = document.getElementById('langEs');
        const langEn = document.getElementById('langEn');
        if (langEs && langEn) {
            langEs.classList.toggle('active', prefs.language === 'es');
            langEn.classList.toggle('active', prefs.language === 'en');
        }

        localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
        if (animate) triggerFlash();
        renderSwatches();
        renderFontOptions();
        
        // Sync custom picker value if active
        const customPicker = document.getElementById('customAccentPicker');
        if (customPicker && prefs.customAccent) {
            customPicker.value = prefs.customAccent;
        }
    }

    function renderFontOptions() {
        const sizeOptions = document.querySelectorAll('#sizeSelector .select-option');
        sizeOptions.forEach(opt => {
            opt.classList.toggle('selected', opt.dataset.size === prefs.fontSize);
        });

        const fontOptions = document.querySelectorAll('#fontSelector .select-option');
        fontOptions.forEach(opt => {
            opt.classList.toggle('selected', opt.dataset.font === prefs.fontFamily);
        });
    }

    function renderSwatches() {
        const accentGrid = document.getElementById('accentSwatches');
        if (!accentGrid) return;
        accentGrid.innerHTML = '';
        const isDark = prefs.mode === 'dark';
        const currentAccentIdx = isDark ? prefs.darkAccent : prefs.lightAccent;

        const customPicker = document.getElementById('customAccentPicker');
        if (customPicker) {
            // If custom accent is NOT active, we could reset the picker color, 
            // but usually it's better to keep it as it was if there's no custom color.
            customPicker.classList.toggle('active', !!prefs.customAccent);
        }

        accentColors.forEach((c, i) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'swatch-wrapper';
            const el = document.createElement('div');
            el.className = 'swatch';
            let swatchColor = c.value;
            if (c.name === i18n.monochrome) swatchColor = isDark ? '#ffffff' : '#000000';
            el.style.background = swatchColor;
            el.title = c.name;
            el.addEventListener('click', () => {
                prefs.customAccent = null; // Clear custom color when predefined is selected
                if (isDark) prefs.darkAccent = i; else prefs.lightAccent = i;
                apply(true);
            });
            if (!prefs.customAccent && currentAccentIdx === i) el.classList.add('selected');
            const label = document.createElement('span');
            label.className = 'swatch-name';
            label.textContent = c.name;
            wrapper.appendChild(el);
            wrapper.appendChild(label);
            accentGrid.appendChild(wrapper);
        });

        // ── Render Custom Swatch ──
        const customWrapper = document.createElement('div');
        customWrapper.className = 'swatch-wrapper';
        const customEl = document.createElement('div');
        customEl.className = 'swatch' + (prefs.customAccent ? ' selected' : '');
        
        // Use custom color or rainbow gradient
        customEl.style.background = prefs.customAccent || 'linear-gradient(45deg, #f09433 0%, #e6683c 25%, #dc2743 50%, #cc2366 75%, #bc1888 100%)';
        customEl.title = 'Color personalizado';
        
        customEl.addEventListener('click', () => {
            const picker = document.getElementById('customAccentPicker');
            if (picker) picker.click();
        });
        
        const customLabel = document.createElement('span');
        customLabel.className = 'swatch-name';
        customLabel.textContent = i18n.custom;
        
        // Add a "plus" icon only if no custom color is set
        if (!prefs.customAccent) {
            const icon = document.createElement('i');
            icon.className = 'bi bi-plus-lg';
            icon.style.cssText = 'position:absolute; inset:0; display:flex; align-items:center; justify-content:center; color:white; font-size:1.2rem; text-shadow: 0 1px 3px rgba(0,0,0,0.5); pointer-events:none;';
            customEl.appendChild(icon);
        }

        customWrapper.appendChild(customEl);
        customWrapper.appendChild(customLabel);
        accentGrid.appendChild(customWrapper);

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

    const elModeDark = document.getElementById('modeDark');
    const elModeLight = document.getElementById('modeLight');

    if (elModeDark) {
        elModeDark.addEventListener('click', () => {
            if (prefs.mode !== 'dark') {
                prefs.mode = 'dark';
                apply(true);
            }
        });
    }

    if (elModeLight) {
        elModeLight.addEventListener('click', () => {
            if (prefs.mode !== 'light') {
                prefs.mode = 'light';
                apply(true);
            }
        });
    }

    // ── Language Selectors ──
    const elLangEs = document.getElementById('langEs');
    const elLangEn = document.getElementById('langEn');
    
    if (elLangEs) {
        elLangEs.addEventListener('click', (e) => {
            e.stopPropagation();
            console.log("Switching to Spanish...");
            prefs.language = 'es';
            localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
            
            const url = new URL(window.location.href);
            if (url.searchParams.get('lang') === 'es') {
                window.location.reload();
            } else {
                url.searchParams.set('lang', 'es');
                window.location.href = url.toString();
            }
        });
    }

    if (elLangEn) {
        elLangEn.addEventListener('click', (e) => {
            e.stopPropagation();
            console.log("Switching to English...");
            prefs.language = 'en';
            localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
            
            const url = new URL(window.location.href);
            if (url.searchParams.get('lang') === 'en') {
                window.location.reload();
            } else {
                url.searchParams.set('lang', 'en');
                window.location.href = url.toString();
            }
        });
    }

    document.querySelectorAll('#sizeSelector .select-option').forEach(opt => {
        opt.addEventListener('click', () => {
            prefs.fontSize = opt.dataset.size;
            apply(true);
        });
    });

    document.querySelectorAll('#fontSelector .select-option').forEach(opt => {
        opt.addEventListener('click', () => {
            prefs.fontFamily = opt.dataset.font;
            apply(true);
        });
    });

    const customPicker = document.getElementById('customAccentPicker');
    if (customPicker) {
        if (prefs.customAccent) customPicker.value = prefs.customAccent;
        
        customPicker.addEventListener('input', (e) => {
            prefs.customAccent = e.target.value;
            apply(false); // Applied live without flash
        });
        
        customPicker.addEventListener('change', () => {
            localStorage.setItem('tpv-prefs', JSON.stringify(prefs));
        });
    }

    apply(false);
})();