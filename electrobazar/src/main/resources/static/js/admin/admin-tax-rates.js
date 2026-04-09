/**
 * admin-tax-rates.js
 * Tax rate (IVA) management functions.
 */

function openCreateTaxRateModal() {
    document.getElementById('newTaxRateDescription').value = '';
    document.getElementById('newTaxRateVat').value = '';
    document.getElementById('newTaxRateRe').value = '';
    document.getElementById('newTaxRateValidFrom').value = new Date().toISOString().split('T')[0];
    document.getElementById('newTaxRateValidTo').value = '';
    document.getElementById('newTaxRateActive').checked = true;
    document.getElementById('createTaxRateError').style.display = 'none';
    new bootstrap.Modal(document.getElementById('createTaxRateModal')).show();
}

function saveTaxRate() {
    const description = document.getElementById('newTaxRateDescription').value.trim();
    const vatRate = document.getElementById('newTaxRateVat').value;
    const reRate = document.getElementById('newTaxRateRe').value;
    const validFrom = document.getElementById('newTaxRateValidFrom').value;
    const validTo = document.getElementById('newTaxRateValidTo').value || null;
    const active = document.getElementById('newTaxRateActive').checked;
    const errorEl = document.getElementById('createTaxRateError');

    if (!description || !vatRate || !reRate || !validFrom) {
        errorEl.textContent = 'Descripción, IVA, RE y Fecha Inicio son obligatorios.';
        errorEl.style.display = 'block';
        return;
    }

    fetch('/admin/api/tax-rates', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description, vatRate: parseFloat(vatRate), reRate: parseFloat(reRate), validFrom, validTo, active })
    }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => { throw new Error(d.error || d.message || 'Error al guardar'); });
        }
    }).catch(e => {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function openEditTaxRateModal(btn) {
    document.getElementById('editTaxRateId').value = btn.dataset.id;
    document.getElementById('editTaxRateDescription').value = btn.dataset.description;
    document.getElementById('editTaxRateVat').value = btn.dataset.vatrate;
    document.getElementById('editTaxRateRe').value = btn.dataset.rerate;
    document.getElementById('editTaxRateValidFrom').value = btn.dataset.validfrom;
    document.getElementById('editTaxRateValidTo').value = btn.dataset.validto;
    document.getElementById('editTaxRateActive').checked = btn.dataset.active === 'true';
    document.getElementById('editTaxRateError').style.display = 'none';
    new bootstrap.Modal(document.getElementById('editTaxRateModal')).show();
}

function updateTaxRate() {
    const id = document.getElementById('editTaxRateId').value;
    const description = document.getElementById('editTaxRateDescription').value.trim();
    const vatRate = document.getElementById('editTaxRateVat').value;
    const reRate = document.getElementById('editTaxRateRe').value;
    const validFrom = document.getElementById('editTaxRateValidFrom').value;
    const validTo = document.getElementById('editTaxRateValidTo').value || null;
    const active = document.getElementById('editTaxRateActive').checked;
    const errorEl = document.getElementById('editTaxRateError');

    fetch('/admin/api/tax-rates/' + id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description, vatRate: parseFloat(vatRate), reRate: parseFloat(reRate), validFrom, validTo, active })
    }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => { throw new Error(d.error || d.message || 'Error al actualizar'); });
        }
    }).catch(e => {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function deleteTaxRate(id, msg) {
    if (!confirm('¿Eliminar el tipo de IVA "' + msg + '"?')) return;
    fetch('/admin/api/tax-rates/' + id, { method: 'DELETE' }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => {
                showToast('Error: ' + (d.error || d.message || 'Error al eliminar'), 'warning');
            }).catch(() => {
                showToast('Error al eliminar', 'warning');
            });
        }
    }).catch(() => {
        showToast('Error de red al eliminar', 'warning');
    });
}

function filterSelProducts() {
    const query = document.getElementById('selProductSearch').value.toLowerCase();
    const items = document.querySelectorAll('.sel-product-item-wrap');
    items.forEach(it => {
        const name = it.dataset.name || '';
        it.style.display = name.includes(query) ? 'block' : 'none';
    });
}

function toggleAllSelProducts(checked) {
    document.querySelectorAll('.sel-product-cb').forEach(cb => {
        if (cb.closest('.sel-product-item-wrap').style.display !== 'none') {
            cb.checked = checked;
        }
    });
}

function toggleAllSelCategories(checked) {
    document.querySelectorAll('.sel-category-cb').forEach(cb => cb.checked = checked);
}

function applySelectiveTaxRate() {
    const taxRateId = document.getElementById('selectiveTaxRateId').value;
    if (!taxRateId) {
        showToast('Por favor, selecciona un tipo de IVA.', 'warning');
        return;
    }

    const productIds = Array.from(document.querySelectorAll('.sel-product-cb:checked')).map(cb => parseInt(cb.value));
    const categoryIds = Array.from(document.querySelectorAll('.sel-category-cb:checked')).map(cb => parseInt(cb.value));

    if (productIds.length === 0 && categoryIds.length === 0) {
        showToast('Selecciona al menos un producto o categoría.', 'warning');
        return;
    }

    if (!confirm(`¿Estás seguro de que deseas aplicar el nuevo IVA a seleccionados? Se recalcularán los precios de venta.`)) {
        return;
    }

    const btn = document.getElementById('btnApplySelectiveTax');
    const originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>PROCESANDO...';

    fetch('/admin/api/tax-rates/apply-selective', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            taxRateId: parseInt(taxRateId),
            productIds: productIds,
            categoryIds: categoryIds
        })
    })
        .then(r => {
            if (!r.ok) return r.json().then(d => { throw new Error(d.error || 'Error al aplicar'); });
            return r.json();
        })
        .then(data => {
            showToast('IVA aplicado y tarifas regeneradas correctamente.', 'success');
            setTimeout(() => location.reload(), 1500);
        })
        .catch(e => {
            showToast(e.message, 'warning');
            btn.disabled = false;
            btn.innerHTML = originalHtml;
        });
}

// Global Exports
window.openCreateTaxRateModal = openCreateTaxRateModal;
window.saveTaxRate = saveTaxRate;
window.openEditTaxRateModal = openEditTaxRateModal;
window.updateTaxRate = updateTaxRate;
window.deleteTaxRate = deleteTaxRate;
window.filterSelProducts = filterSelProducts;
window.toggleAllSelProducts = toggleAllSelProducts;
window.toggleAllSelCategories = toggleAllSelCategories;
window.applySelectiveTaxRate = applySelectiveTaxRate;
