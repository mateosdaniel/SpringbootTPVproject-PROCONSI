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
    new bootstrap.Modal(document.getElementById('tariffModal')).show();
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
        bootstrap.Modal.getInstance(document.getElementById('createTariffModal')).hide();
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
    new bootstrap.Modal(document.getElementById('editTariffModal')).show();
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

let tariffSearchTimeout;

function filterTariffComparison() {
    clearTimeout(tariffSearchTimeout);
    const input = document.getElementById('tariffComparisonSearch');
    if (!input) return;
    const filter = input.value.trim();

    // If search is short, we could stick to client-side filtering of the current 50
    // but the user specifically asked for "global search like inventory"
    
    tariffSearchTimeout = setTimeout(() => {
        fetchTariffComparisonData(filter, 0);
    }, 400);
}

function fetchTariffComparisonData(search, page) {
    const tbody = document.getElementById('tariffComparisonTable')?.querySelector('tbody');
    if (!tbody) return;

    // Show loading state
    tbody.innerHTML = '<tr><td colspan="10" class="text-center py-5"><div class="spinner-border text-accent me-2" role="status"></div><span class="text-muted">Buscando en catálogo global...</span></td></tr>';

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    params.append('page', page);
    params.append('size', 50);

    fetch(`/api/admin/products?${params.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderTariffComparisonRows(data.content || []);
            
            const labelEl = document.getElementById('tariffComparisonLabel');
            if (labelEl) {
                if (search) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || []).length} precios coincidentes con "${search}".`;
                } else {
                    labelEl.textContent = 'Mostrando todos los precios actuales.';
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
