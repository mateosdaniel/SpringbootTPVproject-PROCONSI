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
    const tbody = document.getElementById('futurePricesBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay cambios de precio programados.</td></tr>';
        return;
    }

    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${item.productName}</strong></td>
            <td>${formatDecimal(item.price)} €</td>
            <td>${item.vatRate * 100}%</td>
            <td><span class="badge bg-info text-dark">${formatDateTime(item.startDate || item.scheduledDate)}</span></td>
            <td>${formatDateTime(item.endDate) || '—'}</td>
            <td>${item.label || ''}</td>
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
    if (tabId === 'futuros') loadFuturePrices();
}

// Bulk Updates Logic
let bulkSelectedProducts = new Set();
let bulkProductsData = [];

let bulkDefaultData = []; // Store the initial top 100
let bulkSearchTimeout = null;
let bulkSelectAllAbsolute = false;

function loadBulkProducts() {
    bulkSelectAllAbsolute = false;
    fetch('/api/products/bulk-list')
        .then(res => res.json())
        .then(data => {
            bulkDefaultData = data;
            bulkProductsData = data;
            renderBulkProductList(data);
        });
}

function selectAllAbsoluteBulk() {
    bulkSelectAllAbsolute = true;
    bulkSelectedProducts.clear(); 
    renderBulkProductList(bulkProductsData);
    updateBulkSelectedCount();
    showToast("Has seleccionado TODOS los productos que coinciden con el filtro actual.");
}

function renderBulkProductList(products) {
    const container = document.getElementById('bulkProductList');
    if (!container) return;
    container.innerHTML = '';

    const limit = 200;
    const itemsToShow = (products || []).slice(0, limit);

    itemsToShow.forEach(p => {
        const isSelected = bulkSelectAllAbsolute || bulkSelectedProducts.has(p.id);
        const div = document.createElement('div');
        div.className = 'bulk-item d-flex align-items-center p-2 mb-1 rounded hover-surface' + (isSelected ? ' selected' : '');
        div.style.cursor = 'pointer';
        div.style.border = '1px solid var(--border)';
        // Forzar fondo oscuro si está seleccionado o hover para que resalte
        div.onclick = () => handleBulkProductToggle(p.id);
        div.innerHTML = `
            <div class="form-check me-3 mb-0">
                <input class="form-check-input mt-0" type="checkbox" ${isSelected ? 'checked' : ''} onclick="event.stopPropagation(); handleBulkProductToggle(${p.id})">
            </div>
            <div class="bulk-item-info flex-grow-1">
                <div class="fw-bold" style="font-size:0.9rem;">${p.name}</div>
                <div class="small">${p.categoryName || 'Sin categoría'}</div>
            </div>
            <div class="text-end ps-3">
                <span class="badge fw-500">
                    ${formatDecimal(p.price)} €
                </span>
            </div>
        `;
        container.appendChild(div);
    });

    if (products && products.length > limit) {
        const info = document.createElement('div');
        info.className = 'text-center p-2 small text-muted';
        info.innerHTML = `<i class="bi bi-info-circle me-1"></i> Mostrando solo los primeros ${limit} productos. Usa el buscador para filtrar.`;
        container.appendChild(info);
    }

    const labelEl = document.getElementById('bulkProductCountLabel');
    if (labelEl) {
        const totalCount = products ? products.length : 0;
        labelEl.textContent = `Mostrando ${Math.min(totalCount, limit)} de ${totalCount} productos en esta vista.`;
    }

    updateBulkSelectedCount();
}

function handleBulkProductToggle(id) {
    bulkSelectAllAbsolute = false;
    if (bulkSelectedProducts.has(id)) {
        bulkSelectedProducts.delete(id);
    } else {
        bulkSelectedProducts.add(id);
    }
    renderBulkProductList(bulkProductsData);
}

function filterBulkProductList() {
    if (bulkSearchTimeout) clearTimeout(bulkSearchTimeout);
    bulkSearchTimeout = setTimeout(() => {
        const query = document.getElementById('bulkProductSearch').value.toLowerCase();
        const category = document.getElementById('bulkCategoryFilter').value;
        
        if (!query && !category) {
            bulkProductsData = bulkDefaultData;
            renderBulkProductList(bulkProductsData);
            return;
        }

        const url = new URL('/api/products/filter', window.location.origin);
        if (query) url.searchParams.set('search', query);
        if (category) url.searchParams.set('category', category);
        url.searchParams.set('size', 100);

        fetch(url)
            .then(res => res.json())
            .then(data => {
                // Map from the paginated API response to our simpler DTO
                // data is Page object, so we use data.content
                bulkProductsData = (data.content || []).map(p => ({
                    id: p.id,
                    name: p.nameEs || p.name,
                    price: p.price,
                    categoryName: p.category ? (p.category.nameEs || p.category.name) : null
                }));
                renderBulkProductList(bulkProductsData);
            });
    }, 300);
}

function updateBulkSelectedCount() {
    const el = document.getElementById('bulkSelectedCount');
    if (el) {
        if (bulkSelectAllAbsolute) {
            el.textContent = "TODOS (según filtros)";
        } else {
            el.textContent = bulkSelectedProducts.size;
        }
    }
}

function selectAllBulkProducts(select) {
    bulkSelectAllAbsolute = false;
    if (select) {
        bulkProductsData.forEach(p => bulkSelectedProducts.add(p.id));
    } else {
        bulkSelectedProducts.clear();
    }
    renderBulkProductList(bulkProductsData);
}

function toggleBulkPriceFields() {
    const type = document.getElementById('bulkPriceType').value;
    const label = document.getElementById('bulkPriceValueLabel');
    if (label) label.textContent = (type === 'percentage') ? 'Porcentaje (%)' : 'Cantidad Fija (€)';
}

function applyBulkPriceUpdate() {
    if (bulkSelectedProducts.size === 0 && !bulkSelectAllAbsolute) {
        showToast('Selecciona al menos un producto', 'error');
        return;
    }

    const type = document.getElementById('bulkPriceType').value;
    const value = parseFloat(document.getElementById('bulkPriceValue').value);
    const date = document.getElementById('bulkEffectivedate').value;
    const label = document.getElementById('bulkLabel').value;
    const search = document.getElementById('bulkProductSearch').value;
    const categoryName = document.getElementById('bulkCategoryFilter').value;

    if (isNaN(value)) {
        showToast('Debes indicar un valor válido', 'error');
        return;
    }

    // Get selected tariffs
    const tariffIds = Array.from(document.querySelectorAll('.tariff-bulk-checkbox:checked')).map(cb => parseInt(cb.value));

    // Generate a unique task ID for progress tracking
    const taskId = 'bulk_' + Date.now();
    
    const payload = {
        productIds: bulkSelectAllAbsolute ? [] : Array.from(bulkSelectedProducts),
        applyToAll: bulkSelectAllAbsolute,
        search: bulkSelectAllAbsolute ? search : null,
        categoryName: bulkSelectAllAbsolute ? categoryName : null,
        taskId: taskId,
        effectiveDate: date || new Date(Date.now() + 60000).toISOString(),
        label: label || 'Actualización masiva',
        tariffIds: tariffIds
    };

    if (type === 'percentage') {
        payload.percentage = value;
    } else {
        payload.fixedAmount = value;
    }

    // UI Updates: show progress, disable button
    const btn = document.getElementById('btnApplyBulkUpdate');
    const progressContainer = document.getElementById('bulkUpdateProgressContainer');
    if (btn) btn.disabled = true;
    if (progressContainer) progressContainer.style.display = 'block';
    
    const pollInterval = setInterval(() => {
        fetch(`/api/products/bulk-progress/${taskId}`)
            .then(res => res.json())
            .then(progress => {
                const bar = document.getElementById('bulkUpdateProgressBar');
                const percentText = document.getElementById('bulkUpdatePercent');
                const statusText = document.getElementById('bulkUpdateStatusText');
                
                if (bar) bar.style.width = progress.percentage + '%';
                if (percentText) percentText.textContent = progress.percentage + '%';
                if (statusText) statusText.textContent = progress.message;
            }).catch(e => console.error("Error polling progress", e));
    }, 2000);

    fetch('/api/products/bulk-update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).then(res => {
        clearInterval(pollInterval);
        if (btn) btn.disabled = false;
        
        if (res.ok) {
            showToast('Actualización masiva programada con éxito');
            if (progressContainer) progressContainer.style.display = 'none';
            // Clear selection
            bulkSelectedProducts.clear();
            bulkSelectAllAbsolute = false;
            renderBulkProductList(bulkProductsData);
            document.getElementById('bulkResults').style.display = 'block';
            document.getElementById('bulkResultsText').textContent = 'Se han programado cambios.';
        } else {
            showToast('Error al aplicar la actualización', 'error');
            if (progressContainer) progressContainer.style.display = 'none';
        }
    }).catch(err => {
        clearInterval(pollInterval);
        if (btn) btn.disabled = false;
        if (progressContainer) progressContainer.style.display = 'none';
        showToast('Error de red al aplicar actualización', 'error');
    });
}


function selectBulkByCategory() {
    const categoryName = document.getElementById('bulkCategoryFilter').value;
    if (!categoryName) {
        showToast('Selecciona una categoría primero', 'info');
        return;
    }
    
    let count = 0;
    bulkProductsData.forEach(p => {
        if (p.categoryName === categoryName) {
            bulkSelectedProducts.add(p.id);
            count++;
        }
    });
    
    showToast(`${count} productos seleccionados de la categoría ${categoryName}`);
    filterBulkProductList(); // Re-render with filter
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
                    <td class="text-end fw-bold">${parseFloat(item.price).toFixed(2)} €</td>
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
