/**
 * admin-precios.js
 * Price and bulk update management functions.
 */

function openSchedulePriceModal(id, name, currentPrice) {
    document.getElementById('scheduleProductId').value = id;
    document.getElementById('scheduleProductName').textContent = name;
    document.getElementById('scheduleCurrentPrice').textContent = formatDecimal(currentPrice) + ' €';
    document.getElementById('scheduleNewPrice').value = '';
    document.getElementById('scheduleDate').value = '';
    
    schedulePriceModal.show();
}

function saveScheduledPrice() {
    const id = document.getElementById('scheduleProductId').value;
    const price = document.getElementById('scheduleNewPrice').value;
    const date = document.getElementById('scheduleDate').value;

    if (!price || !date) {
        showToast('Precio y fecha son obligatorios', 'error');
        return;
    }

    fetch('/api/products/schedule-price', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            productId: parseInt(id),
            newPrice: parseFloat(price),
            scheduledDate: date
        })
    }).then(res => {
        if (res.ok) {
            schedulePriceModal.hide();
            showToast('Precio programado con éxito');
            loadFuturePrices();
        } else {
            showToast('Error al programar precio', 'error');
        }
    });
}

function loadFuturePrices() {
    fetch('/api/products/future-prices')
        .then(res => res.json())
        .then(data => {
            renderFuturePricesTable(data);
            
            const labelEl = document.getElementById('futurePriceCountLabel');
            if (labelEl) {
                const search = document.getElementById('futurePriceFilterSearch')?.value || '';
                if (search) {
                    labelEl.textContent = `Mostrando ${data.length} precios programados coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los precios programados.';
                }
            }
        });
}

function filterFuturePrices() {
    // Basic client-side filtering or re-fetch with params
    loadFuturePrices();
}

function renderFuturePricesTable(items) {
    const tbody = document.getElementById('futurePricesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">No hay cambios de precio programados.</td></tr>';
        return;
    }

    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${item.productName}</strong></td>
            <td>${formatDecimal(item.oldPrice)} €</td>
            <td class="text-success fw-bold">${formatDecimal(item.newPrice)} €</td>
            <td><span class="badge bg-info text-dark">${formatDateTime(item.scheduledDate)}</span></td>
            <td class="text-end">
                <button class="btn-icon danger" onclick="deletePendingPrice(${item.id})"><i class="bi bi-trash"></i></button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetFuturePriceFilters() {
    loadFuturePrices();
}

function loadPriceHistory(productId) {
    // Implementation for price history
}

function showPreciosTab(tabId) {
    document.querySelectorAll('.precios-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.precios-pane').forEach(p => p.style.display = 'none');
    
    const tab = document.getElementById(tabId + 'Tab');
    if (tab) tab.classList.add('active');
    const pane = document.getElementById(tabId + 'Pane');
    if (pane) pane.style.display = 'block';

    if (tabId === 'bulk') loadBulkProducts();
    if (tabId === 'future') loadFuturePrices();
}

// Bulk Updates Logic
let bulkSelectedProducts = new Set();
let bulkProductsData = [];

function loadBulkProducts() {
    fetch('/api/products/bulk-list')
        .then(res => res.json())
        .then(data => {
            bulkProductsData = data;
            renderBulkProductList(data);
        });
}

function renderBulkProductList(products) {
    const container = document.getElementById('bulkProductList');
    if (!container) return;
    container.innerHTML = '';

    products.forEach(p => {
        const div = document.createElement('div');
        div.className = 'bulk-item' + (bulkSelectedProducts.has(p.id) ? ' selected' : '');
        div.onclick = () => handleBulkProductToggle(p.id);
        div.innerHTML = `
            <div class="form-check">
                <input class="form-check-input" type="checkbox" ${bulkSelectedProducts.has(p.id) ? 'checked' : ''} onclick="event.stopPropagation(); handleBulkProductToggle(${p.id})">
            </div>
            <div class="bulk-item-info">
                <strong>${p.name}</strong>
                <small>${p.categoryName || 'Sin categoría'}</small>
            </div>
            <div class="ms-auto">${formatDecimal(p.price)} €</div>
        `;
        container.appendChild(div);
    });
    const labelEl = document.getElementById('bulkProductCountLabel');
    if (labelEl) {
        const query = document.getElementById('bulkProductSearch')?.value || '';
        const category = document.getElementById('bulkCategoryFilter')?.value || '';
        if (query || category) {
            labelEl.textContent = `Mostrando ${products.length} productos coincidentes.`;
        } else {
            labelEl.textContent = 'Mostrando todos los productos disponibles.';
        }
    }

    updateBulkSelectedCount();
}

function handleBulkProductToggle(id) {
    if (bulkSelectedProducts.has(id)) {
        bulkSelectedProducts.delete(id);
    } else {
        bulkSelectedProducts.add(id);
    }
    renderBulkProductList(bulkProductsData);
}

function updateBulkSelectedCount() {
    const el = document.getElementById('bulkSelectedCount');
    if (el) el.textContent = bulkSelectedProducts.size;
}

function selectAllBulkProducts(select) {
    if (select) {
        bulkProductsData.forEach(p => bulkSelectedProducts.add(p.id));
    } else {
        bulkSelectedProducts.clear();
    }
    renderBulkProductList(bulkProductsData);
}

function toggleBulkPriceFields() {
    const type = document.getElementById('bulkUpdateType').value;
    document.getElementById('bulkFixedPriceField').style.display = (type === 'FIXED') ? 'block' : 'none';
    document.getElementById('bulkPercentageField').style.display = (type === 'PERCENTAGE' || type === 'MARGIN') ? 'block' : 'none';
}

function applyBulkPriceUpdate() {
    if (bulkSelectedProducts.size === 0) {
        showToast('Selecciona al menos un producto', 'error');
        return;
    }
    // Implement bulk save API call
}

function filterBulkProductList() {
    const query = document.getElementById('bulkSearch').value.toLowerCase();
    const filtered = bulkProductsData.filter(p => p.name.toLowerCase().includes(query));
    renderBulkProductList(filtered);
}

function selectBulkByCategory(categoryId) {
    bulkProductsData.forEach(p => {
        if (!categoryId || p.categoryId == categoryId) {
            bulkSelectedProducts.add(p.id);
        }
    });
    renderBulkProductList(bulkProductsData);
}

function openIpcUpdateModal() {
    ipcUpdateModal.show();
}

function updateIpcPreview() {
    const val = document.getElementById('ipcPercentage').value;
    const preview = document.getElementById('ipcPreviewText');
    if (preview) preview.textContent = `Aumento del ${val}% en todos los productos.`;
}

function applyIpcConfirm() {
    const val = document.getElementById('ipcPercentage').value;
    if (!confirm(`¿Seguro que quieres aumentar TODAS las tarifas un ${val}%?`)) return;
    
    fetch('/api/admin/prices/apply-ipc', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ percentage: parseFloat(val) })
    }).then(res => {
        if (res.ok) {
            showToast('IPC aplicado con éxito');
            ipcUpdateModal.hide();
            setTimeout(() => location.reload(), 1500);
        } else {
            showToast('Error al aplicar IPC', 'error');
        }
    });
}

function toggleTariffEditMode(enabled) {
    // Logic for tariff edit mode
}

function openApplyPricesModal() {
    if (window.applyPricesModal) {
        loadPendingChangesList();
        window.applyPricesModal.show();
    }
}

function loadPendingChangesList() {
    // This would ideally collect changes from the "Editable" matrix (cell status)
    // For now, let's mock it or clear it if no logic is provided for the matrix yet
    const tbody = document.getElementById('pendingChangesList');
    const countEl = document.getElementById('pendingChangesCount');
    if (!tbody || !countEl) return;
    
    tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted">No se han detectado cambios manuales en la matriz.</td></tr>';
    countEl.textContent = '0';
}

function openPriceChangesHistoryModal() {
    if (window.priceChangesHistoryModal) {
        loadPendingPriceChanges();
        loadPastPriceChanges();
        window.priceChangesHistoryModal.show();
    }
}

function loadPendingPriceChanges() {
    fetch('/api/products/future-prices')
        .then(res => res.json())
        .then(data => {
            const tbody = document.getElementById('tablePendingPrices')?.querySelector('tbody');
            if (!tbody) return;
            tbody.innerHTML = '';
            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No hay cambios pendientes</td></tr>';
                return;
            }
            data.forEach(item => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${new Date(item.scheduledDate).toLocaleString()}</td>
                    <td>${item.productName}</td>
                    <td>${item.tariffName || 'BASE'}</td>
                    <td class="text-end fw-bold">${parseFloat(item.newPrice).toFixed(2)} €</td>
                    <td class="text-center">
                        <button class="btn-icon danger" onclick="deletePendingPrice(${item.id})"><i class="bi bi-trash"></i></button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function loadPastPriceChanges() {
    // Past changes logic if backend supports it
}

function submitBulkPriceUpdate() {
    // Implementation for submitting bulk updates from the matrix
    showToast('Función de actualización masiva en desarrollo', 'info');
}

function deletePendingPrice(id) {
    if (!confirm('¿Eliminar este cambio programado?')) return;
    fetch('/api/products/future-prices/' + id, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Cambio eliminado');
                loadFuturePrices();
            } else {
                showToast('Error al eliminar', 'error');
            }
        });
}

function renderBulkPagination() {
    // Implementation if needed
}

// Global Exports
window.openSchedulePriceModal = openSchedulePriceModal;
window.saveScheduledPrice = saveScheduledPrice;
window.loadFuturePrices = loadFuturePrices;
window.filterFuturePrices = filterFuturePrices;
window.renderFuturePricesTable = renderFuturePricesTable;
window.resetFuturePriceFilters = resetFuturePriceFilters;
window.loadPriceHistory = loadPriceHistory;
window.showPreciosTab = showPreciosTab;
window.loadBulkProducts = loadBulkProducts;
window.renderBulkProductList = renderBulkProductList;
window.renderBulkPagination = renderBulkPagination;
window.handleBulkProductToggle = handleBulkProductToggle;
window.updateBulkSelectedCount = updateBulkSelectedCount;
window.selectAllBulkProducts = selectAllBulkProducts;
window.toggleBulkPriceFields = toggleBulkPriceFields;
window.applyBulkPriceUpdate = applyBulkPriceUpdate;
window.filterBulkProductList = filterBulkProductList;
window.selectBulkByCategory = selectBulkByCategory;
window.openIpcUpdateModal = openIpcUpdateModal;
window.updateIpcPreview = updateIpcPreview;
window.applyIpcConfirm = applyIpcConfirm;
window.toggleTariffEditMode = toggleTariffEditMode;
window.openApplyPricesModal = openApplyPricesModal;
window.submitBulkPriceUpdate = submitBulkPriceUpdate;
window.openPriceChangesHistoryModal = openPriceChangesHistoryModal;
window.loadPendingPriceChanges = loadPendingPriceChanges;
window.loadPastPriceChanges = loadPastPriceChanges;
window.deletePendingPrice = deletePendingPrice;
