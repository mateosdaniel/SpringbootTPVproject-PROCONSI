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
    var name = document.getElementById('categoryName').value;
    if (!name || name.trim() === '') {
        showToast('El nombre de la categoría es obligatorio', 'error');
        return;
    }

    var category = {
        id: id ? parseInt(id) : null,
        nameEs: name.trim(),
        descriptionEs: document.getElementById('categoryDescription').value,
        active: document.getElementById('categoryActive').checked
    };

    fetch('/api/categories' + (id ? '/' + id : ''), {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(category)
    }).then(function (res) {
        if (res.ok) {
            categoryModal.hide();
            showToast(getAdminI18n('successSave'));
            setTimeout(function () { location.reload(); }, 1000);
        } else {
            res.json().then(function (err) {
                showToast(getAdminI18n('errorSave') + ': ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function () {
                showToast(getAdminI18n('errorSave'), 'error');
            });
        }
    }).catch(function () {
        showToast(getAdminI18n('errorNetwork'), 'error');
    });
}

function deleteCategory(id) {
    if (!confirm(getAdminI18n('confirmDelete'))) return;
    fetch('/api/categories/' + id, { method: 'DELETE' })
        .then(function (res) {
            if (res.ok) {
                showToast(getAdminI18n('successDelete'));
                setTimeout(function () { location.reload(); }, 1000);
            } else {
                res.json().then(function (err) {
                    showToast(getAdminI18n('errorDelete') + ': ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast(getAdminI18n('errorDelete'), 'error');
                });
            }
        }).catch(function () {
            showToast(getAdminI18n('errorNetwork'), 'error');
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
