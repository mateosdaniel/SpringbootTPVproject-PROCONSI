/* inventory-filter.js */

let debounceTimer;

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

function runSharedBackendFilter() {
    // Tomamos el buscador del header del Admin o el buscador del Fragment (Productos-categorias)
    const globalSearch = document.getElementById('sharedFilterSearch');
    const fragSearch = document.getElementById('fragmentFilterSearch');
    const search = (globalSearch ? globalSearch.value.trim() : '') || (fragSearch ? fragSearch.value.trim() : '');

    const category = document.getElementById('sharedFilterCategory').value;
    const stock = document.getElementById('sharedFilterStock').value;
    let active = document.getElementById('sharedFilterActive').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (category) queryParams.append('category', category);
    if (stock) queryParams.append('stock', stock);
    if (active) queryParams.append('active', active);

    fetch(`/api/products/filter?${queryParams.toString()}`)
        .then(res => res.json())
        .then(products => {
            renderSharedProductsTable(products);
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
    // Apuntamos al tbody que se ha definido en HTML en ambas vistas con id "productsTableBody"
    const tbody = document.getElementById('productsTableBody');
    if (!tbody) return;

    tbody.innerHTML = ''; // Limpiamos tabla actual

    if (!products || products.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center p-4 text-muted">No se encontraron productos con esos filtros.</td></tr>`;
        return;
    }

    products.forEach(p => {
        // Parsear datos para la lógica visual
        const formattedPrice = (p.price || 0).toFixed(2) + ' €';
        const stockStyle = p.stock < 5 ? 'fw-bold text-danger' : '';
        const badgeLowStock = p.stock < 5 ? '<span class="badge-stock-low ms-1">Stock Bajo</span>' : '';
        const catName = p.category ? p.category.name : '—';
        const imgHtml = p.imageUrl
            ? `<img src="${p.imageUrl}" class="thumb" alt="">`
            : `<div class="thumb-placeholder"><i class="bi bi-image"></i></div>`;
        const activeBadge = p.active
            ? `<span class="badge-active yes">Sí</span>`
            : `<span class="badge-active no">No</span>`;

        // Make sure to escape quotes for the name string inside onclick!
        const escapedName = p.name ? p.name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';

        let tr = document.createElement('tr');
        tr.className = 'product-row';
        tr.innerHTML = `
            <td>${imgHtml}</td>
            <td>
                <strong>${p.name}</strong>
                <div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px">${p.description ? p.description.substring(0, 60) : ''}</div>
            </td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--accent)">${formattedPrice}</td>
            <td><span class="${stockStyle}">${p.stock}</span> ${badgeLowStock}</td>
            <td><span style="font-size:0.82rem;padding:0.2rem 0.5rem;border-radius:6px;background:var(--surface);color:var(--text-muted)">${catName}</span></td>
            <td>${activeBadge}</td>
            <td style="text-align:right">
                <div class="d-flex gap-1 justify-content-end">
                    <button class="btn-icon" title="Editar" onclick="openProductModal(${p.id})"><i class="bi bi-pencil"></i></button>
                    <button class="btn-icon danger" title="Eliminar" onclick="deleteProduct(${p.id}, '${escapedName}')"><i class="bi bi-trash"></i></button>
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

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);

    fetch(`/api/categories/filter?${queryParams.toString()}`)
        .then(res => res.json())
        .then(categories => {
            renderSharedCategoriesTable(categories);
        })
        .catch(err => console.error("Error filtrando categorias combinadas:", err));
}

function renderSharedCategoriesTable(categories) {
    const tbody = document.getElementById('categoriesTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (!categories || categories.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center p-4 text-muted">No hay categorías que coincidan.</td></tr>`;
        return;
    }

    categories.forEach(c => {
        const escapedName = c.name ? c.name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';
        const desc = c.description || '—';
        const activeBadge = c.active
            ? `<span class="badge-active yes">Sí</span>`
            : `<span class="badge-active no">No</span>`;

        let tr = document.createElement('tr');
        tr.className = 'category-row';
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${c.id}</td>
            <td><strong>${c.name}</strong></td>
            <td>${desc}</td>
            <td>${activeBadge}</td>
            <td style="text-align:right">
                <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                    <button class="btn-icon" title="Editar" onclick="openCategoryModal(${c.id})"><i class="bi bi-pencil"></i></button>
                    <button class="btn-icon danger" title="Eliminar" onclick="deleteCategory(${c.id}, '${escapedName}')"><i class="bi bi-trash"></i></button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}
