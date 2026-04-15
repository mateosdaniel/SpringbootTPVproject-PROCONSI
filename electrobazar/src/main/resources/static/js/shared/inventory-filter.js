/* inventory-filter.js */

var debounceTimer;
let productFetchController = null;
let categoryFetchController = null;

// Función Anti-rebote para no spamear el servidor mientras el usuario teclea
function debounceSharedFilter() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        const activeTab = document.querySelector('#mgmtTabs .nav-link.active');
        if (!activeTab) return;
        
        if (activeTab.id === 'categories-tab') {
            runSharedBackendCategoryFilter();
        } else if (activeTab.id === 'products-tab') {
            runSharedBackendFilter();
        }
    }, 350);
}

// Aislamiento estricto de pestañas
document.addEventListener('DOMContentLoaded', () => {
    const mgmtTabs = document.getElementById('mgmtTabs');
    if (mgmtTabs) {
        mgmtTabs.addEventListener('shown.bs.tab', (event) => {
            const targetId = event.target.id;
            if (targetId === 'products-tab') {
                // Limpiar categorías para evitar mezclas
                const catBody = document.getElementById('categoriesTableBody');
                if (catBody) catBody.innerHTML = '';
                const catPag = document.getElementById('categoriesPagination');
                if (catPag) catPag.innerHTML = '';
                
                runSharedBackendFilter();
            } else if (targetId === 'categories-tab') {
                // Limpiar productos para evitar mezclas
                const prodBody = document.getElementById('productsTableBody');
                if (prodBody) prodBody.innerHTML = '';
                const prodPag = document.getElementById('productsPagination');
                if (prodPag) prodPag.innerHTML = '';
                
                runSharedBackendCategoryFilter();
            }
        });
    }
});

var sharedInventoryI18n = Object.assign({
    lowStock: 'Stock Bajo',
    yes: 'Sí',
    no: 'No',
    noItems: 'No se encontraron productos con esos filtros.',
    noCats: 'No hay categorías que coincidan.',
    actions_edit: 'Editar',
    actions_delete: 'Eliminar'
}, window.sharedInventoryI18n || {});

function getSharedInvLocale() {
    try {
        const prefs = JSON.parse(localStorage.getItem('tpv-prefs'));
        return (prefs && prefs.language) ? prefs.language : 'es';
    } catch (e) {
        return 'es';
    }
}

function runSharedBackendFilter(page = 0) {
    // Guard: Only run if we are actually on the products tab
    const activeTab = document.querySelector('#mgmtTabs .nav-link.active');
    if (activeTab && activeTab.id !== 'products-tab') return;

    // Tomamos el buscador del header del Admin o el buscador del Fragment (Productos-categorias)
    const globalSearch = document.getElementById('sharedFilterSearch');
    const fragSearch = document.getElementById('fragmentFilterSearch');
    const search = (globalSearch ? globalSearch.value.trim() : '') || (fragSearch ? fragSearch.value.trim() : '');

    const category = document.getElementById('sharedFilterCategory').value;
    const stock = document.getElementById('sharedFilterStock').value;
    let active = document.getElementById('sharedFilterActive').value;
    let unitId = (document.getElementById('sharedFilterUnitId') || {}).value || '';

    const sortBy = (document.getElementById('sharedFilterSortBy') || {}).value || 'id';
    const sortDir = (document.getElementById('sharedFilterSortDir') || {}).value || 'asc';

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (category) queryParams.append('category', category);
    if (stock) queryParams.append('stock', stock);
    if (active) queryParams.append('active', active);
    if (unitId) queryParams.append('unitId', unitId);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);
    queryParams.append('page', page);
    queryParams.append('size', 50);

    const tbody = document.getElementById('productsTableBody');
    if (tbody) tbody.style.opacity = '0.5';

    if (productFetchController) productFetchController.abort();
    productFetchController = new AbortController();

    fetch(`/api/admin/products?${queryParams.toString()}`, { signal: productFetchController.signal })
        .then(res => res.json())
        .then(data => {
            const items = data.content || data;
            renderSharedProductsTable(items);
            
            const labelEl = document.getElementById('productCountLabel');
            if (labelEl) {
                if (search || category || stock || active || unitId) {
                    labelEl.textContent = `Mostrando ${data.totalElements || items.length} productos coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todas las fichas de productos.';
                }
            }

            if (data.totalPages !== undefined) {
                renderInventoryPagination('productsPagination', data, runSharedBackendFilter);
            }
        })
        .catch(err => {
            if (err.name === 'AbortError') return;
            console.error("Error filtrando productos combinados:", err);
        })
        .finally(() => {
            if (tbody) tbody.style.opacity = '1';
        });
}

function resetSharedBackendFilter() {
    const globalSearch = document.getElementById('sharedFilterSearch');
    const fragSearch = document.getElementById('fragmentFilterSearch');
    if (globalSearch) globalSearch.value = '';
    if (fragSearch) fragSearch.value = '';

    document.getElementById('sharedFilterCategory').value = '';
    document.getElementById('sharedFilterStock').value = '';
    document.getElementById('sharedFilterActive').value = '';
    if (document.getElementById('sharedFilterUnitId')) document.getElementById('sharedFilterUnitId').value = '';
    
    const sortByEl = document.getElementById('sharedFilterSortBy');
    const sortDirEl = document.getElementById('sharedFilterSortDir');
    if (sortByEl) sortByEl.value = 'id';
    if (sortDirEl) sortDirEl.value = 'asc';

    const btn = document.getElementById('btnSharedLowStock');
    if (btn) {
        btn.classList.remove('btn-danger');
        btn.classList.add('btn-outline-danger');
    }

    runSharedBackendFilter();
}

function sharedFilterToggleLowStock() {
    const stockSelect = document.getElementById('sharedFilterStock');
    const btn = document.getElementById('btnSharedLowStock');
    if (!stockSelect || !btn) return;

    if (stockSelect.value === 'low') {
        stockSelect.value = '';
        btn.classList.remove('btn-danger');
        btn.classList.add('btn-outline-danger');
    } else {
        stockSelect.value = 'low';
        btn.classList.remove('btn-outline-danger');
        btn.classList.add('btn-danger');
    }
    runSharedBackendFilter();
}

/**
 * Encargada de redibujar la tabla del DOM con los datos nuevos que retorna la API.
 */
function renderSharedProductsTable(products) {
    const tbody = document.getElementById('productsTableBody');
    if (!tbody) return;

    // Limpiar el contador de categorías para evitar confusión si estuviera visible
    const catLabel = document.getElementById('categoryCountLabel');
    if (catLabel) catLabel.textContent = '';

    tbody.innerHTML = ''; 

    if (!products || products.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" class="text-center p-4 text-muted">${sharedInventoryI18n.noItems}</td></tr>`;
        return;
    }

    const locale = getSharedInvLocale();
    const isEn = locale === 'en';

    products.forEach(p => {
        const name = isEn && p.nameEn ? p.nameEn : (p.nameEs || p.name);
        const description = isEn && p.descriptionEn ? p.descriptionEn : (p.descriptionEs || p.description);
        
        const unitDecimals = (p.measurementUnit && p.measurementUnit.decimalPlaces !== undefined) ? p.measurementUnit.decimalPlaces : 0;
        const symbol = (p.measurementUnit && p.measurementUnit.symbol) ? p.measurementUnit.symbol : '—';
        
        const priceDecimals = p.priceDecimals !== undefined ? p.priceDecimals : Math.max(2, unitDecimals);
        const formattedPrice = typeof formatDecimal === 'function' ? formatDecimal(p.price, priceDecimals, priceDecimals) : (p.price || 0).toFixed(priceDecimals) + ' €';
        const formattedStock = (p.stock === 0 || p.stock === null || p.stock === undefined) ? '0' : p.stock.toFixed(unitDecimals);
        
        const stockStyle = p.stock < 5 ? 'fw-bold text-danger' : '';
        const badgeLowStock = p.stock < 5 ? `<span class="badge-stock-low ms-1">${sharedInventoryI18n.lowStock}</span>` : '';
        
        // Category can also be multilingual
        let catName = '—';
        if (p.categoryName) {
            catName = p.categoryName;
        } else if (p.category) {
            catName = isEn && p.category.nameEn ? p.category.nameEn : (p.category.nameEs || p.category.name);
        }

        const imgHtml = p.imageUrl
            ? `<img src="${p.imageUrl}" class="thumb" alt="">`
            : `<div class="thumb-placeholder"><i class="bi bi-image"></i></div>`;
        const activeBadge = p.active
            ? `<span class="badge-active yes">${sharedInventoryI18n.yes}</span>`
            : `<span class="badge-active no">${sharedInventoryI18n.no}</span>`;

        const ivaRate = p.vatRate !== undefined ? p.vatRate : (p.taxRate ? p.taxRate.vatRate : null);
        const ivaDisplay = ivaRate != null ? (ivaRate * 100).toFixed(0) + '%' : '—';
        const descTruncated = description ? (description.length > 60 ? description.substring(0, 57) + '...' : description) : '—';
        const escapedName = name ? name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';

        let tr = document.createElement('tr');
        tr.className = 'product-row';
        tr.setAttribute('data-iva', ivaRate);
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${p.id}</td>
            <td>${imgHtml}</td>
            <td>
                <strong>${name}</strong>
                <div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px">${descTruncated}</div>
            </td>
            <td>${ivaDisplay}</td>
            <td style="font-size:1rem;font-weight:700;color:var(--accent);text-align:right">${formattedPrice}</td>
            <td>
                <div class="d-flex flex-column align-items-center">
                    <span class="${stockStyle}">${formattedStock}</span>
                    ${badgeLowStock}
                </div>
            </td>
            <td style="text-align:center"><span style="font-size:0.8rem;font-weight:600;color:var(--text-muted)">${symbol}</span></td>
            <td><span style="font-size:0.82rem;padding:0.2rem 0.5rem;border-radius:6px;background:var(--surface);color:var(--text-muted)">${catName}</span></td>
            <td>${activeBadge}</td>
            <td style="text-align:right">
                <div class="d-flex gap-1 justify-content-end">
                    <button class="btn-icon" title="${sharedInventoryI18n.actions_edit}" onclick="openProductModal(${p.id})"><i class="bi bi-pencil"></i></button>
                    <button class="btn-icon danger" title="${sharedInventoryI18n.actions_delete}" onclick="deleteProduct(${p.id}, '${escapedName}')"><i class="bi bi-trash"></i></button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function runSharedBackendCategoryFilter(page = 0) {
    // Guard: Only run if we are actually on the categories tab
    const activeTab = document.querySelector('#mgmtTabs .nav-link.active');
    if (activeTab && activeTab.id !== 'categories-tab') return;

    const globalSearch = document.getElementById('sharedFilterSearch');
    const catSearch = document.getElementById('categoryFilterSearch');
    const search = (globalSearch ? globalSearch.value.trim() : '') || (catSearch ? catSearch.value.trim() : '');

    const sortBy = (document.getElementById('categoryFilterSortBy') || {}).value || 'id';
    const sortDir = (document.getElementById('categoryFilterSortDir') || {}).value || 'asc';

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);
    queryParams.append('page', page);
    queryParams.append('size', 50);

    const tbody = document.getElementById('categoriesTableBody');
    if (tbody) tbody.style.opacity = '0.5';

    if (categoryFetchController) categoryFetchController.abort();
    categoryFetchController = new AbortController();

    fetch(`/api/admin/categories?${queryParams.toString()}`, { signal: categoryFetchController.signal })
        .then(res => res.json())
        .then(data => {
            const items = data.content || data;
            renderSharedCategoriesTable(items);

            const labelEl = document.getElementById('categoryCountLabel');
            if (labelEl) {
                if (search) {
                    labelEl.textContent = `Mostrando ${data.totalElements || items.length} categorías coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todas las categorías.';
                }
            }

            if (data.totalPages !== undefined) {
                renderInventoryPagination('categoriesPagination', data, runSharedBackendCategoryFilter);
            }
        })
        .catch(err => {
            if (err.name === 'AbortError') return;
            console.error("Error filtrando categorias combinadas:", err);
        })
        .finally(() => {
            if (tbody) tbody.style.opacity = '1';
        });
}

function renderInventoryPagination(containerId, pageData, callbackName) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const totalPages = pageData.totalPages || 0;
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    const currentPage = pageData.currentPage || pageData.number || 0;
    const callbackStr = (typeof callbackName === 'function') ? callbackName.name : callbackName;

    let html = `
        <div class="pagination-wrap mt-3">
            <button class="pagination-btn" onclick="${callbackStr}(0)" ${currentPage === 0 ? 'disabled' : ''} title="Primera página">
                <i class="bi bi-chevron-double-left"></i>
            </button>

            <button class="pagination-btn" onclick="${callbackStr}(${currentPage - 1})" ${currentPage === 0 ? 'disabled' : ''} title="Página anterior">
                <i class="bi bi-chevron-left"></i>
            </button>

            <div class="pagination-jump">
                <input type="number" value="${currentPage + 1}" min="1" max="${totalPages}"
                    onkeydown="if(event.key === 'Enter') { 
                        let p = parseInt(this.value)-1; 
                        if(p >= 0 && p < ${totalPages}) ${callbackStr}(p); 
                    }">
                <span class="small text-muted">de ${totalPages}</span>
            </div>

            <button class="pagination-btn" onclick="${callbackStr}(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''} title="Página siguiente">
                <i class="bi bi-chevron-right"></i>
            </button>

            <button class="pagination-btn" onclick="${callbackStr}(${totalPages - 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''} title="Última página">
                <i class="bi bi-chevron-double-right"></i>
            </button>
        </div>
    `;
    container.innerHTML = html;
}

function renderSharedCategoriesTable(categories) {
    const tbody = document.getElementById('categoriesTableBody');
    if (!tbody) return;

    // Limpiar el contador de productos
    const prodLabel = document.getElementById('productCountLabel');
    if (prodLabel) prodLabel.textContent = '';

    tbody.innerHTML = '';

    if (!categories || categories.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center p-4 text-muted">${sharedInventoryI18n.noCats}</td></tr>`;
        return;
    }

    const locale = getSharedInvLocale();
    const isEn = locale === 'en';

    categories.forEach(c => {
        const name = isEn && c.nameEn ? c.nameEn : (c.nameEs || c.name);
        const desc = isEn && c.descriptionEn ? c.descriptionEn : (c.descriptionEs || c.description || '—');
        const escapedName = name ? name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';
        const activeBadge = c.active
            ? `<span class="badge-active yes">${sharedInventoryI18n.yes}</span>`
            : `<span class="badge-active no">${sharedInventoryI18n.no}</span>`;

        let tr = document.createElement('tr');
        tr.className = 'category-row';
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${c.id}</td>
            <td><strong>${name}</strong></td>
            <td>${desc}</td>
            <td>${activeBadge}</td>
            <td style="text-align:right">
                <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                    <button class="btn-icon" title="${sharedInventoryI18n.actions_edit}" onclick="openCategoryModal(${c.id})"><i class="bi bi-pencil"></i></button>
                    <button class="btn-icon danger" title="${sharedInventoryI18n.actions_delete}" onclick="deleteCategory(${c.id}, '${escapedName}')"><i class="bi bi-trash"></i></button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}
