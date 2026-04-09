/**
 * admin-abonos.js
 * Abonos (credits/refunds) management functions.
 */

function openAbonoModal() {
    document.getElementById('abonoForm').reset();
    abonoModal.show();
}

function saveAbono() {
    const customerId = document.getElementById('abonoCustomerId').value;
    const amount = document.getElementById('abonoAmount').value;
    const reason = document.getElementById('abonoReason').value;

    if (!customerId || !amount) {
        showToast('Cliente e importe son obligatorios', 'error');
        return;
    }

    fetch('/api/admin/abonos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            customerId: parseInt(customerId),
            amount: parseFloat(amount),
            reason: reason
        })
    }).then(res => {
        if (res.ok) {
            abonoModal.hide();
            showToast('Abono creado con éxito');
            filterAbonos();
        } else {
            showToast('Error al crear abono', 'error');
        }
    });
}

function filterAbonos() {
    const customerId = document.getElementById('abonoFilterSearch').value;
    if (!customerId) return;

    fetch(`/api/admin/abonos?customerId=${customerId}`)
        .then(res => res.json())
        .then(data => {
            const tbody = document.getElementById('abonosTableBody');
            if (!tbody) return;
            tbody.innerHTML = '';
            data.forEach(a => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${formatDateTime(a.createdAt)}</td>
                    <td>${formatDecimal(a.amount)} €</td>
                    <td>${escHtml(a.reason || '—')}</td>
                    <td>${a.status === 'ACTIVE' ? '<span class="badge bg-success">Activo</span>' : '<span class="badge bg-danger">Anulado</span>'}</td>
                    <td class="text-end">
                        ${a.status === 'ACTIVE' ? `<button class="btn-icon danger" onclick="anularAbono(${a.id})"><i class="bi bi-x-circle"></i></button>` : ''}
                    </td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function anularAbono(id) {
    if (!confirm('¿Anular este abono?')) return;
    fetch(`/api/admin/abonos/${id}/anular`, { method: 'POST' })
        .then(res => {
            if (res.ok) {
                showToast('Abono anulado');
                filterAbonos();
            }
        });
}

function loadAbonos() {
    // Initial load logic if needed, or just clear/prepare view
    const tbody = document.getElementById('abonosTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">Seleccione un cliente para ver sus abonos</td></tr>';
}

// Global Exports
window.openAbonoModal = openAbonoModal;
window.saveAbono = saveAbono;
window.filterAbonos = filterAbonos;
window.anularAbono = anularAbono;
window.loadAbonos = loadAbonos;
