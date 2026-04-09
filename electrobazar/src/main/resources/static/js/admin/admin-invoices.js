/**
 * admin-invoices.js
 * Invoice and session (cash closure) management functions.
 */

let currentSalesPage = 0;
let salesTotalPages = 1;

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

    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error('Error de red');
        const data = await response.json();
        
        salesTotalPages = data.totalPages;
        renderSalesTable(data.content);
        updateSalesPaginationUI(data);
    } catch (error) {
        console.error('Error fetching sales:', error);
        showToast('Error al cargar ventas', 'error');
    } finally {
        if (tbody) tbody.style.opacity = '1';
    }
}

function renderSalesTable(sales) {
    const tbody = document.getElementById('invoicesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (sales.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center py-4">No se encontraron ventas con los filtros aplicados.</td></tr>';
        return;
    }

    sales.forEach(sale => {
        const isCancelled = sale.status === 'CANCELLED';
        const tr = document.createElement('tr');
        if (isCancelled) tr.style.opacity = '0.6';

        tr.innerHTML = `
            <td><strong>#${sale.id}</strong></td>
            <td>${formatDateTime(sale.createdAt)}</td>
            <td>${escHtml(sale.customerName || 'Cliente General')}</td>
            <td>${escHtml(sale.paymentMethod)}</td>
            <td class="fw-bold">${formatDecimal(sale.totalAmount)} €</td>
            <td>${isCancelled ? '<span class="badge bg-danger">Anulada</span>' : '<span class="badge bg-success">Completada</span>'}</td>
            <td class="text-end">
                <button class="btn-icon" onclick="printInvoice(${sale.id})" title="Imprimir"><i class="bi bi-printer"></i></button>
                ${!isCancelled ? `<button class="btn-icon danger" onclick="cancelSale(${sale.id})" title="Anular"><i class="bi bi-x-circle"></i></button>` : ''}
            </td>
        `;
        tbody.appendChild(tr);
    });
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

function jumpToSalesPage() {
    const input = document.getElementById('salesJumpInput');
    if (!input) return;
    const val = parseInt(input.value) - 1;
    if (val >= 0 && val < salesTotalPages) {
        fetchSalesPage(val);
    } else {
        showToast('Página inválida', 'warning');
    }
}

function filterInvoices() {
    fetchSalesPage(0);
}

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
    
    fetch(`/api/admin/cash-closings?date=${date}&workerId=${workerId}`)
        .then(res => res.json())
        .then(data => {
            renderCashClosuresTable(data.content || data);
        });
}

function renderCashClosuresTable(items) {
    const tbody = document.getElementById('cashClosuresTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!items || items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4">No se encontraron cierres de caja.</td></tr>';
        return;
    }

    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${formatDateTime(item.openingTime)}</td>
            <td>${item.closedAt ? formatDateTime(item.closedAt) : '<span class="badge bg-warning text-dark">Abierta</span>'}</td>
            <td>${escHtml(item.workerUsername)}</td>
            <td class="fw-bold">${formatDecimal(item.totalCalculated)} €</td>
            <td class="${item.difference < 0 ? 'text-danger' : 'text-success'}">${formatDecimal(item.difference)} €</td>
            <td class="text-end">
                <button class="btn-icon" onclick="openCashRegisterDetail(${item.id})"><i class="bi bi-eye"></i></button>
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
