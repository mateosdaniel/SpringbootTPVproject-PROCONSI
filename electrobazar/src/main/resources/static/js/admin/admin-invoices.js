/**
 * admin-invoices.js
 * Invoice and session (cash closure) management functions.
 */

let currentSalesPage = 0;
let salesTotalPages = 1;
let salesFetchController = null; // AbortController for cancelling in-flight requests

async function fetchSalesPage(page) {
    if (page < 0) return;
    currentSalesPage = page;

    const search = (document.getElementById('invoiceFilterSearch').value || '').trim();
    const type = document.getElementById('invoiceFilterType').value;
    const method = document.getElementById('invoiceFilterMethod').value;
    const date = document.getElementById('invoiceFilterdate').value;
    const sortBy = (document.getElementById('invoiceSortBy') || {}).value || 'createdAt';
    const sortDir = (document.getElementById('invoiceSortDir') || {}).value || 'desc';

    const url = `/api/admin/sales?page=${page}&search=${encodeURIComponent(search)}&type=${type}&method=${method}&date=${date}&sortBy=${sortBy}&sortDir=${sortDir}`;

    const tbody = document.getElementById('invoicesTableBody');
    if (tbody) tbody.style.opacity = '0.5';


    if (salesFetchController) salesFetchController.abort();
    salesFetchController = new AbortController();

    try {
        const response = await fetch(url, { signal: salesFetchController.signal });
        if (!response.ok) throw new Error('Error de red');
        const data = await response.json();


        currentSalesPage = data.currentPage;
        salesTotalPages = data.totalPages || 0;

        renderSalesTable(data.content, data.hasMore);

        const labelEl = document.getElementById('invoiceCountLabel');
        if (labelEl) {
            if (search || type || method || date) {
                if (data.totalElements !== undefined) {
                    labelEl.textContent = `Mostrando ${data.totalElements} ventas/facturas coincidentes.`;
                } else {
                    labelEl.textContent = `Mostrando resultados para "${search}".`;
                }
            } else {
                labelEl.textContent = 'Mostrando todas las ventas y facturas.';
            }
        }

        updateSalesPaginationUI(data);
    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('Error fetching sales:', error);
        showToast('Error al cargar ventas', 'error');
    } finally {
        if (tbody) tbody.style.opacity = '1';
    }
}

function renderSalesTable(sales, hasMore) {
    const tbody = document.getElementById('invoicesTableBody');
    if (!tbody) return;

    // 1. LIMPIEZA TOTAL: Esto garantiza que no veas resultados viejos
    tbody.innerHTML = '';

    // 2. GESTIÓN DE CERO RESULTADOS
    if (!sales || sales.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center py-5">
                    <div class="py-4">
                        <i class="bi bi-search" style="font-size: 2rem; color: var(--text-muted); opacity: 0.5;"></i>
                        <p class="mt-3 mb-0" style="color: var(--text-muted)">No se han encontrado ventas con esos criterios.</p>
                    </div>
                </td>
            </tr>`;
        return;
    }

    // 3. DIBUJADO DE LAS FILAS (Tu código original recuperado)
    sales.forEach(sale => {
        const isCancelled = sale.status === 'CANCELLED';
        const tr = document.createElement('tr');
        tr.className = 'invoice-row';
        tr.style.cursor = 'pointer';
        if (isCancelled) tr.style.opacity = '0.6';

        tr.onclick = () => window.location.href = `/admin/sale/${sale.id}`;

        const typeLabel = sale.type === 'factura' ? 'Factura' : 'Ticket';
        const typeClass = sale.type === 'factura' ? 'yes' : 'no';
        const cancelBadge = isCancelled ? `
            <span class="badge mb-1 d-block" style="background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 6px; padding: 0.25rem 0.6rem; font-size: 0.75rem; font-weight: 600;">
                <i class="bi bi-x-circle me-1"></i>anulada
            </span>` : '';

        let customerHtml = `<span style="color:var(--text-muted)">— Consumidor Final —</span>`;
        if (sale.customerName) {
            customerHtml = `
                <div>
                    <strong>${escHtml(sale.customerName)}</strong>
                    <div style="font-size:0.75rem;color:var(--text-muted)">${escHtml(sale.customerTaxId || '-')}</div>
                </div>`;
        }

        const isCash = sale.paymentMethod === 'CASH';
        const methodIcon = isCash ? 'bi-cash' : 'bi-credit-card';
        const methodLabel = isCash ? 'Efectivo' : 'Tarjeta';

        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">${escHtml(sale.displayId || '-')}</td>
            <td>${formatDateTime(sale.createdAt)}</td>
            <td>
                ${cancelBadge}
                <span class="badge-active ${typeClass}">${typeLabel}</span>
            </td>
            <td>${customerHtml}</td>
            <td style="font-weight: 500;">${escHtml(sale.workerUsername || 'Sistema')}</td>
            <td>
                <i class="bi ${methodIcon}" style="margin-right:0.3rem"></i>
                <span>${methodLabel}</span>
            </td>
            <td style="font-size:1rem;font-weight:700;color:var(--text-main);text-align:right">
                ${formatDecimal(sale.totalAmount)} €
            </td>
            <td style="text-align:right">
                <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                    ${!isCancelled ? `
                    <button class="btn-icon danger" title="Anular" onclick="event.stopPropagation(); cancelSale(${sale.id})">
                        <i class="bi bi-x-circle"></i>
                    </button>` : ''}
                    <button type="button" class="btn-icon" title="Vista Previa" onclick="event.stopPropagation(); showDocPreview('/tpv/receipt/${sale.id}')">
                        <i class="bi bi-eye" style="color:#3498db;"></i>
                    </button>
                    <a href="/tpv/receipt/${sale.id}?autoPrint=true" target="_blank" class="btn-icon" title="Imprimir" onclick="event.stopPropagation();">
                        <i class="bi bi-printer" style="color:#e74c3c;"></i>
                    </a>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });

    // 4. MENSAJE DE LÍMITE (Top 15)
    if (hasMore) {
        const warningTr = document.createElement('tr');
        warningTr.innerHTML = `
            <td colspan="8" class="text-center py-3" style="background: rgba(var(--accent-rgb), 0.05); color: var(--text-muted); font-style: italic; border-top: 1px dashed var(--border);">
                <i class="bi bi-info-circle me-2"></i>
                Mostrando los mejores resultados para optimizar la búsqueda. Sé más específico si no encuentras lo que buscas.
            </td>
        `;
        tbody.appendChild(warningTr);
    }
}

function updateSalesPaginationUI(data) {
    const info = document.getElementById('salesPaginationInfo');
    if (info) {
        info.textContent = `Página ${data.number + 1} de ${data.totalPages} (${data.totalElements} ventas en total)`;
    }

    // Support individual page detail spans
    const currentEl = document.getElementById('salesCurrentPage');
    const totalEl = document.getElementById('salesTotalPages');
    if (currentEl) currentEl.textContent = data.number + 1;
    if (totalEl) totalEl.textContent = data.totalPages;

    const prevBtn = document.getElementById('salesPagePrev');
    const nextBtn = document.getElementById('salesPageNext');
    if (prevBtn) prevBtn.disabled = data.first;
    if (nextBtn) nextBtn.disabled = data.last;
}

function changeSalesPage(delta) {
    const next = currentSalesPage + delta;
    if (next >= 0 && next < salesTotalPages) {
        fetchSalesPage(next);
    }
}

function jumpToSalesPage(val) {
    const input = document.getElementById('salesJumpInput');
    const pageVal = val !== undefined ? parseInt(val) : (input ? parseInt(input.value) : 1);
    const target = pageVal - 1;
    if (target >= 0 && target < salesTotalPages) {
        fetchSalesPage(target);
    } else {
        showToast('Página inválida', 'warning');
    }
}

const filterInvoices = debounce(function () {
    fetchSalesPage(0);
}, 200);

function resetInvoiceFilters() {
    document.getElementById('invoiceFilterSearch').value = '';
    document.getElementById('invoiceFilterType').value = '';
    document.getElementById('invoiceFilterMethod').value = '';
    document.getElementById('invoiceFilterdate').value = '';
    fetchSalesPage(0);
}

function cancelSale(id) {
    if (!confirm('¿Seguro que quieres anular esta venta? Esta operación no se puede deshacer.')) return;

    fetch(`/api/admin/sales/${id}/cancel`, { method: 'POST' })
        .then(res => {
            if (res.ok) {
                showToast('Venta anulada correctamente');
                fetchSalesPage(currentSalesPage);
            } else {
                showToast('Error al anular la venta', 'error');
            }
        });
}

function filterCashClosures() {
    const date = document.getElementById('cashFilterdate').value;
    const workerId = document.getElementById('cashFilterWorker').value;
    const sortBy = document.getElementById('cashFilterSortBy')?.value || 'id';
    const sortDir = document.getElementById('cashFilterSortDir')?.value || 'desc';

    fetch(`/api/admin/cash-closings?date=${date}&worker=${encodeURIComponent(workerId)}&sortBy=${sortBy}&sortDir=${sortDir}`)
        .then(res => res.json())
        .then(data => {
            renderCashClosuresTable(data.content || data);

            const labelEl = document.getElementById('cashCountLabel');
            if (labelEl) {
                const date = document.getElementById('cashFilterdate').value;
                const workerId = document.getElementById('cashFilterWorker').value;
                if (date || workerId) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || data).length} cierres de caja coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los cierres de caja.';
                }
            }
        });
}

function renderCashClosuresTable(items) {
    const tbody = document.getElementById('cashClosuresTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    const list = Array.isArray(items) ? items : (items.content || []);

    if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4">No se encontraron cierres de caja.</td></tr>';
        return;
    }

    list.forEach(item => {
        const tr = document.createElement('tr');
        tr.className = 'cash-row';
        tr.style.cursor = 'pointer';
        tr.onclick = () => window.location.href = `/admin/cash-register/${item.id}`;

        // Difference Badge logic
        const diff = item.difference || 0;
        let diffBadge = '';
        if (diff === 0) {
            diffBadge = `<span class="badge-active yes">Cuadrado (0.00)</span>`;
        } else if (diff > 0) {
            diffBadge = `<span class="badge-active yes">+${formatDecimal(diff)}</span>`;
        } else {
            diffBadge = `<span class="badge-active no">${formatDecimal(diff)}</span>`;
        }

        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${item.id}</td>
            <td>${formatDateTime(item.openingTime)}</td>
            <td>${item.closedAt ? formatDateTime(item.closedAt) : '<span class="badge bg-warning text-dark" style="font-size:0.7rem">Abierta</span>'}</td>
            <td style="font-weight:600;text-align:right">${formatDecimal(item.totalCalculated)} €</td>
            <td style="font-weight:600;text-align:right">${formatDecimal(item.closingBalance)} €</td>
            <td style="text-align:right">${diffBadge}</td>
            <td style="font-weight: 500;">${escHtml(item.workerUsername || 'Sistema')}</td>
            <td style="text-align:right">
                <a href="/admin/download/cash-close/${item.id}" target="_blank" class="btn-icon" title="Descargar PDF" onclick="event.stopPropagation();" style="text-decoration: none;">
                    <i class="bi bi-file-earmark-pdf" style="color:#e74c3c;"></i>
                </a>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetCashFilters() {
    document.getElementById('cashFilterdate').value = '';
    document.getElementById('cashFilterWorker').value = '';
    filterCashClosures();
}

function openCashRegisterDetail(id) {
    window.open(`/admin/cash-closures/${id}/detail`, '_blank');
}

// Global Exports
window.fetchSalesPage = fetchSalesPage;
window.renderSalesTable = renderSalesTable;
window.updateSalesPaginationUI = updateSalesPaginationUI;
window.changeSalesPage = changeSalesPage;
window.jumpToSalesPage = jumpToSalesPage;
window.filterInvoices = filterInvoices;
window.resetInvoiceFilters = resetInvoiceFilters;
window.cancelSale = cancelSale;
window.filterCashClosures = filterCashClosures;
window.renderCashClosuresTable = renderCashClosuresTable;
window.resetCashFilters = resetCashFilters;
window.openCashRegisterDetail = openCashRegisterDetail;
