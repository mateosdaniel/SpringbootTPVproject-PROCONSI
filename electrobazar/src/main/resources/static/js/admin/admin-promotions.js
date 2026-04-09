/**
 * admin-promotions.js
 * Promotion management functions.
 */

let selectedPromoProducts = new Set();
let selectedPromoCategories = new Set();

function searchPromoProducts() {
    const query = document.getElementById('promoProductSearch').value;
    if (query.length < 2) return;
    
    fetch(`/api/products/search?q=${query}`)
        .then(res => res.json())
        .then(products => {
            const results = document.getElementById('promoProductSearchResults');
            results.innerHTML = products.map(p => `
                <div class="search-result-item" onclick="addPromoProduct(${p.id}, '${escHtml(p.name)}')">
                    ${escHtml(p.name)}
                </div>
            `).join('');
        });
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
    selectedPromoProducts.add({ id, name });
    renderSelectedPromoProducts();
    document.getElementById('promoProductSearchResults').innerHTML = '';
    document.getElementById('promoProductSearch').value = '';
}

function addPromoCategory(id, name) {
    selectedPromoCategories.add({ id, name });
    renderSelectedPromoCategories();
    document.getElementById('promoCategorySearchResults').innerHTML = '';
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
        <span class="badge bg-primary me-1 mb-1">
            ${p.name} <i class="bi bi-x-circle cursor-pointer" onclick="removePromoProduct(${p.id})"></i>
        </span>
    `).join('');
}

function renderSelectedPromoCategories() {
    const container = document.getElementById('selectedPromoCategories');
    container.innerHTML = [...selectedPromoCategories].map(c => `
        <span class="badge bg-secondary me-1 mb-1">
            ${c.name} <i class="bi bi-x-circle cursor-pointer" onclick="removePromoCategory(${c.id})"></i>
        </span>
    `).join('');
}

function openPromotionModal(id) {
    document.getElementById('promotionForm').reset();
    document.getElementById('promotionId').value = id || '';
    selectedPromoProducts.clear();
    selectedPromoCategories.clear();
    renderSelectedPromoProducts();
    renderSelectedPromoCategories();
    
    if (id) {
        fetch(`/api/promotions/${id}`)
            .then(res => res.json())
            .then(p => {
                document.getElementById('promotionName').value = p.name;
                document.getElementById('promotionDiscount').value = p.discount;
                document.getElementById('promotionType').value = p.type;
                p.products.forEach(prod => selectedPromoProducts.add(prod));
                p.categories.forEach(cat => selectedPromoCategories.add(cat));
                renderSelectedPromoProducts();
                renderSelectedPromoCategories();
            });
    }
    promotionModal.show();
}

function savePromotion() {
    const promo = {
        id: document.getElementById('promotionId').value,
        name: document.getElementById('promotionName').value,
        discount: parseFloat(document.getElementById('promotionDiscount').value),
        type: document.getElementById('promotionType').value,
        productIds: [...selectedPromoProducts].map(p => p.id),
        categoryIds: [...selectedPromoCategories].map(c => c.id)
    };
    
    fetch('/api/promotions', {
        method: promo.id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(promo)
    }).then(res => {
        if (res.ok) {
            promotionModal.hide();
            showToast('Promoción guardada');
            setTimeout(() => location.reload(), 1000);
        }
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
