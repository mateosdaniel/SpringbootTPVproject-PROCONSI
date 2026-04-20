/**
 * admin-tax-rates.js
 * Tax rate (IVA) management functions.
 */

function openCreateTaxRateModal() {
    document.getElementById('newTaxRateDescription').value = '';
    document.getElementById('newTaxRateVat').value = '';
    document.getElementById('newTaxRateRe').value = '';
    document.getElementById('newTaxRateValidFrom').value = new Date().toISOString().split('T')[0];
    document.getElementById('newTaxRateValidTo').value = '';
    document.getElementById('newTaxRateActive').checked = true;
    document.getElementById('createTaxRateError').style.display = 'none';
    new bootstrap.Modal(document.getElementById('createTaxRateModal')).show();
}

function saveTaxRate() {
    const description = document.getElementById('newTaxRateDescription').value.trim();
    const vatRate = document.getElementById('newTaxRateVat').value;
    const reRate = document.getElementById('newTaxRateRe').value;
    const validFrom = document.getElementById('newTaxRateValidFrom').value;
    const validTo = document.getElementById('newTaxRateValidTo').value || null;
    const active = document.getElementById('newTaxRateActive').checked;
    const errorEl = document.getElementById('createTaxRateError');

    if (!description || !vatRate || !reRate || !validFrom) {
        errorEl.textContent = 'Descripción, IVA, RE y Fecha Inicio son obligatorios.';
        errorEl.style.display = 'block';
        return;
    }

    fetch('/admin/api/tax-rates', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description, vatRate: parseFloat(vatRate), reRate: parseFloat(reRate), validFrom, validTo, active })
    }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => { throw new Error(d.error || d.message || 'Error al guardar'); });
        }
    }).catch(e => {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function openEditTaxRateModal(btn) {
    document.getElementById('editTaxRateId').value = btn.dataset.id;
    document.getElementById('editTaxRateDescription').value = btn.dataset.description;
    document.getElementById('editTaxRateVat').value = btn.dataset.vatrate;
    document.getElementById('editTaxRateRe').value = btn.dataset.rerate;
    document.getElementById('editTaxRateValidFrom').value = btn.dataset.validfrom;
    document.getElementById('editTaxRateValidTo').value = btn.dataset.validto;
    document.getElementById('editTaxRateActive').checked = btn.dataset.active === 'true';
    document.getElementById('editTaxRateError').style.display = 'none';
    new bootstrap.Modal(document.getElementById('editTaxRateModal')).show();
}

function updateTaxRate() {
    const id = document.getElementById('editTaxRateId').value;
    const description = document.getElementById('editTaxRateDescription').value.trim();
    const vatRate = document.getElementById('editTaxRateVat').value;
    const reRate = document.getElementById('editTaxRateRe').value;
    const validFrom = document.getElementById('editTaxRateValidFrom').value;
    const validTo = document.getElementById('editTaxRateValidTo').value || null;
    const active = document.getElementById('editTaxRateActive').checked;
    const errorEl = document.getElementById('editTaxRateError');

    fetch('/admin/api/tax-rates/' + id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ description, vatRate: parseFloat(vatRate), reRate: parseFloat(reRate), validFrom, validTo, active })
    }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => { throw new Error(d.error || d.message || 'Error al actualizar'); });
        }
    }).catch(e => {
        errorEl.textContent = e.message;
        errorEl.style.display = 'block';
    });
}

function deleteTaxRate(id, msg) {
    if (!confirm('¿Eliminar el tipo de IVA "' + msg + '"?')) return;
    fetch('/admin/api/tax-rates/' + id, { method: 'DELETE' }).then(r => {
        if (r.ok) {
            location.reload();
        } else {
            return r.json().then(d => {
                showToast('Error: ' + (d.error || d.message || 'Error al eliminar'), 'warning');
            }).catch(() => {
                showToast('Error al eliminar', 'warning');
            });
        }
    }).catch(() => {
        showToast('Error de red al eliminar', 'warning');
    });
}

// State: whether "select ALL system products" mode is active for IVA
let ivaSelectAllAbsolute = false;

// Cache the original static (top-100) list HTML to restore when search is cleared
let _selProductStaticHtml = null;
let _selProductSearchTimeout = null;

async function loadTaxRates() {
    const tableBody = document.getElementById('taxRatesTableBody');
    const selProductList = document.getElementById('selProductList');
    const selCategoryList = document.getElementById('selCategoryList');
    const selectiveTaxSelect = document.getElementById('selectiveTaxRateId');

    if (!tableBody) return;

    // Show skeletons/loading
    tableBody.innerHTML = '<tr><td colspan="7" class="text-center py-4"><span class="spinner-border spinner-border-sm me-2"></span>Cargando tipos de IVA...</td></tr>';

    try {
        // 1. Fetch Tax Rates
        const resRates = await fetch('/admin/api/tax-rates');
        const rates = await resRates.json();

        // Render Table
        tableBody.innerHTML = rates.map(tr => `
            <tr>
                <td><strong>${tr.description}</strong></td>
                <td>${(tr.vatRate * 100).toFixed(1)}%</td>
                <td>${(tr.reRate * 100).toFixed(2)}%</td>
                <td>
                    <span class="badge-active ${tr.active ? 'yes' : 'no'}">
                        ${tr.active ? 'Si' : 'No'}
                    </span>
                </td>
                <td>${tr.validFrom}</td>
                <td>${tr.validTo || '—'}</td>
                <td style="text-align:right">
                    <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                        <button class="btn-icon" title="Editar" 
                            data-id="${tr.id}"
                            data-description="${tr.description}" 
                            data-vatrate="${tr.vatRate}"
                            data-rerate="${tr.reRate}" 
                            data-active="${tr.active}"
                            data-validfrom="${tr.validFrom}" 
                            data-validto="${tr.validTo || ''}"
                            onclick="openEditTaxRateModal(this)">
                            <i class="bi bi-pencil"></i>
                        </button>
                        <button class="btn-icon danger" title="Eliminar" 
                            onclick="deleteTaxRate(${tr.id}, '${tr.description}')">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

        // Update selective VAT dropdown
        if (selectiveTaxSelect) {
            const currentVal = selectiveTaxSelect.value;
            selectiveTaxSelect.innerHTML = '<option value="">Elige un tipo de IVA</option>' + 
                rates.filter(tr => tr.active).map(tr => 
                    `<option value="${tr.id}" ${currentVal == tr.id ? 'selected' : ''}>${tr.description} (${(tr.vatRate * 100).toFixed(1)}%)</option>`
                ).join('');
        }

        // 2. Fetch Top Products for selection
        const resProducts = await fetch('/api/products/filter?size=100&sortBy=salesRank&sortDir=desc');
        const productsData = await resProducts.json();
        const products = productsData.content || productsData || [];
        
        selProductList.innerHTML = products.map(p => `
            <div class="sel-product-item-wrap mb-1" data-name="${(p.name || '').toLowerCase()}">
                <div class="rounded hover-surface shadow-sm" style="background:var(--surface);border:1px solid var(--border);transition:all 0.2s;">
                    <label class="d-flex align-items-center p-2 m-0 w-100" style="cursor:pointer;" for="selProd${p.id}">
                        <input class="form-check-input sel-product-cb me-3 mt-0 flex-shrink-0"
                            type="checkbox" value="${p.id}" id="selProd${p.id}" style="margin-left:5px;">
                        <div class="flex-grow-1 d-flex justify-content-between align-items-center pe-1">
                            <span class="fw-600" style="font-size:0.85rem; color:var(--text-main);">${p.name}</span>
                            <span class="badge bg-secondary text-muted rounded-pill small px-2">
                                IVA: ${p.taxRate ? (p.taxRate.vatRate * 100).toFixed(0) + '%' : '—'}
                            </span>
                        </div>
                    </label>
                </div>
            </div>
        `).join('');
        _selProductStaticHtml = selProductList.innerHTML;

        // 3. Fetch Categories
        const resCats = await fetch('/api/categories');
        const cats = await resCats.json();
        selCategoryList.innerHTML = cats.map(c => `
            <div class="col-md-6 mb-2">
                <div class="rounded-3 hover-surface shadow-sm overflow-hidden" 
                     style="background: var(--secondary); border: 1px solid var(--border); transition: all 0.2s;">
                    <label class="d-flex align-items-center p-3 m-0 w-100 h-100" style="cursor: pointer;" for="selCat${c.id}">
                        <input class="form-check-input sel-category-cb me-3 mt-0 flex-shrink-0"
                            type="checkbox" value="${c.id}" id="selCat${c.id}" style="margin-left: 2px;">
                        <div class="flex-grow-1">
                            <span class="fw-700 d-block" style="line-height: 1.2; color:var(--text-main);">${c.name}</span>
                            <div class="text-muted small fw-normal mt-1" style="font-size: 0.7rem;">Incluye productos de esta categoría</div>
                        </div>
                    </label>
                </div>
            </div>
        `).join('');

    } catch (e) {
        console.error('Error loading tax rates:', e);
        if (tableBody) tableBody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-4">Error al cargar datos.</td></tr>';
    }
}

function filterSelProducts() {
    ivaSelectAllAbsolute = false;
    const badge = document.getElementById('ivaSelectAllBadge');
    if (badge) badge.style.display = 'none';

    const container = document.getElementById('selProductList');
    if (!container) return;

    // Save static HTML once
    if (_selProductStaticHtml === null) {
        _selProductStaticHtml = container.innerHTML;
    }

    const query = document.getElementById('selProductSearch').value.trim();

    // No query → restore static list
    if (!query) {
        container.innerHTML = _selProductStaticHtml;
        return;
    }

    // Debounce backend search
    if (_selProductSearchTimeout) clearTimeout(_selProductSearchTimeout);
    _selProductSearchTimeout = setTimeout(() => {
        container.innerHTML = '<div class="text-center py-3 small text-muted"><span class="spinner-border spinner-border-sm me-1"></span>Buscando...</div>';

        const url = new URL('/api/products/filter', window.location.origin);
        url.searchParams.set('search', query);
        url.searchParams.set('size', 200);

        fetch(url)
            .then(r => r.json())
            .then(data => {
                const products = data.content || data || [];
                if (products.length === 0) {
                    container.innerHTML = '<div class="text-center py-3 small text-muted">No se encontraron productos.</div>';
                    return;
                }
                container.innerHTML = products.map(p => `
                    <div class="sel-product-item-wrap mb-1" data-name="${(p.nameEs || p.name || '').toLowerCase()}">
                        <div class="rounded hover-surface shadow-sm" style="background:var(--surface);border:1px solid var(--border);transition:all 0.2s;">
                            <label class="d-flex align-items-center p-2 m-0 w-100" style="cursor:pointer;" for="selProdDyn${p.id}">
                                <input class="form-check-input sel-product-cb me-3 mt-0 flex-shrink-0"
                                    type="checkbox" value="${p.id}" id="selProdDyn${p.id}" style="margin-left:5px;">
                                <div class="flex-grow-1 d-flex justify-content-between align-items-center pe-1">
                                    <span class="fw-600" style="font-size:0.85rem; color:var(--text-main);">${p.nameEs || p.name}</span>
                                    <span class="badge bg-secondary text-muted rounded-pill small px-2">
                                        IVA: ${p.taxRate ? Math.round(p.taxRate.vatRate * 100) + '%' : '—'}
                                    </span>
                                </div>
                            </label>
                        </div>
                    </div>`).join('');
            })
            .catch(() => {
                container.innerHTML = '<div class="text-center py-3 small text-muted text-danger">Error al buscar productos.</div>';
            });
    }, 300);
}

function toggleAllSelProducts(checked) {
    ivaSelectAllAbsolute = false; // manual selection resets global mode
    const badge = document.getElementById('ivaSelectAllBadge');
    if (badge) badge.style.display = 'none';
    document.querySelectorAll('.sel-product-cb').forEach(cb => {
        if (cb.closest('.sel-product-item-wrap').style.display !== 'none') {
            cb.checked = checked;
        }
    });
}

function selectAllSystemIva() {
    ivaSelectAllAbsolute = true;
    // Visually check all rendered checkboxes to give feedback
    document.querySelectorAll('.sel-product-cb').forEach(cb => cb.checked = true);
    const badge = document.getElementById('ivaSelectAllBadge');
    if (badge) badge.style.display = 'inline-flex';
    showToast('Se aplicará el IVA a TODOS los productos del sistema.', 'success');
}

function toggleAllSelCategories(checked) {
    document.querySelectorAll('.sel-category-cb').forEach(cb => cb.checked = checked);
}

function applySelectiveTaxRate() {
    const taxRateId = document.getElementById('selectiveTaxRateId').value;
    if (!taxRateId) {
        showToast('Por favor, selecciona un tipo de IVA.', 'warning');
        return;
    }

    const productIds = ivaSelectAllAbsolute
        ? []
        : Array.from(document.querySelectorAll('.sel-product-cb:checked')).map(cb => parseInt(cb.value));
    const categoryIds = Array.from(document.querySelectorAll('.sel-category-cb:checked')).map(cb => parseInt(cb.value));

    if (!ivaSelectAllAbsolute && productIds.length === 0 && categoryIds.length === 0) {
        showToast('Selecciona al menos un producto o categoría.', 'warning');
        return;
    }

    const scopeMsg = ivaSelectAllAbsolute
        ? 'TODOS los productos del sistema'
        : `${productIds.length} productos y/o categorías seleccionadas`;

    if (!confirm(`¿Estás seguro de que deseas aplicar el nuevo IVA a ${scopeMsg}? Se recalcularán los precios de venta.`)) {
        return;
    }

    const btn = document.getElementById('btnApplySelectiveTax');
    const originalHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>PROCESANDO...';

    // Generate unique task ID for progress tracking
    const taskId = 'iva_' + Date.now();

    // Show progress bar
    const progressContainer = document.getElementById('ivaSelectiveProgressContainer');
    const resultContainer = document.getElementById('ivaSelectiveResult');
    if (progressContainer) progressContainer.style.display = 'block';
    if (resultContainer) resultContainer.style.display = 'none';

    // Poll progress every 1.5s
    const pollInterval = setInterval(() => {
        fetch(`/api/products/bulk-progress/${taskId}`)
            .then(r => r.json())
            .then(progress => {
                const bar = document.getElementById('ivaSelectiveProgressBar');
                const pct = document.getElementById('ivaSelectivePercent');
                const status = document.getElementById('ivaSelectiveStatusText');
                if (bar) bar.style.width = progress.percentage + '%';
                if (pct) pct.textContent = progress.percentage + '%';
                if (status) status.textContent = progress.message;
            })
            .catch(() => {});
    }, 1500);

    fetch('/admin/api/tax-rates/apply-selective', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            taxRateId: parseInt(taxRateId),
            productIds: ivaSelectAllAbsolute ? [] : productIds,
            categoryIds: categoryIds,
            applyToAll: ivaSelectAllAbsolute,
            taskId: taskId
        })
    })
        .then(r => {
            if (!r.ok) return r.json().then(d => { throw new Error(d.error || d.message || 'Error al aplicar'); });
            return r.json();
        })
        .then(data => {
            clearInterval(pollInterval);
            ivaSelectAllAbsolute = false;
            if (progressContainer) progressContainer.style.display = 'none';
            // Update bar to 100% briefly
            const bar = document.getElementById('ivaSelectiveProgressBar');
            if (bar) bar.style.width = '100%';
            // Show result
            if (resultContainer) {
                resultContainer.style.display = 'block';
                const resultText = document.getElementById('ivaSelectiveResultText');
                if (resultText) resultText.textContent = `IVA aplicado correctamente a ${data.count} productos. Las tarifas han sido regeneradas.`;
                
                // Refresh page after 1.5s to let user see success message
                setTimeout(() => location.reload(), 1500);
            }
            btn.disabled = false;
            btn.innerHTML = originalHtml;
        })
        .catch(e => {
            clearInterval(pollInterval);
            if (progressContainer) progressContainer.style.display = 'none';
            showToast(e.message, 'warning');
            btn.disabled = false;
            btn.innerHTML = originalHtml;
        });
}


// Global Exports
window.openCreateTaxRateModal = openCreateTaxRateModal;
window.saveTaxRate = saveTaxRate;
window.openEditTaxRateModal = openEditTaxRateModal;
window.updateTaxRate = updateTaxRate;
window.deleteTaxRate = deleteTaxRate;
window.filterSelProducts = filterSelProducts;
window.toggleAllSelProducts = toggleAllSelProducts;
window.toggleAllSelCategories = toggleAllSelCategories;
window.applySelectiveTaxRate = applySelectiveTaxRate;
window.selectAllSystemIva = selectAllSystemIva;
window.loadTaxRates = loadTaxRates;
