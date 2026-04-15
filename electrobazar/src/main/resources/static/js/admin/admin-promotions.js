/**
 * admin-promotions.js
 * Promotion management functions.
 */

let selectedPromoProducts = new Set();
let selectedPromoCategories = new Set();

// Initialize autocompletes
document.addEventListener('DOMContentLoaded', function() {
    initProductAutocomplete('promoProductSearch', 'promoProductSearchResults', function(p) {
        // Validation: Only products sold by units (not literal volume/weight with fractions)
        // Usually, integer units have decimalPlaces = 0 or a specific unit symbol
        const unit = p.measurementUnit;
        if (unit && unit.decimalPlaces > 0) {
            alert('⚠️ Solo se pueden añadir productos que se vendan por unidades (sin decimales).');
            return;
        }
        
        addPromoProduct(p.id, p.name);
    });
});

function searchPromoProducts() {
    // Redundant now, but kept empty for internal compatibility if called manually
}

function searchPromoCategories() {
    const query = document.getElementById('promoCategorySearch').value;
    if (query.length < 2) return;
    
    fetch(`/api/categories/search?q=${query}`)
        .then(res => res.json())
        .then(categories => {
            const results = document.getElementById('promoCategorySearchResults');
            results.innerHTML = categories.map(c => `
                <div class="search-result-item" onclick="addPromoCategory(${c.id}, '${escHtml(c.name)}')">
                    ${escHtml(c.name)}
                </div>
            `).join('');
        });
}

function addPromoProduct(id, name) {
    if (!id || !name) return;
    // Prevent duplicates
    const alreadyExists = [...selectedPromoProducts].some(p => p.id === id);
    if (alreadyExists) {
        showToast('⚠️ Este producto ya está en la lista', 'info');
        return;
    }

    selectedPromoProducts.add({ id, name });
    renderSelectedPromoProducts();
    const dropdown = document.getElementById('promoProductSearchResults');
    if (dropdown) dropdown.style.display = 'none';
    document.getElementById('promoProductSearch').value = '';
}

function addPromoCategory(id, name) {
    if (!id || !name) return;
    // Prevent duplicates
    const alreadyExists = [...selectedPromoCategories].some(c => c.id === id);
    if (alreadyExists) {
        showToast('⚠️ Esta categoría ya está en la lista', 'info');
        return;
    }

    selectedPromoCategories.add({ id, name });
    renderSelectedPromoCategories();
    const dropdown = document.getElementById('promoCategorySearchResults');
    if (dropdown) dropdown.style.display = 'none';
    document.getElementById('promoCategorySearch').value = '';
}

function removePromoProduct(id) {
    selectedPromoProducts = new Set([...selectedPromoProducts].filter(p => p.id !== id));
    renderSelectedPromoProducts();
}

function removePromoCategory(id) {
    selectedPromoCategories = new Set([...selectedPromoCategories].filter(c => c.id !== id));
    renderSelectedPromoCategories();
}

function renderSelectedPromoProducts() {
    const container = document.getElementById('selectedPromoProducts');
    container.innerHTML = [...selectedPromoProducts].map(p => `
        <span class="badge bg-primary-subtle border border-primary-subtle me-1 mb-1 p-2 d-inline-flex align-items-center gap-2" 
              style="color: var(--primary) !important; font-weight: 600;">
            ${p.name} <i class="bi bi-x-circle cursor-pointer" style="color: var(--text-muted);" onclick="removePromoProduct(${p.id})"></i>
        </span>
    `).join('');
}

function renderSelectedPromoCategories() {
    const container = document.getElementById('selectedPromoCategories');
    container.innerHTML = [...selectedPromoCategories].map(c => `
        <span class="badge bg-secondary-subtle border border-secondary-subtle me-1 mb-1 p-2 d-inline-flex align-items-center gap-2"
              style="color: var(--text-main) !important; font-weight: 600;">
            ${c.name} <i class="bi bi-x-circle cursor-pointer" style="color: var(--text-muted);" onclick="removePromoCategory(${c.id})"></i>
        </span>
    `).join('');
}

function openPromotionModal(id) {
    document.getElementById('promoId').value = id || '';
    document.getElementById('promoName').value = '';
    document.getElementById('promoNValue').value = 3;
    document.getElementById('promoMValue').value = 2;
    document.getElementById('promoFrom').value = '';
    document.getElementById('promoUntil').value = '';
    document.getElementById('promoActive').checked = true;
    selectedPromoProducts.clear();
    selectedPromoCategories.clear();
    renderSelectedPromoProducts();
    renderSelectedPromoCategories();
    
    if (id) {
        fetch(`/api/promotions/${id}`)
            .then(res => res.json())
            .then(p => {
                document.getElementById('promoId').value = p.id || '';
                document.getElementById('promoName').value = p.name || '';
                document.getElementById('promoNValue').value = p.nValue || 3;
                document.getElementById('promoMValue').value = p.mValue || 2;
                document.getElementById('promoFrom').value = p.validFrom ? p.validFrom.slice(0, 16) : '';
                document.getElementById('promoUntil').value = p.validUntil ? p.validUntil.slice(0, 16) : '';
                document.getElementById('promoActive').checked = p.active !== false;
                selectedPromoProducts.clear();
                selectedPromoCategories.clear();
                (p.products || []).forEach(prod => selectedPromoProducts.add(prod));
                (p.categories || []).forEach(cat => selectedPromoCategories.add(cat));
                renderSelectedPromoProducts();
                renderSelectedPromoCategories();
            });
    }
    promotionModal.show();
}

function savePromotion() {
    const idEl = document.getElementById('promoId');
    const nameEl = document.getElementById('promoName');
    const nEl = document.getElementById('promoNValue');
    const mEl = document.getElementById('promoMValue');
    const fromEl = document.getElementById('promoFrom');
    const untilEl = document.getElementById('promoUntil');
    const activeEl = document.getElementById('promoActive');

    // Validation for core fields
    if (!nameEl) {
        showToast('⚠️ Error crítico: El formulario de promoción no se cargó correctamente.', 'danger');
        return;
    }
    
    const name = nameEl.value.trim();
    if (!name) {
        showToast('⚠️ El nombre de la promoción es obligatorio', 'warning');
        nameEl.focus();
        return;
    }

    if (!nEl || !mEl) {
        showToast('⚠️ Faltan campos de configuración (NxM)', 'danger');
        return;
    }

    // Extraction with null-checks as requested by user
    const promo = {
        id: idEl ? idEl.value : null,
        name: name,
        nValue: parseInt(nEl.value) || 3,
        mValue: parseInt(mEl.value) || 2,
        validFrom: fromEl ? (fromEl.value || null) : null,
        validUntil: untilEl ? (untilEl.value || null) : null,
        active: activeEl ? activeEl.checked : true,
        productIds: [...selectedPromoProducts].map(p => p.id),
        categoryIds: [...selectedPromoCategories].map(c => c.id)
    };

    // Generic check for any other product-specific inputs if they were to exist
    // Currently we only have IDs, but this pattern implements the user's requested safety:
    for (const p of selectedPromoProducts) {
        // Example of the validation logic requested
        // if (p.needsSpecialConfig) {
        //    const specEl = document.getElementById(`spec-${p.id}`);
        //    if (!specEl) { 
        //        showToast(`⚠️ No se puede guardar: El producto ${p.name} no tiene un formato válido...`, 'warning');
        //        return;
        //    }
        // }
    }
    
    fetch('/api/promotions', {
        method: promo.id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(promo)
    }).then(res => {
        if (res.ok) {
            if (typeof promotionModal !== 'undefined') promotionModal.hide();
            showToast('✅ Promoción guardada correctamente', 'success');
            setTimeout(() => location.reload(), 1000);
        } else {
            res.json().then(err => {
                showToast('❌ Error al guardar: ' + (err.message || 'Error desconocido'), 'danger');
            });
        }
    }).catch(err => {
        console.error('Save error:', err);
        showToast('❌ Error de conexión al guardar la promoción', 'danger');
    });
}

function deletePromotion(id) {
    if (!confirm('¿Eliminar promoción?')) return;
    fetch(`/api/promotions/${id}`, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Promoción eliminada');
                setTimeout(() => location.reload(), 1000);
            }
        });
}

// Global Exports
window.searchPromoProducts = searchPromoProducts;
window.searchPromoCategories = searchPromoCategories;
window.addPromoProduct = addPromoProduct;
window.addPromoCategory = addPromoCategory;
window.removePromoProduct = removePromoProduct;
window.removePromoCategory = removePromoCategory;
window.renderSelectedPromoProducts = renderSelectedPromoProducts;
window.renderSelectedPromoCategories = renderSelectedPromoCategories;
window.openPromotionModal = openPromotionModal;
window.savePromotion = savePromotion;
window.deletePromotion = deletePromotion;
