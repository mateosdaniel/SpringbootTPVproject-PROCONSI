// -- Estado del ticket --
document.addEventListener('DOMContentLoaded', function () {
    const initialGrid = document.querySelector('.products-grid');
    if (initialGrid) {
        initialProductsGridHtml = initialGrid.innerHTML;
        recordOriginalOrder();
    }

    if (typeof attachNifCifValidator === 'function') {
        attachNifCifValidator('newCustomerTaxId');
    }

    // -- Lógica de Favoritos (Servidor) --
    fetch('/api/products/favorites')
        .then(r => r.json())
        .then(favs => {
            window.tpv_user_favorites = favs.map(String);
            window.tpv_user_favorites.forEach(id => {
                document.querySelector(`.product-favorite-btn[data-product-id="${id}"]`)?.classList.replace('bi-star', 'bi-star-fill');
            });
            reorderGridWithFavorites();
            loadFavoriteExtras();
        }).catch(err => console.error('Error loading favorites:', err));

    document.addEventListener('click', function (e) {
        const btn = e.target.closest('.product-favorite-btn');
        if (btn) {
            e.preventDefault();
            e.stopPropagation();
            toggleFavorite(btn);
        }
    }, true);

    // Add RE compatibility listener for TPV quick customer form
    const tpvTariffSelect = document.getElementById('newCustomerTariffId');
    if (tpvTariffSelect) {
        tpvTariffSelect.addEventListener('change', checkNewCustomerReCompatibility);
    }

    setTimeout(updateStockBubbles, 50); // Small delay to ensure all elements are rendered

    // -- Physical Keyboard Listener for PIN Overlay --
    document.addEventListener('keydown', function (e) {
        const overlay = document.getElementById('pinOverlay');
        if (!overlay || overlay.style.display === 'none') return;

        // Numbers 0-9
        if (e.key >= '0' && e.key <= '9') {
            appendPinToSell(e.key);
        } else if (e.key === 'Backspace') {
            clearPinToSell();
        } else if (e.key === 'Enter') {
            if (currentPin.length === 4) {
                submitPinToSell();
            }
        }
    });
});
var ticket = {}; // { productId: { name, price, quantity, stock } }

// ── Estado tipo documento ──────────────────────────────────────────────────
var currentDocType = 'FACTURA_SIMPLIFICADA'; // FACTURA_COMPLETA | FACTURA_SIMPLIFICADA
var currentFacturaPuntual = null; // { nombre, nif, direccion, codigoPostal, ciudad } | null

function validarNifCif(valor) {
    if (!valor) return false;
    var v = valor.trim().toUpperCase();
    // CIF: letra + 7 dígitos + letra/dígito
    if (/^[ABCDEFGHJKLMNPQRSUVW]\d{7}[0-9A-J]$/.test(v)) return true;
    // DNI: 8 dígitos + letra
    if (/^\d{8}[A-Z]$/.test(v)) return true;
    // NIE: X/Y/Z + 7 dígitos + letra
    if (/^[XYZ]\d{7}[A-Z]$/.test(v)) return true;
    return false;
}

function openFacturaPuntualModal() {
    var modal = new bootstrap.Modal(document.getElementById('facturaPuntualModal'));
    // Pre-fill if already entered
    if (currentFacturaPuntual) {
        document.getElementById('fp_nombre').value = currentFacturaPuntual.nombre || '';
        document.getElementById('fp_nif').value = currentFacturaPuntual.nif || '';
        document.getElementById('fp_direccion').value = currentFacturaPuntual.direccion || '';
        document.getElementById('fp_cp').value = currentFacturaPuntual.codigoPostal || '';
        document.getElementById('fp_ciudad').value = currentFacturaPuntual.ciudad || '';
    }
    document.getElementById('facturaPuntualAlert').style.display = 'none';
    modal.show();
}

function aplicarFacturaPuntual() {
    var nombre = document.getElementById('fp_nombre').value.trim();
    var nif = document.getElementById('fp_nif').value.trim().toUpperCase();
    var direccion = document.getElementById('fp_direccion').value.trim();
    var cp = document.getElementById('fp_cp').value.trim();
    var ciudad = document.getElementById('fp_ciudad').value.trim();
    var alertEl = document.getElementById('facturaPuntualAlert');

    if (!nombre || !nif || !direccion || !cp || !ciudad) {
        alertEl.textContent = 'Todos los campos son obligatorios.';
        alertEl.style.display = 'block';
        return;
    }
    if (!validarNifCif(nif)) {
        alertEl.textContent = 'El formato del NIF/CIF no es válido (DNI: 12345678A, NIE: X1234567A, CIF: B12345678).';
        alertEl.style.display = 'block';
        return;
    }

    currentFacturaPuntual = { nombre: nombre, nif: nif, direccion: direccion, codigoPostal: cp, ciudad: ciudad };
    currentDocType = 'FACTURA_COMPLETA';

    // Update summary UI
    document.getElementById('summaryNoCustomerTicket').style.display = 'none';
    document.getElementById('summaryPuntualActive').style.display = 'block';
    document.getElementById('summaryPuntualNombre').textContent = nombre + ' · ';
    document.getElementById('summaryPuntualNif').textContent = 'NIF: ' + nif;

    bootstrap.Modal.getInstance(document.getElementById('facturaPuntualModal')).hide();
}

function cancelarFacturaPuntual() {
    currentFacturaPuntual = null;
    currentDocType = 'FACTURA_SIMPLIFICADA';
    document.getElementById('summaryNoCustomerTicket').style.display = 'block';
    document.getElementById('summaryPuntualActive').style.display = 'none';
    var m = document.getElementById('facturaPuntualModal');
    var inst = m ? bootstrap.Modal.getInstance(m) : null;
    if (inst) inst.hide();
}

function onDocTypeToggle(tipo) {
    currentDocType = tipo;
    var ind = document.getElementById('docTypeIndicatorWithCustomer');
    if (!ind) return;
    if (tipo === 'FACTURA_COMPLETA') {
        ind.textContent = '📄 Se generará Factura Completa';
    } else {
        ind.textContent = '🎫 Se generará Ticket (venta asociada al cliente)';
    }
}

// Helper for i18n in JS
function getTpvI18n(key) {
    const el = document.getElementById('tpv-js-translations');
    if (!el) return '';
    return el.dataset[key] || '';
}

/**
 * Formats a price with dynamic decimal precision.
 * Shows 2 decimals by default, but up to 4 if the price has extra digits.
 */
/**
 * Helper to parse prices in '2.345,67€' format reliably
 */
function parsePrice(text) {
    if (!text) return 0;
    // Remove Euro symbol, non-breaking spaces (\xA0), and then normalize decimal separator
    let clean = text.replace('€', '').replace('\u20AC', '').replace(/\s+/g, '').replace('\u00A0', '');
    // If there's a dot and a comma, the dot is likely thousands separator
    if (clean.includes('.') && clean.includes(',')) {
        clean = clean.replace(/\./g, '').replace(',', '.');
    } else {
        // Just normalize comma to dot
        clean = clean.replace(',', '.');
    }
    let val = parseFloat(clean);
    return isNaN(val) ? 0 : val;
}

function formatPrice(price, decimals) {
    if (price === null || price === undefined) return '0,00';
    var d = Math.max(2, (decimals !== undefined && decimals !== null) ? decimals : 0);
    return parseFloat(price).toLocaleString('es-ES', {
        minimumFractionDigits: d,
        maximumFractionDigits: d
    });
}

// -- Estado de tarifa --
var currentTariffId = null;
var currentDiscountPct = 0; // 0–100
var currentTariffLabel = 'MINORISTA';
var currentHasRE = false;
var currentCoupon = null; // { code, discountType, discountValue }
var cartTariffId = null;  // Override manual del carrito (toma prioridad sobre el cliente)
var autoPromoDiscount = 0; // NxM discount calculated by backend
var appliedPromoNames = [];

/**
 * Returns the effective tariff ID to apply for a given product line.
 * Priority: line-level > cart-level > customer-level > null (MINORISTA default)
 * @param {string} productId  Key in the ticket object
 */
function getEffectiveTariffId(productId) {
    if (ticket[productId] && ticket[productId].lineTariffId) return ticket[productId].lineTariffId;
    if (window.cartTariffId) return window.cartTariffId;
    if (window.currentTariffId) return window.currentTariffId;
    return null;
}

// -- Explicitly global tariff functions --
window.onCartTariffChange = function (selectEl) {
    var newTariffId = selectEl.value || null;
    window.cartTariffId = newTariffId;

    var clearBtn = document.getElementById('cartTariffClearBtn');
    if (clearBtn) clearBtn.style.display = newTariffId ? 'inline' : 'none';

    var productIds = Object.keys(ticket);
    if (productIds.length === 0) { renderTicket(); return; }

    var promises = productIds.map(function (id) {
        if (ticket[id] && ticket[id].lineTariffId) return Promise.resolve();
        var effectiveTariffId = getEffectiveTariffId(id);
        var url = '/tpv/api/products/' + id + '/price';
        if (effectiveTariffId &&
            effectiveTariffId !== 'undefined' &&
            effectiveTariffId !== 'null' &&
            !isNaN(effectiveTariffId)) {
            url += '?tariffId=' + effectiveTariffId;
        }

        console.log('[TPV] Fetching price for product:', id, 'Tariff:', effectiveTariffId, 'URL:', url);

        return fetch(url)
            .then(function (r) {
                if (!r.ok) {
                    console.error('[TPV] Price fetch failed status:', r.status, 'for ID:', id);
                }
                return r.json();
            })
            .then(function (priceData) {
                if (ticket[id]) {
                    ticket[id].price = parseFloat(priceData.price);
                    ticket[id].priceWithRe = parseFloat(priceData.priceWithRe);
                }
            });
    });

    Promise.all(promises).then(function () {
        syncPromotions();
        // Sync tariffIdInput and badge when cart tariff changes
        var sel = document.getElementById('cartTariffSelect');
        var selOption = sel ? sel.options[sel.selectedIndex] : null;
        var selLabel = selOption ? selOption.text : 'MINORISTA';
        var selDiscount = selOption ? parseFloat(selOption.dataset.discount || 0) : 0;
        applyTariffById(newTariffId, selDiscount, selLabel);
    }).catch(function (err) {
        console.error('[CartTariff] Error refreshing prices', err);
        renderTicket();
    });
};

window.clearCartTariff = function () {
    window.cartTariffId = null;
    var sel = document.getElementById('cartTariffSelect');
    if (sel) sel.value = '';
    var clearBtn = document.getElementById('cartTariffClearBtn');
    if (clearBtn) clearBtn.style.display = 'none';
    onCartTariffChange({ value: '' });
};

window.onLineTariffChange = function () {
    var productId = _editPriceProductId;
    if (!productId) return;

    var tariffSel = document.getElementById('editPrice_lineTariff');
    var spinner = document.getElementById('editPrice_tariffFetching');
    var priceInput = document.getElementById('editPrice_newPrice');
    var selectedTariffId = tariffSel ? tariffSel.value : '';

    var url;
    if (!selectedTariffId) {
        var eff = getEffectiveTariffId(productId);
        url = '/tpv/api/products/' + productId + '/price';
        if (eff &&
            eff !== 'undefined' &&
            eff !== 'null' &&
            !isNaN(eff)) {
            url += '?tariffId=' + eff;
        }
    } else {
        url = '/tpv/api/products/' + productId + '/price?tariffId=' + selectedTariffId;
    }

    if (spinner) spinner.style.display = 'inline-flex';
    fetch(url)
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (priceInput) priceInput.value = parseFloat(data.price).toFixed(2);
            if (spinner) spinner.style.display = 'none';
        })
        .catch(function (err) {
            if (spinner) spinner.style.display = 'none';
            console.error('[LineTariff] Error fetching price', err);
        });
};

window._syncLineTariffSelector = function (productId) {
    var lineTariffSel = document.getElementById('editPrice_lineTariff');
    if (!lineTariffSel) return;
    lineTariffSel.value = (ticket[productId] && ticket[productId].lineTariffId)
        ? ticket[productId].lineTariffId
        : '';
};


function addToTicket(card) {
    if (window.tpv_is_register_open !== true) return;
    var id = card.dataset.id;
    var name = card.dataset.name;
    var price = parseFloat(card.dataset.price);
    var stock = parseFloat(card.dataset.stock) || 0;
    var promptOnAdd = card.dataset.promptOnAdd === 'true';
    var unitSymbol = card.dataset.unitSymbol || 'uds.';
    var decimalPlaces = parseInt(card.dataset.decimalPlaces) || 0;
    var categoryId = card.dataset.categoryId;

    var currentQty = ticket[id] ? ticket[id].quantity : 0;

    if (promptOnAdd) {
        // Open professional modal instead of prompt()
        window._pendingAddCard = card;
        document.getElementById('quantityInput').value = '';
        document.getElementById('quantity_productName').textContent = name;
        document.getElementById('quantity_unitSymbol').textContent = unitSymbol;
        document.getElementById('quantity_stockAvailable').textContent = stock.toLocaleString('es-ES', { minimumFractionDigits: decimalPlaces, maximumFractionDigits: decimalPlaces }) + ' ' + unitSymbol;

        var modal = new bootstrap.Modal(document.getElementById('quantityModal'));
        modal.show();

        // Focus input after modal is shown
        document.getElementById('quantityModal').addEventListener('shown.bs.modal', function () {
            var input = document.getElementById('quantityInput');
            input.focus();
            input.onkeydown = function (e) {
                if (e.key === 'Enter') {
                    document.getElementById('confirmQuantityBtn').click();
                }
            };
        }, { once: true });

        // Set up the confirm button (one-time handler)
        var confirmBtn = document.getElementById('confirmQuantityBtn');
        confirmBtn.onclick = function () {
            var valStr = document.getElementById('quantityInput').value;
            var val = parseFloat(valStr.replace(',', '.'));

            if (isNaN(val) || val <= 0) {
                showToast("Cantidad no válida", 'warning');
                return;
            }
            if (currentQty + val > stock) {
                showToast("Stock insuficiente para este producto", 'warning');
                return;
            }

            bootstrap.Modal.getInstance(document.getElementById('quantityModal')).hide();

            // Finish adding
            finishAddingToTicket(id, name, price, val, stock, categoryId, card);
        };
        return;
    } else {
        if (currentQty + 1 > stock) {
            showToast("Stock insuficiente para este producto", 'warning');
            return;
        }
        finishAddingToTicket(id, name, price, 1, stock, categoryId, card);
    }
}

function finishAddingToTicket(id, name, price, quantity, stock, categoryId, card) {
    var decimalPlaces = parseInt(card ? card.dataset.decimalPlaces : 0) || 0;
    if (ticket[id]) {
        ticket[id].quantity += quantity;
    } else {
        ticket[id] = {
            name: name, price: price,
            originalPrice: price,
            quantity: quantity, stock: stock, categoryId: categoryId,
            decimalPlaces: decimalPlaces
        };
    }

    // ALWAYS fetch the price from the API to respect the "only tariffs" rule.
    var effectiveTariffId = getEffectiveTariffId(id);
    var url = '/tpv/api/products/' + id + '/price';
    if (effectiveTariffId &&
        effectiveTariffId !== 'undefined' &&
        effectiveTariffId !== 'null' &&
        !isNaN(effectiveTariffId)) {
        url += '?tariffId=' + effectiveTariffId;
    }

    console.log('[TPV] Adding product fetching price:', id, 'URL:', url);

    fetch(url)
        .then(function (r) {
            if (!r.ok) {
                console.error('[TPV] Add product price fetch failed:', r.status, 'for ID:', id);
            }
            return r.json();
        })
        .then(function (priceData) {
            if (ticket[id]) {
                ticket[id].price = parseFloat(priceData.price);
                ticket[id].priceWithRe = parseFloat(priceData.priceWithRe);
                if (priceData.vatRate != null) ticket[id].vatRate = parseFloat(priceData.vatRate);
                // Keep originalPrice as the catalogue (non-discounted) price
                if (ticket[id].originalPrice == null) {
                    ticket[id].originalPrice = ticket[id].price;
                }
            }
            syncPromotions();
            renderTicket();
        })
        .catch(function (err) {
            console.error('Error fetching tariff price', err);
            renderTicket();
        });

    // Feedback visual en la tarjeta
    card.style.borderColor = 'var(--success)';
    setTimeout(function () { card.style.borderColor = ''; }, 300);
}

function changeQty(id, delta) {
    if (window.tpv_is_register_open !== true) return;
    if (!ticket[id]) return;

    // For products sold by weight, -1 button might not make sense if current is 0.5
    // But we'll keep it simple: -1 or +1.
    var newQty = ticket[id].quantity + delta;

    if (delta > 0 && newQty > ticket[id].stock) {
        showToast("Stock insuficiente para este producto", 'warning');
        return;
    }

    var dp = ticket[id].decimalPlaces !== undefined ? ticket[id].decimalPlaces : 3;
    ticket[id].quantity = Math.max(0, parseFloat(newQty.toFixed(dp)));
    if (ticket[id].quantity <= 0) delete ticket[id];
    syncPromotions();
    renderTicket();
}

function removeLine(id) {
    if (window.tpv_is_register_open !== true) return;
    delete ticket[id];
    syncPromotions();
    renderTicket();
}

function editQty(el, id) {
    if (window.tpv_is_register_open !== true) return;
    var current = ticket[id].quantity;
    var dp = ticket[id].decimalPlaces !== undefined ? ticket[id].decimalPlaces : 0;
    var stepVal = dp > 0 ? Math.pow(10, -dp) : 1;

    var input = document.createElement('input');
    input.type = 'number';
    input.className = 'qty-input';
    input.value = current;
    input.min = stepVal;
    input.step = stepVal;
    input.setAttribute('inputmode', 'decimal');
    el.replaceWith(input);
    input.focus();
    input.select();

    function confirmQty() {
        var val = parseFloat(input.value.replace(',', '.'));
        if (val && val > 0) {
            if (val > ticket[id].stock) {
                showToast("Stock insuficiente para este producto", 'warning');
                renderTicket();
                return;
            }
            ticket[id].quantity = parseFloat(val.toFixed(dp));
        }
        syncPromotions();
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
    var saleNotesTextarea = document.getElementById('saleNotes');
    if (saleNotesTextarea) saleNotesTextarea.value = '';
    
    // Reset notes modal textarea and button label
    const modalNotes = document.getElementById('notesModalTextarea');
    if (modalNotes) modalNotes.value = '';
    const btnLabel = document.getElementById('btnNotesLabel');
    if (btnLabel) btnLabel.textContent = 'Añadir comentario';
    const btnNotes = document.querySelector('.btn-notes');
    if (btnNotes) btnNotes.style.background = 'var(--surface)';

    autoPromoDiscount = 0;
    appliedPromoNames = [];
    renderTicket();
}

function openNotesModal() {
    if (window.tpv_is_register_open !== true) return;
    const globalNotes = document.getElementById('saleNotes').value;
    const modalNotes = document.getElementById('notesModalTextarea');
    if (modalNotes) modalNotes.value = globalNotes;
    new bootstrap.Modal(document.getElementById('notesModal')).show();
}

function saveNotesFromModal() {
    const val = document.getElementById('notesModalTextarea').value;
    const globalNotes = document.getElementById('saleNotes');
    if (globalNotes) globalNotes.value = val;
    
    // Update button visual state
    const btnLabel = document.getElementById('btnNotesLabel');
    const btnNotes = document.querySelector('.btn-notes');
    if (val.trim().length > 0) {
        if (btnLabel) btnLabel.textContent = 'Comentario añadido';
        if (btnNotes) btnNotes.style.background = 'rgba(var(--accent-rgb), 0.1)';
    } else {
        if (btnLabel) btnLabel.textContent = 'Añadir comentario';
        if (btnNotes) btnNotes.style.background = 'var(--surface)';
    }
    
    const modalEl = document.getElementById('notesModal');
    const modalInstance = bootstrap.Modal.getInstance(modalEl);
    if (modalInstance) modalInstance.hide();
    
    showToast('Comentario guardado', 'success');
}

window.openNotesModal = openNotesModal;
window.saveNotesFromModal = saveNotesFromModal;

/**
 * Updates the visual stock bubbles on all product cards.
 * Subtracts the quantity currently in the ticket from the initial data-stock.
 */
function updateStockBubbles() {
    document.querySelectorAll('.product-card').forEach(function (card) {
        if (card.dataset.id === 'wildcard' || card.classList.contains('wildcard-card')) return;
        var id = card.dataset.id;
        var initialStock = parseFloat(card.dataset.stock) || 0;
        var inTicket = ticket[id] ? ticket[id].quantity : 0;
        var available = parseFloat((initialStock - inTicket).toFixed(4));
        var decimalPlaces = parseInt(card.dataset.decimalPlaces) || 0;

        var badge = card.querySelector('.stock-badge');
        if (badge) {
            // Use data attribute instead of textContent to avoid rounding issues causing false animations
            var lastAvailable = parseFloat(badge.dataset.lastAvailable);
            if (isNaN(lastAvailable)) {
                // First time load: parse from text to avoid animation on first render if possible, 
                // but better to just set it.
                lastAvailable = available;
                badge.dataset.lastAvailable = available;
            }

            // Format available to show decimals based on measurement unit
            var dispDecimals = Math.min(3, decimalPlaces);
            badge.textContent = available.toLocaleString('es-ES', {
                minimumFractionDigits: dispDecimals,
                maximumFractionDigits: dispDecimals
            });

            // Update color states
            badge.classList.remove('stock-danger', 'stock-warning', 'stock-neutral');
            if (available <= 0) {
                badge.classList.add('stock-danger');
            } else if (available < 5) {
                badge.classList.add('stock-warning');
            } else {
                badge.classList.add('stock-neutral');
            }

            // Only animate if the numeric value actually changed
            if (Math.abs(lastAvailable - available) > 0.0001) {
                badge.style.transform = 'scale(1.3)';
                setTimeout(function () { badge.style.transform = 'scale(1)'; }, 150);
                badge.dataset.lastAvailable = available;
            }
        }
    });
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
    const abonoSidebar = document.getElementById('abonos-sidebar');
    if (ids.length === 0) {
        if (abonoSidebar) abonoSidebar.style.display = 'none';
        const transTable = document.getElementById('tpv-js-translations');
        const emptyMsg = transTable ? transTable.dataset.ticketEmpty : 'Pulsa un producto para añadirlo';
        linesEl.innerHTML = `
                <div class="ticket-empty">
                    <i class="bi bi-cart"></i>
                    <span>${emptyMsg}</span>
                </div>`;
        countEl.textContent = '0';
        totalEl.textContent = '0.00€';
        cobrarBtn.disabled = true;
        if (suspenderBtn) { suspenderBtn.disabled = true; suspenderBtn.style.opacity = '0.4'; }
        formLines.innerHTML = '';
        updateStockBubbles();
        return;
    }

    var totalItems = 0;
    var totalAmount = 0;
    var totalRE = 0;
    var linesHTML = '';
    var formHTML = ''; ids.forEach(function (id) {
        var item = ticket[id];
        var unitPrice = item.price;
        // Replicar el redondeo fiscal del backend: base+IVA redondeados por separado
        // para que el total del frontend coincida exactamente con el cálculo del servidor.
        var vatRate = item.vatRate != null ? item.vatRate : 0.21;
        var netUnit = unitPrice / (1 + vatRate);
        var baseAmount = Math.round((netUnit * item.quantity + Number.EPSILON) * 100) / 100;
        var vatAmount = Math.round((baseAmount * vatRate + Number.EPSILON) * 100) / 100;
        var subtotal = baseAmount + vatAmount;
        totalItems += item.quantity;
        totalAmount += subtotal;

        if (window.currentHasRE) {
            var vat = item.vatRate || 0.21;
            var reRate = getReRate(vat);
            var net = unitPrice / (1 + vat);
            var reLine = Math.round((net * reRate * item.quantity + Number.EPSILON) * 100) / 100;
            totalRE += reLine;
        }

        // Determine if a non-default tariff is applied to this line
        var lineTariffLabel = null;
        var lineDiscountPct = 0;
        if (item.lineTariffLabel) {
            lineTariffLabel = item.lineTariffLabel;
        } else if (window.cartTariffId && currentTariffLabel && currentTariffLabel !== 'MINORISTA') {
            lineTariffLabel = currentTariffLabel;
        } else if (window.currentTariffId && currentTariffLabel && currentTariffLabel !== 'MINORISTA') {
            lineTariffLabel = currentTariffLabel;
        }
        // Calculate actual discount % from original vs selling price
        if (lineTariffLabel && item.originalPrice && item.originalPrice > 0 && item.price < item.originalPrice) {
            lineDiscountPct = Math.round(((item.originalPrice - item.price) / item.originalPrice) * 100);
        }
        var tariffBadgeHtml = lineTariffLabel
            ? `<span style="font-size:0.68rem; font-weight:700; letter-spacing:0.5px; background:rgba(39,174,96,0.15); color:#27ae60; border:1px solid rgba(39,174,96,0.3); border-radius:4px; padding:1px 5px; margin-left:4px;" title="Tarifa ${escapeHtml(lineTariffLabel)} aplicada"><i class="bi bi-tags-fill" style="margin-right:2px;"></i>${escapeHtml(lineTariffLabel)}${lineDiscountPct > 0 ? ' -' + lineDiscountPct + '%' : ''}</span>`
            : '';

        linesHTML += `
            <div class="ticket-line-item">
                <div class="ticket-line-main">
                    <div class="ticket-line-info">
                        <div class="ticket-line-name">${escapeHtml(item.name)}${tariffBadgeHtml}</div>
                        <div class="ticket-line-detail">
                            ${formatPrice(unitPrice)}€ × ${item.quantity.toLocaleString('es-ES', { maximumFractionDigits: 3 })}
                        </div>
                    </div>
                    <div class="ticket-line-total">${formatPrice(subtotal)}€</div>
                </div>
                <div class="ticket-line-actions">
                    <button class="btn-ticket-action" title="Editar precio / tarifa" onclick="openEditPriceModal('${id}', '${escapeHtml(item.name)}', ${unitPrice})">
                        <i class="bi bi-pencil-square"></i>
                    </button>
                    <div class="ticket-qty-stepper">
                        <button class="btn-qty-step" onclick="changeQty('${id}',-1)"><i class="bi bi-dash"></i></button>
                        <span class="qty-display" onclick="editQty(this, '${id}')">${item.quantity.toLocaleString('es-ES', { maximumFractionDigits: 3 })}</span>
                        <button class="btn-qty-step" onclick="changeQty('${id}',1)"><i class="bi bi-plus"></i></button>
                    </div>
                    <button class="btn-ticket-action danger ms-auto" title="Eliminar" onclick="removeLine('${id}')">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </div>`;

        var isManual = (typeof id === 'string' && id.indexOf('manual-') === 0);
        var productIdToSend = isManual ? 0 : id;
        formHTML += `<input type="hidden" name="productIds" value="${productIdToSend}">`;
        formHTML += `<input type="hidden" name="quantities" value="${item.quantity}">`;
        formHTML += `<input type="hidden" name="unitPrices" value="${unitPrice.toFixed(4)}">`;
        // Send the tariff name applied to this line for invoice traceability
        var lineTariffName = item.lineTariffLabel || currentTariffLabel || 'MINORISTA';
        formHTML += `<input type="hidden" name="lineTariffNames" value="${escapeHtml(lineTariffName)}">`;
        // Send original unit price for discount display on invoice
        var origPrice = item.originalPrice != null ? item.originalPrice : unitPrice;
        formHTML += `<input type="hidden" name="originalUnitPrices" value="${parseFloat(origPrice).toFixed(4)}">`;
        
        // Update tariffId form input to reflect current effective tariff (cart or customer level)
        var effectiveTariffForForm = item.lineTariffId || window.cartTariffId || window.currentTariffId || '';
        var tariffInput = document.getElementById('tariffIdInput');
        if (tariffInput && !tariffInput.value && effectiveTariffForForm) {
            tariffInput.value = effectiveTariffForForm;
        }
        formHTML += `<input type="hidden" name="productNames" value="${escapeHtml(item.name)}">`;
        formHTML += `<input type="hidden" name="vatRates" value="${item.vatRate != null ? item.vatRate : ''}">`;
    });

    // Calculate Coupon Discount
    var couponDiscountAmount = 0;
    if (currentCoupon) {
        var eligibleAmount = 0;
        var hasProductRestrictions = currentCoupon.restrictedProductIds && currentCoupon.restrictedProductIds.length > 0;
        var hasCategoryRestrictions = currentCoupon.restrictedCategoryIds && currentCoupon.restrictedCategoryIds.length > 0;

        if (hasProductRestrictions || hasCategoryRestrictions) {
            ids.forEach(function (id) {
                var item = ticket[id];
                var isProductRestricted = hasProductRestrictions && currentCoupon.restrictedProductIds.map(rid => String(rid)).includes(id);
                var isCategoryRestricted = hasCategoryRestrictions && item.categoryId && currentCoupon.restrictedCategoryIds.map(cid => String(cid)).includes(String(item.categoryId));

                if (isProductRestricted || isCategoryRestricted) {
                    var unitPrice = (window.currentHasRE && item.priceWithRe) ? item.priceWithRe : item.price;
                    eligibleAmount += unitPrice * item.quantity;
                }
            });
        } else {
            eligibleAmount = totalAmount;
        }

        if (eligibleAmount > 0) {
            if (currentCoupon.discountType === 'PERCENTAGE') {
                couponDiscountAmount = eligibleAmount * (currentCoupon.discountValue / 100);
            } else {
                couponDiscountAmount = currentCoupon.discountValue;
            }
            couponDiscountAmount = Math.round((couponDiscountAmount + Number.EPSILON) * 100) / 100;
            if (couponDiscountAmount > eligibleAmount) couponDiscountAmount = eligibleAmount;
        }
    }

    var finalTotal = totalAmount - couponDiscountAmount;
    if (finalTotal < 0) finalTotal = 0;

    // Update UI
    linesEl.innerHTML = linesHTML;
    countEl.textContent = totalItems;
    totalEl.textContent = formatPrice(finalTotal) + '€';
    formLines.innerHTML = formHTML;

    // Coupon UI
    var couponRow = document.getElementById('couponRow');
    var couponCodeInput = document.getElementById('couponCodeInput');
    if (couponRow) {
        if (currentCoupon) {
            couponRow.style.display = 'flex';
            document.getElementById('couponLabel').textContent = currentCoupon.code;
            document.getElementById('couponAmountDisplay').textContent = '-' + couponDiscountAmount.toFixed(2) + '\u20AC';
            if (couponCodeInput) couponCodeInput.value = currentCoupon.code;
        } else {
            couponRow.style.display = 'none';
            if (couponCodeInput) couponCodeInput.value = '';
        }
    }
    formLines.innerHTML = formHTML;
    countEl.textContent = totalItems;

    // Prices stored in ticket[id].price are already final (returned by the backend
    // including any tariff discount). No additional discount calculation is applied here.

    // Recargo de Equivalencia UI
    var reRow = document.getElementById('reRow');
    var reAmountEl = document.getElementById('reAmount');
    if (reRow && reAmountEl) {
        if (window.currentHasRE && totalRE > 0) {
            reRow.style.display = 'flex';
            reAmountEl.textContent = '+' + totalRE.toFixed(2) + '€';
        } else {
            reRow.style.display = 'none';
        }
    }

    // 2. Abonos logic (Single selection via Modal Radio or Manual Input)
    let totalAbonos = 0;
    const selectedAbonoRadio = document.querySelector('.abono-modal-radio:checked');
    if (selectedAbonoRadio) {
        let amt = parseFloat(selectedAbonoRadio.dataset.amount || 0);
        totalAbonos = amt;
    } else if (window._manualAbonoAmount > 0) {
        if (window._manualAbonoType === 'percent') {
            totalAbonos = (totalAmount + totalRE - (couponDiscountAmount || 0) - (autoPromoDiscount || 0)) * (window._manualAbonoAmount / 100);
            totalAbonos = Math.round((totalAbonos + Number.EPSILON) * 100) / 100;
        } else {
            totalAbonos = window._manualAbonoAmount;
        }
    }

    if (abonoSidebar) abonoSidebar.style.display = 'block';

    // Final Calculation: Basis + RE - Coupon - AutoPromo - Abono
    var finalTotal = totalAmount + totalRE - (couponDiscountAmount || 0) - (autoPromoDiscount || 0) - totalAbonos;
    finalTotal = Math.round((finalTotal + Number.EPSILON) * 100) / 100;
    if (finalTotal < 0) finalTotal = 0;
    totalEl.textContent = formatPrice(finalTotal) + '€';

    // Update Abono sidebar total display
    const abonoTotalDisp = document.getElementById('abonosSidebarTotal');
    const manualInputForm = document.getElementById('manualAbonoAmountInput');
    if (abonoTotalDisp) {
        if (totalAbonos > 0) {
            abonoTotalDisp.style.display = 'block';
            
            let pctText = '';
            const divisor = (totalAmount + totalRE - (couponDiscountAmount || 0) - (autoPromoDiscount || 0));
            if (divisor > 0) {
                let pct = (totalAbonos / divisor) * 100;
                pctText = ` (-${pct.toFixed(0)}%)`;
            }
            
            abonoTotalDisp.querySelector('span').textContent = '-' + totalAbonos.toFixed(2) + '€' + pctText;
            // Sync with hidden form input for submission
            if (manualInputForm && window._manualAbonoAmount > 0) {
                manualInputForm.value = totalAbonos.toFixed(2);
            }
        } else {
            abonoTotalDisp.style.display = 'none';
            if (manualInputForm && !selectedAbonoRadio) manualInputForm.value = '';
        }
    }

    // AutoPromo UI
    var autoPromoRow = document.getElementById('autoPromoRow');
    if (autoPromoRow) {
        if (autoPromoDiscount > 0) {
            autoPromoRow.style.display = 'flex';
            autoPromoRow.querySelector('#autoPromoAmountDisplay').textContent = '-' + formatPrice(autoPromoDiscount) + '€';
            autoPromoRow.querySelector('#autoPromoLabel').textContent = appliedPromoNames.join(', ') || 'PROMO NX';
        } else {
            autoPromoRow.style.display = 'none';
        }
    }

    cobrarBtn.disabled = false;
    if (suspenderBtn) { suspenderBtn.disabled = false; suspenderBtn.style.opacity = '1'; }

    // Sync bubbles
    updateStockBubbles();
}

/**
 * Fetches automatic NxM promotion discounts from the backend.
 * Uses a small debounce to avoid excessive requests.
 */
var promoSyncTimeout = null;
function syncPromotions() {
    if (promoSyncTimeout) clearTimeout(promoSyncTimeout);

    var ids = Object.keys(ticket).filter(id => !String(id).startsWith('manual-'));
    if (ids.length === 0) {
        autoPromoDiscount = 0;
        appliedPromoNames = [];
        return;
    }

    promoSyncTimeout = setTimeout(function () {
        var payload = {
            lines: ids.map(id => ({
                productId: parseInt(id),
                quantity: ticket[id].quantity,
                unitPrice: ticket[id].price
            }))
        };

        fetch('/api/promotions/calculate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
            .then(r => r.json())
            .then(data => {
                autoPromoDiscount = parseFloat(data.totalDiscount) || 0;
                appliedPromoNames = data.appliedPromotions || [];
                renderTicket();
            })
            .catch(err => {
                console.error('[Promotions] Error syncing', err);
            });
    }, 150);
}

function selectPayment(method) {
    var btnCash = document.getElementById('btnCash');
    var btnCard = document.getElementById('btnCard');
    var btnMixed = document.getElementById('btnMixed');
    var paymentInput = document.getElementById('paymentMethodInput');

    if (paymentInput) paymentInput.value = method;

    if (btnCash) btnCash.classList.toggle('selected', method === 'CASH');
    if (btnCard) btnCard.classList.toggle('selected', method === 'CARD');
    if (btnMixed) btnMixed.classList.toggle('selected', method === 'MIXED');

    // Automatically switch sections if modal is somehow open
    var cashSection = document.getElementById('cashInputSection');
    var mixedSection = document.getElementById('mixedInputSection');
    if (cashSection && mixedSection) {
        if (method === 'CASH') {
            cashSection.style.display = 'block';
            mixedSection.style.display = 'none';
        } else if (method === 'MIXED') {
            cashSection.style.display = 'none';
            mixedSection.style.display = 'block';
        } else {
            cashSection.style.display = 'none';
            mixedSection.style.display = 'none';
        }
    }
}

function submitSale() {
    if (Object.keys(ticket).length === 0) return;

    const form = document.getElementById('saleForm');
    const formData = new URLSearchParams(new FormData(form));

    const btn = document.getElementById('btnSubmitCheckout');
    if (btn) btn.disabled = true;

    fetch(form.action, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData.toString()
    })
        .then(r => {
            if (r.redirected) {
                window.location.href = r.url; // Use same tab
            } else if (r.ok) {
                return r.json().then(data => {
                    if (data.redirectUrl) window.location.href = data.redirectUrl;
                    else location.reload();
                });
            } else {
                return r.json().then(data => { throw new Error(data.error || 'Error al procesar venta'); });
            }
        })
        .catch(err => {
            showToast(err.message, 'error');
            if (btn) btn.disabled = false;
        });
}

var invoiceModalInstance;

function openCheckoutModal() {
    if (Object.keys(ticket).length === 0) return;

    // Determine if payment is CASH or MIXED
    var method = document.getElementById('paymentMethodInput').value;
    var isCash = method === 'CASH';
    var isMixed = method === 'MIXED';
    var cashSection = document.getElementById('cashInputSection');
    var mixedSection = document.getElementById('mixedInputSection');
    var receivedInput = document.getElementById('receivedAmount');
    var receivedInputForm = document.getElementById('receivedAmountInput');

    if (isCash) {
        if (cashSection) cashSection.style.display = 'block';
        if (mixedSection) mixedSection.style.display = 'none';
        receivedInput.value = '';
        calculateChange();
        receivedInputForm.value = '';
    } else if (isMixed) {
        if (cashSection) cashSection.style.display = 'none';
        if (mixedSection) mixedSection.style.display = 'block';
        document.getElementById('mixedCardAmount').value = '';
        document.getElementById('mixedCashAmount').value = '';
        var tTotal = document.getElementById('ticketTotal').textContent;
        document.getElementById('mixedRemainingAmount').textContent = 'Faltan ' + tTotal;
        document.getElementById('mixedRemainingAmount').className = 'fw-bold fs-5 text-danger';
        receivedInputForm.value = '';
    } else {
        if (cashSection) cashSection.style.display = 'none';
        if (mixedSection) mixedSection.style.display = 'none';
        receivedInput.value = '';
        calculateChange();
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
        // Reset toggle to Factura (default with customer)
        var radioFactura = document.getElementById('docTypeFactura');
        if (radioFactura) { radioFactura.checked = true; }
        currentDocType = 'FACTURA_COMPLETA';
        currentFacturaPuntual = null;
        var ind = document.getElementById('docTypeIndicatorWithCustomer');
        if (ind) ind.textContent = '📄 Se generará Factura Completa';
    } else {
        summaryWith.style.display = 'none';
        summaryNo.style.display = 'block';
        // Reset puntual state
        currentDocType = 'FACTURA_SIMPLIFICADA';
        currentFacturaPuntual = null;
        var noTicket = document.getElementById('summaryNoCustomerTicket');
        var puntualActive = document.getElementById('summaryPuntualActive');
        if (noTicket) noTicket.style.display = 'block';
        if (puntualActive) puntualActive.style.display = 'none';
    }

    invoiceModalInstance = new bootstrap.Modal(document.getElementById('checkoutModal'));
    invoiceModalInstance.show();

    if (isCash) {
        setTimeout(function () {
            receivedInput.focus();
        }, 150);
    }
}
// Live calculation for change
function calculateChange() {
    var input = document.getElementById('receivedAmount');
    if (!input) return;

    var inputVal = input.value.trim();
    var received = inputVal === '' ? 0 : parseFloat(inputVal.replace(',', '.'));
    var totalElement = document.getElementById('ticketTotal');
    if (!totalElement) return;

    var total = parsePrice(totalElement.textContent);

    // Subtract selected abonos from the total to pay
    const selectedAbonoRadio = document.querySelector('.abono-modal-radio:checked');
    let totalAbono = selectedAbonoRadio ? parseFloat(selectedAbonoRadio.dataset.amount) : 0;
    total = Math.max(0, total - totalAbono);

    var changeEl = document.getElementById('changeAmount');
    if (!changeEl) return;

    var diff = received - total;

    const transTable = document.getElementById('tpv-js-translations');
    const missingTr = transTable ? transTable.dataset.checkoutMissing : 'Faltan';

    if (received === 0 && total === 0) {
        // Realmente es cero
        changeEl.textContent = '0,00€';
        changeEl.style.color = 'var(--text-muted)';
    } else if (diff < 0) {
        // Falta dinero
        changeEl.textContent = `${missingTr} ${Math.abs(diff).toFixed(2).replace('.', ',')}€`;
        changeEl.style.color = 'var(--danger)';
    } else {
        // Cambio o exacto
        changeEl.textContent = diff.toFixed(2).replace('.', ',') + '€';
        changeEl.style.color = diff > 0 ? 'var(--success)' : 'var(--text-muted)';
    }
}

function setExactAmount() {
    var totalText = document.getElementById('ticketTotal').textContent;
    var totalVal = parsePrice(totalText);
    var input = document.getElementById('receivedAmount');
    input.value = totalVal.toFixed(2);
    // Explicitly update change calculation
    calculateChange();
}

function calculateMixedChange() {
    var cardVal = parseFloat(document.getElementById('mixedCardAmount').value) || 0;
    var cashVal = parseFloat(document.getElementById('mixedCashAmount').value) || 0;
    var total = parsePrice(document.getElementById('ticketTotal').textContent);
    var remainingEl = document.getElementById('mixedRemainingAmount');

    const transTable = document.getElementById('tpv-js-translations');
    const missingTr = transTable ? transTable.dataset.checkoutMissing : 'Faltan';
    const exactTr = transTable ? transTable.dataset.checkoutExact : 'Exacto';
    const changeTr = transTable ? transTable.dataset.checkoutChangePrefix : 'Cambio';

    var diff = (cardVal + cashVal) - total;
    if (diff < 0) {
        remainingEl.textContent = `${missingTr} ${Math.abs(diff).toFixed(2).replace('.', ',')}€`;
        remainingEl.className = 'fw-bold fs-5 text-danger';
    } else if (diff === 0) {
        remainingEl.textContent = `0,00€ (${exactTr})`;
        remainingEl.className = 'fw-bold fs-5 text-success';
    } else {
        remainingEl.textContent = `${changeTr}: ${diff.toFixed(2).replace('.', ',')}€`;
        remainingEl.className = 'fw-bold fs-5 text-success';
    }
}

function fillMixedMissing(type) {
    var total = parsePrice(document.getElementById('ticketTotal').textContent);
    var cardInput = document.getElementById('mixedCardAmount');
    var cashInput = document.getElementById('mixedCashAmount');

    var currentCard = parseFloat(cardInput.value) || 0;
    var currentCash = parseFloat(cashInput.value) || 0;

    if (type === 'card') {
        var missing = total - currentCash;
        if (missing > 0) cardInput.value = missing.toFixed(2);
    } else {
        var missing = total - currentCard;
        if (missing > 0) cashInput.value = missing.toFixed(2);
    }
    calculateMixedChange();
}



function checkNewCustomerReCompatibility() {
    const tariffSelect = document.getElementById('newCustomerTariffId');
    const reCheckbox = document.getElementById('newCustomerHasRecargo');
    const reSection = document.getElementById('newCustomerReSection');
    const reWarning = document.getElementById('newCustomerReIncompatibleMsg');

    if (!tariffSelect || !reCheckbox) return;

    const selectedOption = tariffSelect.options[tariffSelect.selectedIndex];
    const tariffText = selectedOption ? selectedOption.text.toLowerCase() : "";
    const isMinorista = (tariffSelect.value === "" || tariffText.includes("minorista"));

    if (isMinorista) {
        reCheckbox.disabled = false;
        if (reSection) reSection.style.opacity = "1";
        if (reWarning) reWarning.classList.add('d-none');
    } else {
        reCheckbox.checked = false;
        reCheckbox.disabled = true;
        if (reSection) reSection.style.opacity = "0.7";
        if (reWarning) {
            reWarning.classList.remove('d-none');
        }
    }
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
            : 'NIF/NIE <span class="text-danger">*</span>';
    }

    if (taxInput) taxInput.setAttribute('required', 'required');

    // Show RE toggle only for Companies
    var reSection = document.getElementById('newCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) {
            document.getElementById('newCustomerHasRecargo').checked = false;
        } else {
            // Check compatibility if section becomes visible
            checkNewCustomerReCompatibility();
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
    var saleNotesTextarea = document.getElementById('saleNotes');
    var hiddenNotesInput = document.getElementById('notesInput');

    // Collect notes
    if (saleNotesTextarea && hiddenNotesInput) {
        hiddenNotesInput.value = saleNotesTextarea.value.trim();
    }

    // Determine if a customer was selected in the sidebar
    var customerId = document.getElementById('customerIdInput').value;
    var hasCustomer = !!customerId;

    // Set invoice flag (legacy compat) and new document-type hidden inputs
    if (requestInvoiceInput) {
        requestInvoiceInput.value = (hasCustomer || currentFacturaPuntual) ? 'true' : 'false';
    }
    var tipoDocInput = document.getElementById('tipoDocumentoParamInput');
    var puntualJsonInput = document.getElementById('clientePuntualJsonInput');
    if (tipoDocInput) tipoDocInput.value = currentDocType;
    if (puntualJsonInput) {
        puntualJsonInput.value = currentFacturaPuntual ? JSON.stringify(currentFacturaPuntual) : '';
    }

    // Validar límite de pago en efectivo (Ley 11/2021)
    var total = parsePrice(document.getElementById('ticketTotal').textContent);
    var customerIdInputEl = document.getElementById('customerIdInput');
    var customerTypeInputEl = document.getElementById('customerTypeInput');

    var customerId = customerIdInputEl ? customerIdInputEl.value : null;
    var customerType = customerTypeInputEl ? customerTypeInputEl.value : null;

    if (paymentMethod === 'CASH') {
        if (customerType === 'COMPANY' && total >= 1000) {
            showError('No es posible realizar pagos en efectivo superiores a 1.000€ entre empresarios (Ley 11/2021). Seleccione otro método de pago.');
            return;
        } else if (total >= 10000) {
            // Particular limit warning but allow submission as per user instruction
            showToast('Aviso: El pago en efectivo supera los 10.000 €. Superar los límites legales puede conllevar sanciones según la Ley 11/2021.', 'warning');
        }
    }

    // Handle Cash Received Logic
    if (paymentMethod === 'CASH') {
        var rVal = parseFloat(receivedAmountInput.value);
        if (isNaN(rVal)) {
            showError('Por favor, indica la cantidad entregada en efectivo.');
            return;
        }
        if (rVal < 0) {
            showError('No se permiten importes de pago negativos.');
            return;
        }
        if (rVal < total) {
            showError('La cantidad entregada es menor al total a cobrar.');
            return;
        }
        hiddenReceivedAmount.value = rVal.toFixed(2);
    } else if (paymentMethod === 'MIXED') {
        var cardVal = parseFloat(document.getElementById('mixedCardAmount').value) || 0;
        var cashVal = parseFloat(document.getElementById('mixedCashAmount').value) || 0;
        if (cardVal < 0 || cashVal < 0) {
            showError('No se permiten importes de pago negativos.');
            return;
        }
        if ((cardVal + cashVal) < total) {
            showError('La cantidad mixta entregada es menor al total a cobrar.');
            return;
        }
        document.getElementById('cardAmountInput').value = cardVal.toFixed(2);
        document.getElementById('cashAmountInput').value = cashVal.toFixed(2);
    } else {
        hiddenReceivedAmount.value = '';
    }

    // --- Offline Handling ---
    if (!navigator.onLine) {
        var cashAmt = document.getElementById('cashAmountInput').value;
        var cardAmt = document.getElementById('cardAmountInput').value;
        saveOfflineSale({
            paymentMethod: paymentMethod,
            customerId: customerId,
            receivedAmount: hiddenReceivedAmount.value,
            cashAmount: cashAmt,
            cardAmount: cardAmt,
            requestInvoice: hasCustomer,
            total: total,
            lines: Object.keys(ticket).map(id => ({
                productId: id,
                quantity: ticket[id].quantity,
                unitPrice: ticket[id].price
            }))
        });
        return; // Prevent standard form submission
    }

    // customerIdInput already set by selectCustomer() in sidebar — just submit
    
    // ── COLECTAR ABONOS SELECCIONADOS ──
    const formAbonos = document.getElementById('formAbonoIds');
    if (formAbonos) {
        formAbonos.innerHTML = '';
        const selectedAbono = document.querySelector('.abono-modal-radio:checked');
        if (selectedAbono) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'abonoIds';
            input.value = selectedAbono.value;
            formAbonos.appendChild(input);
        }
    }

    saleForm.submit();
}

/**
 * Render function for abonos sidebar
 */
function renderAbonosSidebar(abonos) {
    const section = document.getElementById('abonos-sidebar');
    if (!section) return;

    // El sidebar siempre debe estar visible si hay ticket, no lo ocultamos aquí
    /*
    if (!abonos || abonos.length === 0) {
        section.style.display = 'none';
        return;
    }
    */

    // Solo mostramos el botón de acción
    section.style.display = 'block';
}

function updateAbonoTotal() {
    // This was used when abonos were in the modal. Now it's handled by renderTicket().
    renderTicket();
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
var initialProductsGridHtml = ''; // Caché para el estado inicial de Thymeleaf

function loadProducts(endpoint) {
    if (endpoint === '/api/products') {
        const grid = document.querySelector('.products-grid');
        if (grid && initialProductsGridHtml) {
            grid.innerHTML = initialProductsGridHtml;
            recordOriginalOrder();
            // Refetch or reset stars based on current session favorites
            if (window.tpv_user_favorites) {
                window.tpv_user_favorites.forEach(id => {
                    grid.querySelector(`.product-favorite-btn[data-product-id="${id}"]`)?.classList.replace('bi-star', 'bi-star-fill');
                });
                reorderGridWithFavorites();
                loadFavoriteExtras();
            }
            if (typeof updateStockBubbles === 'function') updateStockBubbles();
        }
        return;
    }

    fetch(endpoint)
        .then(function (response) {
            if (!response.ok) throw new Error('Network response was not ok');
            return response.json();
        })
        .then(function (products) {
            if (products) renderProducts(products);
            if (endpoint === '/api/products') loadFavoriteExtras();
        })
        .catch(function (error) { console.error('Error loading products:', error); });
}

function renderProducts(data, append = false) {
    var productGrid = document.querySelector('.products-grid');
    if (!productGrid) return;

    var products = Array.isArray(data) ? data : (data.content || []);

    var wildcardHtml = `
                <!-- Wildcard Product (Always first) -->
                <div class="product-card wildcard-card ${window.tpv_is_register_open ? '' : 'disabled-tpv'}"
                    onclick="event.stopPropagation(); openWildcardModal()" data-id="wildcard" data-stock="&infin;"
                    style="border: 2px dashed var(--accent); background: rgba(var(--accent-rgb), 0.05);">
                    <div class="product-image-container">
                        <i class="bi bi-plus-circle-dotted product-icon"
                            style="color: var(--accent); font-size: 2.5rem;"></i>
                        <span class="stock-badge stock-neutral"
                            style="background: var(--accent); color: var(--primary);">∞</span>
                    </div>
                    <div class="product-info text-center">
                        <div class="product-name" style="color: var(--accent); font-weight: 800; font-size: 0.95rem;">
                            PRODUCTO COMODÍN</div>
                        <div class="product-price" style="color: var(--text-muted); font-size: 0.85rem;">Nombre y precio
                            manual</div>
                        <div class="product-category-badge"
                            style="background: var(--accent); color: var(--primary); font-weight: 700;">GENÉRICO</div>
                    </div>
                </div>`;

    if (!append && (!products || products.length === 0)) {
        const transTable = document.getElementById('tpv-js-translations');
        const isSearch = searchInput && searchInput.value.trim().length > 0;
        
        let noProductsMsg = 'No hay productos disponibles';
        if (transTable) {
            noProductsMsg = isSearch ? 
                (transTable.dataset.searchNoResults || 'No se han encontrado coincidencias') : 
                (transTable.dataset.noProducts || 'No hay productos disponibles');
        }

        productGrid.innerHTML = wildcardHtml + `
                <div class="no-products" style="grid-column: 1/-1; text-align: center;">
                    <i class="bi bi-search" style="font-size: 3rem; display: block; margin-bottom: 1rem; color: var(--text-muted);"></i>
                    <p>${noProductsMsg}</p>
                </div>`;
        return;
    }

    var productsHtml = products.map(function (product) {
        var imgHtml = product.imageUrl
            ? '<img src="' + escapeHtml(product.imageUrl) + '" alt="Imagen producto" class="product-image">'
            : '<i class="bi bi-box product-icon"></i>';

        var catName = product.category ? escapeHtml(product.category.name) : '';
        var catId = product.category ? product.category.id : '';
        var mu = product.measurementUnit || {};
        var unitSymbol = mu.symbol || 'uds.';
        var decimalPlaces = mu.decimalPlaces !== undefined ? mu.decimalPlaces : (mu.decimal_places !== undefined ? mu.decimal_places : 0);
        var initialStock = parseFloat(product.stock) || 0;
        var inTicket = ticket[product.id] ? ticket[product.id].quantity : 0;
        var available = initialStock - inTicket;

        var badgeClass = 'stock-neutral';
        if (available <= 0) badgeClass = 'stock-danger';
        else if (available < 5) badgeClass = 'stock-warning';

        var dispDecimals = Math.min(3, decimalPlaces);
        var disabledClass = window.tpv_is_register_open ? '' : ' disabled-tpv';
        var outOfStockClass = (available <= 0 && window.tpv_is_register_open) ? ' out-of-stock' : '';

        var isFav = (window.tpv_user_favorites || []).includes(String(product.id));
        var starClass = isFav ? 'bi-star-fill' : 'bi-star';

        return `<div class="product-card${disabledClass}${outOfStockClass}" 
                     style="position: relative; animation: fadeIn 0.3s ease;"
                     data-id="${product.id}" 
                     data-name="${escapeHtml(product.name)}" 
                     data-price="${product.price}"
                     data-category="${catName}"
                     data-category-id="${catId}"
                     data-stock="${product.stock}"
                     data-sales-rank="${product.salesRank || 0}"
                     data-prompt-on-add="${decimalPlaces > 0}"
                     data-unit-symbol="${unitSymbol}"
                     data-decimal-places="${decimalPlaces}">
                    <i class="bi ${starClass} product-favorite-btn" 
                       data-product-id="${product.id}"
                       style="position: absolute; top: 8px; left: 8px; z-index: 10; font-size: 1.25rem; cursor: pointer; text-shadow: 0 0 3px rgba(0,0,0,0.3);"></i>
                    <div class="product-image-container">
                        ${imgHtml}
                        <span class="stock-badge ${badgeClass}"
                              data-initial-stock="${initialStock}">
                            ${available.toLocaleString('es-ES', { minimumFractionDigits: dispDecimals, maximumFractionDigits: dispDecimals })}
                        </span>
                    </div>
                    <div class="product-info text-center">
                        <div class="product-name">${escapeHtml(product.name)}</div>
                        <div class="product-price">${formatPrice(product.price, decimalPlaces)}€</div>
                        <div class="product-category-badge">${catName}</div>
                    </div>
                </div>`;
    }).join('');

    if (append) {
        var temp = document.createElement('div');
        temp.innerHTML = productsHtml;
        while (temp.firstChild) {
            productGrid.insertBefore(temp.firstChild, productGrid.firstChild);
        }
    } else {
        productGrid.innerHTML = wildcardHtml + productsHtml;
    }

    // Ensure bubbles are up-to-date and have their correct colors/animations
    updateStockBubbles();
    reorderGridWithFavorites();
}

function toggleFavorite(btn) {
    const id = btn.dataset.productId;
    const isAdding = btn.classList.contains('bi-star');

    btn.classList.add('favorite-pulse');
    setTimeout(() => btn.classList.remove('favorite-pulse'), 400);

    fetch(`/api/products/${id}/favorite`, { method: isAdding ? 'POST' : 'DELETE' })
        .then(r => {
            if (r.ok) {
                const grid = document.querySelector('.products-grid');
                const existingCard = grid ? grid.querySelector('[data-id="' + id + '"]') : null;

                if (isAdding) {
                    btn.classList.replace('bi-star', 'bi-star-fill');
                    if (!window.tpv_user_favorites.includes(id)) window.tpv_user_favorites.push(id);

                    if (existingCard) {
                        // Ensure it's visible and move to front
                        existingCard.style.display = '';
                        grid.insertBefore(existingCard, grid.firstChild);
                    } else {
                        // Fetch and create new card
                        fetch('/api/products/' + id)
                            .then(response => response.json())
                            .then(product => {
                                renderProducts([product], true);
                            });
                    }
                } else {
                    btn.classList.replace('bi-star-fill', 'bi-star');
                    window.tpv_user_favorites = window.tpv_user_favorites.filter(f => f !== id);

                    const isSearchActive = searchInput && searchInput.value.trim() !== '';
                    const salesRank = existingCard ? parseInt(existingCard.dataset.salesRank ?? '1', 10) : 1;
                    const shouldRemove = existingCard && !isSearchActive && salesRank === 0;

                    if (shouldRemove) {
                        existingCard.remove();
                    }
                }
                reorderGridWithFavorites();
            }
        });
}

function reorderGridWithFavorites() {
    const grid = document.querySelector('.products-grid');
    if (!grid) return;

    const cards = Array.from(grid.querySelectorAll('.product-card:not(.wildcard-card)'));
    const wildcard = grid.querySelector('.wildcard-card');

    // Sort logic: Favorites first, then respect original ID sequence
    cards.sort((a, b) => {
        const aFav = a.querySelector('.product-favorite-btn.bi-star-fill') ? 1 : 0;
        const bFav = b.querySelector('.product-favorite-btn.bi-star-fill') ? 1 : 0;

        if (aFav !== bFav) return bFav - aFav;

        // If both are same status, respect original order
        const aIdx = window.originalCardOrder ? window.originalCardOrder.indexOf(String(a.dataset.id)) : 0;
        const bIdx = window.originalCardOrder ? window.originalCardOrder.indexOf(String(b.dataset.id)) : 0;
        return aIdx - bIdx;
    });

    // Re-append to preserve DOM nodes (and their listeners if any, though we use delegation)
    if (wildcard) grid.appendChild(wildcard);
    cards.forEach(c => grid.appendChild(c));
}

function recordOriginalOrder() {
    window.originalCardOrder = Array.from(document.querySelectorAll('.products-grid .product-card:not(.wildcard-card)'))
        .map(card => String(card.dataset.id));
}

function loadFavoriteExtras() {
    const grid = document.querySelector('.products-grid');
    if (!grid || !window.tpv_user_favorites) return;

    const missingIds = window.tpv_user_favorites.filter(id =>
        !grid.querySelector('[data-id="' + id + '"]')
    );

    Promise.all(missingIds.map(id =>
        fetch('/api/products/' + id)
            .then(r => r.json())
            .then(product => renderProducts([product], true))
    )).then(() => reorderGridWithFavorites());
}

// -- PRODUCT GRID SEARCH (Back to Basics) --
var productSearchTimeout = null;

function performGridSearch() {
    var query = searchInput.value.trim();
    var clearBtn = document.getElementById('clearSearchBtn');

    if (clearBtn) clearBtn.style.display = query.length > 0 ? 'block' : 'none';

    if (query.length < 2) {
        if (query.length === 0) loadProducts('/api/products');
        return;
    }

    clearTimeout(productSearchTimeout);
    productSearchTimeout = setTimeout(function () {
        fetch(`/api/products/search?q=${encodeURIComponent(query)}&size=100`)
            .then(r => r.json())
            .then(data => {
                var products = data.content || data; // Handle both paginated and list responses
                renderProducts(products);
                // After searching, categories should be inactive
                updateCategoryButtons(null);
            })
            .catch(err => console.error('[GridSearch] Error:', err));
    }, 300);
}

// Event delegation for product card clicks - attach to the products container
if (productsContainer) {
    productsContainer.addEventListener('click', function (e) {
        var productCard = e.target.closest('.product-card');
        if (productCard && !productCard.classList.contains('wildcard-card')) {
            addToTicket(productCard);
        }
    });
}

// Global protection: Prevent typing '-' or 'e' in numeric inputs that don't allow negatives
document.addEventListener('keydown', function (e) {
    if (e.target && e.target.tagName === 'INPUT' && e.target.type === 'number') {
        const minAttr = e.target.getAttribute('min');
        if (minAttr !== null && parseFloat(minAttr) >= 0) {
            if (e.key === '-' || e.key === 'e' || e.key === 'E') {
                e.preventDefault();
            }
        }
    }
});

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

// Real-time grid search
if (searchInput) {
    searchInput.addEventListener('input', performGridSearch);

    // Clear button functionality
    var clearBtn = document.getElementById('clearSearchBtn');
    if (clearBtn) {
        clearBtn.addEventListener('click', function () {
            searchInput.value = '';
            performGridSearch();
            searchInput.focus();
        });
    }
}

// Category button click handlers
var catAllBtn = document.getElementById('catAll');
if (catAllBtn) {
    catAllBtn.addEventListener('click', function () {
        searchInput.value = '';
        currentCategoryId = null;
        updateCategoryButtons(null);
        loadProducts('/api/products');
        loadFavoriteExtras();
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
function openAdminPinModal() {
    var overlay = document.getElementById('adminPinOverlay');
    var input = document.getElementById('pinInput');
    var error = document.getElementById('pinError');
    if (!overlay || !input || !error) return;

    input.value = '';
    error.textContent = '';
    input.classList.remove('error');
    overlay.classList.add('open');
    setTimeout(function () { input.focus(); }, 100);
}

function submitAdminPin() {
    var input = document.getElementById('pinInput');
    var error = document.getElementById('pinError');
    if (!input || !error) return;

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

function closeAdminPinModal() {
    var overlay = document.getElementById('adminPinOverlay');
    if (overlay) overlay.classList.remove('open');
}

document.addEventListener('DOMContentLoaded', function () {
    var pinSubmit = document.getElementById('pinSubmit');
    if (pinSubmit) pinSubmit.addEventListener('click', submitAdminPin);

    var pinCancel = document.getElementById('pinCancel');
    if (pinCancel) pinCancel.addEventListener('click', closeAdminPinModal);

    var adminPinInput = document.getElementById('pinInput');
    if (adminPinInput) {
        adminPinInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') submitAdminPin();
            if (e.key === 'Escape') closeAdminPinModal();
        });
    }
});

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
            var container = document.getElementById('customerSearchResults');
            if (container) {
                container.style.display = 'none';
                container.innerHTML = '';
            }
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
        var query = this.value.trim();
        if (query.length === 0) {
            loadAllCustomers();
        } else {
            fetch('/api/customers/search?query=' + encodeURIComponent(query))
                .then(function (r) { return r.json(); })
                .then(function (customers) {
                    renderCustomerResults(customers);
                })
                .catch(function (err) { console.error('Error searching customers', err); });
        }
    });
}

function renderCustomerResults(customers) {
    var container = document.getElementById('customerSearchResults');
    var input = document.getElementById('customerSearchInput');

    container.innerHTML = '';

    if (customers.length === 0) {
        const transTable = document.getElementById('tpv-js-translations');
        const notFoundMsg = transTable ? transTable.dataset.customerNotFound : 'No se encontraron clientes';
        container.innerHTML = `<div class="p-3" style="color: var(--text-main);">${notFoundMsg}</div>`;
    } else {
        customers.forEach(function (c) {
            var div = document.createElement('div');
            div.className = 'customer-search-item';
            div.style.color = 'var(--text-main)';
            div.style.backgroundColor = 'var(--surface)';
            div.innerHTML = '<span style="color: var(--text-main); display:block; font-weight:600;">' + escapeHtml(c.name) + '</span>' +
                '<span style="color: var(--text-muted); font-size: 0.75rem;">' +
                (c.taxId || 'Sin NIF') + ' · ' + (c.city || '') + '</span>';
            div.onclick = function () { selectCustomer(c); };
            container.appendChild(div);
        });
    }

    container.style.display = 'block';
}

function selectCustomer(c) {
    document.getElementById('customerIdInput').value = c.id;
    document.getElementById('customerTypeInput').value = c.type;
    document.getElementById('sidebarCustomerName').textContent = c.name;
    document.getElementById('sidebarCustomerTaxId').textContent = c.taxId || 'Sin NIF';

    document.getElementById('customerSearchInput').value = '';
    document.getElementById('customerSearchResults').style.display = 'none';
    document.getElementById('customerSearchResults').innerHTML = '';
    document.getElementById('selectedCustomerCard').style.display = 'block';
    document.getElementById('customerSelectionControls').style.display = 'none';

    window.currentHasRE = !!c.hasRecargoEquivalencia;
    // Auto-apply the customer's tariff and update ticket prices
    var badge = document.getElementById('sidebarTariffBadge');
    if (c.tariff) {
        window.currentTariffId = c.tariff.id;
        window.currentTariffColor = c.tariff.color;

        if (badge) {
            badge.textContent = c.tariff.name + ' (-' + (c.tariff.discountPercentage || 0) + '%)';
            badge.style.display = 'inline-block';
            if (c.tariff.color) badge.style.backgroundColor = c.tariff.color;
        }

        updateTicketPricesForTariff(c.tariff.id, c.tariff.name, parseFloat(c.tariff.discountPercentage || 0));
    } else {
        window.currentTariffId = null;
        window.currentTariffColor = null;
        if (badge) badge.style.display = 'none';
        updateTicketPricesForTariff(null, 'MINORISTA', 0);
    }

    // ── CARGAR ABONOS DEL CLIENTE PARA EL CARRITO ──
    fetch(`/api/customers/${c.id}/abonos`)
        .then(r => r.json())
        .then(abonos => renderAbonosSidebar(abonos))
        .catch(err => console.error("Error loading customer abonos:", err));
}

function clearSelectedCustomer() {
    document.getElementById('customerIdInput').value = '';
    document.getElementById('customerTypeInput').value = '';
    document.getElementById('selectedCustomerCard').style.display = 'none';
    document.getElementById('customerSelectionControls').style.display = 'block';
    
    // No ocultamos el sidebar de abonos ya que ahora permite entrada manual/búsqueda
    // const abonosSection = document.getElementById('abonos-sidebar');
    // if (abonosSection) abonosSection.style.display = 'none';
    var _csr = document.getElementById('customerSearchResults');
    if (_csr) { _csr.style.display = 'none'; _csr.innerHTML = ''; }
    window.currentTariffId = null; // clear so addToTicket uses base prices again
    window.currentTariffColor = null;
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

    var promises = productIds.map(function (id) {
        // Lines with their own tariff override are NOT updated here — they keep their own price
        if (ticket[id] && ticket[id].lineTariffId) return Promise.resolve();

        var url = '/tpv/api/products/' + id + '/price';
        if (tariffId) url += '?tariffId=' + tariffId;

        return fetch(url)
            .then(function (r) { return r.json(); })
            .then(function (priceData) {
                if (ticket[id]) {
                    // Preserve catalogue price before updating to tariff price
                    if (ticket[id].originalPrice == null) {
                        ticket[id].originalPrice = ticket[id].price;
                    }
                    ticket[id].price = parseFloat(priceData.price);
                    ticket[id].priceWithRe = parseFloat(priceData.priceWithRe);
                    if (priceData.vatRate != null) ticket[id].vatRate = parseFloat(priceData.vatRate);
                }
            });
    });

    Promise.all(promises).then(function () {
        // Recalculate promotions now that prices have changed
        syncPromotions();
        // discountPct=0: prices are already final, renderTicket must not re-discount
        applyTariffById(tariffId, 0, tariffName);
        var badge = document.getElementById('sidebarTariffBadge');
        if (badge) {
            badge.textContent = tariffName + (discountPct > 0 ? ' -' + discountPct + '%' : '');
            badge.style.display = 'inline-block';

            // Apply subtle colors
            badge.className = 'badge-tariff-small'; // Reset to base classes

            // If the tariff object has a custom color, use it!
            // We need to pass the color to this function or get it from global scope
            // For now, let's assume we can find it in the current customer if we are here
            if (window.currentTariffColor) {
                badge.style.backgroundColor = window.currentTariffColor + '15';
                badge.style.color = window.currentTariffColor;
                badge.style.borderColor = window.currentTariffColor + '30';
                badge.style.borderStyle = 'solid';
                badge.style.borderWidth = '1px';
            } else {
                badge.style.backgroundColor = '';
                badge.style.color = '';
                badge.style.borderColor = '';
                var lowerName = tariffName.toLowerCase();
                if (lowerName.includes('minorista')) {
                    badge.classList.add('badge-tariff-minorista');
                } else if (lowerName.includes('mayorista')) {
                    badge.classList.add('badge-tariff-mayorista');
                } else if (lowerName.includes('vip')) {
                    badge.classList.add('badge-tariff-vip');
                } else {
                    badge.classList.add('badge-tariff-custom');
                }
            }
        }
    }).catch(function (err) {
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

    var promises = productIds.map(function (id) {
        if (id === 'wildcard') return Promise.resolve();
        console.log('[TPV] Resetting price for product:', id);
        return fetch('/tpv/api/products/' + id + '/price')
            .then(function (r) {
                if (!r.ok) {
                    console.error('[TPV] Reset price fetch failed:', r.status, 'for ID:', id);
                }
                return r.json();
            })
            .then(function (priceData) {
                if (ticket[id]) {
                    ticket[id].price = parseFloat(priceData.price);
                    ticket[id].priceWithRe = parseFloat(priceData.priceWithRe);
                    if (priceData.vatRate != null) ticket[id].vatRate = parseFloat(priceData.vatRate);
                    // Back to catalogue price — reset originalPrice so no discount badge shows
                    ticket[id].originalPrice = ticket[id].price;
                }
            });
    });

    Promise.all(promises).then(function () {
        syncPromotions();
        resetTariffToDefault();
    }).catch(function (err) {
        console.error('Error resetting ticket prices', err);
        resetTariffToDefault();
    });
}

// ── SHARED CUSTOMER LOGIC (EXTRACTED FROM ADMIN) ────────────────────────────
const DOC_TYPE_CONFIG = {
    DNI: {
        label: 'DNI',
        placeholder: 'Ej: 12345678Z',
        hint: '🇪🇸 DNI – 8 dígitos + letra (ej: 12345678Z)',
        validate: validateDni,
    },
    NIE: {
        label: 'NIE',
        placeholder: 'Ej: X1234567L',
        hint: '🇪🇸 NIE – X, Y o Z + 7 dígitos + letra (ej: X1234567L)',
        validate: validateNie,
    },
    NIF: {
        label: 'CIF / NIF',
        placeholder: 'Ej: B12345678',
        hint: '🏢 NIF/CIF – letra de empresa + 7 dígitos + control (ej: B12345678)',
        validate: validateNif,
    },
    PASSPORT: {
        label: 'N.º Pasaporte',
        placeholder: 'Ej: AAB123456',
        hint: '🌍 Pasaporte – entre 5 y 9 caracteres alfanuméricos',
        validate: validatePassport,
    },
    FOREIGN_ID: {
        label: 'Doc. identidad extranjero',
        placeholder: 'Ej: 987654321',
        hint: '🌐 Documento extranjero – entre 4 y 25 caracteres',
        validate: validateForeignId,
    },
    INTRACOMMUNITY_VAT: {
        label: 'NIF intracomunitario',
        placeholder: 'Ej: DE123456789',
        hint: '🇪🇺 NIF UE – 2 letras de país + sufijo (ej: DE123456789, FR12345678901)',
        validate: validateIntracom,
    },
};

const DNI_LETTERS = 'TRWAGMYFPDXBNJZSQVHLCKE';

function validateDni(val) {
    if (!/^\d{8}[A-Z]$/.test(val)) return '8 dígitos + letra (ej: 12345678Z)';
    const n = parseInt(val.substring(0, 8), 10);
    const expected = DNI_LETTERS[n % 23];
    return expected === val[8] ? null : `Letra de control incorrecta (esperada: ${expected})`;
}

function validateNie(val) {
    if (!/^[XYZ]\d{7}[A-Z]$/.test(val)) return 'Formato: X/Y/Z + 7 dígitos + letra (ej: X1234567L)';
    const map = { X: '0', Y: '1', Z: '2' };
    return validateDni(map[val[0]] + val.substring(1));
}

function validateNif(val) {
    if (!/^[ABCDEFGHJNPQRSUVW]\d{7}[0-9A-Z]$/.test(val))
        return 'Formato CIF: letra de empresa + 7 dígitos + control (ej: B12345678)';
    return validateCifAlgorithm(val) ? null : 'Dígito/letra de control CIF incorrecto';
}

function validateCifAlgorithm(cif) {
    const digits = cif.substring(1, 8);
    const control = cif[8];
    let even = 0, odd = 0;
    for (let i = 0; i < digits.length; i++) {
        const d = parseInt(digits[i], 10);
        if (i % 2 === 0) { const dd = d * 2; odd += dd >= 10 ? dd - 9 : dd; }
        else { even += d; }
    }
    const ctrl = (10 - (even + odd) % 10) % 10;
    const expectedLetter = 'JABCDEFGHI'[ctrl];
    const expectedDigit = String(ctrl);
    return control === expectedDigit || control === expectedLetter;
}

function validatePassport(val) { return /^[A-Z0-9]{5,9}$/.test(val) ? null : '5–9 caracteres alfanuméricos'; }
function validateForeignId(val) { return /^[A-Z0-9\-]{4,25}$/.test(val) ? null : 'Entre 4 y 25 caracteres alfanuméricos/guiones'; }
function validateIntracom(val) { return /^[A-Z]{2}[A-Z0-9]{2,12}$/.test(val) ? null : '2 letras de país ISO + sufijo alfanumérico (ej: DE123456789)'; }

function toggleAdminCustomerType() {
    const isCompany = document.getElementById('adminTypeCompany').checked;
    document.getElementById('customerType').value = isCompany ? 'COMPANY' : 'INDIVIDUAL';
    const nameLabel = document.getElementById('lblAdminCustomerName');
    if (nameLabel) nameLabel.innerHTML = isCompany ? 'Razón Social <span class="text-danger">*</span>' : 'Nombre y Apellidos <span class="text-danger">*</span>';
    const taxSection = document.getElementById('customerTaxIdSection');
    if (taxSection) taxSection.style.display = isCompany ? '' : 'none';
    const nifTypeSection = document.getElementById('customerNifTypeSection');
    if (nifTypeSection) nifTypeSection.style.display = isCompany ? '' : 'none';
    const docTypeSection = document.getElementById('customerDocTypeSection');
    const docNumberSection = document.getElementById('customerDocNumberSection');
    const docHintSection = document.getElementById('customerDocHintSection');
    if (docTypeSection) docTypeSection.style.display = isCompany ? 'none' : '';
    if (docNumberSection) docNumberSection.style.display = isCompany ? 'none' : '';
    if (docHintSection) docHintSection.style.display = isCompany ? 'none' : '';
    const reSection = document.getElementById('adminCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) document.getElementById('customerRecargoEquivalencia').checked = false;
        else checkCustomerReCompatibility();
    }
    clearDocValidation();
    if (!isCompany) onDocTypeChange();
}

function onDocTypeChange() {
    const sel = document.getElementById('customerIdDocumentType');
    const inp = document.getElementById('customerIdDocumentNumber');
    const hint = document.getElementById('customerDocHint');
    const hintSection = document.getElementById('customerDocHintSection');
    const lbl = document.getElementById('lblDocNumber');
    if (!sel) return;
    const type = sel.value;
    const config = DOC_TYPE_CONFIG[type];
    if (inp) inp.value = '';
    clearDocValidation();
    if (config && type) {
        if (lbl) lbl.textContent = config.label;
        if (inp) inp.placeholder = config.placeholder;
        if (hint) hint.textContent = config.hint;
        if (hintSection) hintSection.style.display = '';
        const numSection = document.getElementById('customerDocNumberSection');
        if (numSection) numSection.style.display = '';
    } else {
        if (hintSection) hintSection.style.display = 'none';
        const numSection = document.getElementById('customerDocNumberSection');
        if (numSection) numSection.style.display = 'none';
    }
}

function onNifDocTypeChange() {
    const sel = document.getElementById('customerNifDocType');
    const inp = document.getElementById('customerTaxId');
    if (!sel || !inp) return;
    const type = sel.value;
    inp.placeholder = (type === 'INTRACOMMUNITY_VAT') ? 'Ej: DE123456789' : 'Ej: B12345678';
    validateTaxIdInline();
}

function validateDocNumberInline() {
    const sel = document.getElementById('customerIdDocumentType');
    const inp = document.getElementById('customerIdDocumentNumber');
    const msg = document.getElementById('docNumberValidationMsg');
    if (!sel || !inp || !msg) return;
    const raw = inp.value.trim().toUpperCase();
    const type = sel.value;
    inp.value = raw;
    if (!raw || !type) { clearDocValidation(); return; }
    const config = DOC_TYPE_CONFIG[type];
    if (!config) return;
    showFieldFeedback(inp, msg, config.validate(raw));
}

function validateTaxIdInline() {
    const inp = document.getElementById('customerTaxId');
    const msg = document.getElementById('taxIdValidationMsg');
    const nifSel = document.getElementById('customerNifDocType');
    if (!inp || !msg) return;
    const raw = inp.value.trim().toUpperCase();
    inp.value = raw;
    if (!raw) { clearTaxIdValidation(); return; }
    const docType = nifSel ? nifSel.value : 'NIF';
    const config = DOC_TYPE_CONFIG[docType] || DOC_TYPE_CONFIG['NIF'];
    showFieldFeedback(inp, msg, config.validate(raw));
}

function showFieldFeedback(input, msgEl, error) {
    if (error) {
        input.classList.remove('is-valid'); input.classList.add('is-invalid');
        msgEl.textContent = error; msgEl.style.color = '#e74c3c';
    } else {
        input.classList.remove('is-invalid'); input.classList.add('is-valid');
        msgEl.textContent = '✓ Formato correcto'; msgEl.style.color = '#22c55e';
    }
}

function clearDocValidation() {
    const inp = document.getElementById('customerIdDocumentNumber');
    const msg = document.getElementById('docNumberValidationMsg');
    if (inp) { inp.classList.remove('is-valid', 'is-invalid'); inp.value = ''; }
    if (msg) msg.textContent = '';
    clearTaxIdValidation();
}

function clearTaxIdValidation() {
    const inp = document.getElementById('customerTaxId');
    const msg = document.getElementById('taxIdValidationMsg');
    if (inp) inp.classList.remove('is-valid', 'is-invalid');
    if (msg) msg.textContent = '';
}

function checkCustomerReCompatibility() {
    const tariffSelect = document.getElementById('customerTariffId');
    const reCheckbox = document.getElementById('customerRecargoEquivalencia');
    const reSection = document.getElementById('adminCustomerReSection');
    const reWarning = document.getElementById('adminCustomerReIncompatibleMsg');
    const reInfo = document.getElementById('adminCustomerReInfoMsg');
    if (!tariffSelect || !reCheckbox) return;
    const selectedOption = tariffSelect.options[tariffSelect.selectedIndex];
    const tariffText = selectedOption ? selectedOption.text.toLowerCase() : '';
    const isMinorista = tariffSelect.value === '' || tariffText.includes('minorista');
    if (isMinorista) {
        reCheckbox.disabled = false;
        if (reSection) reSection.style.opacity = '1';
        if (reWarning) reWarning.classList.add('d-none');
        if (reInfo) reInfo.classList.remove('d-none');
    } else {
        reCheckbox.checked = false; reCheckbox.disabled = true;
        if (reSection) reSection.style.opacity = '0.7';
        if (reWarning) reWarning.classList.remove('d-none');
        if (reInfo) reInfo.classList.add('d-none');
    }
}

// ── TPV CUSTOMER ACTIONS ─────────────────────────────────────────────────────

var customerCreationModalInstance;

function openFullCustomerModal() {
    document.getElementById('customerForm').reset();
    document.getElementById('customerId').value = '';
    document.getElementById('customerRecargoEquivalencia').checked = false;
    clearDocValidation();
    toggleAdminCustomerType();
    checkCustomerReCompatibility();

    customerCreationModalInstance = new bootstrap.Modal(document.getElementById('customerModal'));
    customerCreationModalInstance.show();
}

function saveCustomer() {
    const nameInput = document.getElementById('customerName');
    const name = nameInput ? nameInput.value.trim() : '';
    if (!name) { showToast('El nombre es obligatorio', 'warning'); return; }

    const isComp = document.getElementById('adminTypeCompany').checked;
    let idDocumentType = null, idDocumentNumber = null, taxId = null;

    if (isComp) {
        taxId = document.getElementById('customerTaxId').value.trim().toUpperCase() || null;
        if (!taxId) { showToast('El CIF/NIF es obligatorio para empresas', 'warning'); return; }
        const nifSel = document.getElementById('customerNifDocType');
        idDocumentType = nifSel ? nifSel.value : 'NIF';
        idDocumentNumber = taxId;
        const config = DOC_TYPE_CONFIG[idDocumentType] || DOC_TYPE_CONFIG['NIF'];
        const cfgErr = config.validate(taxId);
        if (cfgErr) { showToast(cfgErr, 'warning'); return; }
    } else {
        const docSel = document.getElementById('customerIdDocumentType');
        const docNumInp = document.getElementById('customerIdDocumentNumber');
        idDocumentType = docSel ? docSel.value.trim() || null : null;
        idDocumentNumber = docNumInp ? docNumInp.value.trim().toUpperCase() || null : null;
        if (idDocumentType && idDocumentNumber) {
            const config = DOC_TYPE_CONFIG[idDocumentType];
            if (config) {
                const err = config.validate(idDocumentNumber);
                if (err) { showToast(err, 'warning'); return; }
            }
            if (idDocumentType === 'DNI' || idDocumentType === 'NIE') taxId = idDocumentNumber;
        } else if (idDocumentType && !idDocumentNumber) {
            showToast('Introduce el número del documento', 'warning'); return;
        }
    }

    const hasRE = document.getElementById('customerRecargoEquivalencia').checked;
    const body = {
        name, taxId, idDocumentType, idDocumentNumber,
        email: document.getElementById('customerEmail').value.trim() || null,
        phone: document.getElementById('customerPhone').value.trim() || null,
        address: document.getElementById('customerAddress').value.trim() || null,
        city: document.getElementById('customerCity').value.trim() || null,
        postalCode: document.getElementById('customerPostalCode').value.trim() || null,
        type: document.getElementById('customerType').value,
        active: document.getElementById('customerActive').checked,
        hasRecargoEquivalencia: hasRE,
        tariffId: (() => {
            const el = document.getElementById('customerTariffId');
            return el && el.value ? parseInt(el.value) : null;
        })(),
    };

    fetch('/api/customers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(r => {
            if (!r.ok) return r.json().then(d => { throw new Error(d.error || d.message || 'Error'); });
            return r.json();
        })
        .then(savedCustomer => {
            if (customerCreationModalInstance) customerCreationModalInstance.hide();
            else bootstrap.Modal.getInstance(document.getElementById('customerModal')).hide();

            selectCustomer(savedCustomer);
            showToast('Cliente creado y seleccionado', 'success');
        })
        .catch(e => {
            console.error(e);
            showToast('Error al crear el cliente: ' + e.message, 'warning');
        });
}

// Ensure global exports for TPV context if needed
window.saveCustomer = saveCustomer;
window.toggleAdminCustomerType = toggleAdminCustomerType;
window.onDocTypeChange = onDocTypeChange;
window.onNifDocTypeChange = onNifDocTypeChange;
window.validateDocNumberInline = validateDocNumberInline;
window.validateTaxIdInline = validateTaxIdInline;
window.checkCustomerReCompatibility = checkCustomerReCompatibility;

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
        var item = ticket[id];
        return {
            productId: (typeof id === 'string' && id.indexOf('manual-') === 0) ? 0 : parseInt(id),
            productName: item.name,
            quantity: item.quantity,
            unitPrice: item.price,
            vatRate: item.vatRate
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
                (sale.lines || []).forEach(function (line, index) {
                    var isManual = (!line.productId || line.productId === 0);
                    var key = isManual ? ('manual-resumed-' + index + '-' + Date.now()) : String(line.productId);
                    ticket[key] = {
                        name: line.productName,
                        price: parseFloat(line.unitPrice),
                        quantity: line.quantity,
                        vatRate: line.vatRate,
                        priceWithRe: parseFloat(line.unitPrice), // Simplified restoration
                        stock: isManual ? '∞' : (line.stock || 999)
                    };

                    // RE recalculation if needed
                    if (isManual && window.currentHasRE) {
                        var v = line.vatRate || 0.21;
                        var net = ticket[key].price / (1 + v);
                        var reRate = 0;
                        if (v >= 0.20) reRate = 0.052;
                        else if (v >= 0.09) reRate = 0.014;
                        else if (v >= 0.03) reRate = 0.005;
                        ticket[key].priceWithRe = net * (1 + v + reRate);
                    }
                });
                renderTicket();
                if (suspendedSalesModalInstance) suspendedSalesModalInstance.hide();
                cleanupModalBackdrop();
                loadSuspendedCount();

                if (sale.warnings && sale.warnings.length > 0) {
                    sale.warnings.forEach(function (w) { showToast(w, 'warning'); });
                } else {
                    showToast('Venta reanudada', 'success');
                }
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

            if (sale.warnings && sale.warnings.length > 0) {
                sale.warnings.forEach(function (w) { showToast(w, 'warning'); });
            } else {
                showToast('Venta reanudada', 'success');
            }
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
    container.innerHTML = '<div style="padding:1.5rem; text-align:center; color:var(--text-muted);"><i class="bi bi-hourglass"></i> ' + getTpvI18n('onHoldLoading') + '</div>';

    fetch('/api/suspended-sales')
        .then(function (r) { return r.json(); })
        .then(function (sales) { renderSuspendedList(sales, container); })
        .catch(function () {
            container.innerHTML = '<div style="padding:1.5rem; text-align:center; color:#ef4444;">' + getTpvI18n('onHoldError') + '</div>';
        });
}

function renderSuspendedList(sales, container) {
    if (!Array.isArray(sales) || sales.length === 0) {
        container.innerHTML = '<div style="padding:2rem; text-align:center; color:var(--text-muted);"><i class="bi bi-check-circle" style="font-size:1.5rem;"></i><br>' + getTpvI18n('onHoldEmpty') + '</div>';
        return;
    }

    var html = '<table style="width:100%; border-collapse:collapse; font-size:0.88rem;">';
    html += '<thead><tr style="border-bottom:1px solid var(--border); color:var(--text-muted); font-size:0.76rem; font-weight:700; text-transform:uppercase;">';
    html += '<th style="padding:0.6rem 1rem;">' + getTpvI18n('onHoldLabel') + '</th>';
    html += '<th style="padding:0.6rem 0.5rem;">' + getTpvI18n('onHoldWorker') + '</th>';
    html += '<th style="padding:0.6rem 0.5rem;">' + getTpvI18n('onHoldDate') + '</th>';
    html += '<th style="padding:0.6rem 0.5rem; text-align:center;">' + getTpvI18n('onHoldLines') + '</th>';
    html += '<th style="padding:0.6rem 1rem; text-align:right;">' + getTpvI18n('onHoldActions') + '</th>';
    html += '</tr></thead><tbody>';

    sales.forEach(function (s) {
        var label = escapeHtml(s.label || getTpvI18n('onHoldNoLabel'));
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
        html += '<button onclick="resumeSale(' + s.id + ')" style="margin-right:0.4rem; padding:0.3rem 0.8rem; border-radius:6px; background:var(--accent); color:var(--primary); border:none; font-size:0.82rem; font-weight:700; cursor:pointer;"><i class="bi bi-play-fill"></i> ' + getTpvI18n('onHoldResume') + '</button>';
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
    if (selector && selector.options.length > 0) {
        // Find MINORISTA option (first with 0 discount)
        for (var i = 0; i < selector.options.length; i++) {
            if (parseFloat(selector.options[i].dataset.discount) === 0) {
                selector.selectedIndex = i;
                applyTariffById(selector.options[i].value, 0, selector.options[i].text);
                return;
            }
        }
        var firstOption = selector.options[0];
        applyTariffById(firstOption.value, parseFloat(firstOption.dataset.discount || 0), firstOption.text);
    } else {
        // Fallback when no selector is present: ensure we reset to default MINORISTA state
        applyTariffById('', 0, 'MINORISTA');
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


// ── COUPON SYSTEM ────────────────────────────────────────────────────────────
function applyCoupon() {
    var input = document.getElementById('couponInput');
    var code = input.value.trim();
    if (!code) return;

    fetch('/api/coupons/validate?code=' + encodeURIComponent(code))
        .then(function (r) {
            if (!r.ok) return r.json().then(function (d) { throw new Error(d.error || 'error'); });
            return r.json();
        })
        .then(function (data) {
            currentCoupon = {
                code: data.code,
                discountType: data.discountType,
                discountValue: parseFloat(data.discountValue),
                restrictedProductIds: data.restrictedProductIds || [],
                restrictedCategoryIds: data.restrictedCategoryIds || []
            };
            input.value = '';
            showToast('Cupón aplicado: ' + data.code, 'success');
            renderTicket();
        })
        .catch(function (err) {
            // FALLBACK: Probamos si es un ID/CODE de Abono del cliente
            var customerId = document.getElementById('customerIdInput').value;
            if (customerId) {
                // Hacemos el fetch para ver si el código existe y pertenece al cliente
                fetch(`/api/customers/${customerId}/abonos`)
                    .then(r => r.json())
                    .then(abonos => {
                        const found = abonos.find(a => a.code === code || a.id.toString() === code);
                        if (found) {
                            // Simulamos selección en el modal logic (aunque el modal esté cerrado)
                            // Creamos un radio fantasma o usamos una variable global
                            window.selectedAbonoViaCode = found; 
                            input.value = '';
                            showToast('Abono aplicado: ' + (found.code || found.id), 'success');
                            renderTicket();
                        } else {
                            showToast('Código no válido o no pertenece al cliente', 'warning');
                        }
                    });
                return;
            }
            showToast(err.message || 'Código no válido', 'warning');
        });
}

function removeCoupon() {
    currentCoupon = null;
    showToast('Cupón eliminado', 'success');
    renderTicket();
}

// Initialise tariff selector to MINORISTA on load
document.addEventListener('DOMContentLoaded', function () {
    var tariffBar = document.getElementById('tariffBar');
    if (tariffBar) tariffBar.style.display = 'flex'; // Always show tariff bar
    resetTariffToDefault();
});

// --- OFFLINE MODE & CONNECTIVITY ---
var db;
const DB_NAME = 'TPV_Offline_DB';
const DB_VERSION = 1;

function initOfflineDB() {
    if (!window.indexedDB) {
        console.warn('Your browser does not support IndexedDB. Offline mode will be limited.');
        return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = function (e) {
        let db = e.target.result;
        if (!db.objectStoreNames.contains('pending_sales')) {
            db.createObjectStore('pending_sales', { keyPath: 'id', autoIncrement: true });
        }
    };
    request.onsuccess = function (e) {
        db = e.target.result;
        console.log('IndexedDB initialized');
        if (navigator.onLine) syncOfflineSales();
    };
}

function updateConnectivityUI() {
    const led = document.getElementById('connectivity-led');
    if (!led) return;
    const ledText = led.querySelector('.led-text');
    if (navigator.onLine) {
        led.classList.remove('offline');
        led.classList.add('online');
        led.title = "Conexión con el servidor OK";
        if (ledText) ledText.textContent = "Online";
        syncOfflineSales();
    } else {
        led.classList.remove('online');
        led.classList.add('offline');
        led.title = "Sin conexión. Las ventas se guardarán localmente.";
        if (ledText) ledText.textContent = "Offline";
    }
}

function saveOfflineSale(sale) {
    if (!db) {
        showToast("Error crítico: Base de datos local no inicializada", 'warning');
        return;
    }
    const transaction = db.transaction(['pending_sales'], 'readwrite');
    const store = transaction.objectStore('pending_sales');
    const request = store.add({ ...sale, createdAt: new Date().toISOString() });

    transaction.oncomplete = function () {
        showToast("Venta guardada localmente (Modo Offline)", 'success');
        clearTicket();
        if (typeof invoiceModalInstance !== 'undefined' && invoiceModalInstance) {
            invoiceModalInstance.hide();
        }
    };
    transaction.onerror = function () {
        showToast("Error al guardar la venta localmente", 'warning');
    };
}

function syncOfflineSales() {
    if (!db || !navigator.onLine) return;

    try {
        const transaction = db.transaction(['pending_sales'], 'readonly');
        const store = transaction.objectStore('pending_sales');
        const request = store.getAll();

        request.onsuccess = function () {
            const sales = request.result;
            if (sales.length === 0) return;

            console.log(`Syncing ${sales.length} offline sales...`);
            processSyncQueue(sales);
        };
    } catch (e) {
        console.error('Sync failed to initiate', e);
    }
}

function processSyncQueue(sales) {
    if (sales.length === 0) {
        showToast("Sincronización completada", 'success');
        return;
    }
    const sale = sales[0];

    const formData = new URLSearchParams();
    formData.append('paymentMethod', sale.paymentMethod);
    if (sale.customerId) formData.append('customerId', sale.customerId);
    if (sale.receivedAmount) formData.append('receivedAmount', sale.receivedAmount);
    if (sale.cashAmount) formData.append('cashAmount', sale.cashAmount);
    if (sale.cardAmount) formData.append('cardAmount', sale.cardAmount);
    formData.append('requestInvoice', sale.requestInvoice);

    sale.lines.forEach(line => {
        formData.append('productIds', line.productId);
        formData.append('quantities', line.quantity);
        formData.append('unitPrices', line.unitPrice.toFixed(2));
    });

    fetch('/tpv/sale', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    })
        .then(r => {
            if (r.ok || r.status === 400) {
                // 400 usually means business logic error (like out of stock now), 
                // but we remove it from queue to avoid blockages OR move to a "dead-letter" store
                const delTx = db.transaction(['pending_sales'], 'readwrite');
                delTx.objectStore('pending_sales').delete(sale.id);
                delTx.oncomplete = () => {
                    processSyncQueue(sales.slice(1));
                };
            }
        })
        .catch(err => {
            console.error('Network error during sync', err);
        });
}

window.addEventListener('online', updateConnectivityUI);
window.addEventListener('offline', updateConnectivityUI);

// Initialization
document.addEventListener('DOMContentLoaded', function () {
    initOfflineDB();
    updateConnectivityUI();

    // Register Service Worker
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js')
            .then(reg => console.log('Service Worker registered', reg))
            .catch(err => console.warn('Service Worker registration failed', err));
    }
});

function formatDecimal(val, minFrac = 2, maxFrac = 8) {
    if (val === null || val === undefined) return '0,00';
    return new Intl.NumberFormat('es-ES', {
        minimumFractionDigits: minFrac,
        maximumFractionDigits: maxFrac
    }).format(parseFloat(val));
}

function getReRate(vatRate) {
    var v = parseFloat(vatRate);
    if (v >= 0.20) return 0.052;
    if (v >= 0.09) return 0.014;
    if (v >= 0.03) return 0.005;
    return 0;
}

// ─────────────────────────────────────────────────────────────────────────────
// EDIT PRICE MODAL LOGIC
// ─────────────────────────────────────────────────────────────────────────────

/** State for the currently-editing cart line */
var _editPriceProductId = null;
var _editPriceModalInstance = null;

/**
 * Opens the price-edit modal for a given cart item.
 * @param {string} productId  - The product ID key in the ticket object
 * @param {string} productName - Display name of the product
 * @param {number} currentPrice - Current unit price in the ticket
 */
function openEditPriceModal(productId, productName, currentPrice) {
    if (window.tpv_is_register_open !== true) return;

    _editPriceProductId = productId;

    // Populate modal fields
    document.getElementById('editPrice_productName').textContent = productName;
    document.getElementById('editPrice_currentPrice').textContent = formatPrice(currentPrice);
    document.getElementById('editPrice_newPrice').value = currentPrice.toFixed(2);

    // Reset state
    document.getElementById('editPrice_optA').checked = true;
    document.getElementById('editPrice_pinSection').style.display = 'none';
    document.getElementById('editPrice_adminPin').value = '';
    document.getElementById('editPrice_alert').style.display = 'none';
    document.getElementById('editPrice_success').style.display = 'none';
    document.getElementById('editPrice_confirmBtn').disabled = false;

    // Reset option border highlights
    document.getElementById('editPrice_optA_label').style.borderColor = 'var(--border)';
    document.getElementById('editPrice_optB_label').style.borderColor = 'var(--border)';

    // Show modal
    var modalEl = document.getElementById('editPriceModal');
    _editPriceModalInstance = bootstrap.Modal.getOrCreateInstance(modalEl);
    _editPriceModalInstance.show();

    // Focus the price input after transition
    modalEl.addEventListener('shown.bs.modal', function focusPriceInput() {
        var input = document.getElementById('editPrice_newPrice');
        if (input) { input.focus(); input.select(); }
        modalEl.removeEventListener('shown.bs.modal', focusPriceInput);
    });
}

/** Called when the save-mode radio changes */
function onEditPriceModeChange() {
    var isDb = document.getElementById('editPrice_optB').checked;
    var pinSection = document.getElementById('editPrice_pinSection');
    var optALabel = document.getElementById('editPrice_optA_label');
    var optBLabel = document.getElementById('editPrice_optB_label');

    pinSection.style.display = isDb ? 'block' : 'none';
    document.getElementById('editPrice_alert').style.display = 'none';

    if (isDb) {
        optBLabel.style.borderColor = '#ef4444';
        optALabel.style.borderColor = 'var(--border)';
        setTimeout(function () {
            document.getElementById('editPrice_adminPin').focus();
        }, 50);
    } else {
        optALabel.style.borderColor = 'var(--accent)';
        optBLabel.style.borderColor = 'var(--border)';
    }
}

/** Input event: clear error when user types a new price */
function editPriceOnInput() {
    document.getElementById('editPrice_alert').style.display = 'none';
}

/** Shows an error message inside the edit-price modal */
function _editPriceShowError(msg) {
    var alert = document.getElementById('editPrice_alert');
    alert.textContent = msg;
    alert.style.display = 'block';
    // Shake animation
    alert.style.animation = 'none';
    void alert.offsetWidth; // reflow
    alert.style.animation = 'editPriceShake 0.3s ease';
}

/**
 * Submits the price change to the backend.
 * On success, updates the local ticket state and re-renders.
 */
function confirmEditPrice() {
    var productId = _editPriceProductId;
    if (!productId || !ticket[productId]) {
        _editPriceShowError('Producto no válido en el carrito.');
        return;
    }

    var newPriceVal = parseFloat(document.getElementById('editPrice_newPrice').value);
    if (isNaN(newPriceVal) || newPriceVal < 0) {
        _editPriceShowError('Introduce un precio válido (mayor o igual a 0).');
        return;
    }

    var saveMode = document.querySelector('input[name="editPriceSaveMode"]:checked').value;
    var adminPin = saveMode === 'DATABASE' ? document.getElementById('editPrice_adminPin').value : '';

    if (saveMode === 'DATABASE' && (!adminPin || adminPin.trim() === '')) {
        _editPriceShowError('El PIN de administrador es obligatorio para guardar en base de datos.');
        document.getElementById('editPrice_adminPin').focus();
        return;
    }

    // Disable button to prevent double-click
    var btn = document.getElementById('editPrice_confirmBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Aplicando...';

    var params = new URLSearchParams({
        productId: productId,
        newPrice: newPriceVal.toFixed(4),
        saveMode: saveMode,
        adminPin: adminPin
    });

    var csrfToken = document.querySelector('meta[name="_csrf"]') ? document.querySelector('meta[name="_csrf"]').getAttribute('content') : '';
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]') ? document.querySelector('meta[name="_csrf_header"]').getAttribute('content') : 'X-CSRF-TOKEN';

    var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrfToken) headers[csrfHeader] = csrfToken;

    fetch('/tpv/cart/update-price', {
        method: 'POST',
        headers: headers,
        body: params.toString()
    })
        .then(function (response) {
            return response.json().then(function (data) {
                return { status: response.status, data: data };
            });
        })
        .then(function (res) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-lg me-1"></i>Aplicar cambio';

            if (res.status !== 200) {
                _editPriceShowError(res.data.error || 'Error al actualizar el precio.');
                return;
            }

            // ✅ Update local ticket state with the new price
            var adjustedPrice = parseFloat(res.data.newPrice);
            if (ticket[productId]) {
                ticket[productId].price = adjustedPrice;
                // Clear any RE cached price to force recalc from base
                ticket[productId].priceWithRe = null;
            }

            // Show success briefly, then close
            var successEl = document.getElementById('editPrice_success');
            successEl.textContent = '✓ ' + (res.data.message || 'Precio actualizado correctamente.');
            successEl.style.display = 'block';
            document.getElementById('editPrice_alert').style.display = 'none';

            setTimeout(function () {
                if (_editPriceModalInstance) _editPriceModalInstance.hide();
                renderTicket();
                showToast(res.data.message || 'Precio actualizado.', 'success');
            }, 900);
        })
        .catch(function (err) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-lg me-1"></i>Aplicar cambio';
            _editPriceShowError('Error de conexión. Inténtalo de nuevo.');
            console.error('[EditPrice] Fetch error:', err);
        });
}

// ─────────────────────────────────────────────────────────────────────────────
// CART-LEVEL TARIFF OVERRIDE
// ─────────────────────────────────────────────────────────────────────────────

// (onCartTariffChange and onLineTariffChange moved to top for global visibility)


// Patch openEditPriceModal to also sync the tariff selector
(function () {
    var _orig = openEditPriceModal;
    openEditPriceModal = function (productId, productName, currentPrice) {
        _orig(productId, productName, currentPrice);
        // Delay slightly so the modal DOM is ready
        setTimeout(function () { _syncLineTariffSelector(productId); }, 10);
    };
})();

// When the edit-price modal closes: persist the chosen lineTariffId on the ticket item.
document.addEventListener('DOMContentLoaded', function () {
    var modalEl = document.getElementById('editPriceModal');
    if (!modalEl) return;
    modalEl.addEventListener('hidden.bs.modal', function () {
        var productId = _editPriceProductId;
        if (!productId || !ticket[productId]) return;

        var tariffSel = document.getElementById('editPrice_lineTariff');
        if (!tariffSel) return;

        var chosenId = tariffSel.value || null;
        var chosenLabel = chosenId
            ? tariffSel.options[tariffSel.selectedIndex].text
            : null;

        ticket[productId].lineTariffId = chosenId;
        ticket[productId].lineTariffLabel = chosenLabel;
        renderTicket();
    });
});

// Reset cartTariffId when the ticket is cleared
(function () {
    var _orig = clearTicket;
    clearTicket = function () {
        _orig();
        window.cartTariffId = null;
        var sel = document.getElementById('cartTariffSelect');
        if (sel) sel.value = '';
        var clearBtn = document.getElementById('cartTariffClearBtn');
        if (clearBtn) clearBtn.style.display = 'none';
    };
})();

/* ── PRODUCTO COMODÍN ── */
var wildcardModalInstance;
/* ── PRODUCTO COMODÍN ── */
var wildcardModalInstance;
window.openWildcardModal = function () {
    if (typeof window.tpv_is_register_open !== 'undefined' && !window.tpv_is_register_open) {
        showToast('Debes abrir la caja antes de vender', 'warning');
        return;
    }
    if (!wildcardModalInstance) {
        wildcardModalInstance = new bootstrap.Modal(document.getElementById('wildcardModal'));
    }
    var nameEl = document.getElementById('wildcardName');
    var priceEl = document.getElementById('wildcardPrice');
    var vatEl = document.getElementById('wildcardVat');

    if (nameEl) nameEl.value = '';
    if (priceEl) priceEl.value = '0.00';
    if (vatEl) vatEl.value = '0.21';

    wildcardModalInstance.show();
    setTimeout(function () { if (nameEl) nameEl.focus(); }, 400);
};

window.submitWildcard = function () {
    var name = (document.getElementById('wildcardName').value || '').trim();
    var priceStr = document.getElementById('wildcardPrice').value;
    var price = parseFloat(priceStr);
    var vat = parseFloat(document.getElementById('wildcardVat').value);

    if (!name) {
        showToast('Introduce un nombre para el producto', 'warning');
        return;
    }
    if (isNaN(price) || price < 0) {
        showToast('Precio no válido', 'warning');
        return;
    }

    addWildcardToTicket(name, price, vat);
    if (wildcardModalInstance) wildcardModalInstance.hide();
};

/**
 * Añade un producto manual al ticket con un ID ficticio "manual-X".
 */
function addWildcardToTicket(name, price, vat) {
    var id = 'manual-' + Date.now();
    var pWithRE = price; // Unused but kept for structure parity

    ticket[id] = {
        name: name,
        price: price,
        priceWithRe: pWithRE,
        quantity: 1,
        vatRate: vat,
        categoryId: null,
        stock: '∞'
    };

    renderTicket();
    showToast('Producto manual añadido: ' + name, 'success');
}

// ── TPV LOCK SYSTEM ──
var currentPin = "";
function lockTPV() {
    currentPin = "";
    updatePinDots();
    document.getElementById('pinErrorMsg').style.display = 'none';
    document.getElementById('pinOverlay').style.display = 'flex';
}

function appendPinToSell(val) {
    if (currentPin.length < 4) {
        currentPin += val;
        updatePinDots();
    }
    if (currentPin.length === 4) {
        document.getElementById('btnPinSubmit').disabled = false;
    }
}

function clearPinToSell() {
    if (currentPin.length > 0) {
        currentPin = currentPin.slice(0, -1);
        updatePinDots();
        document.getElementById('btnPinSubmit').disabled = true;
        document.getElementById('pinErrorMsg').style.display = 'none';
    }
}

function updatePinDots() {
    const dots = document.querySelectorAll('.pin-dot');
    dots.forEach((dot, i) => {
        if (i < currentPin.length) dot.classList.add('filled');
        else dot.classList.remove('filled');
    });
}

function submitPinToSell() {
    if (currentPin.length !== 4) return;

    const btn = document.getElementById('btnPinSubmit');
    btn.disabled = true;

    fetch('/api/workers/verify-pin?pin=' + currentPin)
        .then(r => {
            if (r.ok) {
                document.getElementById('pinOverlay').style.display = 'none';
                currentPin = "";
            } else {
                document.getElementById('pinErrorMsg').style.display = 'block';
                currentPin = "";
                updatePinDots();
                btn.disabled = true;
            }
        })
        .catch(err => {
            console.error('Error verifying PIN', err);
            btn.disabled = false;
        });
}

function togglePasswordVisibility(inputId, button) {
    const input = document.getElementById(inputId);
    const icon = button.querySelector('i');
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.replace('bi-eye', 'bi-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.replace('bi-eye-slash', 'bi-eye');
    }
}

// ── MANEJO DE ABONOS / VALES ──
window._manualAbonoAmount = 0;
window._manualAbonoType = 'fixed';

window.setAbonoType = function(type) {
    const btnFixed = document.getElementById('btn-abono-fixed');
    const btnPercent = document.getElementById('btn-abono-percent');
    const suffix = document.getElementById('abono-manual-suffix');
    const modeInput = document.getElementById('abono-manual-mode');
    
    if (type === 'percent') {
        window._manualAbonoType = 'percent';
        if (btnFixed) { 
            btnFixed.style.background = 'transparent'; 
            btnFixed.style.color = 'var(--text-muted)'; 
            btnFixed.style.fontWeight = '600';
        }
        if (btnPercent) { 
            btnPercent.style.background = 'var(--accent)'; 
            btnPercent.style.color = '#ffffff'; 
            btnPercent.style.fontWeight = '700';
        }
        if (suffix) suffix.textContent = '%';
        if (modeInput) modeInput.value = 'percent';
    } else {
        window._manualAbonoType = 'fixed';
        if (btnFixed) { 
            btnFixed.style.background = 'var(--accent)'; 
            btnFixed.style.color = '#ffffff'; 
            btnFixed.style.fontWeight = '700';
        }
        if (btnPercent) { 
            btnPercent.style.background = 'transparent'; 
            btnPercent.style.color = 'var(--text-muted)'; 
            btnPercent.style.fontWeight = '600';
        }
        if (suffix) suffix.textContent = '€';
        if (modeInput) modeInput.value = 'fixed';
    }
};

window.openAbonoSelectionModal = function() {
    const customerIdInput = document.getElementById('customerIdInput');
    const customerId = customerIdInput ? customerIdInput.value : null;
    const abonoList = document.getElementById('tpv-abono-selection-list');
    const manualInput = document.getElementById('tpv-abono-manual-input');
    const instructions = document.getElementById('abono-modal-instructions');
    
    if (!abonoList || !manualInput || !instructions) return;

    // Reset search results if any
    const searchRes = document.getElementById('abono-search-result');
    if (searchRes) {
        searchRes.innerHTML = '';
        searchRes.style.display = 'none';
    }
    const searchInput = document.getElementById('abono-search-code');
    if (searchInput) searchInput.value = '';

    if (customerId) {
        instructions.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Cargando bonos del cliente...';
        abonoList.innerHTML = '';
        abonoList.style.display = 'none';
        manualInput.style.display = 'none';

        fetch(`/api/customers/${customerId}/abonos`)
            .then(r => r.json())
            .then(abonos => {
                if (abonos && abonos.length > 0) {
                    instructions.textContent = "Seleccione el abono que desea aplicar a esta venta.";
                    let html = '';
                    abonos.forEach(a => {
                        html += `
                            <div class="form-check p-3 mb-2 rounded border" style="background: var(--surface); cursor: pointer;" onclick="this.querySelector('input').click()">
                                <input class="form-check-input abono-modal-radio" type="radio" name="abonoSelection" id="abono-${a.id}" value="${a.id}" data-amount="${a.importe}">
                                <label class="form-check-label ps-2 w-100" for="abono-${a.id}" style="cursor: pointer;">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <strong>${parseFloat(a.importe).toFixed(2).replace('.', ',')}€</strong>
                                        <span class="badge bg-secondary">${new Date(a.createdAt).toLocaleDateString()}</span>
                                    </div>
                                    <div class="small text-muted text-truncate" style="max-width: 300px;">${a.motivo || 'Bono'}</div>
                                </label>
                            </div>`;
                    });
                    abonoList.innerHTML = html;
                    abonoList.style.display = 'block';
                } else {
                    instructions.textContent = "El cliente no tiene bonos disponibles.";
                }
            })
            .catch(err => {
                console.error("[Abonos] Error fetching:", err);
                instructions.textContent = "Error al cargar los bonos.";
            });
    } else {
        // No hay cliente, mostrar entrada manual y búsqueda por código
        instructions.textContent = "Busque un bono por código o introduzca el importe manualmente.";
        abonoList.style.display = 'none';
        manualInput.style.display = 'block';
        const manAmtInput = document.getElementById('abono-manual-amount');
        if (manAmtInput) {
            if (window._manualAbonoAmount > 0) {
                manAmtInput.value = window._manualAbonoAmount;
            } else {
                manAmtInput.value = '';
            }
        }
        setTimeout(() => {
            const el = document.getElementById('abono-search-code');
            if(el) el.focus();
        }, 500);
    }
    
    const modalEl = document.getElementById('abonoSelectionModal');
    if (modalEl) new bootstrap.Modal(modalEl).show();
}

window.searchAbonoByCode = function() {
    const codeInput = document.getElementById('abono-search-code');
    const resultDiv = document.getElementById('abono-search-result');
    if (!codeInput || !resultDiv) return;
    
    const code = codeInput.value.trim();
    if (!code) return;

    resultDiv.innerHTML = '<div class="text-center p-2"><span class="spinner-border spinner-border-sm text-primary"></span> Buscando...</div>';
    resultDiv.style.display = 'block';

    fetch(`/api/abonos/search?code=${encodeURIComponent(code)}`)
        .then(r => {
            if (!r.ok) throw new Error("Bono no encontrado o ya canjeado");
            return r.json();
        })
        .then(a => {
            resultDiv.innerHTML = `
                <div class="alert alert-success d-flex align-items-center mb-0" style="border-radius: 12px; border: 2px solid var(--success);">
                    <div class="form-check w-100" onclick="this.querySelector('input').click()">
                        <input class="form-check-input abono-modal-radio" type="radio" name="abonoSelection" id="abono-${a.id}" value="${a.id}" data-amount="${a.importe}" checked>
                        <label class="form-check-label ps-2 w-100" for="abono-${a.id}" style="cursor: pointer;">
                            <div class="fw-bold">Bono encontrado: ${parseFloat(a.importe).toFixed(2).replace('.', ',')}€</div>
                            <div class="small">Código: ${a.code} | Cliente: ${a.cliente ? a.cliente.name : 'N/D'}</div>
                        </label>
                    </div>
                </div>`;
            // Al encontrar uno, borramos el importe manual para evitar confusiones
            const manAmtInput = document.getElementById('abono-manual-amount');
            if (manAmtInput) manAmtInput.value = '';
        })
        .catch(err => {
            resultDiv.innerHTML = `<div class="alert alert-danger mb-0 small" style="border-radius: 12px;">${err.message}</div>`;
        });
}

window.confirmAbonoSelection = function() {
    const customerIdInput = document.getElementById('customerIdInput');
    const customerId = customerIdInput ? customerIdInput.value : null;
    const manualAmountEl = document.getElementById('abono-manual-amount');
    const manualInputForm = document.getElementById('manualAbonoAmountInput');
    
    const selectedAbonoRadio = document.querySelector('.abono-modal-radio:checked');

    if (selectedAbonoRadio) {
        // Prioritize found/selected voucher
        window._manualAbonoAmount = 0;
        window._manualAbonoType = 'fixed'; // Reset to fixed when using real abono
        if (manualInputForm) manualInputForm.value = '';
    } else if (!customerId) {
        // Try manual amount if NO radio is selected and NO customer is present
        let valStr = (manualAmountEl ? manualAmountEl.value || '' : '').replace(',', '.');
        const amt = parseFloat(valStr);
        if (isNaN(amt) || amt < 0) {
            showToast("Importe no válido o bono no seleccionado", "warning");
            return;
        }

        const mode = document.getElementById('abono-manual-mode') ? document.getElementById('abono-manual-mode').value : 'fixed';
        window._manualAbonoType = mode;
        window._manualAbonoAmount = amt;
        
        // If it's a percentage, we might need special handling during final POST if backend doesn't expect it.
        // But for UI it works now.
        if (manualInputForm) manualInputForm.value = amt > 0 ? amt.toFixed(2) : '';
    } else {
        // Customer present but nothing selected
        window._manualAbonoAmount = 0;
        if (manualInputForm) manualInputForm.value = '';
    }
    
    const modalEl = document.getElementById('abonoSelectionModal');
    if (modalEl) {
        const modalInstance = bootstrap.Modal.getInstance(modalEl);
        if (modalInstance) modalInstance.hide();
    }
    
    renderTicket();
};

// Reset abonos when clearing customer
(function() {
    const origClear = window.clearSelectedCustomer;
    if (typeof origClear === 'function') {
        window.clearSelectedCustomer = function() {
            origClear();
            window._manualAbonoAmount = 0;
            const input = document.getElementById('manualAbonoAmountInput');
            if (input) input.value = '';
            renderTicket();
        };
    }
    
    const origTicketClear = window.clearTicket;
    if (typeof origTicketClear === 'function') {
        window.clearTicket = function() {
            origTicketClear();
            window._manualAbonoAmount = 0;
            const input = document.getElementById('manualAbonoAmountInput');
            if (input) input.value = '';
            renderTicket();
        };
    }
})();

