/**
 * admin-coupons.js
 * Coupon management functions.
 */

let selectedCouponProducts = new Set();
let selectedCouponCategories = new Set();

function searchCouponProducts() {
    const query = document.getElementById('couponProductSearch').value;
    if (query.length < 2) return;
    
    fetch(`/api/products/search?q=${query}`)
        .then(res => res.json())
        .then(products => {
            const results = document.getElementById('couponProductSearchResults');
            results.innerHTML = products.map(p => `
                <div class="search-result-item" onclick="addCouponProduct(${p.id}, '${escHtml(p.name)}')">
                    ${escHtml(p.name)}
                </div>
            `).join('');
        });
}

function searchCouponCategories() {
    const query = document.getElementById('couponCategorySearch').value;
    if (query.length < 2) return;
    
    fetch(`/api/categories/search?q=${query}`)
        .then(res => res.json())
        .then(categories => {
            const results = document.getElementById('couponCategorySearchResults');
            results.innerHTML = categories.map(c => `
                <div class="search-result-item" onclick="addCouponCategory(${c.id}, '${escHtml(c.name)}')">
                    ${escHtml(c.name)}
                </div>
            `).join('');
        });
}

function addCouponProduct(id, name) {
    selectedCouponProducts.add({ id, name });
    renderSelectedCouponProducts();
    document.getElementById('couponProductSearchResults').innerHTML = '';
    document.getElementById('couponProductSearch').value = '';
}

function addCouponCategory(id, name) {
    selectedCouponCategories.add({ id, name });
    renderSelectedCouponCategories();
    document.getElementById('couponCategorySearchResults').innerHTML = '';
    document.getElementById('couponCategorySearch').value = '';
}

function removeCouponProduct(id) {
    selectedCouponProducts = new Set([...selectedCouponProducts].filter(p => p.id !== id));
    renderSelectedCouponProducts();
}

function removeCouponCategory(id) {
    selectedCouponCategories = new Set([...selectedCouponCategories].filter(c => c.id !== id));
    renderSelectedCouponCategories();
}

function renderSelectedCouponProducts() {
    const container = document.getElementById('selectedCouponProducts');
    container.innerHTML = [...selectedCouponProducts].map(p => `
        <span class="badge bg-primary me-1 mb-1">
            ${p.name} <i class="bi bi-x-circle cursor-pointer" onclick="removeCouponProduct(${p.id})"></i>
        </span>
    `).join('');
}

function renderSelectedCouponCategories() {
    const container = document.getElementById('selectedCouponCategories');
    container.innerHTML = [...selectedCouponCategories].map(c => `
        <span class="badge bg-secondary me-1 mb-1">
            ${c.name} <i class="bi bi-x-circle cursor-pointer" onclick="removeCouponCategory(${c.id})"></i>
        </span>
    `).join('');
}

function openCouponModal(id) {
    document.getElementById('couponForm').reset();
    document.getElementById('couponId').value = id || '';
    selectedCouponProducts.clear();
    selectedCouponCategories.clear();
    renderSelectedCouponProducts();
    renderSelectedCouponCategories();
    
    if (id) {
        fetch(`/api/coupons/${id}`)
            .then(res => res.json())
            .then(c => {
                document.getElementById('couponCode').value = c.code;
                document.getElementById('couponDiscount').value = c.discount;
                document.getElementById('couponType').value = c.type;
                c.products.forEach(p => selectedCouponProducts.add(p));
                c.categories.forEach(cat => selectedCouponCategories.add(cat));
                renderSelectedCouponProducts();
                renderSelectedCouponCategories();
            });
    }
    couponModal.show();
}

function saveCoupon() {
    const coupon = {
        id: document.getElementById('couponId').value,
        code: document.getElementById('couponCode').value,
        discount: parseFloat(document.getElementById('couponDiscount').value),
        type: document.getElementById('couponType').value,
        productIds: [...selectedCouponProducts].map(p => p.id),
        categoryIds: [...selectedCouponCategories].map(c => c.id)
    };
    
    fetch('/api/coupons', {
        method: coupon.id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(coupon)
    }).then(res => {
        if (res.ok) {
            couponModal.hide();
            showToast('Cupón guardado');
            setTimeout(() => location.reload(), 1000);
        }
    });
}

function deleteCoupon(id) {
    if (!confirm('¿Eliminar cupón?')) return;
    fetch(`/api/coupons/${id}`, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Cupón eliminado');
                setTimeout(() => location.reload(), 1000);
            }
        });
}

// Global Exports
window.searchCouponProducts = searchCouponProducts;
window.searchCouponCategories = searchCouponCategories;
window.addCouponProduct = addCouponProduct;
window.addCouponCategory = addCouponCategory;
window.removeCouponProduct = removeCouponProduct;
window.removeCouponCategory = removeCouponCategory;
window.renderSelectedCouponProducts = renderSelectedCouponProducts;
window.renderSelectedCouponCategories = renderSelectedCouponCategories;
window.openCouponModal = openCouponModal;
window.saveCoupon = saveCoupon;
window.deleteCoupon = deleteCoupon;
