/**
 * admin-abonos.js
 * Modern Abonos (credits/refunds) management.
 */

function openAbonoModal() {
    const form = document.getElementById('abonoForm');
    if (form) form.reset();
    if (window.abonoModal) window.abonoModal.show();
}

/**
 * Saves a new credit/abono
 */
function saveAbono() {
    const clienteId = document.getElementById('abonoFormClienteId').value;
    const ventaId = document.getElementById('abonoFormVentaId').value;
    const importe = document.getElementById('abonoFormImporte').value;
    const tipo = document.getElementById('abonoFormTipo').value;
    const pago = document.getElementById('abonoFormPago').value;
    const motivo = document.getElementById('abonoFormMotivo').value;

    if (!clienteId || !importe || !tipo || !pago) {
        if (typeof showToast === 'function') showToast(getAdminI18n('errorSave') || 'Campos obligatorios', 'error');
        return;
    }

    const payload = {
        customerId: parseInt(clienteId),
        originalSaleId: ventaId ? parseInt(ventaId) : null,
        amount: parseFloat(importe),
        type: tipo,
        paymentMethod: pago,
        reason: motivo
    };

    fetch('/api/admin/abonos', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        },
        body: JSON.stringify(payload)
    })
    .then(async res => {
        if (res.ok) {
            if (window.abonoModal) window.abonoModal.hide();
            if (typeof showToast === 'function') showToast(getAdminI18n('successSave') || 'Éxito', 'success');
            // Refresh search if we have a client ID
            const searchVal = document.getElementById('abonoClienteSearch').value;
            if (searchVal) filterAbonos();
        } else {
            const err = await res.text();
            if (typeof showToast === 'function') showToast(err || 'Error', 'error');
        }
    })
    .catch(err => {
        console.error(err);
        if (typeof showToast === 'function') showToast(getAdminI18n('errorConnection') || 'Error de conexión', 'error');
    });
}

/**
 * Filters the list by Client ID
 */
function filterAbonos() {
    const customerId = document.getElementById('abonoClienteSearch').value;
    const tbody = document.getElementById('abonosTableBody');
    if (!tbody) return;

    if (!customerId) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">Introduce un ID de Cliente</td></tr>';
        return;
    }

    tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4"><span class="spinner-border spinner-border-sm me-2"></span>Cargando...</td></tr>';

    fetch(`/api/admin/abonos/customer/${customerId}`)
        .then(res => res.json())
        .then(data => {
            tbody.innerHTML = '';
            
            const labelEl = document.getElementById('abonoCountLabel');
            if (labelEl) {
                if (customerId) {
                    labelEl.textContent = `Mostrando ${data.length} abonos del cliente #${customerId}.`;
                } else {
                    labelEl.textContent = 'Mostrando abonos del cliente.';
                }
            }

            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No se encontraron abonos para este cliente</td></tr>';
                return;
            }

            data.forEach(a => {
                const tr = document.createElement('tr');
                const statusClass = a.status === 'ACTIVE' ? 'badge-status-p status-active-p' : 'badge-status-p status-cancelled-p';
                const statusText = a.status === 'ACTIVE' ? 'ACTIVO' : 'ANULADO';

                tr.innerHTML = `
                    <td><strong>#${a.id}</strong></td>
                    <td>${new Date(a.createdAt).toLocaleString()}</td>
                    <td>${a.customerId}</td>
                    <td><span class="text-accent fw-bold">${a.type}</span></td>
                    <td>${a.paymentMethod}</td>
                    <td class="text-end fw-bold">${parseFloat(a.amount).toFixed(2)} €</td>
                    <td><span class="${statusClass}">${statusText}</span></td>
                    <td class="text-end">
                        <div class="d-flex justify-content-end gap-2">
                             ${a.status === 'ACTIVE' ? `
                                <button class="btn-icon danger" onclick="anularAbono(${a.id})" title="Anular">
                                    <i class="bi bi-trash"></i>
                                </button>
                             ` : ''}
                        </div>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">Error al cargar datos: ${err.message}</td></tr>`;
        });
}

/**
 * Cancels an abono
 */
function anularAbono(id) {
    if (!confirm('¿Seguro que desea anular este abono?')) return;

    fetch(`/api/admin/abonos/${id}/cancel`, {
        method: 'POST',
        headers: { 
            [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
        }
    })
    .then(res => {
        if (res.ok) {
            if (typeof showToast === 'function') showToast('Abono anulado correctamente', 'success');
            filterAbonos();
        } else {
            if (typeof showToast === 'function') showToast('No se pudo anular el abono', 'error');
        }
    });
}

// Global exports
window.openAbonoModal = openAbonoModal;
window.saveAbono = saveAbono;
window.filterAbonos = filterAbonos;
window.anularAbono = anularAbono;
