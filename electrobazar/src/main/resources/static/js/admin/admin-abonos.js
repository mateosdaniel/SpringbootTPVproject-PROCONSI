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
        clienteId: clienteId.trim(),
        ventaOriginalId: ventaId ? parseInt(ventaId) : null,
        importe: parseFloat(importe),
        tipoAbono: tipo,
        metodoPago: pago,
        motivo: motivo
    };

    fetch('/api/abonos', {
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
            filterAbonos();
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
 * Loads abonos paginated — all or filtered by client.
 * Called on view init and on every filter change.
 */
function filterAbonos() {
    const clienteVal = (document.getElementById('abonoClienteSearch')?.value || '').trim();
    const tbody = document.getElementById('abonosTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4"><span class="spinner-border spinner-border-sm me-2"></span>Cargando...</td></tr>';

    const params = new URLSearchParams();
    if (clienteVal) params.append('cliente', clienteVal);
    params.append('sortBy', 'fecha');
    params.append('sortDir', 'desc');
    params.append('size', '50');

    fetch(`/api/abonos?${params.toString()}`)
        .then(res => res.json())
        .then(data => {
            const list = data.content || [];
            tbody.innerHTML = '';

            const labelEl = document.getElementById('abonoCountLabel');
            if (labelEl) {
                if (clienteVal) {
                    labelEl.textContent = `Mostrando ${data.totalElements ?? list.length} abonos del cliente "${clienteVal}".`;
                } else {
                    labelEl.textContent = `Mostrando ${data.totalElements ?? list.length} abonos en total.`;
                }
            }

            if (list.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No se encontraron abonos.</td></tr>';
                return;
            }

            list.forEach(a => {
                const tr = document.createElement('tr');
                const statusClass = a.estado === 'PENDIENTE' ? 'badge-status-p status-active-p' : 'badge-status-p status-cancelled-p';
                const statusText = a.estado === 'PENDIENTE' ? 'ACT. PAGO' : (a.estado === 'APLICADO' ? 'APLICADO' : 'ANULADO');

                tr.innerHTML = `
                    <td><strong>#${a.id}</strong></td>
                    <td>${new Date(a.fecha).toLocaleString()}</td>
                    <td>${a.cliente ? (a.cliente.idDocumentNumber || a.cliente.taxId || '#' + a.cliente.id) : '--'}</td>
                    <td><span class="text-accent fw-bold">${a.tipoAbono}</span></td>
                    <td>${a.metodoPago}</td>
                    <td class="text-end fw-bold">${parseFloat(a.importe).toFixed(2)} €</td>
                    <td><span class="${statusClass}">${statusText}</span></td>
                    <td class="text-end">
                        <div class="d-flex justify-content-end gap-2">
                             ${a.estado === 'PENDIENTE' ? `
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

    fetch(`/api/abonos/${id}/anular`, {
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

