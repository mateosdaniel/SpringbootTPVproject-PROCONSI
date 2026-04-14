/**
 * admin-customers.js
 * Customer management – includes document-type-aware validation for
 * DNI, NIE, NIF/CIF, PASSPORT, FOREIGN_ID, INTRACOMMUNITY_VAT.
 */

'use strict';

// ── Document-type configuration ──────────────────────────────────────────────

const DOC_TYPE_CONFIG = {
    DNI: {
        label:       'DNI',
        placeholder: 'Ej: 12345678Z',
        hint:        '🇪🇸 DNI – 8 dígitos + letra (ej: 12345678Z)',
        validate:    validateDni,
    },
    NIE: {
        label:       'NIE',
        placeholder: 'Ej: X1234567L',
        hint:        '🇪🇸 NIE – X, Y o Z + 7 dígitos + letra (ej: X1234567L)',
        validate:    validateNie,
    },
    NIF: {
        label:       'CIF / NIF',
        placeholder: 'Ej: B12345678',
        hint:        '🏢 NIF/CIF – letra de empresa + 7 dígitos + control (ej: B12345678)',
        validate:    validateNif,
    },
    PASSPORT: {
        label:       'N.º Pasaporte',
        placeholder: 'Ej: AAB123456',
        hint:        '🌍 Pasaporte – entre 5 y 9 caracteres alfanuméricos',
        validate:    validatePassport,
    },
    FOREIGN_ID: {
        label:       'Doc. identidad extranjero',
        placeholder: 'Ej: 987654321',
        hint:        '🌐 Documento extranjero – entre 4 y 25 caracteres',
        validate:    validateForeignId,
    },
    INTRACOMMUNITY_VAT: {
        label:       'NIF intracomunitario',
        placeholder: 'Ej: DE123456789',
        hint:        '🇪🇺 NIF UE – 2 letras de país + sufijo (ej: DE123456789, FR12345678901)',
        validate:    validateIntracom,
    },
};

// ── Client-side validators (mirrors NifCifValidator.java) ────────────────────

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
    const digits  = cif.substring(1, 8);
    const control = cif[8];
    let even = 0, odd = 0;
    for (let i = 0; i < digits.length; i++) {
        const d = parseInt(digits[i], 10);
        if (i % 2 === 0) { const dd = d * 2; odd += dd >= 10 ? dd - 9 : dd; }
        else              { even += d; }
    }
    const ctrl = (10 - (even + odd) % 10) % 10;
    const expectedLetter = 'JABCDEFGHI'[ctrl];
    const expectedDigit  = String(ctrl);
    return control === expectedDigit || control === expectedLetter;
}

function validatePassport(val) {
    return /^[A-Z0-9]{5,9}$/.test(val) ? null : '5–9 caracteres alfanuméricos';
}

function validateForeignId(val) {
    return /^[A-Z0-9\-]{4,25}$/.test(val) ? null : 'Entre 4 y 25 caracteres alfanuméricos/guiones';
}

function validateIntracom(val) {
    return /^[A-Z]{2}[A-Z0-9]{2,12}$/.test(val) ? null
        : '2 letras de país ISO + sufijo alfanumérico (ej: DE123456789)';
}

// ── Modal initialization ─────────────────────────────────────────────────────

function openCustomerModal(id) {
    document.getElementById('customerForm').reset();
    document.getElementById('customerId').value = id || '';
    document.getElementById('customermodalLabel').textContent = id ? 'Editar Cliente' : 'Nuevo Cliente';
    document.getElementById('customerRecargoEquivalencia').checked = false;
    clearDocValidation();

    if (id) {
        fetch('/api/customers/' + id)
            .then(r => { if (!r.ok) throw new Error(); return r.json(); })
            .then(c => {
                document.getElementById('customerName').value        = c.name        || '';
                document.getElementById('customerTaxId').value       = c.taxId       || '';
                document.getElementById('customerEmail').value       = c.email       || '';
                document.getElementById('customerPhone').value       = c.phone       || '';
                document.getElementById('customerAddress').value     = c.address     || '';
                document.getElementById('customerCity').value        = c.city        || '';
                document.getElementById('customerPostalCode').value  = c.postalCode  || '';

                // Customer type
                const type = c.type || 'INDIVIDUAL';
                document.getElementById('customerType').value = type;
                document.getElementById(type === 'COMPANY' ? 'adminTypeCompany' : 'adminTypeIndividual').checked = true;

                // Document type + number
                const docType = c.idDocumentType || '';
                const docNum  = c.idDocumentNumber || '';

                if (type === 'COMPANY') {
                    const nifSel = document.getElementById('customerNifDocType');
                    if (nifSel) nifSel.value = docType || 'NIF';
                } else {
                    const docSel = document.getElementById('customerIdDocumentType');
                    if (docSel) docSel.value = docType;
                    document.getElementById('customerIdDocumentNumber').value = docNum;
                }

                document.getElementById('customerActive').checked = c.active !== false;
                document.getElementById('customerRecargoEquivalencia').checked = c.hasRecargoEquivalencia === true;

                const tariffSelect = document.getElementById('customerTariffId');
                if (tariffSelect && c.tariff) tariffSelect.value = c.tariff.id;
                else if (tariffSelect)         tariffSelect.value = '';

                toggleAdminCustomerType();
                checkCustomerReCompatibility();
                onDocTypeChange();
            })
            .catch(() => showToast('Error al cargar el cliente', 'error'));
    } else {
        toggleAdminCustomerType();
        checkCustomerReCompatibility();
    }
    customerModal.show();
}

// ── Toggle between INDIVIDUAL / COMPANY layouts ──────────────────────────────

function toggleAdminCustomerType() {
    const isCompany = document.getElementById('adminTypeCompany').checked;
    document.getElementById('customerType').value = isCompany ? 'COMPANY' : 'INDIVIDUAL';

    // Name label
    const nameLabel = document.getElementById('lblAdminCustomerName');
    if (nameLabel) {
        nameLabel.innerHTML = isCompany
            ? 'Razón Social <span class="text-danger">*</span>'
            : 'Nombre y Apellidos <span class="text-danger">*</span>';
    }

    // Show/hide company tax-id row
    const taxSection = document.getElementById('customerTaxIdSection');
    if (taxSection) taxSection.style.display = isCompany ? '' : 'none';

    // Show/hide company NIF type selector
    const nifTypeSection = document.getElementById('customerNifTypeSection');
    if (nifTypeSection) nifTypeSection.style.display = isCompany ? '' : 'none';

    // Show/hide individual document fields
    const docTypeSection   = document.getElementById('customerDocTypeSection');
    const docNumberSection = document.getElementById('customerDocNumberSection');
    const docHintSection   = document.getElementById('customerDocHintSection');
    if (docTypeSection)   docTypeSection.style.display   = isCompany ? 'none' : '';
    if (docNumberSection) docNumberSection.style.display = isCompany ? 'none' : '';
    if (docHintSection)   docHintSection.style.display   = isCompany ? 'none' : '';

    // RE section (only for companies)
    const reSection = document.getElementById('adminCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) {
            document.getElementById('customerRecargoEquivalencia').checked = false;
        } else {
            checkCustomerReCompatibility();
        }
    }

    clearDocValidation();
    if (!isCompany) onDocTypeChange();
}

// ── Document type change handler (INDIVIDUAL) ────────────────────────────────

function onDocTypeChange() {
    const sel  = document.getElementById('customerIdDocumentType');
    const inp  = document.getElementById('customerIdDocumentNumber');
    const hint = document.getElementById('customerDocHint');
    const hintSection = document.getElementById('customerDocHintSection');
    const lbl  = document.getElementById('lblDocNumber');

    if (!sel) return;

    const type   = sel.value;
    const config = DOC_TYPE_CONFIG[type];

    if (inp)  inp.value = '';
    clearDocValidation();

    if (config && type) {
        if (lbl)  lbl.textContent = config.label;
        if (inp)  inp.placeholder = config.placeholder;
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
    // For companies, update the taxId placeholder based on the selected NIF type
    const sel = document.getElementById('customerNifDocType');
    const inp = document.getElementById('customerTaxId');
    if (!sel || !inp) return;

    const type = sel.value;
    if (type === 'INTRACOMMUNITY_VAT') {
        inp.placeholder = 'Ej: DE123456789';
    } else {
        inp.placeholder = 'Ej: B12345678';
    }
    validateTaxIdInline();
}

// ── Inline validators (real-time feedback) ───────────────────────────────────

function validateDocNumberInline() {
    const sel = document.getElementById('customerIdDocumentType');
    const inp = document.getElementById('customerIdDocumentNumber');
    const msg = document.getElementById('docNumberValidationMsg');
    if (!sel || !inp || !msg) return;

    const raw  = inp.value.trim().toUpperCase();
    const type = sel.value;
    inp.value = raw; // auto-uppercase

    if (!raw || !type) { clearDocValidation(); return; }

    const config = DOC_TYPE_CONFIG[type];
    if (!config) return;

    const error = config.validate(raw);
    showFieldFeedback(inp, msg, error);
}

function validateTaxIdInline() {
    const inp     = document.getElementById('customerTaxId');
    const msg     = document.getElementById('taxIdValidationMsg');
    const nifSel  = document.getElementById('customerNifDocType');
    if (!inp || !msg) return;

    const raw  = inp.value.trim().toUpperCase();
    inp.value = raw;

    if (!raw) { clearTaxIdValidation(); return; }

    const docType = nifSel ? nifSel.value : 'NIF';
    const config  = DOC_TYPE_CONFIG[docType] || DOC_TYPE_CONFIG['NIF'];
    const error   = config.validate(raw);
    showFieldFeedback(inp, msg, error);
}

function showFieldFeedback(input, msgEl, error) {
    if (error) {
        input.classList.remove('is-valid');
        input.classList.add('is-invalid');
        msgEl.textContent = error;
        msgEl.style.color = 'var(--danger, #e74c3c)';
    } else {
        input.classList.remove('is-invalid');
        input.classList.add('is-valid');
        msgEl.textContent = '✓ Formato correcto';
        msgEl.style.color = '#22c55e';
    }
}

function clearDocValidation() {
    const inp = document.getElementById('customerIdDocumentNumber');
    const msg = document.getElementById('docNumberValidationMsg');
    if (inp) { inp.classList.remove('is-valid', 'is-invalid'); inp.value = ''; }
    if (msg) { msg.textContent = ''; }
    clearTaxIdValidation();
}

function clearTaxIdValidation() {
    const inp = document.getElementById('customerTaxId');
    const msg = document.getElementById('taxIdValidationMsg');
    if (inp) inp.classList.remove('is-valid', 'is-invalid');
    if (msg) msg.textContent = '';
}

// ── RE compatibility ─────────────────────────────────────────────────────────

function checkCustomerReCompatibility() {
    const tariffSelect = document.getElementById('customerTariffId');
    const reCheckbox   = document.getElementById('customerRecargoEquivalencia');
    const reSection    = document.getElementById('adminCustomerReSection');
    const reWarning    = document.getElementById('adminCustomerReIncompatibleMsg');
    const reInfo       = document.getElementById('adminCustomerReInfoMsg');

    if (!tariffSelect || !reCheckbox) return;

    const selectedOption = tariffSelect.options[tariffSelect.selectedIndex];
    const tariffText     = selectedOption ? selectedOption.text.toLowerCase() : '';
    const isMinorista    = tariffSelect.value === '' || tariffText.includes('minorista');

    if (isMinorista) {
        reCheckbox.disabled = false;
        if (reSection) reSection.style.opacity = '1';
        if (reWarning) reWarning.classList.add('d-none');
        if (reInfo)    reInfo.classList.remove('d-none');
    } else {
        reCheckbox.checked  = false;
        reCheckbox.disabled = true;
        if (reSection) reSection.style.opacity = '0.7';
        if (reWarning) reWarning.classList.remove('d-none');
        if (reInfo)    reInfo.classList.add('d-none');
    }
}

// ── Save customer ─────────────────────────────────────────────────────────────

function saveCustomer() {
    const nameInput = document.getElementById('customerName');
    const name = nameInput ? nameInput.value.trim() : '';
    if (!name) { showToast('El nombre es obligatorio', 'error'); return; }

    const id      = document.getElementById('customerId').value;
    const isComp  = document.getElementById('adminTypeCompany').checked;

    // Resolve document type + number depending on customer type
    let idDocumentType   = null;
    let idDocumentNumber = null;
    let taxId            = null;

    if (isComp) {
        // Company: taxId is the fiscal identifier (CIF/NIF)
        taxId = document.getElementById('customerTaxId').value.trim().toUpperCase() || null;
        if (!taxId) { showToast('El CIF/NIF es obligatorio para empresas', 'error'); return; }

        const nifSel = document.getElementById('customerNifDocType');
        idDocumentType   = nifSel ? nifSel.value : 'NIF';
        idDocumentNumber = taxId;

        // Validate CIF/NIF or intracom
        const config  = DOC_TYPE_CONFIG[idDocumentType] || DOC_TYPE_CONFIG['NIF'];
        const cfgErr  = config.validate(taxId);
        if (cfgErr) { showToast(cfgErr, 'error'); return; }
    } else {
        // Individual: taxId synced from idDocumentNumber if applicable
        const docSel    = document.getElementById('customerIdDocumentType');
        const docNumInp = document.getElementById('customerIdDocumentNumber');
        idDocumentType  = docSel  ? docSel.value.trim()  || null : null;
        idDocumentNumber= docNumInp ? docNumInp.value.trim().toUpperCase() || null : null;

        if (idDocumentType && idDocumentNumber) {
            const config = DOC_TYPE_CONFIG[idDocumentType];
            if (config) {
                const err = config.validate(idDocumentNumber);
                if (err) { showToast(err, 'error'); return; }
            }
            // For DNI / NIE, also set taxId for fiscal use
            if (idDocumentType === 'DNI' || idDocumentType === 'NIE') {
                taxId = idDocumentNumber;
            }
        } else if (idDocumentType && !idDocumentNumber) {
            showToast('Introduce el número del documento seleccionado', 'error');
            return;
        }
    }

    // RE compatibility check
    const hasRE = document.getElementById('customerRecargoEquivalencia').checked;
    if (hasRE) {
        const tariffSelect = document.getElementById('customerTariffId');
        const tariffText   = tariffSelect.options[tariffSelect.selectedIndex]?.text.toLowerCase() || '';
        const isMinorista  = tariffSelect.value === '' || tariffText.includes('minorista');
        if (!isMinorista) {
            showToast('No es posible aplicar el recargo de equivalencia a esta tarifa', 'error');
            return;
        }
    }

    const body = {
        name,
        taxId,
        idDocumentType,
        idDocumentNumber,
        email:                 document.getElementById('customerEmail').value.trim()      || null,
        phone:                 document.getElementById('customerPhone').value.trim()      || null,
        address:               document.getElementById('customerAddress').value.trim()    || null,
        city:                  document.getElementById('customerCity').value.trim()       || null,
        postalCode:            document.getElementById('customerPostalCode').value.trim() || null,
        type:                  document.getElementById('customerType').value,
        active:                document.getElementById('customerActive').checked,
        hasRecargoEquivalencia: hasRE,
        tariffId:              (() => {
            const el = document.getElementById('customerTariffId');
            return el && el.value ? parseInt(el.value) : null;
        })(),
    };

    const method = id ? 'PUT'  : 'POST';
    const url    = id ? '/api/customers/' + id : '/api/customers';

    fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    })
        .then(r => {
            if (!r.ok) {
                return r.json().then(data => {
                    throw new Error(data?.error || data?.message || 'Respuesta inesperada');
                }).catch(() => { throw new Error('Estado HTTP ' + r.status); });
            }
            return r.json();
        })
        .then(() => {
            customerModal.hide();
            showToast(id ? 'Cliente actualizado correctamente' : 'Cliente creado correctamente');
            setTimeout(() => location.reload(), 900);
        })
        .catch(e => showToast('Error al guardar el cliente: ' + (e.message || ''), 'error'));
}

// ── Delete & other utilities ─────────────────────────────────────────────────

function deleteCustomer(id, name) {
    if (!confirm('¿Seguro que quieres eliminar (desactivar) al cliente "' + name + '"?')) return;
    fetch('/api/customers/' + id, { method: 'DELETE' })
        .then(r => {
            if (r.ok) {
                showToast('Cliente desactivado correctamente');
                setTimeout(() => location.reload(), 900);
            } else {
                r.json().then(err =>
                    showToast('Error al eliminar cliente: ' + (err.error || err.message || 'Desconocido'), 'error')
                ).catch(() => showToast('Error al eliminar cliente', 'error'));
            }
        })
        .catch(() => showToast('Error de red al eliminar el cliente', 'error'));
}

function openCustomerSalesModal(id, name) {
    const el = document.getElementById('customerSalesModal');
    if (!el) return;
    const modal = bootstrap.Modal.getOrCreateInstance(el);

    document.getElementById('customerSalesModalName').textContent = name || 'Cliente';
    document.getElementById('customerSalesBody').innerHTML =
        '<div class="text-center py-5"><span class="spinner-border spinner-border-sm me-2"></span>Cargando...</div>';
    const statsDiv = document.getElementById('customerSalesStats');
    if (statsDiv) statsDiv.style.setProperty('display', 'none', 'important');
    modal.show();

    fetch('/api/customers/' + id + '/sales')
        .then(r => { if (!r.ok) throw new Error('Error HTTP ' + r.status); return r.json(); })
        .then(sales => renderCustomerSales(sales))
        .catch(e => {
            document.getElementById('customerSalesBody').innerHTML =
                '<div class="text-center text-danger py-5"><i class="bi bi-exclamation-triangle me-2"></i>Error al cargar las ventas: ' + escHtml(e.message) + '</div>';
        });
}

function renderCustomerSales(sales) {
    const body     = document.getElementById('customerSalesBody');
    const statsDiv = document.getElementById('customerSalesStats');

    if (!sales || sales.length === 0) {
        body.innerHTML =
            '<div class="text-center py-5" style="color:var(--text-muted);">' +
            '<i class="bi bi-receipt fs-1 d-block mb-3"></i>' +
            '<p class="mb-0">Este cliente no tiene ventas registradas.</p></div>';
        if (statsDiv) statsDiv.style.setProperty('display', 'none', 'important');
        return;
    }

    const totalAmt = sales.reduce((s, v) => s + parseFloat(v.totalAmount || 0), 0);
    const lastDate = sales[0] ? formatDateTime(sales[0].createdAt) : '—';
    document.getElementById('csSaleCount').textContent   = sales.length;
    document.getElementById('csTotalAmount').textContent = totalAmt.toFixed(2) + ' €';
    document.getElementById('csAvgAmount').textContent   = (totalAmt / sales.length).toFixed(2) + ' €';
    document.getElementById('csLastSale').textContent    = lastDate;
    if (statsDiv) { statsDiv.style.removeProperty('display'); statsDiv.style.display = 'flex'; }

    const payLabel = {
        CASH: '<i class="bi bi-cash me-1"></i>Efectivo',
        CARD: '<i class="bi bi-credit-card me-1"></i>Tarjeta',
        MIXED:'<i class="bi bi-wallet me-1"></i>Mixto',
    };

    let html = '<div class="accordion" id="csAccordion">';
    sales.forEach((s, idx) => {
        const statusBadge = s.status === 'CANCELLED'
            ? '<span class="badge" style="background:rgba(239,68,68,.15);color:#ef4444;border:1px solid rgba(239,68,68,.3);font-size:.75rem;">ANULADA</span>'
            : '<span class="badge" style="background:rgba(34,197,94,.12);color:#22c55e;border:1px solid rgba(34,197,94,.3);font-size:.75rem;">ACTIVA</span>';

        const pmLabel  = payLabel[s.paymentMethod] || escHtml(s.paymentMethod || '—');
        const dateStr  = formatDateTime(s.createdAt);
        const total    = parseFloat(s.totalAmount || 0).toFixed(2);
        const worker   = s.workerName ? escHtml(s.workerName) : '<span style="color:var(--text-muted)">—</span>';
        const tariff   = s.appliedTariff ? escHtml(s.appliedTariff) : 'MINORISTA';

        let linesHtml = '';
        if (s.lines && s.lines.length > 0) {
            linesHtml = '<table style="width:100%;border-collapse:collapse;font-size:.85rem;">' +
                '<thead><tr>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);">Producto</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:center;">Cant.</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:right;">P. Unit.</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:right;">Subtotal</th>' +
                '</tr></thead><tbody>';
            s.lines.forEach(l => {
                linesHtml +=
                    '<tr>' +
                    '<td style="padding:.4rem .6rem;color:var(--text-main);">'  + escHtml(l.productName) + '</td>' +
                    '<td style="padding:.4rem .6rem;text-align:center;color:var(--text-muted);">' + (l.quantity || 0) + '</td>' +
                    '<td style="padding:.4rem .6rem;text-align:right;color:var(--text-muted);">'  + parseFloat(l.unitPrice || 0).toFixed(2) + ' €</td>' +
                    '<td style="padding:.4rem .6rem;text-align:right;color:var(--text-main);font-weight:600;">' + parseFloat(l.subtotal || 0).toFixed(2) + ' €</td>' +
                    '</tr>';
            });
            linesHtml += '</tbody></table>';
        } else {
            linesHtml = '<p class="mb-0 py-2 text-center" style="color:var(--text-muted);font-size:.85rem;">Sin líneas.</p>';
        }

        html +=
            '<div class="accordion-item" style="background:var(--surface);border:1px solid var(--border);border-radius:10px;margin-bottom:.5rem;">' +
            '<h2 class="accordion-header">' +
            '<button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#csSale' + idx + '"' +
            ' style="background:var(--surface);color:var(--text-main);border-radius:10px;gap:.75rem;">' +
            '<span style="min-width:9rem;font-size:.8rem;color:var(--text-muted);">' + dateStr + '</span>' +
            '<span class="me-2">' + statusBadge + '</span>' +
            '<span style="font-size:.82rem;color:var(--text-muted);">' + pmLabel + '</span>' +
            '<span class="ms-auto fw-bold" style="color:var(--accent);white-space:nowrap;">' + total + ' €</span>' +
            '</button></h2>' +
            '<div id="csSale' + idx + '" class="accordion-collapse collapse" data-bs-parent="#csAccordion">' +
            '<div class="accordion-body pt-2">' +
            '<div class="d-flex gap-3 mb-3 flex-wrap" style="font-size:.82rem;color:var(--text-muted);">' +
            '<span><i class="bi bi-hash me-1"></i>Venta #' + s.id + '</span>' +
            '<span><i class="bi bi-person me-1"></i>' + worker + '</span>' +
            '<span><i class="bi bi-tags me-1"></i>' + tariff + '</span>' +
            '</div>' + linesHtml +
            '</div></div></div>';
    });
    html += '</div>';
    body.innerHTML = html;
}

// ── CRM table filter / render ────────────────────────────────────────────────

function filterCRM() {
    const search  = document.getElementById('crmFilterSearch').value.trim();
    const type    = document.getElementById('crmFilterType').value;
    const re      = document.getElementById('crmFilterRE').value;
    const sortBy  = document.getElementById('crmFilterSortBy').value;
    const sortDir = document.getElementById('crmFilterSortDir').value;

    const params = new URLSearchParams();
    if (search)  params.append('search', search);
    if (type)    params.append('type', type);
    if (re)      params.append('re', re);
    params.append('sortBy', sortBy);
    params.append('sortDir', sortDir);

    fetch('/api/admin/customers?' + params.toString())
        .then(res => res.json())
        .then(data => {
            renderCRMTable(data.content || data);

            const labelEl = document.getElementById('crmCountLabel');
            if (labelEl) {
                if (search || type || re) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || data).length} clientes coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los clientes.';
                }
            }
        })
        .catch(err => console.error('Error filtering CRM:', err));
}

function renderCRMTable(items) {
    const tbody = document.getElementById('crmTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4" style="color:var(--text-muted);">No hay clientes registrados.</td></tr>';
        return;
    }

    items.forEach(c => {
        const badgeType = c.type === 'COMPANY'
            ? `<span class="badge badge-type-company">Empresa / Prof.</span>`
            : `<span class="badge badge-type-individual">Particular</span>`;

        const tariffBadge = c.tariffName
            ? `<span class="badge" style="background-color:${c.tariffColor}15;color:${c.tariffColor};border:1px solid ${c.tariffColor}30;">${c.tariffName}</span>`
            : `<span class="badge badge-tariff-minorista">MINORISTA</span>`;

        const badgeRE = c.hasRecargoEquivalencia
            ? `<span class="badge bg-info text-dark">SÍ</span>`
            : `<span class="badge bg-light text-muted">NO</span>`;

        const tr = document.createElement('tr');
        tr.className = 'crm-row';
        tr.innerHTML = `
            <td><strong>${escHtml(c.name)}</strong></td>
            <td>${escHtml(c.taxId || '—')}</td>
            <td>${escHtml(c.email || '—')}</td>
            <td>${escHtml(c.phone || '—')}</td>
            <td>${escHtml(c.city || '—')}</td>
            <td>${badgeType}</td>
            <td>${tariffBadge}</td>
            <td>${badgeRE}</td>
            <td style="text-align:right">
                <button class="btn-icon" title="Historial ventas"
                    onclick="openCustomerSalesModal(${c.id}, '${escHtml(c.name).replace(/'/g, "\\'")}')">
                    <i class="bi bi-receipt-cutoff"></i>
                </button>
                <button class="btn-icon" title="Editar"
                    onclick="openCustomerModal(${c.id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" title="Eliminar"
                    onclick="deleteCustomer(${c.id}, '${escHtml(c.name).replace(/'/g, "\\'")}')">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetCRMFilters() {
    document.getElementById('crmFilterSearch').value = '';
    document.getElementById('crmFilterType').value   = '';
    document.getElementById('crmFilterRE').value     = '';
    const sortBy  = document.getElementById('crmFilterSortBy');
    const sortDir = document.getElementById('crmFilterSortDir');
    if (sortBy)  sortBy.value  = 'name';
    if (sortDir) sortDir.value = 'asc';
    filterCRM();
}

function uploadCustomersCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    showToast('Importando clientes desde CSV...', 'success');

    fetch('/admin/upload-customers-csv', { method: 'POST', body: formData })
        .then(r => r.json())
        .then(data => {
            if (data.ok) {
                showToast(data.message, 'success');
                setTimeout(() => location.reload(), 2000);
            } else {
                showToast(data.message || 'Error al procesar el archivo.', 'error');
            }
        })
        .catch(err => {
            console.error('Error uploading customers CSV:', err);
            showToast('Error de red al subir el archivo de clientes.', 'error');
        })
        .finally(() => { input.value = ''; });
}

// ── Global exports ────────────────────────────────────────────────────────────
window.openCustomerModal          = openCustomerModal;
window.saveCustomer               = saveCustomer;
window.deleteCustomer             = deleteCustomer;
window.toggleAdminCustomerType    = toggleAdminCustomerType;
window.checkCustomerReCompatibility = checkCustomerReCompatibility;
window.onDocTypeChange            = onDocTypeChange;
window.onNifDocTypeChange         = onNifDocTypeChange;
window.validateDocNumberInline    = validateDocNumberInline;
window.validateTaxIdInline        = validateTaxIdInline;
window.openCustomerSalesModal     = openCustomerSalesModal;
window.renderCustomerSales        = renderCustomerSales;
window.filterCRM                  = filterCRM;
window.renderCRMTable             = renderCRMTable;
window.resetCRMFilters            = resetCRMFilters;
window.uploadCustomersCsvFile     = uploadCustomersCsvFile;
