/**
 * admin-precios.js
 * Price and bulk update management functions.
 */

/**
 * Opens the schedule price modal.
 * @param {number} [id] - Product ID (optional)
 * @param {string} [name] - Product Name (optional, for display)
 * @param {number} [currentPrice] - Current Price (optional, for display)
 */
function openSchedulePriceModal(id, name, currentPrice) {
    const productIdEl = document.getElementById('spProductSelect');
    const searchInput = document.getElementById('spProductSearch');
    const priceInput = document.getElementById('spPrice');
    const dateInput = document.getElementById('spStartdate');
    const labelInput = document.getElementById('spLabel');

    if (!productIdEl) {
        console.error("Required modal elements (spProductSelect) not found.");
        return;
    }

    // Reset selection state
    if (id && name) {
        selectSpProduct({ id, name, price: currentPrice });
    } else {
        clearSpSelection();
    }

    if (priceInput) priceInput.value = '';
    if (dateInput) dateInput.value = '';
    const endDateInput = document.getElementById('spEnddate');
    if (endDateInput) endDateInput.value = '';
    if (labelInput) labelInput.value = '';

    // Initialize/Show modal
    if (window.schedulePriceModal) {
        window.schedulePriceModal.show();
    } else {
        const el = document.getElementById('schedulePriceModal');
        if (el) {
            window.schedulePriceModal = new bootstrap.Modal(el);
            window.schedulePriceModal.show();
        }
    }
}

// Searchable Product Logic
function initSpAutocomplete() {
    const input = document.getElementById('spProductSearch');
    const dropdown = document.getElementById('spProductDropdown');
    const list = document.getElementById('spProductList');
    let timeout = null;

    if (!input || !dropdown) return;

    input.addEventListener('focus', () => {
        if (input.value.trim().length === 0) {
            fetchTopSpProducts();
        } else if (list.children.length > 0) {
            dropdown.style.display = 'block';
        }
    });

    input.addEventListener('input', () => {
        const query = input.value.trim();
        if (query.length === 0) {
            fetchTopSpProducts();
            return;
        }

        if (query.length < 2) {
            dropdown.style.display = 'none';
            return;
        }

        clearTimeout(timeout);
        timeout = setTimeout(() => {
            fetch(`/api/products/search?q=${encodeURIComponent(query)}&size=50`)
                .then(r => r.json())
                .then(data => {
                    renderSpDropdown(data.content || data);
                });
        }, 300);
    });

    // Close on outside click
    document.addEventListener('click', (e) => {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });
}

function fetchTopSpProducts() {
    const list = document.getElementById('spProductList');
    if (list) list.innerHTML = '<div class="p-3 text-center"><span class="spinner-border spinner-border-sm text-warning"></span></div>';
    document.getElementById('spProductDropdown').style.display = 'block';

    fetch('/api/products/bulk-list') // This returns top 100 by rank/sales
        .then(r => r.json())
        .then(data => {
            renderSpDropdown(data);
        }).catch(err => {
            console.error("Error fetching top products", err);
            if (list) list.innerHTML = '<div class="p-3 text-center text-muted">Error al cargar productos sugeridos</div>';
        });
}

function renderSpDropdown(products) {
    const list = document.getElementById('spProductList');
    const dropdown = document.getElementById('spProductDropdown');
    if (!list) return;

    list.innerHTML = '';
    if (!products || products.length === 0) {
        list.innerHTML = '<div class="p-3 text-center text-muted">No se encontraron productos</div>';
    } else {
        products.forEach(p => {
            const item = document.createElement('div');
            item.className = 'search-result-item p-2 border-bottom d-flex align-items-center gap-2';
            item.style.cursor = 'pointer';
            
            const price = typeof p.price === 'number' ? formatDecimal(p.price) : '0,00';
            
            item.innerHTML = `
                <div class="flex-grow-1">
                    <div class="fw-bold small">${p.name}</div>
                    <div class="text-muted" style="font-size: 0.7rem;">${p.categoryName || ''}</div>
                </div>
                <div class="text-end fw-bold text-accent">${price}€</div>
            `;
            
            item.onclick = () => {
                selectSpProduct(p);
                dropdown.style.display = 'none';
            };
            list.appendChild(item);
        });
    }
    dropdown.style.display = 'block';
}

function selectSpProduct(p) {
    document.getElementById('spProductSelect').value = p.id;
    document.getElementById('spProductSearch').value = '';
    document.getElementById('spSelectedProductName').textContent = p.name + (p.price ? ` (${formatDecimal(p.price)}€)` : '');
    document.getElementById('spSelectedProductInfo').style.display = 'block';
    document.getElementById('spProductSearch').style.display = 'none';
}

function clearSpSelection() {
    const select = document.getElementById('spProductSelect');
    if (select) select.value = '';
    const name = document.getElementById('spSelectedProductName');
    if (name) name.textContent = '';
    const info = document.getElementById('spSelectedProductInfo');
    if (info) info.style.display = 'none';
    const search = document.getElementById('spProductSearch');
    if (search) {
        search.style.display = 'block';
        search.value = '';
    }
}

// Initializer
document.addEventListener('DOMContentLoaded', () => {
    initSpAutocomplete();
});

/**
 * Saves the scheduled price via API.
 */
function saveScheduledPrice() {
    const btn = document.querySelector('#schedulePriceModal .btn-save');
    const id = document.getElementById('spProductSelect')?.value;
    const price = document.getElementById('spPrice')?.value;
    const vatRate = document.getElementById('spVatRate')?.value;
    const date = document.getElementById('spStartdate')?.value;
    const endDate = document.getElementById('spEnddate')?.value;
    const label = document.getElementById('spLabel')?.value;

    if (!id || !price || !date) {
        showToast('Producto, precio y fecha son obligatorios', 'error');
        return;
    }

    if (parseFloat(price) < 0) {
        showToast('El precio no puede ser negativo', 'error');
        return;
    }

    // Disable button to prevent double submission
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Guardando...';
    }

    // Format date for LocalDateTime (ensure seconds are present)
    let formattedDate = date;
    if (date && date.length === 16) formattedDate += ':00';
    
    let formattedEndDate = endDate || null;
    if (endDate && endDate.length === 16) formattedEndDate += ':00';

    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch(`/api/product-prices/${id}/schedule`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        },
        body: JSON.stringify({
            price: parseFloat(price),
            vatRate: parseFloat(vatRate),
            startDate: formattedDate,
            endDate: formattedEndDate,
            label: label
        })
    }).then(res => {
        if (res.ok) {
            // Close modal robustly
            const modalEl = document.getElementById('schedulePriceModal');
            const modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl);
            if (modalInstance) modalInstance.hide();
            
            showToast('Precio programado con éxito');
            
            // Clear form
            clearSpSelection();
            
            // Refresh views if they are visible
            setTimeout(() => {
                if (typeof loadFuturePrices === 'function') loadFuturePrices();
                if (typeof loadPriceHistory === 'function') loadPriceHistory();
            }, 300);
        } else {
            res.json().then(data => {
                showToast('Error: ' + (data.message || 'Error al programar precio'), 'error');
            }).catch(() => showToast('Error al programar precio', 'error'));
        }
    }).catch(err => {
        console.error('Error scheduling price:', err);
        showToast('Error de conexión', 'error');
    }).finally(() => {
        // Re-enable button
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-calendar-check me-1"></i> Programar Precio';
        }
    });
}

function loadFuturePrices() {
    fetch('/api/product-prices/future')
        .then(res => res.json())
        .then(data => {
            const items = Array.isArray(data) ? data : (data.content || []);
            renderFuturePricesTable(items);
            
            const labelEl = document.getElementById('futurePriceCountLabel');
            if (labelEl) {
                const search = document.getElementById('futurePriceFilterSearch')?.value || '';
                if (search) {
                    labelEl.textContent = `Mostrando ${items.length} precios programados coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los precios programados.';
                }
            }
        }).catch(err => console.error("Error loading future prices:", err));
}

function filterFuturePrices() {
    // For now simple refresh. Backend search could be added if needed.
    loadFuturePrices();
}

function renderFuturePricesTable(items) {
    const tbody = document.getElementById('futurePricesBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (!items || items.length === 0) {
        const noScheduledText = document.getElementById('admin-js-translations')?.getAttribute('data-no-scheduled-prices') || 'No hay cambios de precio programados.';
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">${noScheduledText}</td></tr>`;
        return;
    }

    items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${item.productName}</strong></td>
            <td>${formatDecimal(item.price)} €</td>
            <td>${(item.vatRate * 100).toFixed(0)}%</td>
            <td><span class="badge bg-info text-dark">${formatDateTime(item.startDate)}</span></td>
            <td>${formatDateTime(item.endDate) || '—'}</td>
            <td>${item.label || ''}</td>
            <td class="text-end">
                <button class="btn-icon btn-delete" onclick="deletePendingPrice(${item.id})"><i class="bi bi-trash"></i></button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetFuturePriceFilters() {
    const input = document.getElementById('futurePriceFilterSearch');
    if (input) input.value = '';
    loadFuturePrices();
}

/**
 * Loads price history for the selected product in the history tab.
 */
function loadPriceHistory() {
    const productId = document.getElementById('historialProductSelect')?.value;
    const tbody = document.getElementById('priceHistoryBody');
    if (!tbody) return;

    if (!productId) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">Selecciona un producto para ver su historial.</td></tr>';
        return;
    }

    tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4"><span class="spinner-border spinner-border-sm"></span></td></tr>';

    fetch(`/api/product-prices/${productId}/history`)
        .then(res => res.json())
        .then(data => {
            tbody.innerHTML = '';
            const items = Array.isArray(data) ? data : (data.content || []);
            if (items.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">Sin historial de precios.</td></tr>';
                return;
            }

            items.forEach(item => {
                const tr = document.createElement('tr');
                if (item.currentlyActive) tr.classList.add('table-success');

                const variation = item.priceChange !== null 
                    ? `<span class="${item.priceChange >= 0 ? 'text-success' : 'text-danger'}">${item.priceChange >= 0 ? '+' : ''}${formatDecimal(item.priceChange)} € (${item.priceChangePct}%)</span>`
                    : '—';

                tr.innerHTML = `
                    <td>${formatDecimal(item.price)} €</td>
                    <td>${(item.vatRate * 100).toFixed(0)}%</td>
                    <td>${formatDateTime(item.startDate)}</td>
                    <td>${formatDateTime(item.endDate) || '—'}</td>
                    <td>${item.label || ''}</td>
                    <td>${variation}</td>
                    <td>
                        <span class="badge ${item.currentlyActive ? 'bg-success' : (new Date(item.startDate) > new Date() ? 'bg-info' : 'bg-secondary')}">
                            ${item.currentlyActive ? 'Activo' : (new Date(item.startDate) > new Date() ? 'Pendiente' : 'Pasado')}
                        </span>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        }).catch(err => {
            console.error("Error loading price history:", err);
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-danger">Error al cargar el historial.</td></tr>';
        });
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
    if (tabId === 'historial') loadPriceHistory();
}

// Bulk Updates Logic (re-using existing structure but fixing endpoints)
let bulkSelectedProducts = new Set();
let bulkProductsData = [];
let bulkDefaultData = [];
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
        el.textContent = bulkSelectAllAbsolute ? "TODOS" : bulkSelectedProducts.size;
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

function selectAllAbsoluteBulk() {
    bulkSelectAllAbsolute = true;
    bulkSelectedProducts.clear();
    renderBulkProductList(bulkProductsData);
    updateBulkSelectedCount();
    showToast('Seleccionados todos los productos del sistema');
}

function selectBulkByCategory() {
    const category = document.getElementById('bulkCategoryFilter').value;
    if (!category) {
        showToast('Selecciona una categoría primero', 'warning');
        return;
    }
    bulkSelectAllAbsolute = false;
    let count = 0;
    bulkProductsData.forEach(p => {
        if (p.categoryName === category) {
            bulkSelectedProducts.add(p.id);
            count++;
        }
    });
    renderBulkProductList(bulkProductsData);
    showToast(`${count} productos de ${category} marcados`);
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

    if (isNaN(value)) {
        showToast('Debes indicar un valor válido', 'error');
        return;
    }

    const taskId = 'bulk_' + Date.now();
    const payload = {
        productIds: Array.from(bulkSelectedProducts),
        applyToAll: bulkSelectAllAbsolute,
        taskId: taskId,
        effectiveDate: date || new Date().toISOString(),
        label: label || 'Actualización masiva',
        tariffIds: Array.from(document.querySelectorAll('.tariff-bulk-checkbox:checked')).map(cb => parseInt(cb.value))
    };

    if (type === 'percentage') payload.percentage = value;
    else payload.fixedAmount = value;

    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch('/api/products/bulk-update', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        },
        body: JSON.stringify(payload)
    }).then(res => {
        if (res.ok) {
            showToast('Actualización masiva programada');
            bulkSelectedProducts.clear();
            renderBulkProductList(bulkProductsData);
        } else {
            showToast('Error al aplicar actualización', 'error');
        }
    });
}

function openIpcUpdateModal() {
    if (window.ipcUpdateModal) window.ipcUpdateModal.show();
}

function updateIpcPreview() {
    const val = document.getElementById('ipcPercentage').value;
    const preview = document.getElementById('ipcPreviewText');
    if (preview) preview.textContent = `Aumento del ${val}% en todos los productos.`;
}

function applyIpcConfirm() {
    const val = document.getElementById('ipcPercentage').value;
    if (!confirm(`¿Seguro que quieres aumentar TODAS las tarifas un ${val}%?`)) return;
    
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch('/api/admin/prices/apply-ipc', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        },
        body: JSON.stringify({ percentage: parseFloat(val) })
    }).then(res => {
        if (res.ok) {
            showToast('IPC aplicado con éxito');
            if (window.ipcUpdateModal) window.ipcUpdateModal.hide();
            setTimeout(() => location.reload(), 1500);
        } else {
            showToast('Error al aplicar IPC', 'error');
        }
    });
}

function deletePendingPrice(id) {
    if (!confirm('¿Eliminar este cambio programado?')) return;
    
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch('/api/product-prices/pending/' + id, { 
        method: 'DELETE',
        headers: { 
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        }
    }).then(res => {
        if (res.ok) {
            showToast('Cambio eliminado');
            loadFuturePrices();
        } else {
            showToast('Error al eliminar', 'error');
        }
    });
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
window.handleBulkProductToggle = handleBulkProductToggle;
window.updateBulkSelectedCount = updateBulkSelectedCount;
window.selectAllBulkProducts = selectAllBulkProducts;
window.selectAllAbsoluteBulk = selectAllAbsoluteBulk;
window.selectBulkByCategory = selectBulkByCategory;
window.toggleBulkPriceFields = toggleBulkPriceFields;
window.applyBulkPriceUpdate = applyBulkPriceUpdate;
window.filterBulkProductList = filterBulkProductList;
window.openIpcUpdateModal = openIpcUpdateModal;
window.updateIpcPreview = updateIpcPreview;
window.applyIpcConfirm = applyIpcConfirm;
window.deletePendingPrice = deletePendingPrice;
window.clearSpSelection = clearSpSelection;
