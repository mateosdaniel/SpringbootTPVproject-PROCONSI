/**
 * admin-returns.js
 * Returns history management functions.
 */

function filterReturns() {
    const search = document.getElementById('returnFilterSearch').value.trim();
    const method = document.getElementById('returnFilterMethod').value;
    const date = document.getElementById('returnFilterdate').value;
    const sortBy = document.getElementById('returnFilterSortBy')?.value || 'createdAt';
    const sortDir = document.getElementById('returnFilterSortDir')?.value || 'desc';

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (method) params.append('method', method);
    if (date) params.append('date', date);
    params.append('sortBy', sortBy);
    params.append('sortDir', sortDir);

    fetch(`/api/admin/returns?${params.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderReturnsTable(data.content || data);

            const labelEl = document.getElementById('returnCountLabel');
            if (labelEl) {
                if (search || method || date) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || data).length} devoluciones coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todas las devoluciones.';
                }
            }
        })
        .catch(err => console.error("Error filtering returns:", err));
}

function renderReturnsTable(items) {
    const tbody = document.getElementById('returnsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const list = Array.isArray(items) ? items : (items.content || []);

    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4" style="color:var(--text-muted)">No se encontraron devoluciones.</td></tr>';
        return;
    }

    list.forEach(ret => {
        const tr = document.createElement('tr');
        tr.className = 'return-row';
        tr.style.cursor = 'pointer';
        tr.onclick = () => window.location.href = `/admin/return/${ret.id}`;

        const typeBadge = ret.type === 'TOTAL' 
            ? '<span class="badge-active yes">Total</span>' 
            : '<span class="badge-active no">Parcial</span>';

        const isCash = ret.paymentMethod === 'CASH';
        const methodIcon = isCash ? 'bi-cash' : 'bi-credit-card';
        const methodLabel = isCash ? 'Efectivo' : 'Tarjeta';

        const workerName = ret.workerName || 'Sistema';
        
        // Orig sale info
        let origSaleInfo = '#' + (ret.originalSaleId || '?');
        if (ret.originalSaleDisplayId) origSaleInfo = ret.originalSaleDisplayId;

        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">${ret.returnNumber}</td>
            <td>${origSaleInfo}</td>
            <td>${formatDateTime(ret.createdAt)}</td>
            <td>${typeBadge}</td>
            <td>${escHtml(ret.reason || '—')}</td>
            <td style="font-weight: 500;">${escHtml(workerName)}</td>
            <td>
                <i class="bi ${methodIcon}" style="margin-right:0.3rem"></i>
                <span>${methodLabel}</span>
            </td>
            <td style="font-size:1rem;font-weight:700;color:var(--danger);text-align:right">
                -${formatDecimal(ret.totalRefunded)} €
            </td>
            <td style="text-align:right">
                <button type="button" class="btn-icon" title="Vista Previa" onclick="event.stopPropagation(); showDocPreview('/tpv/return-receipt/${ret.id}')">
                    <i class="bi bi-eye" style="color:#3498db;"></i>
                </button>
                <a href="/admin/download/return/${ret.id}" target="_blank" class="btn-icon" title="Imprimir" onclick="event.stopPropagation();">
                    <i class="bi bi-printer" style="color:#e74c3c;"></i>
                </a>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetReturnFilters() {
    document.getElementById('returnFilterSearch').value = '';
    document.getElementById('returnFilterMethod').value = '';
    document.getElementById('returnFilterdate').value = '';
    filterReturns();
}

// Global Exports
window.filterReturns = filterReturns;
window.renderReturnsTable = renderReturnsTable;
window.resetReturnFilters = resetReturnFilters;

