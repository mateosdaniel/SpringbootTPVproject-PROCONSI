/**
 * product-autocomplete.js
 * Universal product search with debounce and premium dropdown.
 */

function initProductAutocomplete(inputId, dropdownId, onSelectCallback) {
    const input = document.getElementById(inputId);
    const dropdown = document.getElementById(dropdownId);
    let timeout = null;

    if (!input || !dropdown) return;

    // Create results list container if not exists
    let list = dropdown.querySelector('.autocomplete-results-list');
    if (!list) {
        list = document.createElement('div');
        list.className = 'autocomplete-results-list p-2';
        dropdown.appendChild(list);
    }

    input.addEventListener('input', function() {
        const query = input.value.trim();
        
        if (query.length < 2) {
            dropdown.style.display = 'none';
            return;
        }

        clearTimeout(timeout);
        timeout = setTimeout(() => {
            fetch(`/api/products/search?q=${encodeURIComponent(query)}&size=15`)
                .then(r => r.json())
                .then(data => {
                    // Handle both Page object and simple Array
                    const products = data.content || data || [];
                    renderDropdown(products, dropdown, list, input, onSelectCallback);
                })
                .catch(err => console.error('Error in product search:', err));
        }, 300);
    });

    // Close dropdown on click outside
    document.addEventListener('click', function(e) {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });

    // Handle focus to show results if query exists
    input.addEventListener('focus', function() {
        if (input.value.trim().length >= 2 && list.children.length > 0) {
            dropdown.style.display = 'block';
        }
    });
}

function renderDropdown(products, dropdown, list, input, onSelectCallback) {
    list.innerHTML = '';
    
    if (products.length === 0) {
        list.innerHTML = '<div class="p-3 text-center text-muted">No se encontraron productos</div>';
    } else {
        products.forEach(p => {
            const item = document.createElement('div');
            item.className = 'search-result-item d-flex align-items-center justify-content-between p-3 border-bottom';
            item.style.cursor = 'pointer';
            item.style.borderRadius = '8px';
            item.style.margin = '2px 0';
            item.style.transition = 'all 0.2s ease';

            // Premium styles usually handled by CSS, but added here for safety
            const imgHtml = p.imageUrl 
                ? `<img src="${p.imageUrl}" style="width: 40px; height: 40px; border-radius: 8px; object-fit: cover; border: 1px solid var(--border);">`
                : `<div style="width: 40px; height: 40px; border-radius: 8px; background: var(--surface); display: flex; align-items: center; justify-content: center; border: 1px solid var(--border);"><i class="bi bi-box" style="font-size: 1.2rem; color: var(--text-muted);"></i></div>`;

            const price = typeof p.price === 'number' ? p.price.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '0,00';

            item.innerHTML = `
                <div class="d-flex align-items-center gap-3">
                    ${imgHtml}
                    <div style="line-height: 1.2;">
                        <div class="fw-bold" style="color: var(--text-main); font-size: 0.95rem;">${p.name}</div>
                        <div class="small text-muted" style="font-size: 0.75rem;">${p.category ? p.category.name : ''}</div>
                    </div>
                </div>
                <div class="text-end">
                    <div class="fw-bold" style="color: var(--accent); font-size: 1.1rem;">${price}€</div>
                    <div class="small ${p.stock <= 0 ? 'text-danger' : 'text-success'}" style="font-size: 0.7rem;">Stock: ${p.stock || 0}</div>
                </div>
            `;

            item.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                onSelectCallback(p);
                dropdown.style.display = 'none';
                input.value = '';
            };

            list.appendChild(item);
        });
    }

    dropdown.style.display = 'block';
}

// Export for ES6 or just keep it global
if (typeof module !== 'undefined') {
    module.exports = { initProductAutocomplete };
}
