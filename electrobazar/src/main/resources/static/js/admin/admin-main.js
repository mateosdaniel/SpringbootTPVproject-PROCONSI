// -- Global Function Declarations (Hoisted) --------------------------------
function switchView(viewId, btnElement) {
    // Hide all views
    const views = [
        'dashboardView', 'productsView', 'invoicesView', 'cashCloseView',
        'returnsHistoryView', 'settingsView', 'workersView', 'rolesView', 'analyticsView',
        'crmView', 'preciosTempView', 'preciosMasivosView', 'activityView', 'tarifasView',
        'tiposIvaView', 'couponsView', 'promotionsView', 'measurementUnitsView', 'abonosView'
    ];
    views.forEach(v => {
        const el = document.getElementById(v);
        if (el) el.style.display = 'none';
    });

    // Show selected view
    const target = document.getElementById(viewId);
    if (target) {
        target.style.display = 'block';
        // Persist view in URL WITHOUT reloading
        const url = new URL(window.location);
        url.searchParams.set('view', viewId);
        window.history.replaceState({}, '', url);

        // Reset scroll position to top
        const adminBody = document.querySelector('.admin-body');
        if (adminBody) adminBody.scrollTop = 0;
    }

    // Update active sidebar button state
    document.querySelectorAll('.sidebar-menu-btn').forEach(function (btn) {
        btn.classList.remove('active');
    });
    if (btnElement) btnElement.classList.add('active');

    // Trigger specific view updates
    if (viewId === 'analyticsView') {
        updateAnalytics();
    } else if (viewId === 'activityView') {
        loadActivityLog();
    } else if (viewId === 'preciosTempView') {
        loadFuturePrices();
    } else if (viewId === 'rolesView') {
        loadRoles();
    } else if (viewId === 'preciosMasivosView') {
        loadBulkProducts();
    } else if (viewId === 'settingsView') {
        loadMailSettings();
    } else if (viewId === 'invoicesView') {
        fetchSalesPage(0);
    } else if (viewId === 'measurementUnitsView') {
        loadMeasurementUnits();
    } else if (viewId === 'cashCloseView') {
        filterCashClosures();
    } else if (viewId === 'returnsHistoryView') {
        filterReturns();
    } else if (viewId === 'workersView') {
        filterWorkers();
    } else if (viewId === 'rolesView') {
        filterRoles();
    } else if (viewId === 'crmView') {
        filterCRM();
    } else if (viewId === 'abonosView') {
        document.getElementById('abonosTableBody').innerHTML = '<tr><td colspan="8" class="text-center text-muted">Introduce un ID de Cliente para buscar sus abonos</td></tr>';
    }
}

// Global Modal Variables
var productModal, categoryModal, workerModal, customerModal, roleModal, ipcUpdateModal, couponModal, promotionModal, measurementUnitModal, abonoModal;
var schedulePriceModal; // Also used in the script

document.addEventListener('DOMContentLoaded', function () {
    // Initialize Modals safely once DOM is ready
    const initModal = (id) => {
        const el = document.getElementById(id);
        return el ? new bootstrap.Modal(el) : null;
    };

    productModal = initModal('productModal');
    categoryModal = initModal('categoryModal');
    workerModal = initModal('workerModal');
    customerModal = initModal('customerModal');
    roleModal = initModal('roleModal');
    ipcUpdateModal = initModal('ipcUpdateModal');
    schedulePriceModal = initModal('schedulePriceModal');
    couponModal = initModal('couponModal');
    promotionModal = initModal('promotionModal');
    measurementUnitModal = initModal('measurementUnitModal');
    abonoModal = initModal('abonoModal');

    if (typeof attachNifCifValidator === 'function') {
        attachNifCifValidator('customerTaxId');
    }

    // Add RE compatibility check listener
    const adminTariffSelect = document.getElementById('customerTariffId');
    if (adminTariffSelect) {
        adminTariffSelect.addEventListener('change', checkCustomerReCompatibility);
    }

    // Restore view from URL if present
    const urlParams = new URLSearchParams(window.location.search);
    const savedView = urlParams.get('view');
    if (savedView) {
        const btn = document.querySelector(`.sidebar-menu-btn[onclick*="'${savedView}'"]`);
        switchView(savedView, btn);
    }

    // Initialize pagination state from DOM
    const totalEl = document.getElementById('salesTotalPages');
    if (totalEl) {
        salesTotalPages = parseInt(totalEl.innerText) || 1;
    }
});

// Cache for roles
var rolesCache = null;

function showToast(msg, type) {
    if (!type) type = 'success';
    var el = document.getElementById('toastMsg');
    var icon = document.getElementById('toastIcon');
    var text = document.getElementById('toastText');
    el.classList.remove('success', 'error');
    el.classList.add(type);
    icon.className = 'bi fs-5 ' + (type === 'success' ? 'bi-check-circle text-success' : 'bi-exclamation-circle text-danger');
    text.textContent = msg;
    bootstrap.Toast.getOrCreateInstance(el).show();
}

function previewImage(url) {
    const img = document.getElementById('imgPreview');
    if (url && url.substring(0, 4) === 'http') {
        img.src = url;
        img.style.display = 'block';
        img.onerror = null; // Remove onerror when a valid image is set
    } else {
        img.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%" viewBox="0 0 100 100"><rect fill="%231a1a2e" width="100" height="100"/><text fill="%238892a4" font-family="sans-serif" font-size="20" x="50" y="55" text-anchor="middle">Sin imagen</text></svg>';
        img.style.display = 'block';
        img.onerror = null; // No need for onerror on a data URL
    }
}

// PIN management has been moved to environment variables. Runtime changes are no longer supported.


// -- Product CRUD ---------------------------------------------------------

function openProductModal(id) {
    document.getElementById('productId').value = '';
    document.getElementById('productName').value = '';
    document.getElementById('productDescription').value = '';
    document.getElementById('productPrice').value = '';
    document.getElementById('productStock').value = '0';
    document.getElementById('productCategory').value = '';
    document.getElementById('productMeasurementUnit').value = '';
    document.getElementById('productImageUrl').value = '';
    document.getElementById('productActive').checked = true;
    document.getElementById('productModalLabel').textContent = id ? 'Editar Producto' : 'Nuevo Producto';

    previewImage(null); // Set placeholder by default
    const unitEl = document.getElementById('productMeasurementUnit');

    // Fetch measurement units
    fetch('/api/measurement-units')
        .then(function (res) { return res.json(); })
        .then(function (units) {
            if (unitEl) {
                unitEl.innerHTML = '<option value="">— Sin unidades —</option>';
                units.forEach(function (u) {
                    const opt = document.createElement('option');
                    opt.value = u.id;
                    opt.textContent = u.name + ' (' + u.symbol + ')';
                    unitEl.appendChild(opt);
                });
            }
        });

    // Fetch active tax rates and populate dropdown
    const ivaEl = document.getElementById('productIvaRate');
    fetch('/admin/api/tax-rates/active')
        .then(function (res) { return res.json(); })
        .then(function (rates) {
            if (ivaEl) {
                ivaEl.innerHTML = '';
                let highest = null;
                rates.forEach(function (r) {
                    const opt = document.createElement('option');
                    opt.value = r.id; // Store tax rate ID, not vatRate
                    opt.textContent = r.description + ' (' + (r.vatRate * 100).toFixed(1).replace('.0', '') + '%)';
                    opt.dataset.vatRate = r.vatRate;
                    ivaEl.appendChild(opt);
                    if (!highest || r.vatRate > highest.vatRate) {
                        highest = r;
                    }
                });

                // Default for new products: highest rate
                if (!id && highest) {
                    ivaEl.value = highest.id.toString();
                }
            }

            // If editing, load product AFTER tax rates are ready
            if (id) {
                fetch('/api/products/' + id)
                    .then(function (r) { return r.json(); })
                    .then(function (p) {
                        document.getElementById('productId').value = p.id;
                        document.getElementById('productName').value = p.name || '';
                        document.getElementById('productDescription').value = p.description || '';
                        document.getElementById('productPrice').value = p.price || '';
                        document.getElementById('productStock').value = (p.stock !== undefined && p.stock !== null) ? p.stock : 0;
                        document.getElementById('productCategory').value = p.category ? p.category.id : '';
                        document.getElementById('productMeasurementUnit').value = p.measurementUnit ? p.measurementUnit.id : '';
                        document.getElementById('productImageUrl').value = p.imageUrl || '';
                        document.getElementById('productActive').checked = p.active !== false;
                        // Use taxRate.vatRate for display and taxRate.id for selection
                        if (ivaEl && p.taxRate !== null && p.taxRate !== undefined) {
                            ivaEl.value = p.taxRate.id.toString();
                        }
                        previewImage(p.imageUrl);
                    })
                    .catch(function () { showToast('Error al cargar el producto', 'error'); });
            }
        })
        .catch(function () { showToast('Error al cargar tipos de IVA', 'error'); });

    productModal.show();
}

function saveProduct() {
    const name = document.getElementById('productName').value.trim();
    const price = document.getElementById('productPrice').value;
    if (!name || !price) { showToast('Nombre y precio son obligatorios', 'error'); return; }

    var id = document.getElementById('productId').value;
    var catId = document.getElementById('productCategory').value;
    var ivaEl = document.getElementById('productIvaRate');
    var taxRateId = ivaEl ? parseInt(ivaEl.value) : null;

    const body = {
        name: name,
        description: document.getElementById('productDescription').value.trim() || null,
        price: parseFloat(price),
        taxRateId: taxRateId,
        stock: parseInt(document.getElementById('productStock').value) || 0,
        active: document.getElementById('productActive').checked,
        imageUrl: document.getElementById('productImageUrl').value.trim() || null,
        categoryId: catId ? parseInt(catId) : null,
        measurementUnitId: document.getElementById('productMeasurementUnit').value ? parseInt(document.getElementById('productMeasurementUnit').value) : null
    };

    var method = id ? 'PUT' : 'POST';
    var url = id ? '/api/products/' + id : '/api/products';

    fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
        .then(function (r) {
            if (!r.ok) {
                r.json().then(function (err) {
                    showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al guardar el producto', 'error');
                });
                return;
            }
            productModal.hide();
            showToast(id ? 'Producto actualizado correctamente' : 'Producto creado correctamente');
            setTimeout(function () { location.reload(); }, 900);
        })
        .catch(function () { showToast('Error al guardar el producto', 'error'); });
}

function deleteProduct(id, name) {
    if (!confirm('¿Seguro que quieres eliminar definitivamente el producto "' + name + '"?')) return;

    const token = localStorage.getItem('token');
    fetch('/admin/products/' + id + '/hard', {
        method: 'DELETE',
        credentials: 'include',
        cache: 'no-store',
        headers: token ? { 'Authorization': 'Bearer ' + token } : {}
    })
        .then(function (r) {
            if (r.ok) {
                showToast('Producto "' + name + '" eliminado definitivamente');
                // Remove row from DOM
                var btn = document.querySelector('button.danger[data-id="' + id + '"]');
                if (btn) {
                    var row = btn.closest('tr');
                    if (row) row.remove();
                }
            } else {
                r.json().then(function (err) {
                    showToast('Error al eliminar: ' + (err.error || err.message || 'Error desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al eliminar el producto', 'error');
                });
            }
        })
        .catch(function (err) {
            console.error('Delete error:', err);
            showToast('Error de red al eliminar el producto', 'error');
        });
}


// -- Category CRUD ---------------------------------------------------------

function openCategoryModal(id) {
    document.getElementById('categoryId').value = '';
    document.getElementById('categoryName').value = '';
    document.getElementById('categoryDescription').value = '';
    document.getElementById('categoryActive').checked = true;
    document.getElementById('categoryModalLabel').textContent = id ? 'Editar Categoría' : 'Nueva Categoría';

    if (id) {
        fetch('/api/categories/' + id)
            .then(function (r) { return r.json(); })
            .then(function (c) {
                document.getElementById('categoryId').value = c.id;
                document.getElementById('categoryName').value = c.name || '';
                document.getElementById('categoryDescription').value = c.description || '';
                document.getElementById('categoryActive').checked = c.active !== false;
            })
            .catch(function () { showToast('Error al cargar la categoría', 'error'); });
    }

    categoryModal.show();
}

function saveCategory() {
    const name = document.getElementById('categoryName').value.trim();
    if (!name) { showToast('El nombre es obligatorio', 'error'); return; }

    const id = document.getElementById('categoryId').value;
    const body = {
        name: name,
        description: document.getElementById('categoryDescription').value.trim() || null,
        active: document.getElementById('categoryActive').checked
    };

    var method = id ? 'PUT' : 'POST';
    var url = id ? '/api/categories/' + id : '/api/categories';

    fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
        .then(function (r) {
            if (r.ok) {
                categoryModal.hide();
                showToast(id ? 'Categoría actualizada correctamente' : 'Categoría creada correctamente');
                setTimeout(function () { location.reload(); }, 900);
            } else {
                r.json().then(function (err) {
                    showToast('Error al guardar categoría: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al guardar la categoría', 'error');
                });
            }
        })
        .catch(function (e) {
            console.error('Category save error:', e);
            showToast('Error de red al guardar la categoría', 'error');
        });
}

function deleteCategory(id, name) {
    if (!confirm('¿Desactivar la categoría "' + name + '"?')) return;
    fetch('/api/categories/' + id, { method: 'DELETE' })
        .then(function (r) {
            if (r.ok) {
                showToast('Categoría "' + name + '" desactivada');
                setTimeout(function () { location.reload(); }, 900);
            } else {
                r.json().then(function (err) {
                    showToast('Error al desactivar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al desactivar la categoría', 'error');
                });
            }
        })
        .catch(function () { showToast('Error de red al desactivar la categoría', 'error'); });
}

// -- Importar Productos y Categorías por CSV --
function uploadCsvFile(input) {
    if (!input.files || input.files.length === 0) return;
    var file = input.files[0];
    var formData = new FormData();
    formData.append('file', file);

    // Mostrar un indicador de carga visual simple
    showToast('Subiendo archivo...', 'success');

    fetch('/admin/upload-csv', {
        method: 'POST',
        body: formData
    })
        .then(function (response) { return response.json(); })
        .then(function (data) {
            if (data.ok) {
                showToast(data.message, 'success');
                setTimeout(function () { location.reload(); }, 2000);
            } else {
                showToast(data.message || 'Error al procesar el archivo.', 'error');
            }
        })
        .catch(function (error) {
            console.error('Error uploading CSV:', error);
            showToast('Error de red al subir el archivo.', 'error');
        })
        .finally(() => {
            // Limpiar el input para permitir volver a seleccionar el mismo archivo si hubo error
            input.value = '';
        });
}

// -- Importar Clientes por CSV --
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

// -- Activity Log --------------------------------------------------------
// -- Activity Log --------------------------------------------------------
var activityCurrentPage = 0;

function loadActivityLog() {
    fetchActivityLogs(0);
}

function fetchActivityLogs(page) {
    activityCurrentPage = page;
    var container = document.getElementById('activityFeedContainer');
    if (!container) return;

    container.innerHTML = '<div class="text-center p-4"><div class="spinner-border text-primary" role="status"><span class="visually-hidden">Cargando...</span></div></div>';

    const search = document.getElementById('activityFilterSearch').value;
    const action = document.getElementById('activityFilterAction').value;
    const username = document.getElementById('activityFilterUsername').value;
    const sortBy = document.getElementById('activitySortBy').value;
    const sortDir = document.getElementById('activitySortDir').value;

    const params = new URLSearchParams({
        page: page,
        size: 50,
        search: search,
        action: action,
        username: username,
        sortBy: sortBy,
        sortDir: sortDir
    });

    fetch('/api/admin/activity-logs?' + params.toString())
        .then(function (res) {
            if (!res.ok) throw new Error('Error de red');
            return res.json();
        })
        .then(function (data) {
            renderActivityLogs(data.content);
            renderActivityPagination(data);
        })
        .catch(function (e) {
            container.innerHTML = '<div class="text-center p-4 text-danger">Error al cargar la actividad.</div>';
            console.error(e);
        });
}

function renderActivityLogs(logs) {
    var container = document.getElementById('activityFeedContainer');
    if (!logs || logs.length === 0) {
        container.innerHTML = `<div class="text-center p-4" style="color: var(--text-muted);">${window.adminI18n ? (window.adminI18n.noActivity || 'No hay actividad reciente.') : 'No hay actividad reciente.'}</div>`;
        return;
    }

    var html = '';
    logs.forEach(function (log) {
        var iconClasses = 'bi-info-circle text-info';
        const action = log.action || '';

        if (action === 'VENTA') iconClasses = 'bi-cart-check text-primary';
        else if (action.includes('CREAR')) iconClasses = 'bi-plus-circle text-success';
        else if (action.includes('CIERRE') || action.includes('APERTURA')) iconClasses = 'bi-cash-stack text-info';
        else if (action.includes('ACTUALIZAR')) iconClasses = 'bi-pencil text-warning';
        else if (action.includes('ELIMINAR')) iconClasses = 'bi-trash text-danger';
        else if (action.includes('LOGIN') || action.includes('SESION')) iconClasses = 'bi-key text-info';
        else if (action.includes('FISCAL')) iconClasses = 'bi-shield-check text-success';

        var formattedDate = log.timestamp ? formatTimeAgo(log.timestamp) : '';
        
        html += '<div class="activity-item">' +
            '<div class="activity-icon"><i class="bi ' + iconClasses + '"></i></div>' +
            '<div class="activity-content">' +
            '<div class="activity-text">' + escHtml(log.description || '') + '</div>' +
            '<div class="activity-time">' + formattedDate + ' <small class="ms-2" style="color: var(--text-muted);">por <strong>' + escHtml(log.username || 'Sistema') + '</strong></small></div>' +
            '</div>' +
            '</div>';
    });
    container.innerHTML = html;
}

function renderActivityPagination(data) {
    let container = document.getElementById('activityPaginationContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'activityPaginationContainer';
        container.className = 'mt-4 d-flex justify-content-center';
        document.getElementById('activityFeedContainer').after(container);
    }
    
    // Use shared pagination if available or simple one
    if (typeof createPaginationHTML === 'function') {
        container.innerHTML = createPaginationHTML(data.currentPage, data.totalPages, 'fetchActivityLogs');
    } else {
        container.innerHTML = `Página ${data.currentPage + 1} de ${data.totalPages}`;
    }
}

function formatTimeAgo(dateStr) {
    var date = new Date(dateStr);
    var now = new Date();
    var diffMs = now - date;

    var diffSecs = Math.floor(diffMs / 1000);
    var diffMins = Math.floor(diffSecs / 60);
    var diffHours = Math.floor(diffMins / 60);
    var diffDays = Math.floor(diffHours / 24);

    if (diffSecs < 60) return 'Hace unos segundos';
    if (diffMins < 60) return 'Hace ' + diffMins + ' minuto' + (diffMins !== 1 ? 's' : '');
    if (diffHours < 24) return 'Hace ' + diffHours + ' hora' + (diffHours !== 1 ? 's' : '');
    if (diffDays === 1) return 'Ayer';
    if (diffDays < 7) return 'Hace ' + diffDays + ' días';

    return date.toLocaleDateString('es-ES') + ' a las ' + date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, function (match) {
        switch (match) {
            case '&': return '&amp;';
            case '<': return '&lt;';
            case '>': return '&gt;';
            case '"': return '&quot;';
            case "'": return '&#39;';
            default: return match;
        }
    });
}

// -- Analytics & Charts --------------------------------------------------
let salesChart, categoryChart;

function onAnalyticsPeriodChange() {
    const val = document.getElementById('analyticsPeriod').value;
    const dateInput = document.getElementById('analyticsDate');
    if (dateInput) {
        dateInput.style.display = (val === 'custom') ? 'block' : 'none';
        if (val === 'custom' && !dateInput.value) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }
    }
    updateAnalytics();
}

function updateAnalytics() {
    const periodSelect = document.getElementById('analyticsPeriod');
    const period = periodSelect ? periodSelect.value : '7days';
    const now = new Date();
    let fromDate = new Date();
    let toDate = new Date();
    let chartTitle = 'Ventas Ú\u00FAtimos 7 d\u00EDas';

    const toLocalISO = (d) => {
        const off = d.getTimezoneOffset() * 60000;
        return new Date(d.getTime() - off).toISOString().slice(0, 19);
    };

    // Set standard toDate to end of today
    toDate.setHours(23, 59, 59, 999);

    if (period === 'today') {
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = (window.adminI18n ? window.adminI18n.chartToday : 'Ventas Hoy');
    } else if (period === '7days') {
        fromDate.setDate(now.getDate() - 6);
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = (window.adminI18n ? window.adminI18n.chart7Days : 'Ventas Últimos 7 Días');
    } else if (period === '1month') {
        fromDate.setMonth(now.getMonth() - 1);
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = (window.adminI18n ? window.adminI18n.chart1Month : 'Ventas Último Mes');
    } else if (period === '6months') {
        fromDate.setMonth(now.getMonth() - 6);
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = (window.adminI18n ? window.adminI18n.chart6Months : 'Ventas Últimos 6 Meses');
    } else if (period === '1year') {
        fromDate.setFullYear(now.getFullYear() - 1);
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = (window.adminI18n ? window.adminI18n.chart1Year : 'Ventas Último Año');
    } else if (period === 'custom') {
        const dVal = document.getElementById('analyticsDate').value;
        if (dVal) {
            fromDate = new Date(dVal);
            fromDate.setHours(0, 0, 0, 0);
            toDate = new Date(dVal);
            toDate.setHours(23, 59, 59, 999);
            const customTitleBase = (window.adminI18n ? window.adminI18n.chartCustom : 'Análisis del día');
            chartTitle = customTitleBase + ' ' + fromDate.toLocaleDateString();
        }
    } else if (period === 'all') {
        fromDate = new Date(0);
        chartTitle = (window.adminI18n ? window.adminI18n.chartAll : 'Ventas Histórico Total');
    }

    const url = `/api/sales/analytics?from=${toLocalISO(fromDate)}&to=${toLocalISO(toDate)}`;

    fetch(url)
        .then(r => { if (!r.ok) throw new Error('Status: ' + r.status); return r.json(); })
        .then(analytics => {
            initCharts(analytics, period, chartTitle);
        })
        .catch(err => {
            console.error('Error updating analytics:', err);
            showToast('Error al cargar datos de análisis', 'error');
        });
}

function initCharts(analytics, period = '7days', chartLabel = (window.adminI18n ? window.adminI18n.salesEuro : 'Ventas (€)')) {
    if (!analytics) return;

    // Update dynamic title
    const chartTitleEl = document.getElementById('salesChartTitle');
    if (chartTitleEl) {
        const trendLabel = (window.adminI18n ? window.adminI18n.trend : 'Tendencia');
        chartTitleEl.innerHTML = `<i class="bi bi-graph-up me-2"></i>${trendLabel}: ${chartLabel}`;
    }

    // 1. Update KPI Counters from Analytics DTO
    if (document.getElementById('statTodayRevenue')) {
        document.getElementById('statTodayRevenue').textContent = 
            (analytics.totalRevenue || 0).toLocaleString('es-ES', { minimumFractionDigits: 2 }) + ' €';
    }
    if (document.getElementById('statTodaySales')) {
        document.getElementById('statTodaySales').textContent = analytics.totalSales || 0;
    }
    if (document.getElementById('statTopProduct')) {
        const topP = analytics.topProductName || '—';
        document.getElementById('statTopProduct').textContent = 
            topP.length > 20 ? topP.substring(0, 20) + '...' : topP;
    }
    if (document.getElementById('statLowStock')) {
        document.getElementById('statLowStock').textContent = analytics.lowStockCount || 0;
    }

    const labelSuffix = (period === 'today' || period === 'custom') ? (period === 'today' ? (' ' + (window.adminI18n ? window.adminI18n.today : 'Hoy')) : '') : '';
    const salesLabelStr = (window.adminI18n ? window.adminI18n.sales : 'Ventas');
    const ordersLabelStr = (window.adminI18n ? window.adminI18n.orders : 'Pedidos');
    
    if (document.getElementById('statRevenueLabel')) document.getElementById('statRevenueLabel').textContent = salesLabelStr + labelSuffix;
    if (document.getElementById('statSalesLabel')) document.getElementById('statSalesLabel').textContent = ordersLabelStr + labelSuffix;

    // 2. Trend Chart (using daily revenue from server)
    const trendData = analytics.revenueTrend || {};
    let labels = [];
    let datasetsData = [];

    // Sort dates and format labels
    Object.keys(trendData).sort().forEach(dateStr => {
        const d = new Date(dateStr);
        labels.push(d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' }));
        datasetsData.push(trendData[dateStr]);
    });

    var ctxSales = document.getElementById('salesChart');
    if (ctxSales) {
        if (salesChart) salesChart.destroy();
        salesChart = new Chart(ctxSales.getContext('2d'), {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: chartLabel,
                    data: datasetsData,
                    borderColor: '#f5a623',
                    backgroundColor: 'rgba(245, 166, 35, 0.1)',
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: true, labels: { color: '#8892a4' } } },
                scales: {
                    y: { grid: { color: 'rgba(255,255,255,0.05)' }, border: { display: false }, ticks: { color: '#8892a4' } },
                    x: { grid: { display: false }, border: { display: false }, ticks: { color: '#8892a4' } }
                }
            }
        });
    }

    // 3. Category Distribution Chart (using category totals from server)
    const catSummary = analytics.categoryDistribution || {};
    var ctxCat = document.getElementById('categoryChart');
    if (ctxCat) {
        if (categoryChart) categoryChart.destroy();
        categoryChart = new Chart(ctxCat.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: Object.keys(catSummary),
                datasets: [{
                    data: Object.values(catSummary),
                    backgroundColor: ['#f5a623', '#3b82f6', '#22c55e', '#ef4444', '#a855f7', '#06b6d4'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { position: 'bottom', labels: { color: '#8892a4', usePointStyle: true, boxWidth: 6 } } },
                cutout: '75%'
            }
        });
    }
}

// Theme application moved to <head>

// -- Worker Management --------------------------------------------------------
function openWorkerModal(id, username, active, permissions, roleId) {
    document.getElementById('workerForm').reset();
    document.getElementById('workerId').value = id || '';
    document.getElementById('workerUsername').value = username || '';
    document.getElementById('workerActive').checked = active !== false;

    // Get role from data attribute if passed as string (from Thymeleaf loop)
    if (!roleId && id) {
        // We might need to find the roleId from the DOM or cache
        // but since we are refetching in some cases, let's just use what's passed
    }

    // Password label adjustment based on edit or create
    var pwdLabel = document.getElementById('workerPasswordLabel');
    if (id) {
        pwdLabel.innerHTML = 'Contraseña <small class="font-normal" style="color: var(--text-muted);">(opcional, blanco para mantener)</small>';
    } else {
        pwdLabel.innerHTML = 'Contraseña *';
    }

    // Load roles into select if not already there, then select current
    loadRoles().then(() => {
        document.getElementById('workerRole').value = roleId || '';
    });

    workerModal.show();
}

function saveWorker() {
    var id = document.getElementById('workerId').value;
    var username = document.getElementById('workerUsername').value;
    var password = document.getElementById('workerPassword').value;
    var active = document.getElementById('workerActive').checked;
    var roleId = document.getElementById('workerRole').value;

    var worker = {
        id: id ? parseInt(id) : null,
        username: username,
        password: password || null,
        active: active,
        role: roleId ? { id: parseInt(roleId) } : null
    };

    fetch('/admin/workers/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(worker)
    }).then(function (res) {
        if (res.ok) {
            showToast('Trabajador guardado con éxito');
            setTimeout(function () { location.reload(); }, 1000);
        } else {
            res.json().then(function (err) {
                showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function () {
                showToast('Error al guardar trabajador', 'error');
            });
        }
    }).catch(function () {
        showToast('Error de red', 'error');
    });
}


function deleteWorker(id) {
    if (!confirm('¿Seguro que quieres eliminar a este trabajador?')) return;
    fetch('/admin/workers/delete/' + id, { method: 'DELETE' })
        .then(function (res) {
            if (res.ok) {
                showToast('Trabajador eliminado');
                setTimeout(function () { location.reload(); }, 1000);
            } else {
                res.json().then(function (err) {
                    showToast('Error al eliminar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al eliminar trabajador', 'error');
                });
            }
        }).catch(function () {
            showToast('Error de red', 'error');
        });
}



// -- CRM / Customer Management ------------------------------------------------
function openCustomerModal(id) {
    document.getElementById('customerForm').reset();
    document.getElementById('customerId').value = id || '';
    document.getElementById('customerModalLabel').textContent = id ? 'Editar Cliente' : 'Nuevo Cliente';
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
    document.getElementById('customerType').value = isCompany ? 'COMPANY' : 'INDIVIDUAL';

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
            document.getElementById('customerRecargoEquivalencia').checked = false;
        } else {
            // Re-check compatibility if section becomes visible
            checkCustomerReCompatibility();
        }
    }
}

function saveCustomer() {
    const name = document.getElementById('customerName').value.trim();
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

    // Validate NIF/CIF
    var taxInput = document.getElementById('customerTaxId');
    if (taxInput && taxInput.dataset.invalidNif === 'true') {
        showToast('Por favor, introduce un NIF/CIF válido antes de continuar.', 'error');
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
                // try to parse server message
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

// ── Ventas del Cliente (modal) ──────────────────────────────────────────────
var _customerSalesModal = null;

function openCustomerSalesModal(id, name) {
    if (!_customerSalesModal) {
        _customerSalesModal = new bootstrap.Modal(document.getElementById('customerSalesModal'));
    }
    document.getElementById('customerSalesModalName').textContent = name || 'Cliente';
    document.getElementById('customerSalesBody').innerHTML =
        '<div class="text-center py-5"><span class="spinner-border spinner-border-sm me-2"></span>Cargando...</div>';
    var statsDiv = document.getElementById('customerSalesStats');
    statsDiv.style.setProperty('display', 'none', 'important');
    _customerSalesModal.show();

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
        statsDiv.style.setProperty('display', 'none', 'important');
        return;
    }

    // ── stats ──
    var totalAmt = sales.reduce(function (s, v) { return s + parseFloat(v.totalAmount || 0); }, 0);
    var lastDate = sales[0] ? formatDateTime(sales[0].createdAt) : '—';
    document.getElementById('csSaleCount').textContent = sales.length;
    document.getElementById('csTotalAmount').textContent = totalAmt.toFixed(2) + ' €';
    document.getElementById('csAvgAmount').textContent = (totalAmt / sales.length).toFixed(2) + ' €';
    document.getElementById('csLastSale').textContent = lastDate;
    statsDiv.style.removeProperty('display');
    statsDiv.style.display = 'flex';

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

// ── Precios Temporales ────────────────────────────────────────────────────────

// Modal is now initialized in DOMContentLoaded at the top of the file

/** RE rate map matching the server-side RecargoEquivalenciaCalculator */
var RE_RATE_MAP = {
    '0.21': '5,2%',
    '0.10': '1,4%',
    '0.04': '0,5%',
    '0.02': '0,15%'
};

function updateRecargoPreview() {
    var vatRate = document.getElementById('spVatRate').value;
    var reRate = RE_RATE_MAP[vatRate] || '—';
    var vatPct = Math.round(parseFloat(vatRate) * 100) + '%';
    document.getElementById('spRecargoPreview').textContent = vatPct + ' IVA → +' + reRate + ' RE';
}

function openSchedulePriceModal() {
    document.getElementById('schedulePriceForm').reset();
    // Set default start date to tomorrow at 00:00
    var tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(0, 0, 0, 0);
    var pad = function (n) { return n < 10 ? '0' + n : n; };
    var dtLocal = tomorrow.getFullYear() + '-' + pad(tomorrow.getMonth() + 1) + '-' + pad(tomorrow.getDate())
        + 'T' + pad(tomorrow.getHours()) + ':' + pad(tomorrow.getMinutes());
    document.getElementById('spStartDate').value = dtLocal;
    // Reset VAT rate to default 21%
    document.getElementById('spVatRate').value = '0.21';
    updateRecargoPreview();
    schedulePriceModal.show();
}

// Auto-select product's IVA rate when product is selected in schedule price modal
const spProductSelect = document.getElementById('spProductSelect');
if (spProductSelect) {
    spProductSelect.addEventListener('change', function () {
        var productId = this.value;
        if (productId) {
            fetch('/api/products/' + productId)
                .then(function (r) { return r.json(); })
                .then(function (p) {
                    if (p.taxRate && p.taxRate.vatRate) {
                        document.getElementById('spVatRate').value = String(p.taxRate.vatRate);
                        updateRecargoPreview();
                    }
                })
                .catch(function () { });
        }
    });
}

function saveScheduledPrice() {
    var productId = document.getElementById('spProductSelect').value;
    var price = document.getElementById('spPrice').value;
    var vatRate = document.getElementById('spVatRate').value;
    var startDate = document.getElementById('spStartDate').value;
    var label = document.getElementById('spLabel').value.trim();

    if (!productId) { showToast('Selecciona un producto', 'error'); return; }
    if (!price || parseFloat(price) <= 0) { showToast('El precio debe ser mayor que 0', 'error'); return; }
    if (!startDate) { showToast('La fecha de inicio es obligatoria', 'error'); return; }

    // Convert datetime-local value to ISO format expected by the API
    var isoDate = startDate.replace('T', 'T').substring(0, 19);
    // Ensure seconds are included
    if (isoDate.length === 16) isoDate += ':00';

    var body = {
        price: parseFloat(price),
        vatRate: parseFloat(vatRate),
        startDate: isoDate,
        label: label || null
    };

    fetch('/api/product-prices/' + productId + '/schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(function (r) {
            if (!r.ok) {
                return r.json().then(function (d) {
                    throw new Error(d.message || d.error || 'Error al programar precio');
                }).catch(function () { throw new Error('Estado HTTP ' + r.status); });
            }
            return r.json();
        })
        .then(function (data) {
            schedulePriceModal.hide();
            showToast('Precio programado correctamente para ' + (data.productName || 'el producto'));
            loadFuturePrices();
        })
        .catch(function (e) {
            showToast('Error: ' + (e.message || ''), 'error');
        });
}

function loadFuturePrices() {
    filterFuturePrices();
}

function filterFuturePrices() {
    const search = document.getElementById('futurePriceFilterSearch').value.trim();
    const sortBy = document.getElementById('futurePriceSortBy').value;
    const sortDir = document.getElementById('futurePriceSortDir').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/future-prices?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderFuturePricesTable(data.content || data);
        })
        .catch(err => {
            console.error("Error filtering future prices:", err);
            const el = document.getElementById('futurePricesBody');
            if (el) el.innerHTML = '<tr><td colspan="6" class="text-center py-4 text-danger">Error al cargar los precios programados.</td></tr>';
        });
}

function renderFuturePricesTable(prices) {
    const tbody = document.getElementById('futurePricesBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!prices || prices.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4" style="color: var(--text-muted);">${window.adminI18n.noScheduledPrices || 'No hay precios programados.'}</td></tr>`;
        return;
    }

    tbody.innerHTML = prices.map(function (p) {
        const vatPct = Math.round(parseFloat(p.vatRate) * 100) + '%';
        const reRate = (typeof RE_RATE_MAP !== 'undefined') ? (RE_RATE_MAP[String(p.vatRate)] || '—') : '—';
        return '<tr class="future-price-row">'
            + '<td><strong>' + p.productName + '</strong></td>'
            + '<td>' + (typeof formatDecimal === 'function' ? formatDecimal(p.price, 2, 4) : p.price) + ' &euro;</td>'
            + '<td>' + vatPct + ' <small style="color: var(--text-muted);">(+' + reRate + ' RE)</small></td>'
            + '<td>' + (typeof formatDateTime === 'function' ? formatDateTime(p.startDate) : p.startDate) + '</td>'
            + '<td>' + (p.endDate ? (typeof formatDateTime === 'function' ? formatDateTime(p.endDate) : p.endDate) : '<span style="color: var(--text-muted);">Abierto</span>') + '</td>'
            + '<td>' + (p.label ? p.label : '<span style="color: var(--text-muted);">—</span>') + '</td>'
            + '</tr>';
    }).join('');
}

function resetFuturePriceFilters() {
    document.getElementById('futurePriceFilterSearch').value = '';
    const sortBy = document.getElementById('futurePriceSortBy');
    const sortDir = document.getElementById('futurePriceSortDir');
    if (sortBy) sortBy.value = 'startDate';
    if (sortDir) sortDir.value = 'asc';
    filterFuturePrices();
}

function loadPriceHistory() {
    var productId = document.getElementById('historialProductSelect').value;
    if (!productId) { showToast('Selecciona un producto', 'error'); return; }

    fetch('/api/product-prices/' + productId + '/history')
        .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
        .then(function (prices) {
            var tbody = document.getElementById('priceHistoryBody');
            if (!prices || prices.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4" style="color: var(--text-muted);">No hay historial de precios para este producto.</td></tr>';
                return;
            }
            tbody.innerHTML = prices.map(function (p) {
                var vatPct = Math.round(parseFloat(p.vatRate) * 100) + '%';
                var reRate = RE_RATE_MAP[String(p.vatRate)] || '—';
                var statusBadge = p.currentlyActive
                    ? '<span class="badge bg-success">Activo</span>'
                    : (new Date(p.startDate) > new Date()
                        ? '<span class="badge" style="background-color: rgba(245,158,11,0.18); color: #fbbf24; border: 1px solid rgba(245,158,11,0.3);">Programado</span>'
                        : '<span class="badge" style="background-color: rgba(148,163,184,0.15); color: var(--text-muted); border: 1px solid rgba(148,163,184,0.25);">Expirado</span>');

                // Calculate price variation display
                var variationHtml = '<span style="color: var(--text-muted);">—</span>';
                if (p.priceChange !== null && p.priceChange !== undefined) {
                    var changeAmount = parseFloat(p.priceChange).toFixed(2);
                    var changePct = parseFloat(p.priceChangePct).toFixed(2);
                    if (p.priceChange > 0) {
                        variationHtml = '<span class="text-success fw-bold">+' + changeAmount + ' € (+' + changePct + '%)</span>';
                    } else if (p.priceChange < 0) {
                        variationHtml = '<span class="text-danger fw-bold">' + changeAmount + ' € (' + changePct + '%)</span>';
                    } else {
                        variationHtml = '<span style="color: var(--text-muted);">0.00 € (0.00%)</span>';
                    }
                }

                return '<tr>'
                    + '<td>' + parseFloat(p.price).toFixed(2) + ' &euro;</td>'
                    + '<td>' + vatPct + ' <small style="color: var(--text-muted);">(+' + reRate + ' RE)</small></td>'
                    + '<td>' + formatDateTime(p.startDate) + '</td>'
                    + '<td>' + (p.endDate ? formatDateTime(p.endDate) : '<span style="color: var(--text-muted);">Abierto</span>') + '</td>'
                    + '<td>' + (p.label ? escHtml(p.label) : '<span style="color: var(--text-muted);">—</span>') + '</td>'
                    + '<td>' + variationHtml + '</td>'
                    + '<td>' + statusBadge + '</td>'
                    + '</tr>';
            }).join('');
        })
        .catch(function () {
            const el = document.getElementById('priceHistoryBody');
            if (el) {
                el.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-danger">Error al cargar el historial.</td></tr>';
            }
        });
}

function showPreciosTab(tab) {
    document.getElementById('preciosTabFuturos').style.display = tab === 'futuros' ? 'block' : 'none';
    document.getElementById('preciosTabHistorial').style.display = tab === 'historial' ? 'block' : 'none';
    document.getElementById('tabFuturos').classList.toggle('active', tab === 'futuros');
    document.getElementById('tabHistorial').classList.toggle('active', tab === 'historial');
}

function formatDateTime(dt) {
    if (!dt) return '—';
    try {
        var d = new Date(dt);
        return d.toLocaleDateString('es-ES') + ' ' + d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    } catch (e) { return dt; }
}

function escHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ── PRECIOS MASIVOS ────────────────────────────────────────────────────────
var bulkProductsCache = null;

var bulkSelectedIds = new Set();

function loadBulkProducts(page = 0) {
    const search = document.getElementById('bulkProductSearch').value;
    const container = document.getElementById('bulkProductList');
    if (!container) return;

    const params = new URLSearchParams({
        page: page,
        size: 100, // Show more in bulk view
        search: search,
        active: true,
        sortBy: 'nameEs',
        sortDir: 'asc'
    });

    fetch('/api/admin/products?' + params.toString())
        .then(r => r.json())
        .then(data => {
            renderBulkProductList(data.content);
            renderBulkPagination(data);
        })
        .catch(e => {
            container.innerHTML = '<div class="text-center text-danger py-3">Error cargando productos</div>';
        });
}

function renderBulkProductList(products) {
    var container = document.getElementById('bulkProductList');
    if (!products || products.length === 0) {
        container.innerHTML = `<div class="text-center py-3" style="color: var(--text-muted);">${window.adminI18n ? (window.adminI18n.noProducts || 'No hay productos') : 'No hay productos'}</div>`;
        return;
    }
    container.innerHTML = products.map(function (p) {
        let isChecked = bulkSelectedIds.has(p.id) ? 'checked' : '';
        var catName = p.categoryName || 'S/C';
        return '<div class="form-check bulk-product-item">'
            + '<input class="form-check-input bulk-product-checkbox" type="checkbox" value="' + p.id + '" id="bulkProd' + p.id + '" onchange="handleBulkProductToggle(this)" ' + isChecked + '>'
            + '<label class="form-check-label" for="bulkProd' + p.id + '" style="color: var(--text-main); cursor: pointer;">'
            + '<span style="color: var(--text-main);">' + escHtml(p.name) + '</span>'
            + ' <small style="color: var(--text-muted);">(' + catName + ' - ' + parseFloat(p.price).toFixed(2) + ' €)</small>'
            + '</label>'
            + '</div>';
    }).join('');
}

function renderBulkPagination(data) {
    let container = document.getElementById('bulkPaginationContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'bulkPaginationContainer';
        container.className = 'mt-2 d-flex justify-content-between align-items-center tiny-pagination';
        document.getElementById('bulkProductList').after(container);
    }
    
    container.innerHTML = `
        <button class="btn btn-sm btn-link p-0" onclick="loadBulkProducts(${data.currentPage - 1})" ${data.currentPage === 0 ? 'disabled' : ''}>Anterior</button>
        <small class="text-muted">Pág ${data.currentPage + 1} de ${data.totalPages}</small>
        <button class="btn btn-sm btn-link p-0" onclick="loadBulkProducts(${data.currentPage + 1})" ${data.currentPage + 1 >= data.totalPages ? 'disabled' : ''}>Siguiente</button>
    `;
}

function handleBulkProductToggle(checkbox) {
    const id = parseInt(checkbox.value);
    if (checkbox.checked) {
        bulkSelectedIds.add(id);
    } else {
        bulkSelectedIds.delete(id);
    }
    updateBulkSelectedCount();
}

function updateBulkSelectedCount() {
    const label = document.getElementById('selectedCountLabel');
    if (label) label.textContent = bulkSelectedIds.size + ' productos seleccionados';
}

function selectAllBulkProducts(select) {
    if (select) {
        // Warning: This only selects items in current view. 
        // For a true "Select All", we'd need a backend "Select All matching search" flag.
        // For now, we follow current behavior: Select visible.
        document.querySelectorAll('.bulk-product-checkbox').forEach(cb => {
            cb.checked = true;
            bulkSelectedIds.add(parseInt(cb.value));
        });
    } else {
        bulkSelectedIds.clear();
        document.querySelectorAll('.bulk-product-checkbox').forEach(cb => cb.checked = false);
    }
    updateBulkSelectedCount();
}



function toggleBulkPriceFields() {
    var type = document.getElementById('bulkPriceType').value;
    document.getElementById('bulkPriceValueLabel').textContent = type === 'percentage' ? 'Porcentaje (%)' : 'Cantidad Fija (€)';
    document.getElementById('bulkPriceValue').placeholder = type === 'percentage' ? 'Ej: 10 para +10%' : 'Ej: 5 para +5€';
}

function applyBulkPriceUpdate() {
    var selectedIds = Array.from(bulkSelectedIds);

    if (selectedIds.length === 0) {
        showToast('Selecciona al menos un producto', 'error');
        return;
    }

    var priceType = document.getElementById('bulkPriceType').value;
    var priceValue = parseFloat(document.getElementById('bulkPriceValue').value);

    if (isNaN(priceValue) || priceValue === 0) {
        showToast('Introduce un valor de cambio de precio', 'error');
        return;
    }

    var effectiveDateInput = document.getElementById('bulkEffectiveDate').value;
    // If no date selected, default to now (immediate application)
    var effectiveDate = effectiveDateInput
        ? new Date(effectiveDateInput).toISOString().slice(0, 19)
        : new Date().toISOString().slice(0, 19);
    var label = document.getElementById('bulkLabel').value.trim();

    var tariffIds = [];
    document.querySelectorAll('.tariff-bulk-checkbox:checked').forEach(cb => {
        tariffIds.push(parseInt(cb.value));
    });

    var body = {
        productIds: selectedIds,
        effectiveDate: effectiveDate,
        label: label || null,
        tariffIds: tariffIds
    };

    if (priceType === 'percentage') {
        body.percentage = priceValue;
    } else {
        body.fixedAmount = priceValue;
    }

    fetch('/api/product-prices/bulk-schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(function (r) {
            if (!r.ok) throw new Error('Error en la petición');
            return r.json();
        })
        .then(function (data) {
            var count = Array.isArray(data) ? data.length : 1;
            document.getElementById('bulkResultsText').textContent = 'Se han programado ' + count + ' cambio(s) de precio correctamente.';
            document.getElementById('bulkResults').style.display = 'block';
            showToast('Precios actualizados correctamente', 'success');
            // Reset form
            selectAllBulkProducts(false);
            document.getElementById('bulkPriceValue').value = '';
            document.getElementById('bulkEffectiveDate').value = '';
            document.getElementById('bulkLabel').value = '';
        })
        .catch(function (err) {
            showToast('Error al aplicar precios masivos: ' + err.message, 'error');
        });
}

// ── ROLE MANAGEMENT ────────────────────────────────────────────────────────

function loadRoles() {
    return Promise.all([
        fetch('/api/roles').then(res => { if (!res.ok) throw new Error('HTTP Status roles ' + res.status); return res.json(); }),
        fetch('/api/workers').then(res => { if (!res.ok) throw new Error('HTTP Status workers ' + res.status); return res.json(); })
    ])
        .then(function ([roles, workers]) {
            rolesCache = roles;
            renderRolesTable(roles, workers);
            populateRoleSelect(roles);
            return roles;
        })
        .catch(function (err) {
            console.error('Error loading roles/workers:', err);
            const el = document.getElementById('rolesTableBody');
            if (el) {
                el.innerHTML = '<tr><td colspan="5" class="text-center text-danger">Error al cargar datos: ' + err.message + '</td></tr>';
            }
        });
}

// Duplicate renderRolesTable removed to avoid conflicts with the filtered version below.

function populateRoleSelect(roles) {
    const select = document.getElementById('workerRole');
    const filterSelect = document.getElementById('workerFilterRole');

    if (select) {
        const currentVal = select.value;
        select.innerHTML = '<option value="">Sin rol</option>' +
            roles.map(r => `<option value="${r.id}">${escHtml(r.name)}</option>`).join('');
        select.value = currentVal;
    }

    if (filterSelect) {
        const currentVal = filterSelect.value;
        filterSelect.innerHTML = '<option value="">Cualquier Rol</option>' +
            roles.map(r => `<option value="${r.id}">${escHtml(r.name)}</option>`).join('');
        filterSelect.value = currentVal;
    }
}

function openRoleModal(id) {
    document.getElementById('roleForm').reset();
    document.getElementById('roleId').value = id || '';
    document.getElementById('roleModalLabel').textContent = id ? 'Editar Rol' : 'Nuevo Rol';

    const container = document.getElementById('rolePermissionsContainer');
    container.innerHTML = '<div class="text-center p-2"><div class="spinner-border spinner-border-sm text-primary"></div></div>';

    fetch('/api/permissions')
        .then(function (r) { return r.json(); })
        .then(function (permissions) {
            container.innerHTML = '';
            const role = id ? rolesCache.find(function (r) { return r.id == id; }) : null;

            if (id && role) {
                document.getElementById('roleName').value = role.name;
                document.getElementById('roleDescription').value = role.description || '';
            }

            permissions.forEach(function (p) {
                // EXCLUDE master permission from the list so it can't be assigned to other roles
                if (p === 'ACCESO_TOTAL_ADMIN') return;

                const isSpecial = p === 'ADMIN_ACCESS';
                const isChecked = role && role.permissions && role.permissions.includes(p);

                const div = document.createElement('div');
                div.className = 'form-check mb-2';

                div.innerHTML = '<input class="form-check-input role-perm-checkbox" type="checkbox" value="' + p + '" id="perm_' + p + '"' + (isChecked ? ' checked' : '') + '>' +
                    '<label class="form-check-label ' + (isSpecial ? 'text-danger fw-bold' : '') + '" for="perm_' + p + '" style="color: var(--text-main); cursor: pointer;">' +
                    (isSpecial ? '<i class="bi bi-shield-exclamation me-1"></i>' : '') + p +
                    '</label>';

                container.appendChild(div);
            });
        })
        .catch(function () {
            container.innerHTML = '<div class="text-danger small">Error al cargar permisos</div>';
        });

    roleModal.show();
}

function saveRole() {
    const id = document.getElementById('roleId').value;
    const name = document.getElementById('roleName').value.trim();
    if (!name) { showToast('El nombre del rol es obligatorio', 'error'); return; }

    const permissions = Array.from(document.querySelectorAll('.role-perm-checkbox:checked')).map(function (cb) { return cb.value; });

    const role = {
        name: name,
        description: document.getElementById('roleDescription').value.trim() || null,
        permissions: permissions
    };

    const method = id ? 'PUT' : 'POST';
    const url = id ? '/api/roles/' + id : '/api/roles';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(role)
    }).then(function (res) {
        if (res.ok) {
            roleModal.hide();
            showToast(id ? 'Rol actualizado' : 'Rol creado');
            loadRoles();
        } else {
            res.json().then(function (err) {
                showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function () {
                showToast('Error al guardar el rol', 'error');
            });
        }
    }).catch(function () {
        showToast('Error de red al guardar el rol', 'error');
    });
}

function deleteRole(id) {
    if (!confirm('¿Estás seguro de eliminar este rol? Los trabajadores que lo tengan perderán sus permisos asociados.')) return;
    fetch('/api/roles/' + id, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Rol eliminado');
                loadRoles();
            } else {
                res.json().then(function (err) {
                    showToast('Error al eliminar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast('Error al eliminar el rol', 'error');
                });
            }
        })
        .catch(function () {
            showToast('Error de red al eliminar el rol', 'error');
        });
}

function onWorkerRoleChange() {
    // No longer auto-checking individual permissions as they are removed
}

// ── WORKER FILTERING ────────────────────────────────────────────────────────

function filterWorkers() {
    const search = document.getElementById('workerFilterName').value.trim();
    const roleId = document.getElementById('workerFilterRole').value;
    const active = document.getElementById('workerFilterStatus').value;
    const sortBy = document.getElementById('workerFilterSortBy').value;
    const sortDir = document.getElementById('workerFilterSortDir').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (roleId) queryParams.append('roleId', roleId);
    if (active) queryParams.append('active', active);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/workers?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderWorkersTable(data.content || data);
            
            const total = data.totalElements || data.length;
            const label = document.getElementById('workerCountLabel');
            if (search || roleId || active !== '') {
                label.innerHTML = `Mostrando <b>${total}</b> trabajadores encontrados con los filtros aplicados.`;
            } else {
                label.textContent = 'Mostrando todas las fichas de trabajadores.';
            }
        })
        .catch(err => console.error("Error filtering workers:", err));
}

function renderWorkersTable(items) {
    const tbody = document.querySelector('#workersView table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4" style="color: var(--text-muted);">No hay trabajadores registrados.</td></tr>`;
        return;
    }

    items.forEach(w => {
        const badgeRole = w.roleName 
            ? `<span class="badge" style="font-size:0.75rem; background-color: rgba(6,182,212,0.18); color: #22d3ee; border: 1px solid rgba(6,182,212,0.3);">${w.roleName}</span>`
            : `<span class="small" style="color: var(--text-muted);">Sin rol</span>`;

        const badgeActive = w.active 
            ? `<span class="badge bg-success">Activo</span>`
            : `<span class="badge bg-danger">Inactivo</span>`;

        // Permissions display removed from workers table as requested.

        const tr = document.createElement('tr');
        tr.className = 'worker-row';
        tr.innerHTML = `
            <td><strong>${w.username}</strong></td>
            <td>${badgeRole}</td>
            <td>${badgeActive}</td>
            <td style="text-align:right">
                <button class="btn-icon" title="Editar" 
                    onclick="openWorkerModal(${w.id}, '${w.username}', ${w.active}, null, ${w.roleId || 'null'})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" title="Eliminar" onclick="deleteWorker(${w.id})">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetWorkerFilters() {
    document.getElementById('workerFilterName').value = '';
    document.getElementById('workerFilterRole').value = '';
    document.getElementById('workerFilterStatus').value = '';
    const sortBy = document.getElementById('workerFilterSortBy');
    const sortDir = document.getElementById('workerFilterSortDir');
    if (sortBy) sortBy.value = 'username';
    if (sortDir) sortDir.value = 'asc';
    filterWorkers();
}

// ── ROLE FILTERING ──────────────────────────────────────────────────────────

function filterRoles() {
    const search = document.getElementById('roleFilterName').value.trim();
    const permissions = Array.from(document.querySelectorAll('.role-filter-perm:checked')).map(cb => cb.value);
    const sortBy = document.getElementById('roleFilterSortBy').value;
    const sortDir = document.getElementById('roleFilterSortDir').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (permissions.length > 0) {
        permissions.forEach(p => queryParams.append('permissions', p));
    }
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/roles?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderRolesTable(data.content || data);
        })
        .catch(err => console.error("Error filtering roles:", err));
}

function renderRolesTable(items) {
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4" style="color: var(--text-muted);">No hay roles registrados.</td></tr>`;
        return;
    }

    items.forEach(r => {
        const perms = (Array.from(r.permissions) || []).map(p => {
            const isMaster = p === 'ACCESO_TOTAL_ADMIN';
            const style = isMaster 
                ? 'background-color: rgba(255, 184, 0, 0.15); color: #ffb800; border: 1px solid rgba(255, 184, 0, 0.3); font-weight: 600;' 
                : 'background-color: rgba(148,163,184,0.15); color: var(--text-muted); border: 1px solid rgba(148,163,184,0.25);';
            const text = isMaster ? 'ACCESO TOTAL' : p;
            return `<span class="badge me-1" style="font-size: 0.65rem; ${style}">${text}</span>`;
        }).join('') || `<span class="small" style="color: var(--text-muted);">Sin permisos</span>`;

        const tr = document.createElement('tr');
        tr.className = 'role-row';
        const count = r.workerCount !== undefined ? r.workerCount : 0;
        tr.innerHTML = `
            <td><strong>${r.name}</strong></td>
            <td class="small" style="color: var(--text-muted);">${r.description || '—'}</td>
            <td>${perms}</td>
            <td><span class="badge" style="background-color: rgba(var(--accent-rgb), 0.1); color: var(--accent); border: 1px solid var(--accent);">${count} trabajador(es)</span></td>
            <td style="text-align:right">
                <button class="btn-icon" title="Editar" onclick="openRoleModal(${r.id})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" title="Eliminar" onclick="deleteRole(${r.id})">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetRolePermFilters() {
    document.querySelectorAll('.role-filter-perm').forEach(cb => cb.checked = false);
    updateFilterPermLabel('roleFilterPermBtn', '.role-filter-perm', 'Seleccionar Permisos');
}

function resetRoleFilters() {
    document.getElementById('roleFilterName').value = '';
    resetRolePermFilters();
    const sortBy = document.getElementById('roleFilterSortBy');
    const sortDir = document.getElementById('roleFilterSortDir');
    if (sortBy) sortBy.value = 'name';
    if (sortDir) sortDir.value = 'asc';
    filterRoles();
}


// ── NEW FILTERING FUNCTIONS ──────────────────────────────────────────────────

function filterProducts() {
    const query = document.getElementById('productFilterSearch').value.toLowerCase().trim();
    const category = document.getElementById('productFilterCategory').value;
    const stockFilter = document.getElementById('productFilterStock').value;
    const activeFilter = document.getElementById('productFilterActive').value;

    document.querySelectorAll('.product-row').forEach(row => {
        const name = (row.getAttribute('data-name') || '').toLowerCase();
        const desc = (row.getAttribute('data-desc') || '').toLowerCase();
        const id = (row.getAttribute('data-id') || '');
        const cat = (row.getAttribute('data-category') || '');
        const stock = parseInt(row.getAttribute('data-stock') || '0');
        const active = row.getAttribute('data-active');

        let matches = true;
        if (query && !name.includes(query) && !desc.includes(query) && !id.includes(query)) matches = false;
        if (category && cat !== category) matches = false;
        if (stockFilter === 'low' && stock >= 5) matches = false;
        if (stockFilter === 'normal' && stock < 5) matches = false;
        if (activeFilter && active !== activeFilter) matches = false;

        row.style.display = matches ? '' : 'none';
    });
}
function resetProductFilters() {
    document.getElementById('productFilterSearch').value = '';
    document.getElementById('productFilterCategory').value = '';
    document.getElementById('productFilterStock').value = '';
    document.getElementById('productFilterActive').value = '';
    filterProducts();
}

let currentSalesPage = 0;
let salesTotalPages = 1;

async function fetchSalesPage(page) {
    const search = (document.getElementById('invoiceFilterSearch').value || '').trim();
    const type = document.getElementById('invoiceFilterType').value;
    const method = document.getElementById('invoiceFilterMethod').value;
    const date = document.getElementById('invoiceFilterDate').value;
    const sortBy = (document.getElementById('invoiceSortBy') || {}).value || 'createdAt';
    const sortDir = (document.getElementById('invoiceSortDir') || {}).value || 'desc';
    
    const url = `/api/admin/sales?page=${page}&search=${encodeURIComponent(search)}&type=${type}&method=${method}&date=${date}&sortBy=${sortBy}&sortDir=${sortDir}`;
    
    // Show loading state if needed
    const tbody = document.getElementById('invoicesTableBody');
    if (tbody) tbody.style.opacity = '0.5';

    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error('API server error');
        const data = await response.json();
        
        currentSalesPage = data.currentPage;
        salesTotalPages = data.totalPages;
        
        renderSalesTable(data.content);
        updateSalesPaginationUI(data);
    } catch (error) {
        console.error('Error fetching sales:', error);
        if (typeof showToast === 'function') showToast('Error al cargar facturas', 'error');
    } finally {
        if (tbody) tbody.style.opacity = '1';
    }
}

function renderSalesTable(sales) {
    const tbody = document.getElementById('invoicesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (sales.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:2rem;color:var(--text-muted)">No se encontraron registros.</td></tr>`;
        return;
    }
    
    sales.forEach(sale => {
        const row = document.createElement('tr');
        row.className = 'invoice-row';
        row.style.cursor = 'pointer';
        row.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">${sale.displayId}</td>
            <td>${new Date(sale.createdAt).toLocaleString()}</td>
            <td>
                ${sale.status === 'CANCELLED' ? '<span class="badge mb-1 d-block" style="background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 6px; padding: 0.25rem 0.6rem; font-size: 0.75rem; font-weight: 600;"><i class="bi bi-x-circle me-1"></i>ANULADA</span>' : ''}
                <span class="badge-active ${sale.type === 'factura' ? 'yes' : 'no'}">${sale.type === 'factura' ? 'Factura' : 'Ticket'}</span>
            </td>
            <td>
                ${sale.customerName ? `<strong>${sale.customerName}</strong><div style="font-size:0.75rem;color:var(--text-muted)">${sale.customerTaxId}</div>` : '<span style="color:var(--text-muted)">-- Consumidor Final --</span>'}
            </td>
            <td style="font-weight: 500;">${sale.workerUsername || 'Sistema'}</td>
            <td>
                <i class="${sale.paymentMethod === 'CASH' ? 'bi bi-cash' : 'bi bi-credit-card'}" style="margin-right:0.3rem"></i>
                ${sale.paymentMethod === 'CASH' ? 'Efectivo' : 'Tarjeta'}
            </td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--text-main);text-align:right">${sale.totalAmount.toFixed(2)} €</td>
            <td style="text-align:right">
                <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                    ${sale.status !== 'CANCELLED' ? `<button class="btn-icon danger" title="Anular" onclick="event.stopPropagation(); cancelSale(${sale.id})"><i class="bi bi-x-circle"></i></button>` : ''}
                    <button type="button" class="btn-icon" title="Vista Previa" onclick="event.stopPropagation(); showDocPreview('/tpv/receipt/${sale.id}')"><i class="bi bi-eye" style="color:#3498db;"></i></button>
                    <a href="/tpv/receipt/${sale.id}?autoPrint=true" target="_blank" class="btn-icon" title="Imprimir" onclick="event.stopPropagation();"><i class="bi bi-printer" style="color:#e74c3c;"></i></a>
                </div>
            </td>
        `;
        row.onclick = () => window.location.href = `/admin/sale/${sale.id}`;
        tbody.appendChild(row);
    });
}

function updateSalesPaginationUI(data) {
    const pageEl = document.getElementById('salesCurrentPage');
    const totalEl = document.getElementById('salesTotalPages');
    const jumpInput = document.getElementById('salesJumpInput');
    const prevBtn = document.getElementById('salesPagePrev');
    const nextBtn = document.getElementById('salesPageNext');

    if (pageEl) pageEl.innerText = data.currentPage + 1;
    if (totalEl) totalEl.innerText = data.totalPages;
    if (jumpInput) {
        jumpInput.value = data.currentPage + 1;
        jumpInput.max = data.totalPages;
    }
    
    if (prevBtn) prevBtn.disabled = data.currentPage === 0;
    if (nextBtn) nextBtn.disabled = data.currentPage >= data.totalPages - 1;
}

function changeSalesPage(delta) {
    fetchSalesPage(currentSalesPage + delta);
}

function jumpToSalesPage(page) {
    let p = parseInt(page) - 1;
    if (isNaN(p) || p < 0) p = 0;
    if (p >= salesTotalPages) p = salesTotalPages - 1;
    fetchSalesPage(p);
}

function filterInvoices() {
    fetchSalesPage(0);
}
function resetInvoiceFilters() {
    document.getElementById('invoiceFilterSearch').value = '';
    document.getElementById('invoiceFilterType').value = '';
    document.getElementById('invoiceFilterMethod').value = '';
    document.getElementById('invoiceFilterDate').value = '';
    const sortByEl = document.getElementById('invoiceSortBy');
    const sortDirEl = document.getElementById('invoiceSortDir');
    if (sortByEl) sortByEl.value = 'createdAt';
    if (sortDirEl) sortDirEl.value = 'desc';
    filterInvoices();
}

function filterCashClosures() {
    const worker = document.getElementById('cashFilterWorker').value.trim();
    const date = document.getElementById('cashFilterDate').value;
    const sortBy = document.getElementById('cashFilterSortBy').value;
    const sortDir = document.getElementById('cashFilterSortDir').value;

    const queryParams = new URLSearchParams();
    if (worker) queryParams.append('worker', worker);
    if (date) queryParams.append('date', date);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/cash-closings?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderCashClosuresTable(data.content || data);
            // Update pagination if needed, but the current UI doesn't have it visible for cash closes yet.
            // I'll add it later if the user asks for it, for now just the list.
        })
        .catch(err => console.error("Error filtering cash closures:", err));
}

function renderCashClosuresTable(items) {
    const tbody = document.getElementById('cashClosuresTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:2rem;color:var(--text-muted)">No hay cierres de caja registrados.</td></tr>`;
        return;
    }

    items.forEach(r => {
        const opening = r.openingTime ? new Date(r.openingTime).toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
        const closing = r.closedAt ? new Date(r.closedAt).toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
        
        const calc = (r.totalCalculated || 0).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        const decl = (r.closingBalance || 0).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        const diffVal = r.difference || 0;
        
        let diffBadge = '';
        if (diffVal === 0) {
            diffBadge = `<span class="badge-active yes">Cuadrado (0.00 €)</span>`;
        } else if (diffVal > 0) {
            diffBadge = `<span class="badge-active yes">+${diffVal.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €</span>`;
        } else {
            diffBadge = `<span class="badge-active no">${diffVal.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €</span>`;
        }

        const tr = document.createElement('tr');
        tr.className = 'cash-row';
        tr.style.cursor = 'pointer';
        tr.onclick = () => window.location.href = `/admin/cash-register/${r.id}`;
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">#${r.id}</td>
            <td>${opening}</td>
            <td>${closing}</td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-weight:600;text-align:right">${calc} €</td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-weight:600;text-align:right">${decl} €</td>
            <td style="text-align:right">${diffBadge}</td>
            <td style="font-weight: 500;">${r.workerUsername || 'Sistema'}</td>
            <td style="text-align:right">
                <a href="/admin/download/cash-register/${r.id}" target="_blank" class="btn-icon" title="Imprimir" style="text-decoration: none;" onclick="event.stopPropagation();">
                    <i class="bi bi-printer" style="color:#e74c3c;"></i>
                </a>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetCashFilters() {
    document.getElementById('cashFilterWorker').value = '';
    document.getElementById('cashFilterDate').value = '';
    const sortBy = document.getElementById('cashFilterSortBy');
    const sortDir = document.getElementById('cashFilterSortDir');
    if (sortBy) sortBy.value = 'id';
    if (sortDir) sortDir.value = 'desc';
    filterCashClosures();
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
                    onclick="openCustomerModal(${c.id}, '${c.name}', '${c.taxId || ''}', '${c.email || ''}', '${c.phone || ''}', '${(c.address || '').replace(/'/g, "\\'")}', '${c.city || ''}', '${c.postalCode || ''}', '${c.type}', ${c.hasRecargoEquivalencia}, ${c.tariffId || 'null'})">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" title="Eliminar" onclick="deleteCustomer(${c.id}, '${c.name}')">
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

function filterActivity() {
    fetchActivityLogs(0);
}
function resetActivityFilters() {
    const search = document.getElementById('activityFilterSearch');
    const action = document.getElementById('activityFilterAction');
    const username = document.getElementById('activityFilterUsername');
    const sortBy = document.getElementById('activitySortBy');
    const sortDir = document.getElementById('activitySortDir');
    
    if (search) search.value = '';
    if (action) action.value = '';
    if (username) username.value = '';
    if (sortBy) sortBy.value = 'timestamp';
    if (sortDir) sortDir.value = 'desc';
    
    fetchActivityLogs(0);
}

// Categories filtering is now powered by shared/inventory-filter.js (runSharedBackendCategoryFilter)
function resetCategoryFilters() {
    const srch = document.getElementById('categoryFilterSearch');
    if (srch) srch.value = '';
    const globalSearch = document.getElementById('sharedFilterSearch');
    if (globalSearch) globalSearch.value = '';

    const sortByEl = document.getElementById('categoryFilterSortBy');
    const sortDirEl = document.getElementById('categoryFilterSortDir');
    if (sortByEl) sortByEl.value = 'id';
    if (sortDirEl) sortDirEl.value = 'asc';

    runSharedBackendCategoryFilter();
}

function filterFuturePrices() {
    const query = document.getElementById('futurePriceFilterSearch').value.toLowerCase().trim();
    document.querySelectorAll('.future-price-row').forEach(row => {
        const name = (row.getAttribute('data-product-name') || '');
        row.style.display = name.includes(query) ? '' : 'none';
    });
}
function resetFuturePriceFilters() {
    document.getElementById('futurePriceFilterSearch').value = '';
    filterFuturePrices();
}

function filterBulkProductList() {
    const query = document.getElementById('bulkProductSearch').value.toLowerCase().trim();
    const categoryQuery = document.getElementById('bulkCategoryFilter').value.toLowerCase().trim();

    document.querySelectorAll('.bulk-product-item').forEach(item => {
        const searchData = (item.getAttribute('data-search') || '');
        const categoryData = (item.getAttribute('data-category') || '');

        let matchesSearch = !query || searchData.includes(query);
        let matchesCategory = !categoryQuery || categoryData === categoryQuery;

        item.style.display = (matchesSearch && matchesCategory) ? 'block' : 'none';
    });
}

function selectBulkByCategory() {
    const categoryQuery = document.getElementById('bulkCategoryFilter').value.toLowerCase().trim();
    if (!categoryQuery) {
        showToast('Selecciona primero una categoría del desplegable', 'warning');
        return;
    }

    let countAdded = 0;
    document.querySelectorAll('.bulk-product-item').forEach(item => {
        const categoryData = (item.getAttribute('data-category') || '');
        if (categoryData === categoryQuery) {
            const cb = item.querySelector('.bulk-product-checkbox');
            if (cb && !cb.checked) {
                cb.checked = true;
                countAdded++;
            }
        }
    });

    updateBulkSelectedCount();
    if (countAdded > 0) {
        showToast('Se han marcado ' + countAdded + ' productos adicionales');
    } else {
        showToast('No hay productos nuevos para marcar en esta categoría', 'info');
    }
}

// ── RETURNS FILTERING ────────────────────────────────────────────────────────

function filterReturns() {
    const search = document.getElementById('returnFilterSearch').value.trim();
    const method = document.getElementById('returnFilterMethod').value;
    const date = document.getElementById('returnFilterDate').value;
    const sortBy = document.getElementById('returnFilterSortBy').value;
    const sortDir = document.getElementById('returnFilterSortDir').value;

    const queryParams = new URLSearchParams();
    if (search) queryParams.append('search', search);
    if (method) queryParams.append('method', method);
    if (date) queryParams.append('date', date);
    queryParams.append('sortBy', sortBy);
    queryParams.append('sortDir', sortDir);

    fetch(`/api/admin/returns?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderReturnsTable(data.content || data);
            // Handle pagination if needed
        })
        .catch(err => console.error("Error filtering returns:", err));
}

function renderReturnsTable(items) {
    const tbody = document.getElementById('returnsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted)">No hay devoluciones registradas.</td></tr>`;
        return;
    }

    items.forEach(r => {
        const date = r.createdAt ? new Date(r.createdAt).toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
        const amount = (r.amount || 0).toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        
        const typeBadge = r.type === 'TOTAL' 
            ? `<span class="badge" style="background:#e74c3c22; color:#e74c3c; border:1px solid #e74c3c33;">TOTAL</span>`
            : `<span class="badge" style="background:#f39c1222; color:#f39c12; border:1px solid #f39c1233;">PARCIAL</span>`;

        const tr = document.createElement('tr');
        tr.className = 'return-row';
        tr.style.cursor = 'pointer';
        tr.onclick = () => window.location.href = r.ticketUrl || `/admin/return/${r.id}`;
        tr.innerHTML = `
            <td style="color:var(--text-muted);font-weight:600">${r.returnNumber}</td>
            <td style="font-weight: 500;">${r.originalNumber || '—'}</td>
            <td>${date}</td>
            <td>${typeBadge}</td>
            <td class="small" style="max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${r.reason || '—'}</td>
            <td>${r.workerUsername || '—'}</td>
            <td>${r.paymentMethod || '—'}</td>
            <td style="font-family:'Barlow Condensed',sans-serif;font-weight:600;text-align:right">- ${amount} €</td>
            <td style="text-align:right">
                <a href="/admin/download/return/${r.id}" target="_blank" class="btn-icon" title="Descargar PDF" style="text-decoration: none;" onclick="event.stopPropagation();">
                    <i class="bi bi-file-earmark-pdf" style="color:#e74c3c;"></i>
                </a>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function resetReturnFilters() {
    document.getElementById('returnFilterSearch').value = '';
    document.getElementById('returnFilterMethod').value = '';
    document.getElementById('returnFilterDate').value = '';
    const sortBy = document.getElementById('returnFilterSortBy');
    const sortDir = document.getElementById('returnFilterSortDir');
    if (sortBy) sortBy.value = 'createdAt';
    if (sortDir) sortDir.value = 'desc';
    filterReturns();
}

// ── SETTINGS & SECURITY ──────────────────────────────────────────────────────

function updateAdminPin() {
    const currentPin = document.getElementById('currentPin').value;
    const newPin = document.getElementById('newPin').value;
    const confirmPin = document.getElementById('confirmPin').value;

    if (!currentPin || !newPin || !confirmPin) {
        showToast('Todos los campos son obligatorios', 'error');
        return;
    }

    if (newPin !== confirmPin) {
        showToast('El nuevo PIN y la confirmación no coinciden', 'error');
        return;
    }

    if (newPin.length < 4) {
        showToast('El nuevo PIN debe tener al menos 4 caracteres', 'error');
        return;
    }

    const body = {
        currentPin: currentPin,
        newPin: newPin,
        confirmPin: confirmPin
    };

    fetch('/admin/settings/pin', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    })
        .then(async response => {
            const data = await response.json();
            if (response.ok) {
                showToast(data.message || 'PIN actualizado correctamente');
                document.getElementById('changePinForm').reset();
            } else {
                showToast(data.message || 'Error al actualizar el PIN', 'error');
            }
        })
        .catch(error => {
            console.error('Error updating PIN:', error);
            showToast('Error de conexión al servidor', 'error');
        });
}

function cancelSale(saleId) {
    const reason = prompt('Indica el motivo de la anulación:');
    if (!reason || !reason.trim()) {
        showToast('El motivo es obligatorio para anular', 'error');
        return;
    }

    if (!confirm('¿Seguro que quieres ANULAR la venta #' + saleId + '? Se restaurará el stock y se marcará como anulada.')) return;

    fetch('/admin/sales/cancel/' + saleId, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ reason: reason })
    })
        .then(r => {
            if (r.ok) {
                showToast('Venta anulada correctamente');
                setTimeout(() => location.reload(), 1000);
            } else {
                return r.text().then(msg => showToast('Error al anular: ' + msg, 'error'));
            }
        })
        .catch(() => showToast('Error de conexión', 'error'));
}

// ── MAIL SETTINGS (SMTP) ───────────────────────────────────────────────────

function loadMailSettings() {
    fetch('/admin/api/mail-settings')
        .then(response => response.json())
        .then(data => {
            if (document.getElementById('mailHost')) document.getElementById('mailHost').value = data.host || '';
            if (document.getElementById('mailPort')) document.getElementById('mailPort').value = data.port || '587';
            if (document.getElementById('mailUsername')) document.getElementById('mailUsername').value = data.username || '';
            if (document.getElementById('mailPassword')) document.getElementById('mailPassword').value = data.password || '';
        })
        .catch(error => {
            console.error('Error loading mail settings:', error);
            showToast('Error al cargar la configuración de correo', 'error');
        });
}

function saveMailSettings() {
    const host = document.getElementById('mailHost').value.trim();
    const port = document.getElementById('mailPort').value.trim();
    const username = document.getElementById('mailUsername').value.trim();
    const password = document.getElementById('mailPassword').value;

    const body = {
        host: host,
        port: port,
        username: username,
        password: password
    };

    fetch('/admin/api/mail-settings', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    })
        .then(response => response.json())
        .then(data => {
            showToast(data.message || 'Configuración de correo actualizada correctamente', 'success');
            loadMailSettings();
        })
        .catch(error => {
            console.error('Error saving mail settings:', error);
            showToast('Error al guardar la configuración de correo', 'error');
        });
}

// Variables are now handled at the top of the file and initialized in DOMContentLoaded
var ipcUpdateModalEl = document.getElementById('ipcUpdateModal');

function openIpcUpdateModal() {
    if (!ipcUpdateModal) return;
    ipcUpdateModal.show();

    const statusEl = document.getElementById('ipcApiStatus');
    const inputEl = document.getElementById('ipcValueInput');
    const noteEl = document.getElementById('ipcSourceNote');

    // Default: Reset and show loading
    statusEl.className = 'alert alert-info py-2 d-flex align-items-center gap-2 mb-3';
    statusEl.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Obteniendo IPC actual del INE...';
    inputEl.value = '';

    // Ensure bulk products are loaded for 'all products' scope
    if (!bulkProductsCache) {
        loadBulkProducts();
    }

    fetch('/api/ipc/current')
        .then(r => r.json())
        .then(data => {
            if (data.ipcValue) {
                statusEl.className = 'alert alert-success py-2 d-flex align-items-center gap-2 mb-3';
                statusEl.innerHTML = '<i class="bi bi-check-circle-fill"></i> IPC obtenido del INE con éxito.';
                inputEl.value = data.ipcValue;
                noteEl.textContent = 'Dato oficial INE (Variación anual)';
            } else {
                statusEl.className = 'alert alert-warning py-2 d-flex align-items-center gap-2 mb-3';
                statusEl.innerHTML = '<i class="bi bi-exclamation-triangle-fill"></i> No se pudo obtener el IPC. Introduzca el valor manualmente.';
                noteEl.textContent = 'Introducción manual';
            }

            // Suggest Jan 1st of next year
            const nextYear = new Date().getFullYear() + 1;
            document.getElementById('ipcEffectiveDate').value = nextYear + '-01-01T00:00';
            updateIpcPreview();
        })
        .catch(() => {
            statusEl.className = 'alert alert-danger py-2 d-flex align-items-center gap-2 mb-3';
            statusEl.innerHTML = '<i class="bi bi-exclamation-octagon-fill"></i> Error de conexión con la API del INE.';
            noteEl.textContent = 'Introducción manual requerida';
            updateIpcPreview();
        });
}

const ipcValueInput = document.getElementById('ipcValueInput');
if (ipcValueInput) {
    ipcValueInput.addEventListener('input', debounce(function () {
        updateIpcPreview();
    }, 500));
}

function updateIpcPreview() {
    const val = parseFloat(document.getElementById('ipcValueInput').value) || 0;
    const body = document.getElementById('ipcPreviewBody');

    body.innerHTML = '<tr><td colspan="3" class="text-center py-2"><span class="spinner-border spinner-border-sm"></span></td></tr>';

    fetch('/api/ipc/preview?percentage=' + val)
        .then(r => r.json())
        .then(data => {
            if (!data || data.length === 0) {
                body.innerHTML = '<tr><td colspan="3" class="text-center py-2" style="color: var(--text-muted);">No hay productos para previsualizar</td></tr>';
                return;
            }
            body.innerHTML = data.map(p => `
                <tr>
                    <td>${escHtml(p.productName)}</td>
                    <td class="text-end">${parseFloat(p.currentPrice).toFixed(2)} €</td>
                    <td class="text-end fw-bold" style="color: #000;">${parseFloat(p.newPrice).toFixed(2)} €</td>
                </tr>
            `).join('');
        })
        .catch(() => {
            body.innerHTML = '<tr><td colspan="3" class="text-center py-2 text-danger">Error al cargar vista previa</td></tr>';
        });
}

function applyIpcConfirm() {
    const val = parseFloat(document.getElementById('ipcValueInput').value);
    const dateInput = document.getElementById('ipcEffectiveDate').value;
    const scope = document.getElementById('ipcScope').value;

    if (isNaN(val) || val === 0) {
        showToast('Introduzca un porcentaje de variación válido', 'error');
        return;
    }

    let productIds = [];
    if (scope === 'all') {
        if (!bulkProductsCache) {
            showToast('Cargando lista de productos, inténtelo de nuevo en un segundo', 'warning');
            loadBulkProducts();
            return;
        }
        productIds = bulkProductsCache.map(p => p.id);
    } else {
        productIds = Array.from(document.querySelectorAll('.bulk-product-checkbox:checked')).map(cb => parseInt(cb.value));
    }

    if (productIds.length === 0) {
        showToast('No hay productos seleccionados. Use el filtro masivo o elija el ámbito "Todos".', 'error');
        return;
    }

    if (!confirm('¿Confirma aplicar un incremento del ' + val + '% a ' + productIds.length + ' productos?')) return;

    // Format date for API
    const effectiveDate = dateInput
        ? new Date(dateInput).toISOString().slice(0, 19)
        : new Date().toISOString().slice(0, 19);

    const body = {
        productIds: productIds,
        percentage: val,
        effectiveDate: effectiveDate,
        label: "Actualización IPC INE (" + val + "%)"
    };

    fetch('/api/product-prices/bulk-schedule', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
        .then(res => {
            if (res.ok) {
                showToast('Actualización masiva por IPC realizada con éxito');
                ipcUpdateModal.hide();
                if (typeof loadFuturePrices === 'function') loadFuturePrices();
            } else {
                showToast('Error al procesar la actualización por IPC', 'error');
            }
        })
        .catch(() => showToast('Error de comunicación con el servidor', 'error'));
}

function debounce(func, wait) {
    let timeout;
    return function () {
        const context = this, args = arguments;
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(context, args), wait);
    };
}
function togglePassword(inputId) {
    const input = document.getElementById(inputId);
    // Usamos window.event para evitar errores de scope en browsers o pasarlo en línea no funcionales
    const btn = window.event ? window.event.currentTarget : (event ? event.currentTarget : null);
    const icon = btn ? btn.querySelector('i') : null;

    if (input && input.type === 'password') {
        input.type = 'text';
        if (icon) {
            icon.classList.remove('bi-eye');
            icon.classList.add('bi-eye-slash');
        }
    } else if (input) {
        input.type = 'password';
        if (icon) {
            icon.classList.remove('bi-eye-slash');
            icon.classList.add('bi-eye');
        }
    }
}

/**
 * Filtra la tabla comparativa de precios por tarifas por nombre de producto
 */
function filterTariffComparison() {
    const query = document.getElementById('tariffComparisonSearch').value.toLowerCase();
    const rows = document.querySelectorAll('.comparison-row');

    rows.forEach(row => {
        const productName = row.querySelector('td:first-child .fw-bold').textContent.toLowerCase();
        const categoryName = row.querySelector('td:first-child small').textContent.toLowerCase();

        if (productName.includes(query) || categoryName.includes(query)) {
            row.style.display = '';
        } else {
            row.style.display = 'none';
        }
    });
}

// -- Coupon Management --------------------------------------------------------
var _couponProductsCache = null;
var _couponCategoriesCache = null;
var _selectedCouponProducts = [];
var _selectedCouponCategories = [];

function searchCouponProducts(query, initial = false) {
    const resultsDiv = document.getElementById('couponProductSearchResults');
    if (!resultsDiv) return;

    if (!_couponProductsCache) {
        fetch('/api/products/selection-list').then(r => r.json()).then(data => {
            _couponProductsCache = data;
            searchCouponProducts(query, initial);
        });
        return;
    }

    let filtered = _couponProductsCache;
    if (query && query.length >= 1) {
        const q = query.toLowerCase();
        filtered = _couponProductsCache.filter(p => 
            p.name.toLowerCase().includes(q) || 
            (p.categoryName && p.categoryName.toLowerCase().includes(q))
        );
    }

    if (filtered.length === 0) {
        resultsDiv.style.display = 'none';
        return;
    }

    resultsDiv.innerHTML = filtered.slice(0, 100).map(p => `
        <button type="button" class="list-group-item list-group-item-action py-2" 
            onclick="addCouponProduct(${p.id}, '${escHtml(p.name)}')">
            <div class="d-flex justify-content-between align-items-center">
                <span style="color:var(--text-main); font-size: 0.82rem; font-weight: 500;">${escHtml(p.name)}</span>
                <small class="text-muted" style="font-size: 0.65rem;">${escHtml(p.categoryName || '')} • ${(p.price || 0).toFixed(2)}€</small>
            </div>
        </button>
    `).join('');
    resultsDiv.style.display = 'block';
}

function searchCouponCategories(query, initial = false) {
    const resultsDiv = document.getElementById('couponCategorySearchResults');
    if (!resultsDiv) return;

    if (!_couponCategoriesCache) {
        fetch('/api/categories').then(r => r.json()).then(data => {
            _couponCategoriesCache = data;
            searchCouponCategories(query, initial);
        });
        return;
    }

    let filtered = _couponCategoriesCache;
    if (query && query.length >= 1) {
        const q = query.toLowerCase();
        filtered = _couponCategoriesCache.filter(c => c.name.toLowerCase().includes(q));
    }

    if (filtered.length === 0) {
        resultsDiv.style.display = 'none';
        return;
    }

    resultsDiv.innerHTML = filtered.map(c => `
        <button type="button" class="list-group-item list-group-item-action py-2" 
            onclick="addCouponCategory(${c.id}, '${escHtml(c.name)}')">
            <div class="d-flex align-items-center justify-content-between">
                <span><i class="bi bi-tag me-2 text-accent"></i><span style="color:var(--text-main); font-size: 0.85rem;">${escHtml(c.name)}</span></span>
                <small class="text-muted" style="font-size: 0.7rem;">ID: ${c.id}</small>
            </div>
        </button>
    `).join('');
    resultsDiv.style.display = 'block';
}

function addCouponProduct(id, name) {
    if (_selectedCouponProducts.some(p => p.id === id)) {
        document.getElementById('couponProductSearchResults').style.display = 'none';
        document.getElementById('couponProductSearch').value = '';
        return;
    }
    _selectedCouponProducts.push({ id, name });
    renderSelectedCouponProducts();
    document.getElementById('couponProductSearchResults').style.display = 'none';
    document.getElementById('couponProductSearch').value = '';
}

function addCouponCategory(id, name) {
    if (_selectedCouponCategories.some(c => c.id === id)) {
        document.getElementById('couponCategorySearchResults').style.display = 'none';
        document.getElementById('couponCategorySearch').value = '';
        return;
    }
    _selectedCouponCategories.push({ id, name });
    renderSelectedCouponCategories();
    document.getElementById('couponCategorySearchResults').style.display = 'none';
    document.getElementById('couponCategorySearch').value = '';
}

function removeCouponProduct(id) {
    _selectedCouponProducts = _selectedCouponProducts.filter(p => p.id !== id);
    renderSelectedCouponProducts();
}

function removeCouponCategory(id) {
    _selectedCouponCategories = _selectedCouponCategories.filter(c => c.id !== id);
    renderSelectedCouponCategories();
}

function renderSelectedCouponProducts() {
    const container = document.getElementById('selectedCouponProducts');
    if (!container) return;
    container.innerHTML = _selectedCouponProducts.map(p => `
        <div class="badge d-flex align-items-center gap-2 p-2" 
            style="background: rgba(var(--accent-rgb), 0.1); color: var(--accent); border: 1px solid var(--accent); border-radius: 8px; font-weight: 500;">
            <span>${escHtml(p.name)}</span>
            <i class="bi bi-x-lg cursor-pointer" onclick="removeCouponProduct(${p.id})" style="font-size: 0.7rem; opacity: 0.8;"></i>
        </div>
    `).join('');
}

function renderSelectedCouponCategories() {
    const container = document.getElementById('selectedCouponCategories');
    if (!container) return;
    container.innerHTML = _selectedCouponCategories.map(c => `
        <div class="badge d-flex align-items-center gap-2 p-2" 
            style="background: rgba(var(--primary-rgb), 0.1); color: var(--text-main); border: 1px solid var(--border); border-radius: 8px; font-weight: 500;">
            <i class="bi bi-tag-fill me-1" style="color: var(--accent);"></i>
            <span>${escHtml(c.name)}</span>
            <i class="bi bi-x-lg cursor-pointer" onclick="removeCouponCategory(${c.id})" style="font-size: 0.7rem; opacity: 0.8;"></i>
        </div>
    `).join('');
}

// Close dropdowns on outside click
document.addEventListener('mousedown', function(e) {
    const pResults = document.getElementById('couponProductSearchResults');
    if (pResults && !pResults.contains(e.target) && e.target.id !== 'couponProductSearch') {
        pResults.style.display = 'none';
    }
    const cResults = document.getElementById('couponCategorySearchResults');
    if (cResults && !cResults.contains(e.target) && e.target.id !== 'couponCategorySearch') {
        cResults.style.display = 'none';
    }
});

function openCouponModal(btn) {
    // Reset form
    const couponIdField = document.getElementById('couponId');
    if (!couponIdField) return;

    couponIdField.value = '';
    document.getElementById('couponCode').value = '';
    document.getElementById('couponDesc').value = '';
    document.getElementById('couponType').value = 'PERCENTAGE';
    document.getElementById('couponValue').value = '0';
    document.getElementById('couponLimit').value = '';
    document.getElementById('couponActive').checked = true;
    document.getElementById('couponGeneric').checked = true;
    document.getElementById('couponFrom').value = '';
    document.getElementById('couponUntil').value = '';
    
    // Reset restrictions
    _selectedCouponProducts = [];
    _selectedCouponCategories = [];
    renderSelectedCouponProducts();
    renderSelectedCouponCategories();
    if (document.getElementById('couponProductSearchResults')) document.getElementById('couponProductSearchResults').style.display = 'none';
    if (document.getElementById('couponCategorySearchResults')) document.getElementById('couponCategorySearchResults').style.display = 'none';

    if (btn) {
        const id = btn.dataset.id;
        document.getElementById('couponId').value = id;
        document.getElementById('couponCode').value = btn.dataset.code;
        document.getElementById('couponDesc').value = btn.dataset.desc;
        document.getElementById('couponType').value = btn.dataset.type;
        document.getElementById('couponValue').value = btn.dataset.value;
        document.getElementById('couponLimit').value = btn.dataset.limit;
        document.getElementById('couponActive').checked = btn.dataset.active === 'true';
        document.getElementById('couponGeneric').checked = btn.dataset.generic === 'true';
        if (btn.dataset.from) document.getElementById('couponFrom').value = btn.dataset.from.substring(0, 16);
        if (btn.dataset.until) document.getElementById('couponUntil').value = btn.dataset.until.substring(0, 16);
        
        // Load restrictions from API
        fetch('/api/coupons/' + id)
            .then(r => r.json())
            .then(coupon => {
                if (coupon.restrictedProducts) {
                    _selectedCouponProducts = coupon.restrictedProducts.map(p => ({ id: p.id, name: p.nameEs || p.name }));
                    renderSelectedCouponProducts();
                }
                if (coupon.restrictedCategories) {
                    _selectedCouponCategories = coupon.restrictedCategories.map(c => ({ id: c.id, name: c.name }));
                    renderSelectedCouponCategories();
                }
            }).catch(e => console.error("Error loading coupon details", e));
    }
    
    if (couponModal) couponModal.show();
}

function saveCoupon() {
    const idField = document.getElementById('couponId');
    const id = idField ? idField.value : '';
    const code = document.getElementById('couponCode').value.trim().toUpperCase();
    const valueStr = document.getElementById('couponValue').value;
    
    if (!code) { showToast('El código es obligatorio', 'error'); return; }
    if (!valueStr || isNaN(parseFloat(valueStr))) { showToast('El valor del descuento no es válido', 'error'); return; }

    const body = {
        id: id ? parseInt(id) : null,
        code: code,
        description: document.getElementById('couponDesc').value.trim(),
        discountType: document.getElementById('couponType').value,
        discountValue: parseFloat(valueStr),
        usageLimit: document.getElementById('couponLimit').value ? parseInt(document.getElementById('couponLimit').value) : null,
        active: document.getElementById('couponActive').checked,
        generic: document.getElementById('couponGeneric').checked,
        validFrom: document.getElementById('couponFrom').value || null,
        validUntil: document.getElementById('couponUntil').value || null,
        restrictedProducts: _selectedCouponProducts.map(p => ({ id: p.id })),
        restrictedCategories: _selectedCouponCategories.map(c => ({ id: c.id }))
    };

    fetch('/api/coupons', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(async r => {
        if (r.ok) {
            if (couponModal) couponModal.hide();
            showToast(id ? 'Cupón actualizado' : 'Cupón creado');
            setTimeout(() => location.reload(), 1000);
        } else {
            const data = await r.json().catch(() => ({}));
            const msg = data.error || data.message || 'Error al guardar el cupón';
            showToast(msg, 'error');
        }
    }).catch(err => {
        console.error("Coupon save error:", err);
        showToast('Error de red al conectar con el servidor', 'error');
    });
}

function deleteCoupon(id) {
    if (!confirm('¿Seguro que quieres eliminar este cupón?')) return;
    fetch('/api/coupons/' + id, { method: 'DELETE' }).then(r => {
        if (r.ok) {
            showToast('Cupón eliminado');
            setTimeout(() => location.reload(), 1000);
        } else {
            showToast('Error al eliminar', 'error');
        }
    }).catch(() => showToast('Error de red', 'error'));
}

function openCashRegisterDetail(id) {
    window.location.href = '/admin/cash-register/' + id;
}

// -- Price Matrix Management --------------------------------------------------

let priceMatrixChanges = {}; // Key: "prodId-tariffId", Value: {productId, tariffId, newPrice, originalPrice}
let applyingPricesModal = null;
let priceChangesHistoryModalId = null;

function toggleTariffEditMode() {
    const table = document.getElementById('tariffComparisonTable');
    if (!table) return;

    const btnToggle = document.getElementById('btnToggleTariffEdit');
    const btnFinish = document.getElementById('btnFinishTariffEdit');
    const isEditing = btnToggle.classList.contains('active');

    if (!isEditing) {
        // Enter Edit Mode
        btnToggle.classList.add('active');
        btnToggle.innerHTML = '<i class="bi bi-x-circle me-1"></i> Cancelar Edición';
        btnFinish.style.display = 'inline-block';

        // Transform cells into inputs
        const cells = table.querySelectorAll('.price-cell, .price-value');
        cells.forEach(el => {
            const prodId = el.getAttribute('data-product-id');
            const tariffId = el.getAttribute('data-tariff-id') || 'base';
            const currentText = el.textContent.replace(' €', '').replace(',', '.').trim();
            const currentValue = parseFloat(currentText) || 0;

            const input = document.createElement('input');
            input.type = 'number';
            input.step = '0.0001';
            input.className = 'form-control form-control-sm text-end';
            input.style = 'width: 100px; display: inline-block; font-family: inherit; font-weight: 700;';
            input.value = currentValue;
            input.dataset.originalValue = currentValue;
            input.dataset.productId = prodId;
            input.dataset.tariffId = tariffId;
            if (el.getAttribute('data-tariff-name')) input.dataset.tariffName = el.getAttribute('data-tariff-name');

            input.onchange = function() {
                const newVal = parseFloat(this.value);
                const oldVal = parseFloat(this.dataset.originalValue);
                const key = `${this.dataset.productId}-${this.dataset.tariffId}`;

                if (newVal !== oldVal) {
                    this.style.backgroundColor = 'rgba(245, 166, 35, 0.2)';
                    this.style.borderColor = 'var(--accent)';
                    
                    priceMatrixChanges[key] = {
                        productId: parseInt(this.dataset.productId),
                        tariffId: this.dataset.tariffId === 'base' ? null : parseInt(this.dataset.tariffId),
                        tariffName: this.dataset.tariffName || 'PVP Base',
                        newPrice: newVal,
                        originalPrice: oldVal,
                        productName: this.closest('tr').querySelector('td:first-child .fw-bold').textContent
                    };
                } else {
                    this.style.backgroundColor = '';
                    this.style.borderColor = '';
                    delete priceMatrixChanges[key];
                }
            };

            el.innerHTML = '';
            el.appendChild(input);
        });
    } else {
        // Exit / Cancel Edit Mode
        if (Object.keys(priceMatrixChanges).length > 0) {
            if (!confirm('¿Seguro que quieres cancelar? Perderás los cambios no guardados.')) return;
        }
        location.reload(); // Hard reset for simplicity
    }
}

function openApplyPricesModal() {
    const changes = Object.values(priceMatrixChanges);
    if (changes.length === 0) {
        showToast('No hay cambios que aplicar', 'warning');
        return;
    }

    const modalEl = document.getElementById('applyPricesModal');
    if (!applyingPricesModal) applyingPricesModal = new bootstrap.Modal(modalEl);

    document.getElementById('pendingChangesCount').textContent = changes.length;
    
    const list = document.getElementById('pendingChangesList');
    list.innerHTML = '';
    changes.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="text-white">${c.productName}</td>
            <td><span class="badge bg-secondary">${c.tariffName}</span></td>
            <td class="text-end fw-bold text-accent">${formatDecimal(c.newPrice, 2, 4)} €</td>
        `;
        list.appendChild(tr);
    });

    applyingPricesModal.show();
}

function submitBulkPriceUpdate() {
    const effectiveDate = document.getElementById('applyPricesDate').value;
    const effectiveTime = document.getElementById('applyPricesTime').value;
    
    if (!effectiveDate) {
        showToast('Debes seleccionar una fecha', 'warning');
        return;
    }

    const payload = {
        effectiveDate: effectiveDate + 'T' + (effectiveTime || '00:00') + ':00',
        changes: Object.values(priceMatrixChanges).map(c => ({
            productId: c.productId,
            tariffId: c.tariffId,
            newPrice: c.newPrice
        }))
    };

    fetch('/api/admin/bulk-price-update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => {
        if (!res.ok) throw new Error('Error en la respuesta del servidor');
        return res.json();
    })
    .then(data => {
        showToast('Cambios programados correctamente', 'success');
        if (applyingPricesModal) applyingPricesModal.hide();
        setTimeout(() => location.reload(), 1500);
    })
    .catch(err => {
        console.error(err);
        showToast('Error al guardar los cambios', 'error');
    });
}

function openPriceChangesHistoryModal() {
    const modalEl = document.getElementById('priceChangesHistoryModal');
    const modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl);
    
    loadPendingPriceChanges();
    loadPastPriceChanges();
    
    modalInstance.show();
}

function loadPendingPriceChanges() {
    fetch('/api/admin/price-updates/pending')
        .then(res => res.json())
        .then(data => {
            const body = document.querySelector('#tablePendingPrices tbody');
            if(!body) return;
            body.innerHTML = '';
            
            if (data.length === 0) {
                body.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No hay cambios pendientes</td></tr>';
                return;
            }

            data.forEach(item => {
                const tr = document.createElement('tr');
                const date = new Date(item.startDate).toLocaleString();
                tr.innerHTML = `
                    <td class="text-white">${date}</td>
                    <td class="text-white">${item.productName}</td>
                    <td><span class="badge bg-secondary">${item.tariffName || 'PVP Base'}</span></td>
                    <td class="text-end fw-bold text-accent">${formatDecimal(item.price || item.newPrice, 2, 4)} €</td>
                    <td class="text-center">
                        <button class="btn btn-sm btn-outline-danger" onclick="deletePendingPrice(${item.id})">
                            <i class="bi bi-trash"></i>
                        </button>
                    </td>
                `;
                body.appendChild(tr);
            });
        });
}

function loadPastPriceChanges() {
    fetch('/api/admin/price-updates/history')
        .then(res => res.json())
        .then(data => {
            const body = document.querySelector('#tablePastPrices tbody');
            if(!body) return;
            body.innerHTML = '';
            
            if (data.length === 0) {
                body.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No hay historial reciente</td></tr>';
                return;
            }

            data.forEach(item => {
                const tr = document.createElement('tr');
                const date = new Date(item.createdAt).toLocaleString();
                tr.innerHTML = `
                    <td class="text-white">${date}</td>
                    <td class="text-white">${item.productName}</td>
                    <td><span class="badge bg-secondary">${item.tariffName || 'PVP Base'}</span></td>
                    <td class="text-end text-muted">${formatDecimal(item.oldPrice, 2, 4)} €</td>
                    <td class="text-end fw-bold text-success">${formatDecimal(item.newPrice, 2, 4)} €</td>
                `;
                body.appendChild(tr);
            });
        });
}

function deletePendingPrice(id) {
    if (!confirm('¿Seguro que quieres cancelar este cambio de precio programado?')) return;

    fetch(`/api/admin/price-updates/${id}`, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Cambio cancelado', 'success');
                loadPendingPriceChanges();
            } else {
                showToast('Error al cancelar', 'error');
            }
        });
}

function formatDecimal(val, minFrac = 2, maxFrac = 4) {
    if (val === null || val === undefined) return '0,00';
    return new Intl.NumberFormat('es-ES', {
        minimumFractionDigits: minFrac,
        maximumFractionDigits: maxFrac
    }).format(parseFloat(val));
}

// Expose to global scope
window.switchView = switchView;
window.showToast = showToast;
window.previewImage = previewImage;
window.openProductModal = openProductModal;
window.saveProduct = saveProduct;
window.deleteProduct = deleteProduct;
window.openCategoryModal = openCategoryModal;
window.saveCategory = saveCategory;
window.deleteCategory = deleteCategory;
window.uploadCsvFile = uploadCsvFile;
window.uploadCustomersCsvFile = uploadCustomersCsvFile;
window.loadActivityLog = loadActivityLog;
window.onAnalyticsPeriodChange = onAnalyticsPeriodChange;
window.updateAnalytics = updateAnalytics;
window.openWorkerModal = openWorkerModal;
window.saveWorker = saveWorker;
window.deleteWorker = deleteWorker;
window.openCustomerModal = openCustomerModal;
window.saveCustomer = saveCustomer;
window.deleteCustomer = deleteCustomer;
window.openCustomerSalesModal = openCustomerSalesModal;
window.openSchedulePriceModal = openSchedulePriceModal;
window.saveScheduledPrice = saveScheduledPrice;
window.loadFuturePrices = loadFuturePrices;
window.loadPriceHistory = loadPriceHistory;
window.showPreciosTab = showPreciosTab;
window.loadBulkProducts = loadBulkProducts;
window.selectAllBulkProducts = selectAllBulkProducts;
window.updateBulkSelectedCount = updateBulkSelectedCount;
window.toggleBulkPriceFields = toggleBulkPriceFields;
window.applyBulkPriceUpdate = applyBulkPriceUpdate;
window.openRoleModal = openRoleModal;
window.saveRole = saveRole;
window.deleteRole = deleteRole;
window.filterWorkers = filterWorkers;
window.resetWorkerFilters = resetWorkerFilters;
window.filterRoles = filterRoles;
window.resetRolePermFilters = resetRolePermFilters;
window.filterProducts = filterProducts;
window.resetProductFilters = resetProductFilters;
window.fetchSalesPage = fetchSalesPage;
window.changeSalesPage = changeSalesPage;
window.jumpToSalesPage = jumpToSalesPage;
window.filterInvoices = filterInvoices;
window.resetInvoiceFilters = resetInvoiceFilters;
window.filterCashClosures = filterCashClosures;
window.resetCashFilters = resetCashFilters;
window.filterCRM = filterCRM;
window.resetCRMFilters = resetCRMFilters;
window.filterActivity = filterActivity;
window.resetActivityFilters = resetActivityFilters;
window.resetCategoryFilters = resetCategoryFilters;
window.filterFuturePrices = filterFuturePrices;
window.resetFuturePriceFilters = resetFuturePriceFilters;
window.filterBulkProductList = filterBulkProductList;
window.selectBulkByCategory = selectBulkByCategory;
window.filterReturns = filterReturns;
window.resetReturnFilters = resetReturnFilters;
window.cancelSale = cancelSale;
window.loadMailSettings = loadMailSettings;
window.saveMailSettings = saveMailSettings;
window.openIpcUpdateModal = openIpcUpdateModal;
window.applyIpcConfirm = applyIpcConfirm;
window.togglePassword = togglePassword;
window.filterTariffComparison = filterTariffComparison;
window.searchCouponProducts = searchCouponProducts;
window.searchCouponCategories = searchCouponCategories;
window.addCouponProduct = addCouponProduct;
window.addCouponCategory = addCouponCategory;
window.removeCouponProduct = removeCouponProduct;
window.removeCouponCategory = removeCouponCategory;
window.openCouponModal = openCouponModal;
window.saveCoupon = saveCoupon;
window.deleteCoupon = deleteCoupon;
window.openCashRegisterDetail = openCashRegisterDetail;

window.toggleTariffEditMode = toggleTariffEditMode;
window.openApplyPricesModal = openApplyPricesModal;
window.openPriceChangesHistoryModal = openPriceChangesHistoryModal;
window.submitBulkPriceUpdate = submitBulkPriceUpdate;
window.deletePendingPrice = deletePendingPrice;

// -- Promotion Management -----------------------------------------------------
var _selectedPromoProducts = [];
var _selectedPromoCategories = [];
var _promoProductsCache = null;   // Local cache for products
var _promoCategoriesCache = null; // Local cache for categories

function searchPromoProducts(query, initial = false) {
    const resultsDiv = document.getElementById('promoProductSearchResults');
    if (!resultsDiv) return;

    if (!_promoProductsCache) {
        fetch('/api/products/selection-list').then(r => r.json()).then(data => {
            _promoProductsCache = data;
            searchPromoProducts(query, initial);
        });
        return;
    }

    let filtered = _promoProductsCache;
    if (query && query.length >= 1) {
        const q = query.toLowerCase();
        filtered = _promoProductsCache.filter(p => 
            p.name.toLowerCase().includes(q) || 
            (p.categoryName && p.categoryName.toLowerCase().includes(q))
        );
    }

    if (filtered.length === 0) {
        resultsDiv.style.display = 'none';
        return;
    }

    resultsDiv.innerHTML = filtered.slice(0, 50).map(p => `
        <button type="button" class="list-group-item list-group-item-action py-2" 
            style="background: transparent; color: var(--text-main); border: none;"
            onclick="addPromoProduct(${p.id}, '${escHtml(p.name)}')">
            <div class="d-flex justify-content-between align-items-center">
                <span style="color:var(--text-main); font-size: 0.82rem; font-weight: 500;">${escHtml(p.name)}</span>
                <small class="text-muted" style="font-size: 0.65rem;">${escHtml(p.categoryName || '')} • ${(p.currentPrice || 0).toFixed(2)}€</small>
            </div>
        </button>
    `).join('');
    resultsDiv.style.display = 'block';
}

function searchPromoCategories(query, initial = false) {
    const resultsDiv = document.getElementById('promoCategorySearchResults');
    if (!resultsDiv) return;

    if (!_promoCategoriesCache) {
        fetch('/api/categories').then(r => r.json()).then(data => {
            _promoCategoriesCache = data;
            searchPromoCategories(query, initial);
        });
        return;
    }

    let filtered = _promoCategoriesCache;
    if (query && query.length >= 1) {
        const q = query.toLowerCase();
        filtered = _promoCategoriesCache.filter(c => c.name.toLowerCase().includes(q));
    }

    if (filtered.length === 0) {
        resultsDiv.style.display = 'none';
        return;
    }

    resultsDiv.innerHTML = filtered.map(c => `
        <button type="button" class="list-group-item list-group-item-action py-2" 
            style="background: transparent; color: var(--text-main); border: none;"
            onclick="addPromoCategory(${c.id}, '${escHtml(c.name)}')">
            <div class="d-flex align-items-center justify-content-between">
                <span><i class="bi bi-tag me-2 text-accent"></i><span style="color:var(--text-main); font-size: 0.85rem;">${escHtml(c.name)}</span></span>
                <small class="text-muted" style="font-size: 0.7rem;">ID: ${c.id}</small>
            </div>
        </button>
    `).join('');
    resultsDiv.style.display = 'block';
}

function addPromoProduct(id, name) {
    if (_selectedPromoProducts.some(p => p.id === id)) {
        document.getElementById('promoProductSearchResults').style.display = 'none';
        document.getElementById('promoProductSearch').value = '';
        return;
    }
    _selectedPromoProducts.push({ id, name });
    renderSelectedPromoProducts();
    document.getElementById('promoProductSearchResults').style.display = 'none';
    document.getElementById('promoProductSearch').value = '';
}

function addPromoCategory(id, name) {
    if (_selectedPromoCategories.some(c => c.id === id)) {
        document.getElementById('promoCategorySearchResults').style.display = 'none';
        document.getElementById('promoCategorySearch').value = '';
        return;
    }
    _selectedPromoCategories.push({ id, name });
    renderSelectedPromoCategories();
    document.getElementById('promoCategorySearchResults').style.display = 'none';
    document.getElementById('promoCategorySearch').value = '';
}

function removePromoProduct(id) {
    _selectedPromoProducts = _selectedPromoProducts.filter(p => p.id !== id);
    renderSelectedPromoProducts();
}

function removePromoCategory(id) {
    _selectedPromoCategories = _selectedPromoCategories.filter(c => c.id !== id);
    renderSelectedPromoCategories();
}

function renderSelectedPromoProducts() {
    const container = document.getElementById('selectedPromoProducts');
    if (!container) return;
    container.innerHTML = _selectedPromoProducts.map(p => `
        <div class="badge d-flex align-items-center gap-2 p-2" 
            style="background: rgba(var(--accent-rgb), 0.1); color: var(--accent); border: 1px solid var(--accent); border-radius: 8px; font-weight: 500;">
            <span>${escHtml(p.name)}</span>
            <i class="bi bi-x-lg cursor-pointer" onclick="removePromoProduct(${p.id})" style="font-size: 0.7rem; opacity: 0.8;"></i>
        </div>
    `).join('');
}

function renderSelectedPromoCategories() {
    const container = document.getElementById('selectedPromoCategories');
    if (!container) return;
    container.innerHTML = _selectedPromoCategories.map(c => `
        <div class="badge d-flex align-items-center gap-2 p-2" 
            style="background: rgba(var(--primary-rgb), 0.1); color: var(--text-main); border: 1px solid var(--border); border-radius: 8px; font-weight: 500;">
            <i class="bi bi-tag-fill me-1" style="color: var(--accent);"></i>
            <span>${escHtml(c.name)}</span>
            <i class="bi bi-x-lg cursor-pointer" onclick="removePromoCategory(${c.id})" style="font-size: 0.7rem; opacity: 0.8;"></i>
        </div>
    `).join('');
}

function openPromotionModal(btn) {
    const promoIdField = document.getElementById('promoId');
    if (!promoIdField) return;

    promoIdField.value = '';
    document.getElementById('promoName').value = '';
    document.getElementById('promoNValue').value = '3';
    document.getElementById('promoMValue').value = '2';
    document.getElementById('promoActive').checked = true;
    document.getElementById('promoFrom').value = '';
    document.getElementById('promoUntil').value = '';
    
    _selectedPromoProducts = [];
    _selectedPromoCategories = [];
    renderSelectedPromoProducts();
    renderSelectedPromoCategories();
    if (document.getElementById('promoProductSearchResults')) document.getElementById('promoProductSearchResults').style.display = 'none';
    if (document.getElementById('promoCategorySearchResults')) document.getElementById('promoCategorySearchResults').style.display = 'none';

    if (btn) {
        const id = btn.dataset.id;
        promoIdField.value = id;
        document.getElementById('promoName').value = btn.dataset.name;
        document.getElementById('promoNValue').value = btn.dataset.nvalue;
        document.getElementById('promoMValue').value = btn.dataset.mvalue;
        document.getElementById('promoActive').checked = btn.dataset.active === 'true';
        if (btn.dataset.from) document.getElementById('promoFrom').value = btn.dataset.from.substring(0, 16);
        if (btn.dataset.until) document.getElementById('promoUntil').value = btn.dataset.until.substring(0, 16);
        
        fetch('/api/promotions/' + id)
            .then(r => r.json())
            .then(promo => {
                if (promo.restrictedProducts) {
                    _selectedPromoProducts = promo.restrictedProducts.map(p => ({ id: p.id, name: p.nameEs || p.name }));
                    renderSelectedPromoProducts();
                }
                if (promo.restrictedCategories) {
                    _selectedPromoCategories = promo.restrictedCategories.map(c => ({ id: c.id, name: c.name }));
                    renderSelectedPromoCategories();
                }
            }).catch(e => console.error("Error loading promo details", e));
    }
    
    if (promotionModal) promotionModal.show();
}

function savePromotion() {
    const id = document.getElementById('promoId').value;
    const name = document.getElementById('promoName').value.trim();
    if (!name) { showToast('El nombre es obligatorio', 'error'); return; }

    const body = {
        id: id ? parseInt(id) : null,
        name: name,
        nValue: parseInt(document.getElementById('promoNValue').value),
        mValue: parseInt(document.getElementById('promoMValue').value),
        active: document.getElementById('promoActive').checked,
        validFrom: document.getElementById('promoFrom').value || null,
        validUntil: document.getElementById('promoUntil').value || null,
        restrictedProducts: _selectedPromoProducts.map(p => ({ id: p.id })),
        restrictedCategories: _selectedPromoCategories.map(c => ({ id: c.id }))
    };

    fetch('/api/promotions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(async r => {
        if (r.ok) {
            if (promotionModal) promotionModal.hide();
            showToast(id ? (window.adminI18n.promoSaved || 'Promoción actualizada') : (window.adminI18n.promoSaved || 'Promoción creada'));
            setTimeout(() => location.reload(), 1000);
        } else {
            const data = await r.json().catch(() => ({}));
            showToast(data.error || 'Error al guardar', 'error');
        }
    }).catch(() => showToast('Error de red', 'error'));
}

function deletePromotion(id) {
    if (!confirm('¿Seguro que quieres eliminar esta promoción?')) return;
    fetch('/api/promotions/' + id, { method: 'DELETE' }).then(r => {
        if (r.ok) {
            showToast('Promoción eliminada');
            setTimeout(() => location.reload(), 1000);
        } else {
            showToast('Error al eliminar', 'error');
        }
    }).catch(() => showToast('Error de red', 'error'));
}

window.searchPromoProducts = searchPromoProducts;
window.searchPromoCategories = searchPromoCategories;
window.addPromoProduct = addPromoProduct;
window.addPromoCategory = addPromoCategory;
window.removePromoProduct = removePromoProduct;
window.removePromoCategory = removePromoCategory;
window.openPromotionModal = openPromotionModal;
window.savePromotion = savePromotion;
window.deletePromotion = deletePromotion;

// Dismiss search results when clicking outside
document.addEventListener('click', function(e) {
    const prodSearch = document.getElementById('promoProductSearch');
    const prodResults = document.getElementById('promoProductSearchResults');
    if (prodResults && prodSearch && !prodResults.contains(e.target) && e.target !== prodSearch) {
        prodResults.style.display = 'none';
    }
    
    const catSearch = document.getElementById('promoCategorySearch');
    const catResults = document.getElementById('promoCategorySearchResults');
    if (catResults && catSearch && !catResults.contains(e.target) && e.target !== catSearch) {
        catResults.style.display = 'none';
    }
});


// -- Measurement Unit CRUD ------------------------------------------------
function loadMeasurementUnits() {
    const tbody = document.getElementById('measurementUnitsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="6" class="text-center"><div class="spinner-border spinner-border-sm text-accent"></div></td></tr>';

    fetch('/api/measurement-units')
        .then(r => r.json())
        .then(units => {
            tbody.innerHTML = '';
            units.sort((a,b) => a.name.localeCompare(b.name)).forEach(u => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${escHtml(u.name)}</strong></td>
                    <td><span class="badge bg-secondary text-main">${escHtml(u.symbol)}</span></td>
                    <td class="text-center">${u.decimalPlaces}</td>
                    <td class="text-center">
                        <span class="badge-active ${u.promptOnAdd ? 'yes' : 'no'}">${u.promptOnAdd ? 'Sí' : 'No'}</span>
                    </td>
                    <td class="text-center">
                        <span class="badge-active ${u.active ? 'yes' : 'no'}">${u.active ? 'Sí' : 'No'}</span>
                    </td>
                    <td style="text-align:right">
                        <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                            <button class="btn-icon" title="Editar" onclick="openMeasurementUnitModal(${u.id})">
                                <i class="bi bi-pencil"></i>
                            </button>
                            <button class="btn-icon danger" title="Eliminar" onclick="deleteMeasurementUnit(${u.id}, '${escHtml(u.name)}')">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(() => {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger">Error al cargar unidades</td></tr>';
        });
}

function openMeasurementUnitModal(id) {
    const form = document.getElementById('measurementUnitForm');
    form.reset();
    document.getElementById('measurementUnitId').value = id || '';
    document.getElementById('muActive').checked = true;
    document.getElementById('measurementUnitModalLabel').textContent = id ? 'Editar Unidad de Medida' : 'Nueva Unidad de Medida';

    if (id) {
        fetch('/api/measurement-units/' + id)
            .then(r => r.json())
            .then(u => {
                document.getElementById('muName').value = u.name;
                document.getElementById('muSymbol').value = u.symbol;
                document.getElementById('muDecimals').value = u.decimalPlaces;
                document.getElementById('muPrompt').checked = u.promptOnAdd;
                document.getElementById('muActive').checked = u.active;
            });
    }
    measurementUnitModal.show();
}

function saveMeasurementUnit() {
    const id = document.getElementById('measurementUnitId').value;
    const unit = {
        name: document.getElementById('muName').value.trim(),
        symbol: document.getElementById('muSymbol').value.trim(),
        decimalPlaces: parseInt(document.getElementById('muDecimals').value) || 0,
        promptOnAdd: document.getElementById('muPrompt').checked,
        active: document.getElementById('muActive').checked
    };

    if (!unit.name || !unit.symbol) {
        showToast('Nombre y símbolo son obligatorios', 'error');
        return;
    }

    const url = '/api/measurement-units' + (id ? '/' + id : '');
    const method = id ? 'PUT' : 'POST';

    fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(unit)
    })
    .then(r => {
        if (!r.ok) throw new Error();
        measurementUnitModal.hide();
        showToast(id ? 'Unidad actualizada' : 'Unidad creada');
        loadMeasurementUnits();
    })
    .catch(() => showToast('Error al guardar unidad', 'error'));
}

function deleteMeasurementUnit(id, name) {
    if (!confirm(`¿Seguro que quieres eliminar definitivamente la unidad "${name}"?`)) return;
    fetch(`/api/measurement-units/${id}`, { method: 'DELETE' })
        .then(r => {
            if (r.ok) {
                showToast('Unidad eliminada');
                loadMeasurementUnits();
            } else {
                showToast('Error al eliminar (probablemente en uso)', 'error');
            }
        })
        .catch(() => showToast('Error de red', 'error'));
}

window.openMeasurementUnitModal = openMeasurementUnitModal;
window.saveMeasurementUnit = saveMeasurementUnit;
window.deleteMeasurementUnit = deleteMeasurementUnit;
window.loadMeasurementUnits = loadMeasurementUnits;

// ====== GESTIÓN DE ABONOS ======
function openAbonoModal() {
    document.getElementById('abonoForm').reset();
    if(abonoModal) abonoModal.show();
}

function saveAbono() {
    const clienteId = document.getElementById('abonoFormClienteId').value;
    const importe = document.getElementById('abonoFormImporte').value;
    const tipoAbono = document.getElementById('abonoFormTipo').value;
    if (!clienteId || !importe || !tipoAbono) {
        showToast('Cliente, Importe y Tipo son obligatorios', 'error');
        return;
    }

    const payload = {
        clienteId: parseInt(clienteId),
        ventaOriginalId: document.getElementById('abonoFormVentaId').value ? parseInt(document.getElementById('abonoFormVentaId').value) : null,
        importe: parseFloat(importe),
        tipoAbono: tipoAbono,
        metodoPago: document.getElementById('abonoFormPago').value,
        motivo: document.getElementById('abonoFormMotivo').value
    };

    fetch('/api/abonos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => {
        if (!res.ok) return res.text().then(t => { throw new Error(t) });
        return res.json();
    })
    .then(() => {
        showToast('Abono creado correctamente');
        if(abonoModal) abonoModal.hide();
        document.getElementById('abonoClienteSearch').value = clienteId;
        filterAbonos();
    })
    .catch(err => {
        showToast(err.message || 'Error al guardar el abono', 'error');
    });
}

function filterAbonos() {
    const clienteIdStr = document.getElementById('abonoClienteSearch').value.trim();
    if (!clienteIdStr) {
        document.getElementById('abonosTableBody').innerHTML = '<tr><td colspan="8" class="text-center text-muted">Introduce un ID de Cliente para buscar</td></tr>';
        return;
    }

    const clienteId = parseInt(clienteIdStr);
    if(isNaN(clienteId)) return;
    
    fetch('/api/abonos/cliente/' + clienteId)
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById('abonosTableBody');
            tbody.innerHTML = '';

            if (data.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No se encontraron abonos para este cliente</td></tr>';
                return;
            }

            data.forEach(abono => {
                const tr = document.createElement('tr');
                const badgeClass = abono.estado === 'PENDIENTE' ? 'bg-warning' : (abono.estado === 'ANULADO' ? 'bg-danger' : 'bg-success');
                const isoDate = new Date(abono.fecha).toLocaleString('es-ES');
                
                tr.innerHTML = `
                    <td><strong>${abono.id}</strong></td>
                    <td class="small text-muted">${isoDate}</td>
                    <td>Cliente: <strong>${clienteId}</strong> ${abono.ventaOriginalId ? '(Vta: ' + abono.ventaOriginalId + ')' : ''}</td>
                    <td><span class="badge bg-secondary">${abono.tipoAbono}</span></td>
                    <td>${abono.metodoPago}</td>
                    <td class="text-end fw-bold ${abono.importe < 0 ? 'text-danger' : 'text-success'}">${abono.importe.toFixed(2)} &euro;</td>
                    <td><span class="badge ${badgeClass}">${abono.estado}</span></td>
                    <td class="text-end">
                        ${abono.estado === 'PENDIENTE' ? `<button class="btn btn-sm btn-outline-danger" onclick="anularAbono(${abono.id})"><i class="bi bi-x-circle"></i></button>` : ''}
                    </td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => {
            console.error(err);
            document.getElementById('abonosTableBody').innerHTML = '<tr><td colspan="8" class="text-center text-danger">Error al cargar abonos</td></tr>';
        });
}

function anularAbono(abonoId) {
    if(!confirm('¿Estás seguro de que quieres anular este abono?')) return;
    
    fetch('/api/abonos/' + abonoId + '/anular', {
        method: 'PATCH'
    })
    .then(r => {
        if (!r.ok) return r.text().then(t => { throw new Error(t) });
        return r.text();
    })
    .then(() => {
        showToast('Abono anulado con éxito');
        filterAbonos();
    })
    .catch(err => {
        showToast(err.message || 'Error al anular', 'error');
    });
}

window.openAbonoModal = openAbonoModal;
window.saveAbono = saveAbono;
window.filterAbonos = filterAbonos;
window.anularAbono = anularAbono;
