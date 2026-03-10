const productModal = new bootstrap.Modal(document.getElementById('productModal'));
const categoryModal = new bootstrap.Modal(document.getElementById('categoryModal'));

function showToast(msg, type = 'success') {
    const el = document.getElementById('toastMsg');
    const icon = document.getElementById('toastIcon');
    const text = document.getElementById('toastText');
    el.classList.remove('success', 'error');
    el.classList.add(type);
    icon.className = 'bi fs-5 ' + (type === 'success' ? 'bi-check-circle text-success' : 'bi-exclamation-circle text-danger');
    text.textContent = msg;
    bootstrap.Toast.getOrCreateInstance(el).show();
}

function previewImage(url) {
    const img = document.getElementById('imgPreview');
    img.src = url || '';
    img.style.display = url ? 'block' : 'none';
}
function openProductModal(id) {
    document.getElementById('productId').value = '';
    document.getElementById('productName').value = '';
    document.getElementById('productDescription').value = '';
    document.getElementById('productPrice').value = '';
    document.getElementById('productStock').value = '0';
    document.getElementById('productCategory').value = '';
    document.getElementById('productImageUrl').value = '';
    document.getElementById('productActive').checked = true;
    const ivaEl = document.getElementById('productIvaRate');
    document.getElementById('productModalLabel').textContent = id ? 'Editar Producto' : 'Nuevo Producto';
    previewImage(null);

    // Fetch active tax rates every time the modal opens
    fetch('/api/tax-rates/active')
        .then(function (res) { return res.json(); })
        .then(function (rates) {
            if (ivaEl) {
                ivaEl.innerHTML = '';
                let highest = null;
                rates.forEach(function (r) {
                    const opt = document.createElement('option');
                    opt.value = r.id; // Store tax rate ID, not vatRate
                    opt.textContent = r.description + ' (' + (r.vatRate * 100).toFixed(1).replace('.0', '') + '%)';
                    opt.dataset.vatRate = r.vatRate; // Store vatRate for reference
                    ivaEl.appendChild(opt);
                    if (!highest || r.vatRate > highest.vatRate) {
                        highest = r;
                    }
                });

                // Default for new products: highest rate
                if (!id && highest) {
                    ivaEl.value = highest.id.toString();
                }
            }

            // If editing, load product AFTER tax rates are ready
            if (id) {
                fetch('/api/products/' + id)
                    .then(function (r) { return r.json(); })
                    .then(function (p) {
                        document.getElementById('productId').value = p.id;
                        document.getElementById('productName').value = p.name || '';
                        document.getElementById('productDescription').value = p.description || '';
                        document.getElementById('productPrice').value = p.price || '';
                        document.getElementById('productStock').value = (p.stock !== undefined && p.stock !== null) ? p.stock : 0;
                        document.getElementById('productCategory').value = p.category ? p.category.id : '';
                        document.getElementById('productImageUrl').value = p.imageUrl || '';
                        document.getElementById('productActive').checked = p.active !== false;
                        // Use taxRate.vatRate for display and taxRate.id for selection
                        if (ivaEl && p.taxRate !== null && p.taxRate !== undefined) {
                            ivaEl.value = p.taxRate.id.toString();
                        }
                        previewImage(p.imageUrl);
                    });
            }
        });

    productModal.show();
}

function saveProduct() {
    const name = document.getElementById('productName').value.trim();
    const price = document.getElementById('productPrice').value;
    if (!name || !price) { showToast('Nombre y precio son obligatorios', 'error'); return; }

    const id = document.getElementById('productId').value;
    const ivaEl = document.getElementById('productIvaRate');
    const taxRateId = ivaEl ? parseInt(ivaEl.value) : null;
    
    let body;
    if (id) {
        // PUT request - use taxRate object format
        body = {
            name, price: parseFloat(price),
            description: document.getElementById('productDescription').value.trim() || null,
            stock: parseInt(document.getElementById('productStock').value) || 0,
            active: document.getElementById('productActive').checked,
            imageUrl: document.getElementById('productImageUrl').value.trim() || null,
            taxRate: taxRateId ? { id: taxRateId } : null,
            category: document.getElementById('productCategory').value ? { id: parseInt(document.getElementById('productCategory').value) } : null
        };
    } else {
        // POST request - use taxRateId format
        body = {
            name, price: parseFloat(price),
            description: document.getElementById('productDescription').value.trim() || null,
            stock: parseInt(document.getElementById('productStock').value) || 0,
            active: document.getElementById('productActive').checked,
            imageUrl: document.getElementById('productImageUrl').value.trim() || null,
            taxRateId: taxRateId,
            category: document.getElementById('productCategory').value ? { id: parseInt(document.getElementById('productCategory').value) } : null
        };
    }

    fetch(id ? '/api/products/' + id : '/api/products', {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(function (r) {
        if (r.ok) { 
            location.reload(); 
        } else {
            r.json().then(function(err) {
                showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function() {
                showToast('Error al guardar', 'error');
            });
        }
    });
}

function deleteProduct(id, name) {
    if (confirm('¿Seguro que quieres eliminar definitivamente el producto "' + name + '"?')) {
        fetch('/admin/products/' + id + '/hard', { method: 'DELETE' })
            .then(function (r) {
                if (r.status === 409) {
                    return r.text().then(function (msg) { showToast(msg, 'error'); });
                }
                if (!r.ok) throw new Error();

                showToast('Producto "' + name + '" eliminado definitivamente');

                // Remove row from DOM
                var btn = document.querySelector('button.danger[data-id="' + id + '"]');
                if (btn) {
                    var row = btn.closest('tr');
                    if (row) row.remove();
                }
            })
            .catch(function () { showToast('Error al eliminar el producto', 'error'); });
    }
}

function openCategoryModal(id) {
    document.getElementById('categoryId').value = '';
    document.getElementById('categoryName').value = '';
    document.getElementById('categoryDescription').value = '';
    document.getElementById('categoryActive').checked = true;
    document.getElementById('categoryModalLabel').textContent = id ? 'Editar Categoría' : 'Nueva Categoría';

    if (id) {
        fetch('/api/categories/' + id).then(function (r) { return r.json(); }).then(function (c) {
            document.getElementById('categoryId').value = c.id;
            document.getElementById('categoryName').value = c.name || '';
            document.getElementById('categoryDescription').value = c.description || '';
            document.getElementById('categoryActive').checked = c.active !== false;
        });
    }
    categoryModal.show();
}

function saveCategory() {
    const name = document.getElementById('categoryName').value.trim();
    if (!name) return showToast('El nombre es obligatorio', 'error');
    const id = document.getElementById('categoryId').value;
    const body = { name, description: document.getElementById('categoryDescription').value.trim() || null, active: document.getElementById('categoryActive').checked };

    fetch(id ? '/api/categories/' + id : '/api/categories', {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(function (r) {
        if (r.ok) location.reload(); else showToast('Error al guardar', 'error');
    });
}

function deleteCategory(id, name) {
    if (confirm('¿Desactivar "' + name + '"?')) {
        fetch('/api/categories/' + id, { method: 'DELETE' }).then(function (r) {
            if (r.ok) location.reload(); else showToast('Error al eliminar', 'error');
        });
    }
}

function uploadCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    var formData = new FormData();
    formData.append('file', input.files[0]);
    showToast('Subiendo archivo...', 'success');

    fetch('/admin/upload-csv', { method: 'POST', body: formData })
        .then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        })
        .then(function (data) {
            if (data.ok) {
                showToast(data.message || 'CSV importado correctamente');
                setTimeout(function () { location.reload(); }, 2000);
            } else {
                showToast(data.message || 'Error al procesar el CSV', 'error');
            }
        })
        .catch(function (err) {
            console.error('Error CSV upload:', err);
            showToast('Error al subir el archivo: ' + err.message, 'error');
        })
        .finally(function () { input.value = ''; });
}


