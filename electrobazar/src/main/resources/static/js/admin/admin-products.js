/**
 * admin-products.js
 * Product management functions, pagination, and table rendering.
 */

function openProductModal(id) {
    const form = document.getElementById('productForm');
    if (form) form.reset();
    document.getElementById('productId').value = id || '';
    
    const imagePreview = document.getElementById('imagePreview');
    if (imagePreview) {
        imagePreview.src = '';
        imagePreview.style.display = 'none';
    }
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
                    if (img) {
                        img.src = p.imageUrl;
                        img.style.display = 'block';
                    }
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
        price: parseFloat(document.getElementById('productPrice').value) || 0,
        stock: parseFloat(document.getElementById('productStock').value) || 0,
        active: document.getElementById('productActive').checked,
        categoryId: document.getElementById('productCategory').value ? parseInt(document.getElementById('productCategory').value) : null,
        taxRateId: document.getElementById('productTaxRate').value ? parseInt(document.getElementById('productTaxRate').value) : null,
        measurementUnitId: document.getElementById('productUnit').value ? parseInt(document.getElementById('productUnit').value) : null
    };

    formData.append('product', new Blob([JSON.stringify(product)], { type: 'application/json' }));
    const fileInput = document.getElementById('productImage');
    if (fileInput && fileInput.files && fileInput.files.length > 0) {
        formData.append('image', fileInput.files[0]);
    }

    fetch('/api/products' + (id ? '/' + id : ''), {
        method: id ? 'PUT' : 'POST',
        body: formData
    }).then(res => {
        if (res.ok) {
            productModal.hide();
            showToast(getAdminI18n('successSave'));
            location.reload(); // Simple reload for now to refresh table
        }
    });
}

function deleteProduct(id, name) {
    if (!confirm(getAdminI18n('confirmDelete'))) return;
    fetch('/api/products/' + id, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast(getAdminI18n('successDelete'));
                location.reload();
            }
        });
}

function uploadCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);

    showToast(getAdminI18n('loading'), 'info');
    fetch('/api/admin/products/import-csv', {
        method: 'POST',
        body: formData
    }).then(res => {
        if (res.ok) {
            showToast(getAdminI18n('successSave'));
            location.reload();
        } else {
            showToast(getAdminI18n('errorSave'), 'error');
        }
    });
}

// --- Global Exports ---
window.openProductModal = openProductModal;
window.saveProduct = saveProduct;
window.deleteProduct = deleteProduct;
window.uploadCsvFile = uploadCsvFile;
