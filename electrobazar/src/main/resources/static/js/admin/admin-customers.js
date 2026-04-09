/**
 * admin-customers.js
 * Customer management functions.
 */

function openCustomerModal(id) {
    document.getElementById('customerForm').reset();
    document.getElementById('customerId').value = id || '';
    document.getElementById('customermodalLabel').textContent = id ? 'Editar Cliente' : 'Nuevo Cliente';
    document.getElementById('customerRecargoEquivalencia').checked = false;

    if (id) {
        fetch('/api/customers/' + id)
            .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
            .then(function (c) {
                document.getElementById('customerId').value = c.id;
                document.getElementById('customerName').value = c.name || '';
                document.getElementById('customerTaxId').value = c.taxId || '';
                document.getElementById('customerEmail').value = c.email || '';
                document.getElementById('customerPhone').value = c.phone || '';
                document.getElementById('customerAddress').value = c.address || '';
                document.getElementById('customerCity').value = c.city || '';
                document.getElementById('customerPostalCode').value = c.postalCode || '';

                var type = c.type || 'INDIVIDUAL';
                document.getElementById('customerType').value = type;
                if (type === 'COMPANY') {
                    document.getElementById('adminTypeCompany').checked = true;
                } else {
                    document.getElementById('adminTypeIndividual').checked = true;
                }

                document.getElementById('customerActive').checked = c.active !== false;
                // Load Recargo de Equivalencia flag
                document.getElementById('customerRecargoEquivalencia').checked = c.hasRecargoEquivalencia === true;

                // Load tariff
                var tariffSelect = document.getElementById('customerTariffId');
                if (tariffSelect && c.tariff) {
                    tariffSelect.value = c.tariff.id;
                } else if (tariffSelect) {
                    tariffSelect.value = '';
                }

                toggleAdminCustomerType();
                checkCustomerReCompatibility();
            })
            .catch(function () { showToast('Error al cargar el cliente', 'error'); });
    } else {
        toggleAdminCustomerType();
        checkCustomerReCompatibility();
    }
    customerModal.show();
}

function checkCustomerReCompatibility() {
    const tariffSelect = document.getElementById('customerTariffId');
    const reCheckbox = document.getElementById('customerRecargoEquivalencia');
    const reSection = document.getElementById('adminCustomerReSection');
    const reWarning = document.getElementById('adminCustomerReIncompatibleMsg');
    const reInfo = document.getElementById('adminCustomerReInfoMsg');

    if (!tariffSelect || !reCheckbox) return;

    const selectedOption = tariffSelect.options[tariffSelect.selectedIndex];
    const tariffText = selectedOption ? selectedOption.text.toLowerCase() : "";
    const isMinorista = (tariffSelect.value === "" || tariffText.includes("minorista"));

    if (isMinorista) {
        reCheckbox.disabled = false;
        if (reSection) reSection.style.opacity = "1";
        if (reWarning) reWarning.classList.add('d-none');
        if (reInfo) reInfo.classList.remove('d-none');
    } else {
        reCheckbox.checked = false;
        reCheckbox.disabled = true;
        if (reSection) reSection.style.opacity = "0.7";
        if (reWarning) {
            reWarning.classList.remove('d-none');
        }
        if (reInfo) reInfo.classList.add('d-none');
    }
}

function toggleAdminCustomerType() {
    var isCompany = document.getElementById('adminTypeCompany').checked;
    const typeInput = document.getElementById('customerType');
    if (typeInput) typeInput.value = isCompany ? 'COMPANY' : 'INDIVIDUAL';

    var nameLabel = document.getElementById('lblAdminCustomerName');
    var taxLabel = document.getElementById('lblAdminCustomerTaxId');
    var taxInput = document.getElementById('customerTaxId');

    if (nameLabel) {
        nameLabel.innerHTML = isCompany ? 'Razón Social <span class="text-danger">*</span>' : 'Nombre y Apellidos <span class="text-danger">*</span>';
    }
    if (taxLabel) {
        if (isCompany) {
            taxLabel.innerHTML = 'CIF <span class="text-danger">*</span>';
            if (taxInput) taxInput.setAttribute('required', 'required');
        } else {
            taxLabel.innerHTML = 'NIF/NIE <span class="text-danger">*</span>';
            if (taxInput) taxInput.setAttribute('required', 'required');
        }
    }

    var reSection = document.getElementById('adminCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) {
            const reCheck = document.getElementById('customerRecargoEquivalencia');
            if (reCheck) reCheck.checked = false;
        } else {
            // Re-check compatibility if section becomes visible
            checkCustomerReCompatibility();
        }
    }
}

function saveCustomer() {
    const nameInput = document.getElementById('customerName');
    const name = nameInput ? nameInput.value.trim() : '';
    if (!name) { showToast('El nombre es obligatorio', 'error'); return; }

    const id = document.getElementById('customerId').value;
    const body = {
        name: name,
        taxId: document.getElementById('customerTaxId').value.trim() || null,
        email: document.getElementById('customerEmail').value.trim() || null,
        phone: document.getElementById('customerPhone').value.trim() || null,
        address: document.getElementById('customerAddress').value.trim() || null,
        city: document.getElementById('customerCity').value.trim() || null,
        postalCode: document.getElementById('customerPostalCode').value.trim() || null,
        type: document.getElementById('customerType').value,
        active: document.getElementById('customerActive').checked,
        hasRecargoEquivalencia: document.getElementById('customerRecargoEquivalencia').checked,
        tariffId: document.getElementById('customerTariffId') && document.getElementById('customerTariffId').value
            ? parseInt(document.getElementById('customerTariffId').value) : null
    };

    // validation
    if (!body.taxId) {
        showToast('El NIF/NIE es obligatorio', 'error');
        return;
    }

    // Double check RE compatibility before saving
    if (body.hasRecargoEquivalencia) {
        const tariffSelect = document.getElementById('customerTariffId');
        const selectedOption = tariffSelect.options[tariffSelect.selectedIndex];
        const tariffText = selectedOption ? selectedOption.text.toLowerCase() : "";
        const isMinorista = (tariffSelect.value === "" || tariffText.includes("minorista"));
        
        if (!isMinorista) {
            showToast('No es posible aplicar el recargo de equivalencia a esta tarifa', 'error');
            return;
        }
    }

    const method = id ? 'PUT' : 'POST';
    const url = id ? '/api/customers/' + id : '/api/customers';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(function (r) {
            if (!r.ok) {
                return r.json().then(function (data) {
                    var msg = data && (data.error || data.message) ? data.error || data.message : 'Respuesta inesperada';
                    throw new Error(msg);
                }).catch(function () {
                    throw new Error('Estado HTTP ' + r.status);
                });
            }
            return r.json();
        })
        .then(function () {
            customerModal.hide();
            showToast(id ? 'Cliente actualizado correctamente' : 'Cliente creado correctamente');
            setTimeout(function () { location.reload(); }, 900);
        })
        .catch(function (e) {
            showToast('Error al guardar el cliente: ' + (e.message || ''), 'error');
        });
}

function deleteCustomer(id, name) {
    if (!confirm('¿Seguro que quieres eliminar (desactivar) al cliente "' + name + '"?')) return;
    fetch('/api/customers/' + id, { method: 'DELETE' })
        .then(function (r) {
            if (r.ok) {
                showToast('Cliente desactivado correctamente');
                setTimeout(function () { location.reload(); }, 900);
            } else {
                r.json().then(function (err) {
                    showToast('Error al eliminar cliente: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al eliminar cliente', 'error');
                });
            }
        })
        .catch(function () { showToast('Error de red al eliminar el cliente', 'error'); });
}

function openCustomerSalesModal(id, name) {
    const el = document.getElementById('customerSalesModal');
    if (!el) return;
    const modal = bootstrap.Modal.getOrCreateInstance(el);
    
    document.getElementById('customerSalesModalName').textContent = name || 'Cliente';
    document.getElementById('customerSalesBody').innerHTML =
        '<div class="text-center py-5"><span class="spinner-border spinner-border-sm me-2"></span>Cargando...</div>';
    var statsDiv = document.getElementById('customerSalesStats');
    if (statsDiv) statsDiv.style.setProperty('display', 'none', 'important');
    modal.show();

    fetch('/api/customers/' + id + '/sales')
        .then(function (r) {
            if (!r.ok) throw new Error('Error HTTP ' + r.status);
            return r.json();
        })
        .then(function (sales) {
            renderCustomerSales(sales);
        })
        .catch(function (e) {
            document.getElementById('customerSalesBody').innerHTML =
                '<div class="text-center text-danger py-5"><i class="bi bi-exclamation-triangle me-2"></i>Error al cargar las ventas: ' + escHtml(e.message) + '</div>';
        });
}

function renderCustomerSales(sales) {
    var body = document.getElementById('customerSalesBody');
    var statsDiv = document.getElementById('customerSalesStats');

    if (!sales || sales.length === 0) {
        body.innerHTML =
            '<div class="text-center py-5" style="color:var(--text-muted);">' +
            '<i class="bi bi-receipt fs-1 d-block mb-3"></i>' +
            '<p class="mb-0">Este cliente no tiene ventas registradas.</p></div>';
        if (statsDiv) statsDiv.style.setProperty('display', 'none', 'important');
        return;
    }

    // ── stats ──
    var totalAmt = sales.reduce(function (s, v) { return s + parseFloat(v.totalAmount || 0); }, 0);
    var lastDate = sales[0] ? formatDateTime(sales[0].createdAt) : '—';
    document.getElementById('csSaleCount').textContent = sales.length;
    document.getElementById('csTotalAmount').textContent = totalAmt.toFixed(2) + ' €';
    document.getElementById('csAvgAmount').textContent = (totalAmt / sales.length).toFixed(2) + ' €';
    document.getElementById('csLastSale').textContent = lastDate;
    if (statsDiv) {
        statsDiv.style.removeProperty('display');
        statsDiv.style.display = 'flex';
    }

    // ── tabla de ventas ──
    var payLabel = { 'CASH': '<i class="bi bi-cash me-1"></i>Efectivo', 'CARD': '<i class="bi bi-credit-card me-1"></i>Tarjeta', 'MIXED': '<i class="bi bi-wallet me-1"></i>Mixto' };

    var html = '<div class="accordion" id="csAccordion">';
    sales.forEach(function (s, idx) {
        var statusBadge = s.status === 'CANCELLED'
            ? '<span class="badge" style="background:rgba(239,68,68,.15);color:#ef4444;border:1px solid rgba(239,68,68,.3);font-size:.75rem;">ANULADA</span>'
            : '<span class="badge" style="background:rgba(34,197,94,.12);color:#22c55e;border:1px solid rgba(34,197,94,.3);font-size:.75rem;">ACTIVA</span>';

        var pmLabel = payLabel[s.paymentMethod] || escHtml(s.paymentMethod || '—');
        var dateStr = formatDateTime(s.createdAt);
        var total = parseFloat(s.totalAmount || 0).toFixed(2);
        var worker = s.workerName ? escHtml(s.workerName) : '<span style="color:var(--text-muted)">—</span>';
        var tariff = s.appliedTariff ? escHtml(s.appliedTariff) : 'MINORISTA';

        // lines detail
        var linesHtml = '';
        if (s.lines && s.lines.length > 0) {
            linesHtml += '<table style="width:100%;border-collapse:collapse;font-size:.85rem;">' +
                '<thead><tr>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);">Producto</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:center;">Cant.</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:right;">P. Unit.</th>' +
                '<th style="padding:.4rem .6rem;color:var(--text-muted);font-weight:600;border-bottom:1px solid var(--border);text-align:right;">Subtotal</th>' +
                '</tr></thead><tbody>';
            s.lines.forEach(function (l) {
                linesHtml += '<tr>' +
                    '<td style="padding:.4rem .6rem;color:var(--text-main);">' + escHtml(l.productName) + '</td>' +
                    '<td style="padding:.4rem .6rem;text-align:center;color:var(--text-muted);">' + (l.quantity || 0) + '</td>' +
                    '<td style="padding:.4rem .6rem;text-align:right;color:var(--text-muted);">' + parseFloat(l.unitPrice || 0).toFixed(2) + ' €</td>' +
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
            '</div>' +
            linesHtml +
            '</div></div></div>';
    });
    html += '</div>';
    body.innerHTML = html;
}

function filterCRM() {
    const search = document.getElementById('crmFilterSearch').value.trim();
    const type = document.getElementById('crmFilterType').value;
    const re = document.getElementById('crmFilterRE').value;
    const sortBy = document.getElementById('crmFilterSortBy').value;
    const sortDir = document.getElementById('crmFilterSortDir').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (type) queryParams.append('type', type);
    if (re) queryParams.append('re', re);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/customers?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderCRMTable(data.content || data);
        })
        .catch(err => console.error("Error filtering CRM:", err));
}

function renderCRMTable(items) {
    const tbody = document.getElementById('crmTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="9" class="text-center py-4" style="color: var(--text-muted);">No hay clientes registrados.</td></tr>`;
        return;
    }

    items.forEach(c => {
        const badgeType = c.type === 'COMPANY' 
            ? `<span class="badge badge-type-company">Empresa / Prof.</span>`
            : `<span class="badge badge-type-individual">Particular</span>`;

        const tariffBadge = c.tariffName 
            ? `<span class="badge" style="background-color:${c.tariffColor}15; color:${c.tariffColor}; border: 1px solid ${c.tariffColor}30;">${c.tariffName}</span>`
            : `<span class="badge badge-tariff-minorista">MINORISTA</span>`;

        const badgeRE = c.hasRecargoEquivalencia
            ? `<span class="badge bg-info text-dark">SÍ</span>`
            : `<span class="badge bg-light text-muted">NO</span>`;

        const tr = document.createElement('tr');
        tr.className = 'crm-row';
        tr.innerHTML = `
            <td><strong>${c.name}</strong></td>
            <td>${c.taxId || '—'}</td>
            <td>${c.email || '—'}</td>
            <td>${c.phone || '—'}</td>
            <td>${c.city || '—'}</td>
            <td>${badgeType}</td>
            <td>${tariffBadge}</td>
            <td>${badgeRE}</td>
            <td style="text-align:right">
                <button class="btn-icon" title="Editar" 
                    onclick="openCustomerModal(${c.id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" title="Eliminar" onclick="deleteCustomer(${c.id}, '${c.name.replace(/'/g, "\\'")}')">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetCRMFilters() {
    document.getElementById('crmFilterSearch').value = '';
    document.getElementById('crmFilterType').value = '';
    document.getElementById('crmFilterRE').value = '';
    const sortBy = document.getElementById('crmFilterSortBy');
    const sortDir = document.getElementById('crmFilterSortDir');
    if (sortBy) sortBy.value = 'name';
    if (sortDir) sortDir.value = 'asc';
    filterCRM();
}

function uploadCustomersCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    var file = input.files[0];
    var formData = new FormData();
    formData.append('file', file);

    showToast('Importando clientes desde CSV...', 'success');

    fetch('/admin/upload-customers-csv', {
        method: 'POST',
        body: formData
    })
        .then(function (response) { return response.json(); })
        .then(function (data) {
            if (data.ok) {
                showToast(data.message, 'success');
                setTimeout(function () { location.reload(); }, 2000);
            } else {
                showToast(data.message || 'Error al procesar el archivo de clientes.', 'error');
            }
        })
        .catch(function (error) {
            console.error('Error uploading customers CSV:', error);
            showToast('Error de red al subir el archivo de clientes.', 'error');
        })
        .finally(() => {
            input.value = '';
        });
}

// Global Exports
window.openCustomerModal = openCustomerModal;
window.saveCustomer = saveCustomer;
window.deleteCustomer = deleteCustomer;
window.toggleAdminCustomerType = toggleAdminCustomerType;
window.checkCustomerReCompatibility = checkCustomerReCompatibility;
window.openCustomerSalesModal = openCustomerSalesModal;
window.renderCustomerSales = renderCustomerSales;
window.filterCRM = filterCRM;
window.renderCRMTable = renderCRMTable;
window.resetCRMFilters = resetCRMFilters;
window.uploadCustomersCsvFile = uploadCustomersCsvFile;
