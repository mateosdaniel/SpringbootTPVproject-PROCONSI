/**
 * admin-returns.js
 * Returns history management functions.
 */

function filterReturns() {
    const search = document.getElementById('returnFilterSearch').value.trim();
    const date = document.getElementById('returnFilterDate').value;
    
    fetch(`/api/admin/returns?search=${search}&date=${date}`)
        .then(res => res.json())
        .then(data => {
            renderReturnsTable(data);
        });
}

function renderReturnsTable(items) {
    const tbody = document.getElementById('returnsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4">No se encontraron devoluciones.</td></tr>';
        return;
    }

    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>#${item.id}</td>
            <td>${formatDateTime(item.createdAt)}</td>
            <td>${escHtml(item.productName)}</td>
            <td>${item.quantity}</td>
            <td class="fw-bold">${formatDecimal(item.amount)} €</td>
            <td>${escHtml(item.reason || '—')}</td>
        `;
        tbody.appendChild(tr);
    });
}

function resetReturnFilters() {
    document.getElementById('returnFilterSearch').value = '';
    document.getElementById('returnFilterDate').value = '';
    filterReturns();
}

// Global Exports
window.filterReturns = filterReturns;
window.renderReturnsTable = renderReturnsTable;
window.resetReturnFilters = resetReturnFilters;
