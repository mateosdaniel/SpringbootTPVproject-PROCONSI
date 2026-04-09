/**
 * admin-categories.js
 * Category management functions.
 */

function openCategoryModal(id) {
    document.getElementById('categoryForm').reset();
    document.getElementById('categoryId').value = id || '';
    document.getElementById('categoryModalLabel').textContent = id ? 'Editar Categoría' : 'Nueva Categoría';

    if (id) {
        fetch('/api/categories/' + id)
            .then(function (r) { return r.json(); })
            .then(function (c) {
                document.getElementById('categoryName').value = c.nameEs || '';
                document.getElementById('categoryDescription').value = c.descriptionEs || '';
                document.getElementById('categoryActive').checked = c.active !== false;
            });
    }
    categoryModal.show();
}

function saveCategory() {
    var id = document.getElementById('categoryId').value;
    var category = {
        id: id ? parseInt(id) : null,
        nameEs: document.getElementById('categoryName').value,
        descriptionEs: document.getElementById('categoryDescription').value,
        active: document.getElementById('categoryActive').checked
    };

    fetch('/admin/categories/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category)
    }).then(function (res) {
        if (res.ok) {
            categoryModal.hide();
            showToast('Categoría guardada con éxito');
            setTimeout(function () { location.reload(); }, 1000);
        } else {
            res.json().then(function (err) {
                showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function () {
                showToast('Error al guardar categoría', 'error');
            });
        }
    }).catch(function () {
        showToast('Error de red', 'error');
    });
}

function deleteCategory(id) {
    if (!confirm('¿Seguro que quieres eliminar esta categoría?')) return;
    fetch('/admin/categories/delete/' + id, { method: 'DELETE' })
        .then(function (res) {
            if (res.ok) {
                showToast('Categoría eliminada');
                setTimeout(function () { location.reload(); }, 1000);
            } else {
                res.json().then(function (err) {
                    showToast('Error al eliminar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al eliminar categoría', 'error');
                });
            }
        }).catch(function () {
            showToast('Error de red', 'error');
        });
}

function resetCategoryFilters() {
    const srch = document.getElementById('categoryFilterSearch');
    if (srch) srch.value = '';
    const globalSearch = document.getElementById('sharedFilterSearch');
    if (globalSearch) globalSearch.value = '';

    const sortByEl = document.getElementById('categoryFilterSortBy');
    const sortDirEl = document.getElementById('categoryFilterSortDir');
    if (sortByEl) sortByEl.value = 'id';
    if (sortDirEl) sortDirEl.value = 'asc';

    if (typeof runSharedBackendCategoryFilter === 'function') {
        runSharedBackendCategoryFilter();
    }
}

// Global Exports
window.openCategoryModal = openCategoryModal;
window.saveCategory = saveCategory;
window.deleteCategory = deleteCategory;
window.resetCategoryFilters = resetCategoryFilters;
