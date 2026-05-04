// Pagination logic for products and categories
function goToPage(page) {
    const url = new URL(window.location.href);
    url.searchParams.set('page', page);
    window.location.href = url.toString();
}

function jumpToPage(page) {
    const totalPages = parseInt(document.getElementById('totalPages')?.value || '1');
    let pageNum = parseInt(page);
    if (isNaN(pageNum)) return;
    if (pageNum < 1) pageNum = 1;
    if (pageNum > totalPages) pageNum = totalPages;
    goToPage(pageNum - 1);
}

function goToCategoriesPage(page) {
    const url = new URL(window.location.href);
    url.searchParams.set('categoriesPage', page);
    window.location.href = url.toString();
}

function jumpToCategoriesPage(page) {
    const totalPages = parseInt(document.getElementById('categoriesTotalPages')?.value || '1');
    let pageNum = parseInt(page);
    if (isNaN(pageNum)) return;
    if (pageNum < 1) pageNum = 1;
    if (pageNum > totalPages) pageNum = totalPages;
    goToCategoriesPage(pageNum - 1);
}

// Event Listeners for Modals and UI specific to this view
document.addEventListener('DOMContentLoaded', function() {
    // --- Tab Persistence ---
    const mgmtTabs = document.getElementById('mgmtTabs');
    if (mgmtTabs) {
        // Restore tab from sessionStorage
        const lastTabId = sessionStorage.getItem('mgmtActiveTab');
        if (lastTabId) {
            const tabBtn = document.getElementById(lastTabId);
            if (tabBtn) {
                // Initialize Bootstrap tab if not already done and show it
                const tab = bootstrap.Tab.getOrCreateInstance(tabBtn);
                tab.show();
            }
        }

        // Save tab on change
        mgmtTabs.addEventListener('shown.bs.tab', function(e) {
            sessionStorage.setItem('mgmtActiveTab', e.target.id);
        });
    }

    const productImageUrlInput = document.getElementById('productImageUrl');
    const imagePreview = document.getElementById('imagePreview');
    if (productImageUrlInput && imagePreview) {
        productImageUrlInput.addEventListener('input', function() {
            if (this.value) {
                imagePreview.src = this.value;
                imagePreview.style.display = 'block';
            } else {
                imagePreview.style.display = 'none';
            }
        });
    }

    // Global exports for th:onclick
    window.goToPage = goToPage;
    window.jumpToPage = jumpToPage;
    window.goToCategoriesPage = goToCategoriesPage;
    window.jumpToCategoriesPage = jumpToCategoriesPage;
});
