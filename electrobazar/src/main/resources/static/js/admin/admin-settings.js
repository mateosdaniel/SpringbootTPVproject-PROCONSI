/**
 * admin-settings.js
 * App settings and configuration logic.
 */

function updateAdminPin() {
    const pin = document.getElementById('adminPinInput').value;
    if (!pin || pin.length < 4) {
        showToast('El PIN debe tener al menos 4 dígitos', 'error');
        return;
    }

    fetch('/api/admin/settings/pin', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin: pin })
    }).then(res => {
        if (res.ok) {
            showToast('PIN de administrador actualizado');
            document.getElementById('adminPinInput').value = '';
        } else {
            showToast('Error al actualizar el PIN', 'error');
        }
    });
}

function loadMailSettings() {
    fetch('/api/admin/settings/mail')
        .then(res => res.json())
        .then(settings => {
            document.getElementById('mailHost').value = settings.host || '';
            document.getElementById('mailPort').value = settings.port || '';
            document.getElementById('mailUser').value = settings.username || '';
        });
}

function saveMailSettings() {
    const settings = {
        host: document.getElementById('mailHost').value,
        port: parseInt(document.getElementById('mailPort').value),
        username: document.getElementById('mailUser').value,
        password: document.getElementById('mailPass').value || null
    };

    fetch('/api/admin/settings/mail', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings)
    }).then(res => {
        if (res.ok) {
            showToast('Configuración de correo guardada');
        } else {
            showToast('Error al guardar configuración de correo', 'error');
        }
    });
}

function togglePassword(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;
    input.type = input.type === 'password' ? 'text' : 'password';
}

// Global Exports
window.updateAdminPin = updateAdminPin;
window.loadMailSettings = loadMailSettings;
window.saveMailSettings = saveMailSettings;
window.togglePassword = togglePassword;
