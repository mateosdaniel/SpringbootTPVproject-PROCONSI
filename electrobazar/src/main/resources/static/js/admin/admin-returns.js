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

        // paymentMethod comes already translated from DTO ("Efectivo" / "Tarjeta")
        const isCash = ret.paymentMethod === 'Efectivo';
        const methodIcon = isCash ? 'bi-cash' : 'bi-credit-card';
        const methodLabel = ret.paymentMethod || '—';

        const workerName = ret.workerUsername || 'Sistema';

        // originalNumber comes directly from the DTO
        const origSaleInfo = ret.originalNumber || '—';

        // Abbreviate reason to 40 chars — mirrors Thymeleaf #strings.abbreviate
        const rawReason = ret.reason || '—';
        const reason = rawReason.length > 40 ? rawReason.substring(0, 37) + '...' : rawReason;

        // Mirrors exactly the Thymeleaf tr/td structure
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">${escHtml(ret.returnNumber)}</td>
            <td>${escHtml(origSaleInfo)}</td>
            <td>${formatDateTime(ret.createdAt)}</td>
            <td>${typeBadge}</td>
            <td>${escHtml(reason)}</td>
            <td style="font-weight: 500;"><span>${escHtml(workerName)}</span></td>
            <td>
                <i class="bi ${methodIcon}" style="margin-right:0.3rem"></i>
                <span>${methodLabel}</span>
            </td>
            <td style="font-size:1rem;font-weight:700;color:var(--danger);text-align:right">-${formatDecimal(ret.amount)} €</td>
            <td style="text-align:right">
                <button type="button" class="btn-icon" title="Vista Previa" style="text-decoration:none" onclick="event.stopPropagation(); showDocPreview('/tpv/return-receipt/${ret.id}')">
                    <i class="bi bi-eye" style="color:#3498db;"></i>
                </button>
                <a href="/admin/download/return/${ret.id}" target="_blank" class="btn-icon" title="Imprimir" style="text-decoration:none" onclick="event.stopPropagation();">
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

