// -- Estado del ticket --
var ticket = {}; // { productId: { name, price, quantity, stock } }

function addToTicket(card) {
    var id = card.dataset.id;
    var name = card.dataset.name;
    var price = parseFloat(card.dataset.price);
    var stock = parseInt(card.dataset.stock) || 0;

    var currentQty = ticket[id] ? ticket[id].quantity : 0;
    if (currentQty + 1 > stock) {
        if (!confirm(`El producto "${name}" no tiene stock suficiente. Disponible: ${stock}. ¿Deseas añadirlo de todas formas?`)) {
            return;
        }
    }

    if (ticket[id]) {
        ticket[id].quantity++;
    } else {
        ticket[id] = { name, price, quantity: 1, stock };
    }
    renderTicket();

    // Feedback visual en la tarjeta
    card.style.borderColor = 'var(--success)';
    setTimeout(function () { card.style.borderColor = ''; }, 300);
}

function changeQty(id, delta) {
    if (!ticket[id]) return;
    var newQty = ticket[id].quantity + delta;

    if (delta > 0 && newQty > ticket[id].stock) {
        if (!confirm(`El producto "${ticket[id].name}" no tiene stock suficiente. Disponible: ${ticket[id].stock}. ¿Deseas añadirlo de todas formas?`)) {
            return;
        }
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
                if (!confirm('El producto "' + ticket[id].name + '" no tiene stock suficiente. Disponible: ' + ticket[id].stock + '. ¿Deseas aplicar esta cantidad de todas formas?')) {
                    renderTicket();
                    return;
                }
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
                <input type="hidden" name="quantities" value="${item.quantity}">`;
    });

    linesEl.innerHTML = linesHTML;
    formLines.innerHTML = formHTML;
    countEl.textContent = totalItems;
    totalEl.textContent = totalAmount.toFixed(2) + '\u20AC';
    cobrarBtn.disabled = false;
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
    toggleInvoiceCard(false); // Reset toggle

    // Reset search state
    document.getElementById('customerSearchInput').value = '';
    clearSelectedCustomer();

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

function toggleInvoiceCard(override) {
    var checkbox = document.getElementById('requestInvoiceToggle');
    var card = document.getElementById('invoiceToggleCard').firstElementChild;
    var iconCircle = card.querySelector('.icon-circle');

    // Solo forzamos el estado si override es un booleano estricto (al abrir/cerrar modal)
    if (typeof override === 'boolean') {
        checkbox.checked = override;
    }

    var isChecked = checkbox.checked;

    // Actualizar UI de la tarjeta
    if (isChecked) {
        card.style.borderColor = 'var(--accent)';
        card.style.background = 'rgba(100, 100, 100, 0.1)'; // Fondo sutil para no desentonar
        iconCircle.style.background = 'var(--accent)';
        iconCircle.style.color = 'var(--primary)';
    } else {
        card.style.borderColor = 'var(--border)';
        card.style.background = 'var(--primary)';
        iconCircle.style.background = 'var(--secondary)';
        iconCircle.style.color = 'var(--text-muted)';
    }

    document.getElementById('invoiceSection').style.display = isChecked ? 'block' : 'none';

    if (isChecked && document.getElementById('search-tab').classList.contains('active')) {
        if (document.getElementById('customerSearchInput').value.trim().length === 0) {
            loadAllCustomers();
        }
    }
}

function toggleCustomerType() {
    var isCompany = document.getElementById('typeCompany').checked;
    document.getElementById('lblCustomerName').innerHTML = isCompany ? 'Razón Social <span class="text-danger">*</span>' : 'Nombre y Apellidos <span class="text-danger">*</span>';
    document.getElementById('lblCustomerTaxId').innerHTML = isCompany ? 'CIF <span class="text-danger">*</span>' : 'NIF/NIE <span class="text-danger">*</span>';
}

function showError(msg) {
    var alert = document.getElementById('modalAlert');
    alert.textContent = msg;
    alert.style.display = 'block';
    setTimeout(function () { alert.style.display = 'none'; }, 4000);
}

function processSaleWithInvoiceValidation() {
    var wantsInvoice = document.getElementById('requestInvoiceToggle').checked;
    var saleForm = document.getElementById('saleForm');
    var customerIdInput = document.getElementById('customerIdInput');
    var paymentMethod = document.getElementById('paymentMethodInput').value;
    var receivedAmountInput = document.getElementById('receivedAmount');
    var hiddenReceivedAmount = document.getElementById('receivedAmountInput');

    // Limpiar datos previos
    customerIdInput.value = '';

    // Handle Cash Received Logic
    if (paymentMethod === 'CASH') {
        var rVal = parseFloat(receivedAmountInput.value);
        var total = parseFloat(document.getElementById('ticketTotal').textContent.replace('\u20AC', '').trim());
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

    if (!wantsInvoice) {
        // Cobro simplificado sin cliente
        saleForm.submit();
        return;
    }

    var searchTabActive = document.getElementById('search-tab').classList.contains('active');

    if (searchTabActive) {
        // Cliente Existente
        var selectedId = document.getElementById('existingCustomerId').value;
        if (!selectedId) {
            showError('Por favor, busca y selecciona un cliente.');
            return;
        }
        customerIdInput.value = selectedId;
        saleForm.submit();
    } else {
        // Crear Nuevo Cliente
        var type = document.querySelector('input[name="newCustomerType"]:checked').value;
        var name = document.getElementById('newCustomerName').value.trim();
        var taxId = document.getElementById('newCustomerTaxId').value.trim();
        var address = document.getElementById('newCustomerAddress').value.trim();
        var city = document.getElementById('newCustomerCity').value.trim();
        var postalCode = document.getElementById('newCustomerPostalCode').value.trim();
        var email = document.getElementById('newCustomerEmail').value.trim();
        var phone = document.getElementById('newCustomerPhone').value.trim();

        // Validación básica para ambos (Particular o Empresa)
        if (!name || !taxId || !address || !city || !postalCode) {
            showError('Por favor, rellena todos los campos obligatorios (*).');
            return;
        }

        var newCustomer = { name: name, taxId: taxId, address: address, city: city, postalCode: postalCode, email: email, phone: phone, type: type, active: true };

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
                // Cliente creado con éxito
                customerIdInput.value = savedCustomer.id;
                saleForm.submit();
            })
            .catch(function (err) {
                console.error(err);
                showError('Hubo un problema al crear el cliente. Revisa los datos.');
            });
    }
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Auto-ocultar alerta de éxito tras 4 segundos
var successAlert = document.getElementById('successAlert');
if (successAlert) setTimeout(function () { successAlert.style.display = 'none'; }, 4000);

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

    if (btn) {
        btn.addEventListener('click', function () {
            input.value = '';
            error.textContent = '';
            input.classList.remove('error');
            overlay.classList.add('open');
            setTimeout(function () { input.focus(); }, 100);
        });
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
        container.innerHTML = '<div class="p-3 text-muted">No se encontraron clientes</div>';
    } else {
        customers.forEach(function (c) {
            var div = document.createElement('div');
            div.className = 'customer-search-item';
            div.innerHTML = '<span class="name">' + escapeHtml(c.name) + '</span>' +
                '<span class="details">' + (c.taxId || 'Sin NIF') + ' · ' + (c.city || '') + '</span>';
            div.onclick = function () { selectCustomer(c); };
            container.appendChild(div);
        });
    }
    container.style.display = 'block';
}

function selectCustomer(c) {
    document.getElementById('existingCustomerId').value = c.id;
    document.getElementById('selectedCustomerName').textContent = c.name;
    document.getElementById('selectedCustomerTaxId').textContent = c.taxId || 'Sin NIF';

    document.getElementById('customerSearchInput').value = '';
    document.getElementById('customerSearchResults').style.display = 'none';
    document.getElementById('selectedCustomerDisplay').style.display = 'block';
}

function clearSelectedCustomer() {
    document.getElementById('existingCustomerId').value = '';
    document.getElementById('selectedCustomerDisplay').style.display = 'none';
}

// Cerrar resultados al hacer click fuera
document.addEventListener('click', function (e) {
    if (!e.target.closest('.position-relative')) {
        document.getElementById('customerSearchResults').style.display = 'none';
    }
});

var searchTab = document.getElementById('search-tab');
if (searchTab) {
    searchTab.addEventListener('shown.bs.tab', function () {
        if (document.getElementById('customerSearchInput').value.trim().length === 0) {
            loadAllCustomers();
        }
    });
}

