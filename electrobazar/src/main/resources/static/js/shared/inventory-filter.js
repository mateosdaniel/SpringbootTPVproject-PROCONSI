/* inventory-filter.js */

var debounceTimer;

// Función Anti-rebote para no spamear el servidor mientras el usuario teclea
function debounceSharedFilter() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        const catTab = document.getElementById('categories-tab');
        if (catTab && catTab.classList.contains('active')) {
            runSharedBackendCategoryFilter();
        } else {
            runSharedBackendFilter();
        }
    }, 350);
}

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

function runSharedBackendFilter() {
    // Tomamos el buscador del header del Admin o el buscador del Fragment (Productos-categorias)
    const globalSearch = document.getElementById('sharedFilterSearch');
    const fragSearch = document.getElementById('fragmentFilterSearch');
    const search = (globalSearch ? globalSearch.value.trim() : '') || (fragSearch ? fragSearch.value.trim() : '');

    const category = document.getElementById('sharedFilterCategory').value;
    const stock = document.getElementById('sharedFilterStock').value;
    let active = document.getElementById('sharedFilterActive').value;

    const sortBy = (document.getElementById('sharedFilterSortBy') || {}).value || 'id';
    const sortDir = (document.getElementById('sharedFilterSortDir') || {}).value || 'asc';

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (category) queryParams.append('category', category);
    if (stock) queryParams.append('stock', stock);
    if (active) queryParams.append('active', active);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/products?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            // Check if it's the new paginated API format
            const items = data.content ? data.content : data;
            renderSharedProductsTable(items);
        })
        .catch(err => console.error("Error filtrando productos combinados:", err));
}

function resetSharedBackendFilter() {
    const globalSearch = document.getElementById('sharedFilterSearch');
    const fragSearch = document.getElementById('fragmentFilterSearch');
    if (globalSearch) globalSearch.value = '';
    if (fragSearch) fragSearch.value = '';

    document.getElementById('sharedFilterCategory').value = '';
    document.getElementById('sharedFilterStock').value = '';
    document.getElementById('sharedFilterActive').value = '';
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
        const formattedPrice = (p.price || 0).toFixed(2) + ' €';
        const stockStyle = p.stock < 5 ? 'fw-bold text-danger' : '';
        const badgeLowStock = p.stock < 5 ? `<span class="badge-stock-low ms-1">${sharedInventoryI18n.lowStock}</span>` : '';
        
        // Category can also be multilingual
        let catName = '—';
        if (p.category) {
            catName = isEn && p.category.nameEn ? p.category.nameEn : (p.category.nameEs || p.category.name);
        }

        const imgHtml = p.imageUrl
            ? `<img src="${p.imageUrl}" class="thumb" alt="">`
            : `<div class="thumb-placeholder"><i class="bi bi-image"></i></div>`;
        const activeBadge = p.active
            ? `<span class="badge-active yes">${sharedInventoryI18n.yes}</span>`
            : `<span class="badge-active no">${sharedInventoryI18n.no}</span>`;

        const escapedName = name ? name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';

        let tr = document.createElement('tr');
        tr.className = 'product-row';
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${p.id}</td>
            <td>${imgHtml}</td>
            <td>
                <strong>${name}</strong>
                <div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px">${description ? description.substring(0, 60) : ''}</div>
            </td>
            <td>${p.vatRate ? Math.round(p.vatRate * 100) + '%' : '—'}</td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--accent);text-align:right">${formattedPrice}</td>
            <td>
                <div class="d-flex flex-column align-items-center">
                    <span class="${stockStyle}">${p.stock}</span>
                    ${badgeLowStock}
                </div>
            </td>
            <td style="text-align:center"><span style="font-size:0.8rem;font-weight:600;color:var(--text-muted)">${p.measurementUnitSymbol || '—'}</span></td>
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

function runSharedBackendCategoryFilter() {
    const globalSearch = document.getElementById('sharedFilterSearch');
    const catSearch = document.getElementById('categoryFilterSearch');
    const search = (globalSearch ? globalSearch.value.trim() : '') || (catSearch ? catSearch.value.trim() : '');

    const sortBy = (document.getElementById('categoryFilterSortBy') || {}).value || 'id';
    const sortDir = (document.getElementById('categoryFilterSortDir') || {}).value || 'asc';

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/categories?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            const items = data.content ? data.content : data;
            renderSharedCategoriesTable(items);
        })
        .catch(err => console.error("Error filtrando categorias combinadas:", err));
}

function renderSharedCategoriesTable(categories) {
    const tbody = document.getElementById('categoriesTableBody');
    if (!tbody) return;

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
