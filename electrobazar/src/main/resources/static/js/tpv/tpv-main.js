// -- Estado del ticket --
document.addEventListener('DOMContentLoaded', function () {
    if (typeof attachNifCifValidator === 'function') {
        attachNifCifValidator('newCustomerTaxId');
    }
});
var ticket = {}; // { productId: { name, price, quantity, stock } }

// -- Estado de tarifa --
var currentTariffId = null;
var currentDiscountPct = 0; // 0–100
var currentTariffLabel = 'MINORISTA';
var currentHasRE = false;


function addToTicket(card) {
    var id = card.dataset.id;
    var name = card.dataset.name;
    var price = parseFloat(card.dataset.price);
    var stock = parseInt(card.dataset.stock) || 0;

    var currentQty = ticket[id] ? ticket[id].quantity : 0;
    if (currentQty + 1 > stock) {
        showToast("Stock insuficiente para este producto", 'warning');
        return;
    }

    if (ticket[id]) {
        ticket[id].quantity++;
    } else {
        ticket[id] = { name: name, price: price, quantity: 1, stock: stock };
    }

    // If a customer or tariff is active, fetch the final prices before rendering
    if (window.currentTariffId || window.currentHasRE) {
        var url = '/tpv/api/products/' + id + '/price';
        if (window.currentTariffId) url += '?tariffId=' + window.currentTariffId;

        fetch(url)
            .then(function(r) { return r.json(); })
            .then(function(priceData) {
                if (ticket[id]) {
                    ticket[id].price = priceData.price;
                    ticket[id].priceWithRe = priceData.priceWithRe;
                }
                renderTicket();
            })
            .catch(function() {
                renderTicket(); // fallback: render with base price on error
            });
    } else {
        renderTicket();
    }

    // Feedback visual en la tarjeta
    card.style.borderColor = 'var(--success)';
    setTimeout(function () { card.style.borderColor = ''; }, 300);
}

function changeQty(id, delta) {
    if (!ticket[id]) return;
    var newQty = ticket[id].quantity + delta;

    if (delta > 0 && newQty > ticket[id].stock) {
        showToast("Stock insuficiente para este producto", 'warning');
        return;
    }

    ticket[id].quantity = newQty;
    if (ticket[id].quantity <= 0) delete ticket[id];
    renderTicket();
}

function removeLine(id) {
    delete ticket[id];
    renderTicket();
}

function editQty(el, id) {
    var current = ticket[id].quantity;
    var input = document.createElement('input');
    input.type = 'number';
    input.className = 'qty-input';
    input.value = current;
    input.min = 1;
    input.setAttribute('inputmode', 'numeric');
    el.replaceWith(input);
    input.focus();
    input.select();

    function confirmQty() {
        var val = parseInt(input.value);
        if (val && val > 0) {
            if (val > ticket[id].stock) {
                showToast("Stock insuficiente para este producto", 'warning');
                renderTicket();
                return;
            }
            ticket[id].quantity = val;
        }
        renderTicket();
    }

    input.addEventListener('blur', confirmQty);
    input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') { e.preventDefault(); input.blur(); }
        if (e.key === 'Escape') { renderTicket(); }
    });
}

function clearTicket() {
    Object.keys(ticket).forEach(function (k) { delete ticket[k]; });
    renderTicket();
}

function renderTicket() {
    var linesEl = document.getElementById('ticketLines');
    var countEl = document.getElementById('ticketCount');
    var totalEl = document.getElementById('ticketTotal');
    var cobrarBtn = document.getElementById('btnCobrar');
    var suspenderBtn = document.getElementById('btnSuspender');
    var formLines = document.getElementById('formLines');

    var ids = Object.keys(ticket);

    // Vacío
    if (ids.length === 0) {
        linesEl.innerHTML = `
                <div class="ticket-empty">
                    <i class="bi bi-cart"></i>
                    <span>Pulsa un producto para añadirlo</span>
                </div>`;
        countEl.textContent = '0';
        totalEl.textContent = '0.00\u20AC';
        cobrarBtn.disabled = true;
        if (suspenderBtn) { suspenderBtn.disabled = true; suspenderBtn.style.opacity = '0.4'; }
        formLines.innerHTML = '';
        return;
    }

    var totalItems = 0;
    var totalAmount = 0;
    var linesHTML = '';
    var formHTML = '';

    ids.forEach(function (id) {
        var item = ticket[id];
        var subtotal = item.price * item.quantity;
        totalItems += item.quantity;
        totalAmount += subtotal;

        linesHTML += `
                <div class="ticket-line">
                    <div class="ticket-line-name">${escapeHtml(item.name)}</div>
                    <div class="qty-control">
                        <button class="qty-btn" onclick="changeQty('${id}', -1)">-</button>
                        <span class="qty-num" onclick="editQty(this, '${id}')">${item.quantity}</span>
                        <button class="qty-btn" onclick="changeQty('${id}', 1)">+</button>
                    </div>
                    <div class="ticket-line-price">${subtotal.toFixed(2)}\u20AC</div>
                    <button class="btn-remove-line" onclick="removeLine('${id}')">
                        <i class="bi bi-x"></i>
                    </button>
                </div>`;

        formHTML += `
                <input type="hidden" name="productIds" value="${id}">
                <input type="hidden" name="quantities" value="${item.quantity}">
                <input type="hidden" name="unitPrices" value="${item.price.toFixed(2)}">`;
    });

    linesEl.innerHTML = linesHTML;
    formLines.innerHTML = formHTML;
    countEl.textContent = totalItems;

    // Prices stored in ticket[id].price are already final (returned by the backend
    // including any tariff discount). No additional discount calculation is applied here.
    var originalTotalRow = document.getElementById('originalTotalRow');
    var discountRow = document.getElementById('discountRow');
    if (originalTotalRow) { originalTotalRow.style.display = 'none'; }
    if (discountRow) { discountRow.style.display = 'none'; }

    // Recargo de Equivalencia (RE)
    var reRow = document.getElementById('reRow');
    var reAmountEl = document.getElementById('reAmount');
    if (window.currentHasRE) {
        var totalRe = 0;
        ids.forEach(function(id) {
            var item = ticket[id];
            if (item.priceWithRe && item.price) {
                totalRe += (item.priceWithRe - item.price) * item.quantity;
            }
        });
        if (totalRe > 0) {
            totalAmount += totalRe;
            if (reRow && reAmountEl) {
                reAmountEl.textContent = '+' + totalRe.toFixed(2) + '\u20AC';
                reRow.style.display = 'flex';
            }
        } else {
            if (reRow) reRow.style.display = 'none';
        }
    } else {
        if (reRow) reRow.style.display = 'none';
    }

    totalEl.textContent = totalAmount.toFixed(2) + '\u20AC';

    cobrarBtn.disabled = false;
    if (suspenderBtn) { suspenderBtn.disabled = false; suspenderBtn.style.opacity = '1'; }
}

function selectPayment(method) {
    document.getElementById('paymentMethodInput').value = method;
    document.getElementById('btnCash').classList.toggle('selected', method === 'CASH');
    document.getElementById('btnCard').classList.toggle('selected', method === 'CARD');
}

function submitSale() {
    if (Object.keys(ticket).length === 0) return;
    document.getElementById('saleForm').submit();
}

var invoiceModalInstance;

function openCustomerModal() {
    if (Object.keys(ticket).length === 0) return;

    // Determine if payment is CASH
    var isCash = document.getElementById('paymentMethodInput').value === 'CASH';
    var cashSection = document.getElementById('cashInputSection');
    var receivedInput = document.getElementById('receivedAmount');
    var receivedInputForm = document.getElementById('receivedAmountInput');

    if (isCash) {
        cashSection.style.display = 'block';
        receivedInput.value = '';
        document.getElementById('changeAmount').textContent = '0.00\u20AC';
        receivedInputForm.value = '';
    } else {
        cashSection.style.display = 'none';
        receivedInput.value = '';
        receivedInputForm.value = '';
    }

    // Populate amounts
    document.getElementById('cobrarAmount').textContent = document.getElementById('ticketTotal').textContent;

    // Reset modal state
    document.getElementById('modalAlert').style.display = 'none';

    // Populate customer summary
    var customerId = document.getElementById('customerIdInput').value;
    var summaryWith = document.getElementById('summaryWithCustomer');
    var summaryNo = document.getElementById('summaryNoCustomer');
    
    if (customerId) {
        document.getElementById('checkoutCustomerName').textContent = document.getElementById('sidebarCustomerName').textContent;
        document.getElementById('checkoutCustomerTaxId').textContent = document.getElementById('sidebarCustomerTaxId').textContent;
        summaryWith.style.display = 'block';
        summaryNo.style.display = 'none';
        if (document.getElementById('requestInvoiceInput')) {
            document.getElementById('requestInvoiceInput').value = 'true';
        }
    } else {
        summaryWith.style.display = 'none';
        summaryNo.style.display = 'block';
        if (document.getElementById('requestInvoiceInput')) {
            document.getElementById('requestInvoiceInput').value = 'false';
        }
    }

    invoiceModalInstance = new bootstrap.Modal(document.getElementById('customerModal'));
    invoiceModalInstance.show();

    if (isCash) {
        setTimeout(function () { receivedInput.focus(); }, 150);
    }
}
// Live calculation for change
document.getElementById('receivedAmount')?.addEventListener('input', function (e) {
    var el = e.target;
    var val = parseFloat(el.value);
    var total = parseFloat(document.getElementById('ticketTotal').textContent.replace('\u20AC', '').trim());
    var changeEl = document.getElementById('changeAmount');

    if (isNaN(val)) {
        changeEl.textContent = '0.00\u20AC';
        changeEl.style.color = 'var(--text-muted)';
    } else {
        var diff = val - total;
        if (diff < 0) {
            changeEl.textContent = 'Faltan ' + Math.abs(diff).toFixed(2) + '\u20AC';
            changeEl.style.color = 'var(--danger)';
        } else {
            changeEl.textContent = diff.toFixed(2) + '\u20AC';
            changeEl.style.color = 'var(--success)';
        }
    }
});

function setExactAmount() {
    var totalText = document.getElementById('ticketTotal').textContent;
    var totalVal = parseFloat(totalText.replace('\u20AC', '').trim());
    var input = document.getElementById('receivedAmount');
    input.value = totalVal.toFixed(2);
    // Trigger input event manually to update change calculation
    input.dispatchEvent(new Event('input'));
}



function toggleCustomerType() {
    var isCompany = document.getElementById('typeCompany').checked;
    var nameLabel = document.getElementById('lblCustomerName');
    var taxLabel = document.getElementById('lblCustomerTaxId');
    var taxInput = document.getElementById('newCustomerTaxId');

    // update labels and required marker
    if (nameLabel) {
        nameLabel.innerHTML = isCompany
            ? 'Razón Social <span class="text-danger">*</span>'
            : 'Nombre y Apellidos <span class="text-danger">*</span>';
    }
    if (taxLabel) {
        taxLabel.innerHTML = isCompany
            ? 'CIF <span class="text-danger">*</span>'
            : 'NIF/NIE'; // no asterisk for individual, tax ID optional
    }

    // browser validation: only require tax id when company
    if (taxInput) {
        if (isCompany) {
            taxInput.setAttribute('required', 'required');
        } else {
            taxInput.removeAttribute('required');
        }
    }

    // Show RE toggle only for Companies
    var reSection = document.getElementById('newCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) {
            document.getElementById('newCustomerHasRecargo').checked = false;
        }
    }
}

function showError(msg) {
    var alert = document.getElementById('modalAlert');
    alert.textContent = msg;
    alert.style.display = 'block';
    setTimeout(function () { alert.style.display = 'none'; }, 4000);
}

function showToast(message, type = 'warning') {
    var container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
        container.style.zIndex = '9999';
        document.body.appendChild(container);
    }

    var toast = document.createElement('div');
    toast.className = 'toast align-items-center text-white border-0 show';
    toast.role = 'alert';
    toast.ariaLive = 'assertive';
    toast.ariaAtomic = 'true';
    toast.style.display = 'block';
    toast.style.marginBottom = '0.75rem';
    toast.style.boxShadow = '0 0.5rem 1rem rgba(0, 0, 0, 0.15)';
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 0.3s ease-in-out';
    toast.style.backgroundColor = type === 'warning' ? 'var(--danger)' : 'var(--success)';
    toast.style.borderRadius = '10px';

    toast.innerHTML =
        '<div class="d-flex">' +
        '<div class="toast-body d-flex align-items-center gap-2">' +
        '<i class="bi ' + (type === 'warning' ? 'bi-exclamation-octagon' : 'bi-check-circle') + ' fs-5"></i>' +
        '<span>' + escapeHtml(message) + '</span>' +
        '</div>' +
        '<button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.parentElement.parentElement.remove()"></button>' +
        '</div>';

    container.appendChild(toast);

    // Fade in
    setTimeout(function () { toast.style.opacity = '1'; }, 10);

    // Fade out and remove
    setTimeout(function () {
        toast.style.opacity = '0';
        setTimeout(function () { if (toast.parentNode) toast.remove(); }, 300);
    }, 4000);
}

function processSaleWithInvoiceValidation() {
    var saleForm = document.getElementById('saleForm');
    var requestInvoiceInput = document.getElementById('requestInvoiceInput');
    var paymentMethod = document.getElementById('paymentMethodInput').value;
    var receivedAmountInput = document.getElementById('receivedAmount');
    var hiddenReceivedAmount = document.getElementById('receivedAmountInput');

    // Determine if a customer was selected in the sidebar
    var customerId = document.getElementById('customerIdInput').value;
    var hasCustomer = !!customerId;

    // Set invoice flag based on whether customer is selected
    if (requestInvoiceInput) {
        requestInvoiceInput.value = hasCustomer ? 'true' : 'false';
    }

    // Validar límite de pago en efectivo (Ley 11/2021)
    var total = parseFloat(document.getElementById('ticketTotal').textContent.replace('\u20AC', '').trim());
    if (paymentMethod === 'CASH' && total >= 1000) {
        showError('El pago en efectivo no está permitido para importes iguales o superiores a 1.000 € según la Ley 11/2021 de prevención del fraude fiscal. Seleccione otro método de pago.');
        return;
    }

    // Handle Cash Received Logic
    if (paymentMethod === 'CASH') {
        var rVal = parseFloat(receivedAmountInput.value);
        if (isNaN(rVal)) {
            showError('Por favor, indica la cantidad entregada en efectivo.');
            return;
        }
        if (rVal < total) {
            showError('La cantidad entregada es menor al total a cobrar.');
            return;
        }
        hiddenReceivedAmount.value = rVal.toFixed(2);
    } else {
        hiddenReceivedAmount.value = '';
    }

    // customerIdInput already set by selectCustomer() in sidebar — just submit
    saleForm.submit();
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Auto-ocultar alerta de éxito tras 4 segundos
var successAlert = document.getElementById('successAlert');
if (successAlert) setTimeout(function () { successAlert.style.display = 'none'; }, 4000);

var errorAlert = document.getElementById('errorAlert');
if (errorAlert) setTimeout(function () { errorAlert.style.display = 'none'; }, 6000);

// -- SEARCH AND FILTER FUNCTIONALITY --
var searchTimeout;
var currentCategoryId = null;

var searchInput = document.getElementById('searchInput');
var productsContainer = document.querySelector('.tpv-products');
var categoryButtons = document.querySelectorAll('.cat-btn');

function loadProducts(endpoint) {
    fetch(endpoint)
        .then(function (response) {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.json();
        })
        .then(function (products) { renderProducts(products); })
        .catch(function (error) { console.error('Error loading products:', error); });
}

function renderProducts(products) {
    var productGrid = document.querySelector('.products-grid');
    if (!productGrid) return;

    if (!products || products.length === 0) {
        productGrid.innerHTML = `
                <div class="no-products" style="grid-column: 1/-1; text-align: center;">
                    <i class="bi bi-box-seam"></i>
                    <p>No hay productos disponibles</p>
                </div>`;
        return;
    }

    productGrid.innerHTML = products.map(function (product) {
        var imgHtml = product.imageUrl
            ? '<img src="' + escapeHtml(product.imageUrl) + '" alt="Imagen producto" class="product-image">'
            : '<i class="bi bi-box product-icon"></i>';

        var catName = product.category ? escapeHtml(product.category.name) : '';

        return '<div class="product-card"' +
            ' data-id="' + product.id + '"' +
            ' data-name="' + escapeHtml(product.name) + '"' +
            ' data-price="' + product.price + '"' +
            ' data-category="' + catName + '"' +
            ' data-stock="' + product.stock + '">' +
            ' <div class="product-image-container">' + imgHtml + ' </div>' +
            ' <div class="product-info">' +
            ' <div class="product-name">' + escapeHtml(product.name) + '</div>' +
            ' <div class="product-price">' + parseFloat(product.price).toFixed(2) + '\u20AC</div>' +
            ' <div class="product-category-badge">' + catName + '</div>' +
            ' </div></div>';
    }).join('');
}

function performSearch() {
    var query = searchInput.value.trim();

    if (query) {
        loadProducts(`/api/products/search?name=${encodeURIComponent(query)}`);
        updateCategoryButtons(null);
        currentCategoryId = null;
    } else if (currentCategoryId) {
        loadProducts(`/api/products/category/${currentCategoryId}`);
    } else {
        loadProducts('/api/products');
    }
}

// Event delegation for product card clicks - attach to the products container
if (productsContainer) {
    productsContainer.addEventListener('click', function (e) {
        var productCard = e.target.closest('.product-card');
        if (productCard) {
            addToTicket(productCard);
        }
    });
}

function updateCategoryButtons(activeId) {
    categoryButtons.forEach(function (btn) {
        btn.classList.remove('active');
    });

    if (activeId === null) {
        document.getElementById('catAll').classList.add('active');
    } else {
        document.querySelector(`[data-category-id="${activeId}"]`)?.classList.add('active');
    }
}

// Real-time search with debounce
if (searchInput) {
    searchInput.addEventListener('input', function () {
        performSearch();
    });
}

// Category button click handlers
var catAllBtn = document.getElementById('catAll');
if (catAllBtn) {
    catAllBtn.addEventListener('click', function () {
        searchInput.value = '';
        currentCategoryId = null;
        updateCategoryButtons(null);
        loadProducts('/api/products');
    });
}

document.querySelectorAll('.cat-btn[data-category-id]').forEach(function (btn) {
    btn.addEventListener('click', function () {
        var categoryId = btn.getAttribute('data-category-id');
        searchInput.value = '';
        currentCategoryId = categoryId;
        updateCategoryButtons(categoryId);
        loadProducts('/api/products/category/' + categoryId);
    });
});

// Search icon - decorative only
// No functionality needed

// -- Hamburger Menu --
(function () {
    var hamburger = document.getElementById('hamburgerBtn');
    var dropdown = document.getElementById('menuDropdown');

    hamburger.addEventListener('click', function (e) {
        e.stopPropagation();
        dropdown.classList.toggle('open');
    });

    document.addEventListener('click', function () { dropdown.classList.remove('open'); });
    dropdown.addEventListener('click', function (e) { e.stopPropagation(); });
})();

// -- Admin PIN Login --
(function () {
    var overlay = document.getElementById('pinOverlay');
    var input = document.getElementById('pinInput');
    var error = document.getElementById('pinError');
    var btn = document.getElementById('adminLockBtn');

    // Expose globally so onclick in HTML works
    window.openAdminPinModal = function () {
        if (!overlay || !input || !error) return;
        input.value = '';
        error.textContent = '';
        input.classList.remove('error');
        overlay.classList.add('open');
        setTimeout(function () { input.focus(); }, 100);
    };

    if (btn) {
        btn.addEventListener('click', window.openAdminPinModal);
    }

    if (document.getElementById('pinCancel')) {
        document.getElementById('pinCancel').addEventListener('click', function () {
            overlay.classList.remove('open');
        });
    }

    if (overlay) {
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) overlay.classList.remove('open');
        });
    }

    function submitPin() {
        var pin = input.value.trim();
        if (!pin) { input.focus(); return; }

        fetch('/admin/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ pin: pin })
        }).then(function (r) {
            if (r.ok) {
                window.location.href = '/admin';
            } else {
                input.classList.remove('error');
                void input.offsetWidth; // force reflow for re-trigger
                input.classList.add('error');
                error.textContent = 'PIN incorrecto';
                input.value = '';
                input.focus();
            }
        }).catch(function () {
            error.textContent = 'Error de conexión';
        });
    }

    if (document.getElementById('pinSubmit')) {
        document.getElementById('pinSubmit').addEventListener('click', submitPin);
    }
    if (input) {
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') submitPin();
            if (e.key === 'Escape') overlay.classList.remove('open');
        });
    }
})();

// -- Buscador de Clientes en Cobro --
var customerSearchTimeout = null;

function loadAllCustomers() {
    fetch('/api/customers')
        .then(function (r) { return r.json(); })
        .then(function (customers) {
            renderCustomerResults(customers);
        })
        .catch(function (err) { console.error('Error loading customers', err); });
}

var customerSearchInput = document.getElementById('customerSearchInput');

if (customerSearchInput) {
    customerSearchInput.addEventListener('input', function (e) {
        var query = e.target.value.trim();
        clearTimeout(customerSearchTimeout);

        if (query.length === 0) {
            loadAllCustomers();
            return;
        }

        if (query.length < 2) {
            document.getElementById('customerSearchResults').style.display = 'none';
            return;
        }

        customerSearchTimeout = setTimeout(function () {
            fetch('/api/customers/search?query=' + encodeURIComponent(query))
                .then(function (r) { return r.json(); })
                .then(function (customers) {
                    renderCustomerResults(customers);
                })
                .catch(function (err) { console.error('Error searching customers', err); });
        }, 300);
    });

    customerSearchInput.addEventListener('focus', function () {
        if (this.value.trim().length === 0) {
            loadAllCustomers();
        }
    });
}

function renderCustomerResults(customers) {
    var container = document.getElementById('customerSearchResults');
    container.innerHTML = '';

    if (customers.length === 0) {
        container.innerHTML = '<div class="p-3" style="color: var(--text-primary); background-color: var(--surface);">No se encontraron clientes</div>';
    } else {
        customers.forEach(function (c) {
            var div = document.createElement('div');
            div.className = 'customer-search-item';
            div.style.color = 'var(--text-primary)';
            div.style.backgroundColor = 'var(--surface)';
            div.innerHTML = '<span class="name" style="color: var(--text-primary);">' + escapeHtml(c.name) + '</span>' +
                '<span class="details" style="color: var(--text-primary); opacity: 0.8;">' + (c.taxId || 'Sin NIF') + ' · ' + (c.city || '') + '</span>';
            div.onclick = function () { selectCustomer(c); };
            container.appendChild(div);
        });
    }
    container.style.display = 'block';
}

function selectCustomer(c) {
    document.getElementById('customerIdInput').value = c.id;
    document.getElementById('sidebarCustomerName').textContent = c.name;
    document.getElementById('sidebarCustomerTaxId').textContent = c.taxId || 'Sin NIF';

    document.getElementById('customerSearchInput').value = '';
    document.getElementById('customerSearchResults').style.display = 'none';
    document.getElementById('selectedCustomerCard').style.display = 'block';
    document.getElementById('customerSelectionControls').style.display = 'none';

    window.currentHasRE = !!c.hasRecargoEquivalencia;

    // Auto-apply the customer's tariff and update ticket prices
    if (c.tariff) {
        window.currentTariffId = c.tariff.id; // expose for addToTicket
        updateTicketPricesForTariff(c.tariff.id, c.tariff.name, parseFloat(c.tariff.discountPercentage || 0));
    } else {
        window.currentTariffId = null;
        resetTicketPrices();
    }
}

function clearSelectedCustomer() {
    document.getElementById('customerIdInput').value = '';
    document.getElementById('selectedCustomerCard').style.display = 'none';
    document.getElementById('customerSelectionControls').style.display = 'block';
    window.currentTariffId = null; // clear so addToTicket uses base prices again
    window.currentHasRE = false;
    resetTicketPrices();
}

/**
 * Updates prices for all items currently in the ticket using the backend price API.
 * The API already returns the final tariff price — no additional discount is applied here.
 */
function updateTicketPricesForTariff(tariffId, tariffName, discountPct) {
    var productIds = Object.keys(ticket);
    if (productIds.length === 0) {
        // Pass discountPct=0 so renderTicket never re-applies a discount
        applyTariffById(tariffId, 0, tariffName);
        return;
    }

    var promises = productIds.map(function(id) {
        return fetch('/tpv/api/products/' + id + '/price?tariffId=' + tariffId)
            .then(function(r) { return r.json(); })
            .then(function(priceData) {
                if (ticket[id]) {
                    ticket[id].price = priceData.price; // already the final price from backend
                    ticket[id].priceWithRe = priceData.priceWithRe;
                }
            });
    });

    Promise.all(promises).then(function() {
        // discountPct=0: prices are already final, renderTicket must not re-discount
        applyTariffById(tariffId, 0, tariffName);
        var badge = document.getElementById('sidebarTariffBadge');
        if (badge) {
            badge.textContent = tariffName + (discountPct > 0 ? ' -' + discountPct + '%' : '');
            badge.style.display = 'inline-block';
        }
    }).catch(function(err) {
        console.error('Error updating ticket prices', err);
        applyTariffById(tariffId, 0, tariffName);
    });
}

/**
 * Resets ticket prices to their base website price.
 */
function resetTicketPrices() {
    var productIds = Object.keys(ticket);
    var badge = document.getElementById('sidebarTariffBadge');
    if (badge) badge.style.display = 'none';

    if (productIds.length === 0) {
        resetTariffToDefault();
        return;
    }

    var promises = productIds.map(function(id) {
        return fetch('/tpv/api/products/' + id + '/price') // endpoint returns base price if no tariffId
            .then(function(r) { return r.json(); })
            .then(function(priceData) {
                if (ticket[id]) {
                    ticket[id].price = priceData.price;
                    ticket[id].priceWithRe = priceData.priceWithRe;
                }
            });
    });

    Promise.all(promises).then(function() {
        resetTariffToDefault();
    });
}

function openFullCustomerModal() {
    // Reset form
    document.getElementById('newCustomerName').value = '';
    document.getElementById('newCustomerTaxId').value = '';
    document.getElementById('newCustomerAddress').value = '';
    document.getElementById('newCustomerCity').value = '';
    document.getElementById('newCustomerPostalCode').value = '';
    document.getElementById('newCustomerEmail').value = '';
    document.getElementById('newCustomerPhone').value = '';
    document.getElementById('typeIndividual').checked = true;
    toggleCustomerType();
    
    var modal = new bootstrap.Modal(document.getElementById('fullCustomerModal'));
    modal.show();
}

function createNewCustomerAjax() {
    var type = document.querySelector('input[name="newCustomerType"]:checked').value;
    var name = document.getElementById('newCustomerName').value.trim();
    var taxId = document.getElementById('newCustomerTaxId').value.trim();
    var address = document.getElementById('newCustomerAddress').value.trim();
    var city = document.getElementById('newCustomerCity').value.trim();
    var postalCode = document.getElementById('newCustomerPostalCode').value.trim();
    var email = document.getElementById('newCustomerEmail').value.trim();
    var phone = document.getElementById('newCustomerPhone').value.trim();
    var hasRecargo = document.getElementById('newCustomerHasRecargo').checked;

    if (!name) { showToast('El nombre es obligatorio', 'warning'); return; }
    if (type === 'COMPANY' && !taxId) { showToast('El CIF de la empresa es obligatorio', 'warning'); return; }

    var newCustomer = {
        name: name, taxId: taxId, address: address, city: city,
        postalCode: postalCode, email: email, phone: phone,
        type: type, active: true, hasRecargoEquivalencia: hasRecargo
    };

    fetch('/api/customers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newCustomer)
    })
    .then(function (response) {
        if (!response.ok) throw new Error('Error al crear el cliente');
        return response.json();
    })
    .then(function (savedCustomer) {
        bootstrap.Modal.getInstance(document.getElementById('fullCustomerModal')).hide();
        selectCustomer(savedCustomer);
        showToast('Cliente creado y seleccionado', 'success');
    })
    .catch(function (err) {
        console.error(err);
        showToast('Error al crear el cliente', 'warning');
    });
}

// Cerrar resultados al hacer click fuera
document.addEventListener('click', function (e) {
    if (!e.target.closest('.position-relative')) {
        document.getElementById('customerSearchResults').style.display = 'none';
    }
});

var searchTab = document.getElementById('sidebar-search-tab');
if (searchTab) {
    searchTab.addEventListener('shown.bs.tab', function () {
        if (document.getElementById('customerSearchInput').value.trim().length === 0) {
            loadAllCustomers();
        }
    });
}

// ── SUSPEND / RESUME SYSTEM ─────────────────────────────────────────────────

var suspendLabelModalInstance = null;
var suspendedSalesModalInstance = null;

/**
 * Opens the label-prompt modal before suspending.
 * The actual suspend happens in confirmSuspend().
 */
function openSuspendLabelModal() {
    if (Object.keys(ticket).length === 0) return;
    var labelInput = document.getElementById('suspendLabelInput');
    if (labelInput) labelInput.value = '';
    suspendLabelModalInstance = new bootstrap.Modal(document.getElementById('suspendLabelModal'));
    suspendLabelModalInstance.show();
    setTimeout(function () { if (labelInput) labelInput.focus(); }, 200);
}

/**
 * Called by the "Suspender" button inside the label modal.
 * Serialises the current cart and POSTs it to the API, then clears the cart.
 */
function confirmSuspend() {
    var label = (document.getElementById('suspendLabelInput').value || '').trim();

    var lines = Object.keys(ticket).map(function (id) {
        return {
            productId: parseInt(id),
            quantity: ticket[id].quantity,
            unitPrice: ticket[id].price
        };
    });

    if (lines.length === 0) {
        if (suspendLabelModalInstance) suspendLabelModalInstance.hide();
        return;
    }

    // Disable the button to prevent double-click
    var confirmBtn = document.getElementById('btnConfirmSuspend');
    if (confirmBtn) confirmBtn.disabled = true;

    fetch('/api/suspended-sales', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lines: lines, label: label || null })
    })
        .then(function (r) {
            if (!r.ok) return r.json().then(function (d) { throw new Error(d.error || 'Error al suspender'); });
            return r.json();
        })
        .then(function () {
            if (suspendLabelModalInstance) suspendLabelModalInstance.hide();
            clearTicket();
            loadSuspendedCount();
            showToast('Venta suspendida correctamente', 'success');
        })
        .catch(function (err) {
            showToast(err.message || 'Error al suspender la venta', 'warning');
        })
        .finally(function () {
            if (confirmBtn) confirmBtn.disabled = false;
        });
}

/**
 * Resumes a suspended sale: POSTs to resume endpoint, then loads
 * the returned lines back into the JS cart, closing the modal.
 */
function resumeSale(id) {
    var hasItems = Object.keys(ticket).length > 0;

    if (hasItems) {
        var label = prompt('Etiqueta para la venta actual antes de suspenderla:', 'Venta interrumpida');
        if (label === null) return; // El usuario canceló el prompt

        // Suspender la venta actual primero, y al terminar reanudar la seleccionada
        var lines = Object.keys(ticket).map(function (productId) {
            return {
                productId: parseInt(productId),
                quantity: ticket[productId].quantity,
                unitPrice: ticket[productId].price
            };
        });

        fetch('/api/suspended-sales', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lines: lines, label: label.trim() || 'Venta interrumpida' })
        })
            .then(function (r) {
                if (!r.ok) throw new Error('Error al suspender la venta actual');
                return r.json();
            })
            .then(function () {
                clearTicket();
                loadSuspendedCount();
                return fetch('/api/suspended-sales/' + id + '/resume', { method: 'POST' });
            })
            .then(function (r) {
                if (!r.ok) return r.json().then(function (d) { throw new Error(d.error || 'Error al reabrir'); });
                return r.json();
            })
            .then(function (sale) {
                (sale.lines || []).forEach(function (line) {
                    var productId = String(line.productId);
                    ticket[productId] = {
                        name: line.productName,
                        price: parseFloat(line.unitPrice),
                        quantity: line.quantity,
                        stock: 999
                    };
                });
                renderTicket();
                if (suspendedSalesModalInstance) suspendedSalesModalInstance.hide();
                cleanupModalBackdrop();
                loadSuspendedCount();
                showToast('Venta reanudada', 'success');
            })
            .catch(function (err) {
                showToast(err.message || 'Error', 'error');
            });

        return; // Salir aquí, el resto lo maneja la cadena de promesas
    }

    // Sin artículos en el ticket, reanudar directamente
    fetch('/api/suspended-sales/' + id + '/resume', { method: 'POST' })
        .then(function (r) {
            if (!r.ok) return r.json().then(function (d) { throw new Error(d.error || 'Error al reabrir'); });
            return r.json();
        })
        .then(function (sale) {
            clearTicket();
            (sale.lines || []).forEach(function (line) {
                var productId = String(line.productId);
                ticket[productId] = {
                    name: line.productName,
                    price: parseFloat(line.unitPrice),
                    quantity: line.quantity,
                    stock: 999
                };
            });
            renderTicket();
            if (suspendedSalesModalInstance) suspendedSalesModalInstance.hide();
            cleanupModalBackdrop();
            loadSuspendedCount();
            showToast('Venta reanudada', 'success');
        })
        .catch(function (err) {
            showToast(err.message || 'Error al reanudar la venta', 'warning');
        });
}

/**
 * Cancels a suspended sale (marks it CANCELLED, removes it from the list).
 */
function cancelSuspendedSale(id) {
    if (!confirm('\u00BFEliminar esta venta en espera?')) return;
    fetch('/api/suspended-sales/' + id + '/cancel', { method: 'POST' })
        .then(function (r) {
            if (!r.ok) return r.json().then(function (d) { throw new Error(d.error || 'Error al cancelar'); });
            return r.json();
        })
        .then(function () {
            loadSuspendedCount();
            if (suspendedSalesModalInstance) suspendedSalesModalInstance.hide();
            cleanupModalBackdrop();
            openSuspendedModal(); // refresh the modal list in place
        })
        .catch(function (err) {
            showToast(err.message || 'Error al cancelar la venta', 'warning');
        });
}

/**
 * Fetches count of currently suspended sales and updates the header badge.
 */
function loadSuspendedCount() {
    fetch('/api/suspended-sales')
        .then(function (r) { return r.json(); })
        .then(function (sales) {
            var badge = document.getElementById('suspendedBadge');
            if (!badge) return;
            var count = Array.isArray(sales) ? sales.length : 0;
            badge.textContent = count;
            badge.style.display = count > 0 ? 'block' : 'none';
        })
        .catch(function () { /* silently ignore */ });
}

/**
 * Opens the "Ventas en espera" modal and renders its list.
 */
function openSuspendedModal() {
    suspendedSalesModalInstance = new bootstrap.Modal(document.getElementById('suspendedSalesModal'));
    suspendedSalesModalInstance.show();

    var container = document.getElementById('suspendedListContainer');
    container.innerHTML = '<div style="padding:1.5rem; text-align:center; color:var(--text-muted);"><i class="bi bi-hourglass"></i> Cargando...</div>';

    fetch('/api/suspended-sales')
        .then(function (r) { return r.json(); })
        .then(function (sales) { renderSuspendedList(sales, container); })
        .catch(function () {
            container.innerHTML = '<div style="padding:1.5rem; text-align:center; color:#ef4444;">Error al cargar las ventas en espera.</div>';
        });
}

function renderSuspendedList(sales, container) {
    if (!Array.isArray(sales) || sales.length === 0) {
        container.innerHTML = '<div style="padding:2rem; text-align:center; color:var(--text-muted);"><i class="bi bi-check-circle" style="font-size:1.5rem;"></i><br>No hay ventas en espera.</div>';
        return;
    }

    var html = '<table style="width:100%; border-collapse:collapse; font-size:0.88rem;">';
    html += '<thead><tr style="border-bottom:1px solid var(--border); color:var(--text-muted); font-size:0.76rem; font-weight:700; text-transform:uppercase;">';
    html += '<th style="padding:0.6rem 1rem;">Etiqueta</th>';
    html += '<th style="padding:0.6rem 0.5rem;">Trabajador</th>';
    html += '<th style="padding:0.6rem 0.5rem;">Fecha y Hora</th>';
    html += '<th style="padding:0.6rem 0.5rem; text-align:center;">L\u00edneas</th>';
    html += '<th style="padding:0.6rem 1rem; text-align:right;">Acciones</th>';
    html += '</tr></thead><tbody>';

    sales.forEach(function (s) {
        var label = escapeHtml(s.label || 'Sin etiqueta');
        var workerName = escapeHtml(s.workerUsername || 'Sistema');
        var createdAt = s.createdAt
            ? new Date(s.createdAt).toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })
            : '?';
        var lineCount = (s.lines || []).length;
        html += '<tr style="border-bottom:1px solid var(--border);">';
        html += '<td style="padding:0.6rem 1rem; font-weight:600;">' + label + '</td>';
        html += '<td style="padding:0.6rem 0.5rem; color:var(--text-muted);">' + workerName + '</td>';
        html += '<td style="padding:0.6rem 0.5rem; color:var(--text-muted);">' + createdAt + '</td>';
        html += '<td style="padding:0.6rem 0.5rem; text-align:center;">' + lineCount + '</td>';
        html += '<td style="padding:0.6rem 1rem; text-align:right;">';
        html += '<button onclick="resumeSale(' + s.id + ')" style="margin-right:0.4rem; padding:0.3rem 0.8rem; border-radius:6px; background:var(--accent); color:var(--primary); border:none; font-size:0.82rem; font-weight:700; cursor:pointer;"><i class="bi bi-play-fill"></i> Reabrir</button>';
        html += '<button onclick="cancelSuspendedSale(' + s.id + ')" style="padding:0.3rem 0.6rem; border-radius:6px; background:var(--surface); color:var(--text-muted); border:1px solid var(--border); font-size:0.82rem; cursor:pointer;"><i class="bi bi-x-lg"></i></button>';
        html += '</td></tr>';
    });

    html += '</tbody></table>';
    container.innerHTML = html;
}

/**
 * Removes stale Bootstrap modal backdrop(s) and restores body styles
 * after a modal is programmatically hidden, but only when no other
 * modal is still visible on screen.
 */
function cleanupModalBackdrop() {
    setTimeout(function () {
        if (document.querySelector('.modal.show')) return; // another modal is open
        document.querySelectorAll('.modal-backdrop').forEach(function (el) { el.remove(); });
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
    }, 300);
}

// -- RETURN TICKET SEARCH (AJAX) ---------------------------------------------
function handleReturnSearch() {
    var queryInput = document.getElementById('returnQueryInput');
    var errorDiv = document.getElementById('returnSearchError');
    var submitBtn = document.getElementById('btnSubmitReturnSearch');
    var query = queryInput.value.trim();

    if (!query) {
        queryInput.focus();
        return;
    }

    // Reset UI
    errorDiv.style.display = 'none';
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';

    fetch('/tpv/return/check?query=' + encodeURIComponent(query))
        .then(function (response) {
            if (!response.ok) {
                return response.json().then(function (data) {
                    throw new Error(data.errorMessage || 'Ticket no encontrado');
                });
            }
            return response.json();
        })
        .then(function (data) {
            // Success: Redirect to the return flow
            window.location.href = data.redirectUrl;
        })
        .catch(function (error) {
            // Error: Show message in modal and refocus
            errorDiv.textContent = error.message;
            errorDiv.style.display = 'block';
            queryInput.focus();
            queryInput.select();
        })
        .finally(function () {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Buscar';
        });
}

// Initialization and Event Listeners
document.addEventListener('DOMContentLoaded', function () {
    // Return search modal focus and Enter key
    var returnModal = document.getElementById('returnSearchModal');
    if (returnModal) {
        var returnInput = document.getElementById('returnQueryInput');

        returnModal.addEventListener('shown.bs.modal', function () {
            returnInput.value = '';
            document.getElementById('returnSearchError').style.display = 'none';
            returnInput.focus();
        });

        returnInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                handleReturnSearch();
            }
        });
    }

    // Load badge count on page load
    loadSuspendedCount();

    // Allow Enter key in label modal to confirm suspend
    var labelInput = document.getElementById('suspendLabelInput');
    if (labelInput) {
        labelInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); confirmSuspend(); }
        });
    }
});

// ── TARIFF SYSTEM ────────────────────────────────────────────────────────────

/**
 * Called when the cashier manually changes the tariff selector.
 */
function onTariffChange(tariffId, discountPct, labelText) {
    applyTariffById(tariffId, parseFloat(discountPct || 0), labelText);
}

/**
 * Applies a tariff: updates state, form input, badge, and re-renders the ticket.
 */
function applyTariffById(tariffId, discountPct, labelText) {
    currentTariffId = tariffId;
    currentDiscountPct = isNaN(discountPct) ? 0 : discountPct;
    currentTariffLabel = labelText || 'MINORISTA';

    // Update hidden form input
    var tariffInput = document.getElementById('tariffIdInput');
    if (tariffInput) tariffInput.value = tariffId || '';

    // Sync dropdown selection
    var selector = document.getElementById('tariffSelector');
    if (selector && tariffId) {
        for (var i = 0; i < selector.options.length; i++) {
            if (selector.options[i].value == tariffId) {
                selector.selectedIndex = i;
                break;
            }
        }
    }

    // Update badge
    updateTariffBadge();

    // Show tariff bar if discount applies
    var tariffBar = document.getElementById('tariffBar');
    if (tariffBar) tariffBar.style.display = 'flex';

    // Recalculate totals
    renderTicket();
}

/**
 * Resets to MINORISTA (no discount) when customer is cleared or page loads.
 */
function resetTariffToDefault() {
    var selector = document.getElementById('tariffSelector');
    var firstOption = selector ? selector.options[0] : null;
    if (firstOption) {
        // Find MINORISTA option (first with 0 discount)
        for (var i = 0; i < selector.options.length; i++) {
            if (parseFloat(selector.options[i].dataset.discount) === 0) {
                selector.selectedIndex = i;
                applyTariffById(selector.options[i].value, 0, selector.options[i].text);
                return;
            }
        }
        applyTariffById(firstOption.value, parseFloat(firstOption.dataset.discount || 0), firstOption.text);
    }
}

/**
 * Updates the tariff badge colour and text.
 */
function updateTariffBadge() {
    var badge = document.getElementById('tariffBadge');
    if (!badge) return;
    badge.textContent = currentTariffLabel;
    // Colour coding
    badge.style.background = 'transparent';
    badge.style.border = '1px solid';
    if (currentDiscountPct === 0) {
        badge.style.color = 'var(--text-muted)';
        badge.style.borderColor = 'var(--border)';
    } else if (currentDiscountPct >= 15) {
        badge.style.color = '#27ae60';
        badge.style.borderColor = 'rgba(39,174,96,0.4)';
        badge.style.background = 'rgba(39,174,96,0.1)';
    } else {
        badge.style.color = '#f39c12';
        badge.style.borderColor = 'rgba(243,156,18,0.4)';
        badge.style.background = 'rgba(243,156,18,0.1)';
    }
    badge.style.padding = '0.15rem 0.45rem';
    badge.style.borderRadius = '6px';
    badge.style.fontSize = '0.75rem';
    badge.style.fontWeight = '700';
}

// Initialise tariff selector to MINORISTA on load
document.addEventListener('DOMContentLoaded', function () {
    var tariffBar = document.getElementById('tariffBar');
    if (tariffBar) tariffBar.style.display = 'flex'; // Always show tariff bar
    resetTariffToDefault();
});

