/**
 * admin-tariffs.js
 * Tariff (price lists) management functions.
 */

function openCreateTariffModal() {
    document.getElementById('newTariffName').value = '';
    document.getElementById('newTariffDiscount').value = '';
    document.getElementById('newTariffDescription').value = '';
    document.getElementById('newTariffColor').value = '#94a3b8'; // Default
    document.getElementById('createTariffError').style.display = 'none';
    initColorPicker('newTariffColorGrid', 'newTariffColor', '#94a3b8');
    if (window.tariffModal) window.tariffModal.show();
    else new bootstrap.Modal(document.getElementById('tariffModal')).show();
}

function saveTariff() {
    var name = document.getElementById('newTariffName').value.trim().toUpperCase();
    var discount = document.getElementById('newTariffDiscount').value;
    var description = document.getElementById('newTariffDescription').value.trim();
    var color = document.getElementById('newTariffColor').value;
    var errorEl = document.getElementById('createTariffError');

    if (!name) { errorEl.textContent = 'El nombre es obligatorio.'; errorEl.style.display = 'block'; return; }
    if (discount === '' || isNaN(parseFloat(discount))) { errorEl.textContent = 'Introduce un descuento válido.'; errorEl.style.display = 'block'; return; }

    fetch('/api/tariffs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, discountPercentage: parseFloat(discount), description, color })
    }).then(function (r) {
        if (r.ok) {
            return r.json();
        }
        return r.json().then(function (d) { throw new Error(d.error || d.message || 'Error al crear'); });
    }).then(function () {
        bootstrap.Modal.getInstance(document.getElementById('tariffModal')).hide();
        showToast('Tarifa creada correctamente', 'success');
        setTimeout(function () { location.reload(); }, 800);
    }).catch(function (e) {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function openEditTariffModal(btn) {
    document.getElementById('editTariffId').value = btn.dataset.id;
    document.getElementById('editTariffNameLabel').textContent = btn.dataset.name;
    document.getElementById('editTariffDiscount').value = btn.dataset.discount;
    document.getElementById('editTariffDescription').value = btn.dataset.description;
    document.getElementById('editTariffColor').value = btn.dataset.color || '#94a3b8';
    document.getElementById('editTariffError').style.display = 'none';
    initColorPicker('editTariffColorGrid', 'editTariffColor', btn.dataset.color || '#94a3b8');
    if (window.editTariffModal) window.editTariffModal.show();
    else new bootstrap.Modal(document.getElementById('editTariffModal')).show();
}

function updateTariff() {
    var id = document.getElementById('editTariffId').value;
    var discount = document.getElementById('editTariffDiscount').value;
    var description = document.getElementById('editTariffDescription').value.trim();
    var color = document.getElementById('editTariffColor').value;
    var errorEl = document.getElementById('editTariffError');

    if (discount === '' || isNaN(parseFloat(discount))) { errorEl.textContent = 'Introduce un descuento válido.'; errorEl.style.display = 'block'; return; }

    fetch('/api/tariffs/' + id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ discountPercentage: parseFloat(discount), description, color })
    }).then(function (r) {
        if (r.ok) {
            return r.json();
        }
        return r.json().then(function (d) { throw new Error(d.error || d.message || 'Error al actualizar'); });
    }).then(function () {
        bootstrap.Modal.getInstance(document.getElementById('editTariffModal')).hide();
        showToast('Tarifa actualizada correctamente', 'success');
        setTimeout(function () { location.reload(); }, 800);
    }).catch(function (e) {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function deactivateTariff(id, name) {
    if (!confirm('¿Desactivar la tarifa "' + name + '"? Los clientes que la tengan asignada pasarán a MINORISTA.')) return;
    fetch('/api/tariffs/' + id + '/deactivate', { method: 'DELETE' })
        .then(function (r) {
            if (r.ok) return r.json();
            return r.json().then(function (d) { throw new Error(d.error || d.message); });
        })
        .then(function () { showToast('Tarifa desactivada', 'success'); setTimeout(function () { location.reload(); }, 800); })
        .catch(function (e) { showToast(e.message || 'Error al desactivar', 'warning'); });
}

function activateTariff(id) {
    fetch('/api/tariffs/' + id + '/activate', { method: 'POST' })
        .then(function (r) {
            if (r.ok) return r.json();
            return r.json().then(function (d) { throw new Error(d.error || d.message || 'Error'); });
        })
        .then(function () { showToast('Tarifa activada', 'success'); setTimeout(function () { location.reload(); }, 800); })
        .catch(function (e) { showToast(e.message || 'Error al activar', 'warning'); });
}

function initColorPicker(gridId, inputId, activeColor) {
    const grid = document.getElementById(gridId);
    if (!grid) return;
    const input = document.getElementById(inputId);
    const swatches = grid.querySelectorAll('.color-swatch-p');

    swatches.forEach(sw => {
        const color = sw.dataset.color;
        sw.classList.toggle('active', color === activeColor);
        sw.onclick = function () {
            swatches.forEach(s => s.classList.remove('active'));
            sw.classList.add('active');
            input.value = color;
        };
    });
}

let isTariffEditMode = false;
let tariffPriceChanges = new Map(); // Key: "productId-tariffId", Value: {productId, tariffId, productName, tariffName, newPrice}

function toggleTariffEditMode() {
    isTariffEditMode = !isTariffEditMode;
    const btn = document.getElementById('btnToggleTariffEdit');
    const finishBtn = document.getElementById('btnFinishTariffEdit');
    
    if (!btn) return;

    if (isTariffEditMode) {
        btn.innerHTML = '<i class="bi bi-x-circle me-1"></i> Cancelar Edición';
        btn.classList.replace('btn-outline-accent', 'btn-outline-danger');
        if (finishBtn) finishBtn.style.display = 'inline-block';
    } else {
        btn.innerHTML = '<i class="bi bi-pencil-square me-1"></i> Modo Edición';
        btn.classList.replace('btn-outline-danger', 'btn-outline-accent');
        if (finishBtn) finishBtn.style.display = 'none';
        tariffPriceChanges.clear();
    }
    
    // Refresh current view to show/hide inputs
    const searchInput = document.getElementById('tariffComparisonSearch');
    fetchTariffComparisonData(searchInput ? searchInput.value.trim() : '', 0);
}

function trackTariffPriceChange(productId, tariffId, productName, tariffName, value) {
    const val = parseFloat(value);
    if (isNaN(val)) return;
    tariffPriceChanges.set(`${productId}-${tariffId}`, { productId, tariffId, productName, tariffName, newPrice: val });
}

function openApplyPricesModal() {
    const list = document.getElementById('pendingChangesList');
    if (!list) return;
    list.innerHTML = '';
    
    if (tariffPriceChanges.size === 0) {
        showToast('No hay cambios pendientes para aplicar', 'warning');
        return;
    }
    
    document.getElementById('pendingChangesCount').textContent = tariffPriceChanges.size;
    
    tariffPriceChanges.forEach((change) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><div class="small fw-bold">${change.productName}</div></td>
            <td><span class="badge ${change.tariffId === 'base' ? 'bg-secondary' : 'bg-info'}">${change.tariffName}</span></td>
            <td class="text-end fw-bold text-accent">${change.newPrice.toFixed(2).replace('.', ',')} €</td>
        `;
        list.appendChild(tr);
    });
    
    if (window.applyPricesModal) {
        window.applyPricesModal.show();
    } else {
        const el = document.getElementById('applyPricesModal');
        if (el) {
            window.applyPricesModal = new bootstrap.Modal(el);
            window.applyPricesModal.show();
        }
    }
}

function submitBulkPriceUpdate() {
    if (tariffPriceChanges.size === 0) return;
    
    const date = document.getElementById('applyPricesdate')?.value;
    const time = document.getElementById('applyPricesTime')?.value || '00:00';
    
    const effectiveDate = date ? `${date}T${time}:00` : new Date().toISOString();
    
    const changes = Array.from(tariffPriceChanges.values()).map(c => ({
        productId: c.productId,
        tariffId: c.tariffId === 'base' ? null : c.tariffId,
        newPrice: c.newPrice
    }));

    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch('/api/admin/bulk-price-update', {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        },
        body: JSON.stringify({ effectiveDate, changes })
    }).then(res => {
        if (res.ok) {
            showToast('Actualización de precios procesada');
            if (window.applyPricesModal) window.applyPricesModal.hide();
            toggleTariffEditMode(); // Exit edit mode
        } else {
            showToast('Error al aplicar cambios', 'error');
        }
    }).catch(err => {
        console.error("Error submitting bulk update:", err);
        showToast('Error de conexión', 'error');
    });
}

function openPriceChangesHistoryModal() {
    fetchPendingMatrixUpdates();
    fetchMatrixUpdateHistory();
    
    if (window.priceChangesHistoryModal) {
        window.priceChangesHistoryModal.show();
    } else {
        const el = document.getElementById('priceChangesHistoryModal');
        if (el) {
            window.priceChangesHistoryModal = new bootstrap.Modal(el);
            window.priceChangesHistoryModal.show();
        }
    }
}

function fetchPendingMatrixUpdates() {
    const tbody = document.getElementById('tablePendingPrices')?.querySelector('tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-3"><span class="spinner-border spinner-border-sm"></span></td></tr>';
    
    fetch('/api/admin/price-updates/pending')
        .then(res => res.json())
        .then(data => {
            tbody.innerHTML = '';
            if (!data || data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No hay cambios pendientes.</td></tr>';
                return;
            }
            
            data.forEach(item => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><span class="small">${formatDateTime(item.effectiveDate)}</span></td>
                    <td><div class="fw-bold">${item.productName}</div></td>
                    <td><span class="badge bg-info">${item.tariffName || 'BASE'}</span></td>
                    <td class="text-end fw-bold text-accent">${formatDecimal(item.newPrice)} €</td>
                    <td class="text-center">
                        <button class="btn btn-sm btn-outline-danger py-0 px-2" onclick="deletePendingPriceUpdate(${item.id})">
                            <i class="bi bi-trash"></i>
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function fetchMatrixUpdateHistory() {
    const tbody = document.getElementById('tablePastPrices')?.querySelector('tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-3"><span class="spinner-border spinner-border-sm"></span></td></tr>';
    
    fetch('/api/admin/price-updates/history')
        .then(res => res.json())
        .then(data => {
            tbody.innerHTML = '';
            if (!data || data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No hay historial reciente.</td></tr>';
                return;
            }
            
            data.forEach(item => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><span class="small">${formatDateTime(item.appliedAt || item.effectiveDate)}</span></td>
                    <td><div class="fw-bold">${item.productName}</div></td>
                    <td><span class="badge bg-secondary">${item.tariffName || 'BASE'}</span></td>
                    <td class="text-end text-muted">${formatDecimal(item.oldPrice)} €</td>
                    <td class="text-end fw-bold text-success">${formatDecimal(item.newPrice)} €</td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function deletePendingPriceUpdate(id) {
    if (!confirm('¿Eliminar este cambio programado?')) return;
    
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;

    fetch('/api/admin/price-updates/' + id, { 
        method: 'DELETE',
        headers: { 
            ...(csrfHeader ? { [csrfHeader]: csrfToken } : {})
        }
    }).then(res => {
        if (res.ok) {
            showToast('Cambio eliminado');
            fetchPendingMatrixUpdates();
        } else {
            showToast('Error al eliminar', 'error');
        }
    });
}

let tariffSearchTimeout;

function filterTariffComparison() {
    clearTimeout(tariffSearchTimeout);
    const input = document.getElementById('tariffComparisonSearch');
    if (!input) return;
    const filter = input.value.trim();

    tariffSearchTimeout = setTimeout(() => {
        fetchTariffComparisonData(filter, 0);
    }, 400);
}

function loadTariffs() {
    fetchTariffComparisonData('', 0);
}

function fetchTariffComparisonData(search, page) {
    const tbody = document.getElementById('tariffComparisonTable')?.querySelector('tbody');
    if (!tbody) return;

    // Show loading state
    tbody.innerHTML = '<tr><td colspan="10" class="text-center py-5"><div class="spinner-border text-accent me-2" role="status"></div><span class="text-muted">Buscando productos...</span></td></tr>';

    const params = new URLSearchParams();
    if (search) {
        params.append('search', search);
        params.append('size', 50);
        params.append('sortBy', 'nameEs');
        params.append('sortDir', 'asc');
    } else {
        params.append('size', 10);
        params.append('sortBy', 'salesRank');
        params.append('sortDir', 'desc');
    }
    params.append('page', page);

    fetch(`/api/admin/products?${params.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderTariffComparisonRows(data.content || []);
            
            const labelEl = document.getElementById('tariffComparisonLabel');
            if (labelEl) {
                if (search) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || []).length} resultados para "${search}".`;
                } else {
                    labelEl.innerHTML = '<i class="bi bi-star-fill text-warning me-1"></i> Mostrando los 10 productos más vendidos (Top Rank).';
                }
            }
        })
        .catch(err => {
            console.error("Error fetching tariff comparison:", err);
            tbody.innerHTML = '<tr><td colspan="10" class="text-center text-danger py-4">Error al conectar con el servidor.</td></tr>';
        });
}

function renderTariffComparisonRows(products) {
    const tbody = document.getElementById('tariffComparisonTable')?.querySelector('tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (products.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted py-5">No se encontraron productos que coincidan con la búsqueda.</td></tr>';
        return;
    }

    const tariffs = window.activeTariffs || [];

    products.forEach(p => {
        const tr = document.createElement('tr');
        tr.className = 'comparison-row';
        
        let html = `
            <td class="py-2">
                <div class="fw-bold" style="color: var(--text-main); font-size: 0.85rem;">${p.name}</div>
                <small class="text-muted" style="font-size: 0.7rem;">${p.categoryName || 'Sin categoría'}</small>
            </td>
        `;

        if (isTariffEditMode) {
            const keyBase = `${p.id}-base`;
            const valBase = tariffPriceChanges.has(keyBase) ? tariffPriceChanges.get(keyBase).newPrice : p.price;
            html += `
                <td class="text-end py-1">
                    <input type="number" class="form-control form-control-sm text-end fw-bold" 
                        step="0.01" value="${parseFloat(valBase || 0).toFixed(2)}"
                        onchange="trackTariffPriceChange(${p.id}, 'base', '${p.name.replace(/'/g, "\\'")}', 'BASE', this.value)"
                        style="background: var(--surface-light); border: 1px solid var(--accent); color: var(--accent); font-size: 0.9rem; width: 100px; display: inline-block;">
                </td>
            `;

            tariffs.forEach(t => {
                const key = `${p.id}-${t.id}`;
                const basePrice = p.price || 0;
                const dto = t.discountPercentage || 0;
                const calculated = basePrice * (1 - dto/100);
                const val = tariffPriceChanges.has(key) ? tariffPriceChanges.get(key).newPrice : calculated;
                
                html += `
                    <td class="text-end py-1">
                        <input type="number" class="form-control form-control-sm text-end fw-bold" 
                            step="0.01" value="${parseFloat(val || 0).toFixed(2)}"
                            onchange="trackTariffPriceChange(${p.id}, ${t.id}, '${p.name.replace(/'/g, "\\'")}', '${t.name.replace(/'/g, "\\'")}', this.value)"
                            style="background: var(--surface-light); border: 1px solid var(--border); color: var(--text-main); font-size: 0.9rem; width: 100px; display: inline-block;">
                    </td>
                `;
            });
        } else {
            html += `
                <td class="text-end fw-bold py-2 price-cell price-base" 
                    data-product-id="${p.id}" data-tariff-id="base"
                    style="font-size: 1rem; color: #8892a4;">
                    ${parseFloat(p.price || 0).toFixed(2).replace('.', ',')} €
                </td>
            `;

            tariffs.forEach(t => {
                const dto = t.discountPercentage || 0;
                const basePrice = p.price || 0;
                const finalPrice = basePrice * (1 - dto/100);
                
                html += `
                    <td class="text-end py-2" style="font-weight: 700; font-size: 1.05rem;">
                        <span class="price-cell" data-product-id="${p.id}" data-tariff-id="${t.id}">
                            ${finalPrice.toFixed(2).replace('.', ',')} €
                        </span>
                    </td>
                `;
            });
        }

        tr.innerHTML = html;
        tbody.appendChild(tr);
    });
}

// Global Exports
window.openCreateTariffModal = openCreateTariffModal;
window.saveTariff = saveTariff;
window.openEditTariffModal = openEditTariffModal;
window.updateTariff = updateTariff;
window.deactivateTariff = deactivateTariff;
window.activateTariff = activateTariff;
window.initColorPicker = initColorPicker;
window.filterTariffComparison = filterTariffComparison;
window.loadTariffs = loadTariffs;
window.fetchTariffComparisonData = fetchTariffComparisonData;
window.renderTariffComparisonRows = renderTariffComparisonRows;
window.toggleTariffEditMode = toggleTariffEditMode;
window.openApplyPricesModal = openApplyPricesModal;
window.submitBulkPriceUpdate = submitBulkPriceUpdate;
window.openPriceChangesHistoryModal = openPriceChangesHistoryModal;
window.deletePendingPriceUpdate = deletePendingPriceUpdate;
window.trackTariffPriceChange = trackTariffPriceChange;

