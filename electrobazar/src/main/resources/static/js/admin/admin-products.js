/**
 * admin-products.js
 * Product management functions, pagination, and table rendering.
 */

function openProductModal(id) {
    const form = document.getElementById('productForm');
    if (form) form.reset();
    document.getElementById('productId').value = id || '';
    document.getElementById('imagePreview').style.display = 'none';
    document.getElementById('productModalLabel').textContent = id ? 'Editar Producto' : 'Nuevo Producto';

    if (id) {
        fetch('/api/products/' + id)
            .then(res => res.json())
            .then(p => {
                document.getElementById('productName').value = p.name;
                document.getElementById('productDescription').value = p.description || '';
                document.getElementById('productPrice').value = p.price;
                document.getElementById('productStock').value = p.stock || 0;
                document.getElementById('productCategory').value = p.category ? p.category.id : '';
                document.getElementById('productTaxRate').value = p.taxRate ? p.taxRate.id : '';
                document.getElementById('productUnit').value = p.measurementUnit ? p.measurementUnit.id : '';
                document.getElementById('productActive').checked = p.active;
                if (p.imageUrl) {
                    const img = document.getElementById('imagePreview');
                    img.src = p.imageUrl;
                    img.style.display = 'block';
                }
            });
    }
    productModal.show();
}

function saveProduct() {
    const id = document.getElementById('productId').value;
    const formData = new FormData();
    const product = {
        id: id ? parseInt(id) : null,
        name: document.getElementById('productName').value,
        description: document.getElementById('productDescription').value,
        price: parseFloat(document.getElementById('productPrice').value),
        stock: parseInt(document.getElementById('productStock').value),
        active: document.getElementById('productActive').checked,
        category: { id: parseInt(document.getElementById('productCategory').value) },
        taxRate: { id: parseInt(document.getElementById('productTaxRate').value) },
        measurementUnit: { id: parseInt(document.getElementById('productUnit').value) }
    };

    formData.append('product', new Blob([JSON.stringify(product)], { type: 'application/json' }));
    const fileInput = document.getElementById('productImage');
    if (fileInput.files.length > 0) {
        formData.append('image', fileInput.files[0]);
    }

    fetch('/api/products' + (id ? '/' + id : ''), {
        method: id ? 'PUT' : 'POST',
        body: formData
    }).then(res => {
        if (res.ok) {
            productModal.hide();
            showToast('Producto guardado');
            location.reload(); // Simple reload for now to refresh table
        }
    });
}

function deleteProduct(id, name) {
    if (!confirm(`¿Eliminar producto "${name}"?`)) return;
    fetch('/api/products/' + id, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Producto eliminado');
                location.reload();
            }
        });
}

function uploadCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);

    showToast('Importando CSV...', 'info');
    fetch('/api/admin/products/import-csv', {
        method: 'POST',
        body: formData
    }).then(res => {
        if (res.ok) {
            showToast('CSV importado con éxito');
            location.reload();
        } else {
            showToast('Error al importar CSV', 'error');
        }
    });
}

// --- PAGINATION ---

function getTotalPages() {
    const el = document.getElementById('totalPages');
    return el ? parseInt(el.value, 10) || 1 : 1;
}

function getPageSize() {
    const el = document.getElementById('pageSize');
    return el ? parseInt(el.value, 10) || 50 : 50;
}

function goToPage(page) {
    const totalPages = getTotalPages();
    if (page < 0) page = 0;
    if (page >= totalPages) page = totalPages - 1;

    const url = new URL(window.location.href);
    url.searchParams.set('page', page);
    url.searchParams.set('size', getPageSize());
    url.searchParams.set('view', 'productsView');
    window.location.href = url.toString();
}

function jumpToPage(value) {
    let page = parseInt(value, 10);
    const totalPages = getTotalPages();
    if (isNaN(page)) return;
    if (page < 1) page = 1;
    if (page > totalPages) page = totalPages;
    goToPage(page - 1);
}

// --- TABLE RENDERING OVERRIDE ---

if (typeof renderSharedProductsTable === 'function' || true) {
    window.renderSharedProductsTable = function (products) {
        const tbody = document.getElementById('productsTableBody');
        if (!tbody) return;

        tbody.innerHTML = '';

        if (!products || products.length === 0) {
            tbody.innerHTML = `<tr><td colspan="10" class="text-center p-4 text-muted">${window.sharedInventoryI18n ? window.sharedInventoryI18n.noItems : 'No hay productos'}</td></tr>`;
            return;
        }

        const locale = (typeof getSharedInvLocale === 'function') ? getSharedInvLocale() : 'es';
        const isEn = locale === 'en';

        products.forEach(p => {
            const name = isEn && p.nameEn ? p.nameEn : (p.nameEs || p.name);
            const description = isEn && p.descriptionEn ? p.descriptionEn : (p.descriptionEs || p.description);
            const formattedPrice = (p.price || 0).toFixed(2) + ' €';
            const stockStyle = p.stock < 5 ? 'fw-bold text-danger' : '';
            const badgeLowStock = p.stock < 5 ? `<span class="badge-stock-low">${window.sharedInventoryI18n ? window.sharedInventoryI18n.lowStock : 'Stock Bajo'}</span>` : '';

            let catName = '-';
            if (p.category) {
                catName = isEn && p.category.nameEn ? p.category.nameEn : (p.category.nameEs || p.category.name);
            }

            const imgHtml = p.imageUrl
                ? `<img src="${p.imageUrl}" class="thumb" alt="">`
                : `<div class="thumb-placeholder"><i class="bi bi-image"></i></div>`;
            const activeBadge = p.active
                ? `<span class="badge-active yes">${window.sharedInventoryI18n ? window.sharedInventoryI18n.yes : 'Sí'}</span>`
                : `<span class="badge-active no">${window.sharedInventoryI18n ? window.sharedInventoryI18n.no : 'No'}</span>`;

            const ivaDisplay = p.taxRate && p.taxRate.vatRate != null ? (p.taxRate.vatRate * 100).toFixed(0) + '%' : '-';
            const unitSymbol = p.measurementUnit ? p.measurementUnit.symbol : '-';
            const descTruncated = description ? (description.length > 60 ? description.substring(0, 57) + '...' : description) : '-';
            const escapedName = name ? name.replace(/'/g, "\\'").replace(/"/g, "&quot;") : '';

            let tr = document.createElement('tr');
            tr.className = 'product-row';
            tr.setAttribute('data-iva', p.taxRate ? p.taxRate.vatRate : null);
            tr.innerHTML = `
                <td style="color:var(--text-muted);font-weight:600">#${p.id}</td>
                <td>${imgHtml}</td>
                <td>
                    <strong>${name}</strong>
                    <div style="font-size:0.75rem;color:var(--text-muted);margin-top:2px">${descTruncated}</div>
                </td>
                <td style="font-size:0.9rem; font-weight: 500;">${ivaDisplay}</td>
                <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--accent);text-align:right">${formattedPrice}</td>
                <td><div class="d-flex flex-column align-items-center" style="gap: 0.2rem;"><span class="${stockStyle}">${p.stock}</span> ${badgeLowStock}</div></td>
                <td style="text-align:center"><span style="font-size:0.8rem;font-weight:600;color:var(--text-muted)">${unitSymbol}</span></td>
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

        if (typeof filterProducts === 'function') filterProducts();
    };
}

function filterProducts() {
    const ivaFilter = document.getElementById('sharedFilterIvaRate');
    if (!ivaFilter) return;

    const selectedIva = ivaFilter.value;
    const rows = document.querySelectorAll('.product-row');

    rows.forEach(row => {
        const rowIva = row.getAttribute('data-iva');
        let matchesIva = true;

        if (selectedIva !== "") {
            const val = rowIva ? parseFloat(rowIva).toFixed(2) : null;
            const target = parseFloat(selectedIva).toFixed(2);
            matchesIva = (val === target);
        }

        row.style.display = matchesIva ? '' : 'none';
    });
}

// Global Exports
window.openProductModal = openProductModal;
window.saveProduct = saveProduct;
window.deleteProduct = deleteProduct;
window.uploadCsvFile = uploadCsvFile;
window.goToPage = goToPage;
window.jumpToPage = jumpToPage;
window.filterProducts = filterProducts;
