/**
 * admin-productos-categorias.js
 * Specific logic for the management of products and categories in the products-categories view.
 */

// Event Listeners for Modals and UI specific to this view
document.addEventListener('DOMContentLoaded', function() {
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
});
