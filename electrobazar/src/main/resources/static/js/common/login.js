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
                document.querySelectorAll('img[alt="Logo"]').forEach(img => img.src = '/icons/favicon-light.svg');
            }
        })();

function showForgotFlow(e) {
    if(e) e.preventDefault();
    document.getElementById('loginForm').style.display = 'none';
    document.getElementById('forgotFlow').style.display = 'block';
    document.getElementById('forgotStep1').style.display = 'block';
    document.getElementById('forgotStep2').style.display = 'none';
    if(document.querySelector('.error-msg')) document.querySelector('.error-msg').style.display = 'none';
}

function showLoginFlow(e) {
    if(e) e.preventDefault();
    document.getElementById('loginForm').style.display = 'block';
    document.getElementById('forgotFlow').style.display = 'none';
}

async function sendResetPin() {
    const email = document.getElementById('resetEmail').value.trim();
    if (!email) {
        alert("Por favor, introduce tu email.");
        return;
    }

    const btn = document.querySelector('#forgotStep1 button');
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Enviando...';

    try {
        const resp = await fetch('/forgot-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });
        
        const data = await resp.json();
        if (data.ok) {
            document.getElementById('forgotStep1').style.display = 'none';
            document.getElementById('forgotStep2').style.display = 'block';
        } else {
            alert(data.message || "Error al enviar el PIN.");
        }
    } catch (err) {
        alert("Error de red. Inténtalo de nuevo.");
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

async function performReset() {
    const email = document.getElementById('resetEmail').value.trim();
    const pin = document.getElementById('resetPin').value.trim();
    const password = document.getElementById('newPassword').value.trim();

    if (!pin || !password) {
        alert("Por favor, rellena todos los campos.");
        return;
    }

    const btn = document.querySelector('#forgotStep2 button');
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Restableciendo...';

    try {
        const resp = await fetch('/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, pin, password })
        });
        
        const data = await resp.json();
        if (data.ok) {
            alert("Contraseña restablecida con éxito. Ya puedes iniciar sesión.");
            location.reload();
        } else {
            alert(data.message || "Error al restablecer la contraseña. El PIN podría ser incorrecto o haber caducado.");
        }
    } catch (err) {
        alert("Error de red. Inténtalo de nuevo.");
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}
