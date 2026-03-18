document.addEventListener('DOMContentLoaded', function () {
    if (typeof attachNifCifValidator === 'function') {
        attachNifCifValidator('customerTaxId');
    }
});

var productModal = new bootstrap.Modal(document.getElementById('productModal'));
var categoryModal = new bootstrap.Modal(document.getElementById('categoryModal'));
var workerModal = new bootstrap.Modal(document.getElementById('workerModal'));
var customerModal = new bootstrap.Modal(document.getElementById('customerModal'));
var roleModal = new bootstrap.Modal(document.getElementById('roleModal'));

// Cache for roles
var rolesCache = null;

// -- View Switching --------------------------------------------------------
function switchView(viewId, btnElement) {
    // Hide all views
    const views = [
        'dashboardView', 'productsView', 'invoicesView', 'cashCloseView',
        'returnsHistoryView', 'settingsView', 'workersView', 'rolesView', 'analyticsView',
        'crmView', 'preciosTempView', 'preciosMasivosView', 'activityView', 'tarifasView',
        'tiposIvaView'
    ];
    views.forEach(v => {
        const el = document.getElementById(v);
        if (el) el.style.display = 'none';
    });

    // Show selected view
    const target = document.getElementById(viewId);
    if (target) target.style.display = 'block';

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
    }
}

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
    document.getElementById('productImageUrl').value = '';
    document.getElementById('productActive').checked = true;
    document.getElementById('productModalLabel').textContent = id ? 'Editar Producto' : 'Nuevo Producto';

    previewImage(null); // Set placeholder by default

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
        categoryId: catId ? parseInt(catId) : null
    };

    var method = id ? 'PUT' : 'POST';
    var url = id ? '/api/products/' + id : '/api/products';

    fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
        .then(function (r) {
            if (!r.ok) {
                r.json().then(function(err) {
                    showToast('Error al guardar: ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function() {
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

// -- Activity Log --------------------------------------------------------
function loadActivityLog() {
    var container = document.getElementById('activityFeedContainer');
    if (!container) return;

    container.innerHTML = '<div class="text-center p-4"><div class="spinner-border text-primary" role="status"><span class="visually-hidden">Cargando...</span></div></div>';

    fetch('/api/activity-log/recent')
        .then(function (res) {
            if (!res.ok) throw new Error('Error de red');
            return res.json();
        })
        .then(function (logs) {
            if (logs.length === 0) {
                container.innerHTML = '<div class="text-center p-4" style="color: var(--text-muted);">No hay actividad reciente.</div>';
                return;
            }

            var html = '';
            logs.forEach(function (log) {
                var iconClasses = 'bi-info-circle text-info';

                // Determinar el ícono y color basado en la acción
                if (log.action === 'VENTA') iconClasses = 'bi-cart-check text-primary';
                else if (log.action.includes('CREAR')) iconClasses = 'bi-plus-circle text-success';
                else if (log.action.includes('CIERRE') || log.action.includes('APERTURA')) iconClasses = 'bi-cash-stack text-info';
                else if (log.action.includes('ACTUALIZAR')) iconClasses = 'bi-pencil text-warning';
                else if (log.action.includes('ELIMINAR')) iconClasses = 'bi-trash text-danger';

                var formattedDate = log.timestamp ? formatTimeAgo(log.timestamp) : '';
                var logShortDate = log.timestamp ? log.timestamp.split('T')[0] : '';

                html += '<div class="activity-item" data-user="' + escHtml(log.username || '') + '" data-action="' + escHtml(log.action) + '" data-date="' + logShortDate + '">' +
                    '<div class="activity-icon"><i class="bi ' + iconClasses + '"></i></div>' +
                    '<div class="activity-content">' +
                    '<div class="activity-text">' + escHtml(log.description) + '</div>' +
                    '<div class="activity-time">' + formattedDate + '</div>' +
                    '</div>' +
                    '</div>';
            });
            container.innerHTML = html;
        })
        .catch(function (e) {
            container.innerHTML = '<div class="text-center p-4 text-danger">Error al cargar la actividad.</div>';
            console.error(e);
        });
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

function updateAnalytics() {
    const periodSelect = document.getElementById('analyticsPeriod');
    const period = periodSelect ? periodSelect.value : '7days';
    const now = new Date();
    let fromDate = new Date();
    let chartTitle = 'Ventas Últimos 7 Días';
    let fetchAll = false;

    if (period === 'today') {
        fromDate.setHours(0, 0, 0, 0);
        chartTitle = 'Ventas Hoy';
    } else if (period === '7days') {
        fromDate.setDate(now.getDate() - 7);
        chartTitle = 'Ventas Últimos 7 Días';
    } else if (period === '1month') {
        fromDate.setMonth(now.getMonth() - 1);
        chartTitle = 'Ventas Último Mes';
    } else if (period === '6months') {
        fromDate.setMonth(now.getMonth() - 6);
        chartTitle = 'Ventas Últimos 6 Meses';
    } else if (period === '1year') {
        fromDate.setFullYear(now.getFullYear() - 1);
        chartTitle = 'Ventas Último Año';
    } else if (period === 'all') {
        fetchAll = true;
        chartTitle = 'Ventas Histórico Total';
    }

    const url = fetchAll ? '/api/sales' : `/api/sales/range?from=${fromDate.toISOString().slice(0, 19)}&to=${now.toISOString().slice(0, 19)}`;

    Promise.all([
        fetch(url).then(r => { if (!r.ok) throw new Error('Error al obtener ventas: ' + r.status); return r.json(); }),
        fetch('/api/products').then(r => { if (!r.ok) throw new Error('Error al obtener productos: ' + r.status); return r.json(); })
    ]).then(([sales, products]) => {
        initCharts(sales, products, period, chartTitle);
    }).catch(err => {
        console.error('Error updating analytics:', err);
        showToast('Error al cargar datos de análisis', 'error');
    });
}

function initCharts(salesDataRaw, productsDataRaw, period = '7days', chartLabel = 'Ventas (\u20AC)') {
    if (!salesDataRaw) {
        // Fallback for initial load if data is still injected
        salesDataRaw = [];
        productsDataRaw = [];
    }

    var now = new Date();

    // 1. Stats Calculation
    let totalRevenue = 0;
    let totalSalesCount = 0;
    const productSalesCount = {};
    let lowStockCount = 0;

    salesDataRaw.forEach(function (sale) {
        totalRevenue += sale.totalAmount || 0;
        totalSalesCount++;
        if (sale.lines) {
            sale.lines.forEach(function (line) {
                var pName = (line.product && line.product.name) ? line.product.name : 'Producto';
                productSalesCount[pName] = (productSalesCount[pName] || 0) + (line.quantity || 0);
            });
        }
    });

    productsDataRaw.forEach(function (p) {
        if (p.stock < 5) lowStockCount++;
    });

    let topP = '—';
    let maxQty = 0;
    for (const [name, qty] of Object.entries(productSalesCount)) {
        if (qty > maxQty) {
            maxQty = qty;
            topP = name;
        }
    }

    // Update DOM Stats
    if (document.getElementById('statTodayRevenue')) document.getElementById('statTodayRevenue').textContent = totalRevenue.toFixed(2) + ' \u20AC';
    if (document.getElementById('statTodaySales')) document.getElementById('statTodaySales').textContent = totalSalesCount;
    if (document.getElementById('statTopProduct')) document.getElementById('statTopProduct').textContent = topP.length > 20 ? topP.substring(0, 20) + '...' : topP;
    if (document.getElementById('statLowStock')) document.getElementById('statLowStock').textContent = lowStockCount;

    const labelSuffix = (period === 'today') ? ' Hoy' : '';
    if (document.getElementById('statRevenueLabel')) document.getElementById('statRevenueLabel').textContent = 'Ventas' + labelSuffix;
    if (document.getElementById('statSalesLabel')) document.getElementById('statSalesLabel').textContent = 'Pedidos' + labelSuffix;

    // 2. Trend Chart
    let labels = [];
    let datasetsData = [];

    if (period === 'today') {
        for (let i = 0; i < 24; i++) {
            labels.push(i + ':00');
            let sum = salesDataRaw.reduce((acc, s) => {
                let h = new Date(s.createdAt).getHours();
                return h === i ? acc + (s.totalAmount || 0) : acc;
            }, 0);
            datasetsData.push(sum);
        }
    } else if (period === '7days' || period === '1month') {
        let daysToTrack = period === '7days' ? 7 : 30;
        for (let i = daysToTrack - 1; i >= 0; i--) {
            let d = new Date();
            d.setDate(d.getDate() - i);
            let dStr = d.toISOString().split('T')[0];
            labels.push(d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' }));
            let total = salesDataRaw.reduce((sum, s) => {
                if (!s.createdAt) return sum;
                try {
                    // Safe date parsing and comparison
                    let sDateIso = new Date(s.createdAt).toISOString().split('T')[0];
                    return (sDateIso === dStr) ? sum + (s.totalAmount || 0) : sum;
                } catch (e) { return sum; }
            }, 0);
            datasetsData.push(total);
        }
    } else {
        let monthsToTrack = (period === 'all') ? 12 : (period === '1year' ? 12 : 6);
        for (let i = monthsToTrack - 1; i >= 0; i--) {
            let d = new Date();
            d.setMonth(d.getMonth() - i);
            labels.push(d.toLocaleDateString('es-ES', { month: 'short', year: '2-digit' }));
            let total = salesDataRaw.reduce((sum, s) => {
                if (!s.createdAt) return sum;
                try {
                    let sDate = new Date(s.createdAt);
                    return (sDate.getMonth() === d.getMonth() && sDate.getFullYear() === d.getFullYear()) ? sum + (s.totalAmount || 0) : sum;
                } catch (e) { return sum; }
            }, 0);
            datasetsData.push(total);
        }
    }

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

    // 3. Category Distribution Chart
    var catSummary = {};
    productsDataRaw.forEach(p => {
        var catName = p.category ? p.category.name : 'Sin Categoría';
        catSummary[catName] = (catSummary[catName] || 0) + 1;
    });

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
            })
            .catch(function () { showToast('Error al cargar el cliente', 'error'); });
    } else {
        toggleAdminCustomerType();
    }
    customerModal.show();
}

function toggleAdminCustomerType() {
    var isCompany = document.getElementById('adminTypeCompany').checked;
    document.getElementById('customerType').value = isCompany ? 'COMPANY' : 'INDIVIDUAL';

    var reSection = document.getElementById('adminCustomerReSection');
    if (reSection) {
        reSection.style.display = isCompany ? 'block' : 'none';
        if (!isCompany) {
            document.getElementById('customerRecargoEquivalencia').checked = false;
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

    // additional validation for company
    if (body.type === 'COMPANY' && !body.taxId) {
        showToast('El CIF es obligatorio para empresas', 'error');
        return;
    }

    // Validate NIF/CIF
    var taxInput = document.getElementById('customerTaxId');
    if (taxInput && taxInput.dataset.invalidNif === 'true') {
        showToast('Por favor, introduce un NIF/CIF válido antes de continuar.', 'error');
        return;
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

var schedulePriceModal = new bootstrap.Modal(document.getElementById('schedulePriceModal'));

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
document.getElementById('spProductSelect').addEventListener('change', function () {
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
    fetch('/api/product-prices/future')
        .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
        .then(function (prices) {
            var tbody = document.getElementById('futurePricesBody');
            if (!prices || prices.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4" style="color: var(--text-muted);">No hay precios programados para el futuro.</td></tr>';
                return;
            }
            tbody.innerHTML = prices.map(function (p) {
                var vatPct = Math.round(parseFloat(p.vatRate) * 100) + '%';
                var reRate = RE_RATE_MAP[String(p.vatRate)] || '—';
                return '<tr class="future-price-row" data-product-name="' + escHtml(p.productName).toLowerCase() + '">'
                    + '<td><strong>' + escHtml(p.productName) + '</strong></td>'
                    + '<td>' + parseFloat(p.price).toFixed(2) + ' &euro;</td>'
                    + '<td>' + vatPct + ' <small style="color: var(--text-muted);">(+' + reRate + ' RE)</small></td>'
                    + '<td>' + formatDateTime(p.startDate) + '</td>'
                    + '<td>' + (p.endDate ? formatDateTime(p.endDate) : '<span style="color: var(--text-muted);">Abierto</span>') + '</td>'
                    + '<td>' + (p.label ? escHtml(p.label) : '<span style="color: var(--text-muted);">—</span>') + '</td>'
                    + '</tr>';
            }).join('');
        })
        .catch(function () {
            document.getElementById('futurePricesBody').innerHTML =
                '<tr><td colspan="6" class="text-center py-4 text-danger">Error al cargar los precios programados.</td></tr>';
        });
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
            document.getElementById('priceHistoryBody').innerHTML =
                '<tr><td colspan="7" class="text-center py-4 text-danger">Error al cargar el historial.</td></tr>';
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

function loadBulkProducts() {
    if (bulkProductsCache) return bulkProductsCache;

    fetch('/api/products')
        .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
        .then(function (products) {
            bulkProductsCache = products;
            renderBulkProductList(products);
        })
        .catch(function () {
            document.getElementById('bulkProductList').innerHTML = '<div class="text-center text-danger py-3">Error cargando productos</div>';
        });
}

function renderBulkProductList(products) {
    var container = document.getElementById('bulkProductList');
    if (!products || products.length === 0) {
        container.innerHTML = '<div class="text-center py-3" style="color: var(--text-muted);">No hay productos disponibles</div>';
        return;
    }
    container.innerHTML = products.map(function (p) {
        var catName = p.category ? p.category.name : 'S/C';
        return '<div class="form-check bulk-product-item" data-category="' + escHtml(catName).toLowerCase() + '" data-search="' + escHtml(p.name).toLowerCase() + ' ' + escHtml(catName).toLowerCase() + '">'
            + '<input class="form-check-input bulk-product-checkbox" type="checkbox" value="' + p.id + '" id="bulkProd' + p.id + '" onchange="updateBulkSelectedCount()">'
            + '<label class="form-check-label" for="bulkProd' + p.id + '" style="color: var(--text-main); cursor: pointer;">'
            + '<span style="color: var(--text-main);">' + escHtml(p.name) + '</span>'
            + ' <small style="color: var(--text-muted);">(' + catName + ' - ' + parseFloat(p.price).toFixed(2) + ' €)</small>'
            + '</label>'
            + '</div>';
    }).join('');
}

function selectAllBulkProducts(select) {
    var checkboxes = document.querySelectorAll('.bulk-product-checkbox');
    checkboxes.forEach(function (cb) { cb.checked = select; });
    updateBulkSelectedCount();
}

function updateBulkSelectedCount() {
    var checked = document.querySelectorAll('.bulk-product-checkbox:checked');
    document.getElementById('selectedCountLabel').textContent = checked.length + ' productos seleccionados';
}

function toggleBulkPriceFields() {
    var type = document.getElementById('bulkPriceType').value;
    document.getElementById('bulkPriceValueLabel').textContent = type === 'percentage' ? 'Porcentaje (%)' : 'Cantidad Fija (€)';
    document.getElementById('bulkPriceValue').placeholder = type === 'percentage' ? 'Ej: 10 para +10%' : 'Ej: 5 para +5€';
}

function applyBulkPriceUpdate() {
    var selectedIds = Array.from(document.querySelectorAll('.bulk-product-checkbox:checked')).map(function (cb) { return parseInt(cb.value); });

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
            document.getElementById('rolesTableBody').innerHTML = '<tr><td colspan="5" class="text-center text-danger">Error al cargar datos: ' + err.message + '</td></tr>';
        });
}

function renderRolesTable(roles, workers) {
    const tbody = document.getElementById('rolesTableBody');
    if (!roles || roles.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-4" style="color: var(--text-muted);">No hay roles definidos</td></tr>';
        return;
    }

    tbody.innerHTML = roles.map(r => {
        const perms = r.permissions.map(p => `<span class="badge me-1" style="font-size:0.7rem; background-color: rgba(148,163,184,0.15); color: var(--text-muted); border: 1px solid rgba(148,163,184,0.25);">${p}</span>`).join('');
        const permsText = r.permissions.join(',');

        // Count workers with this role
        const count = workers ? workers.filter(w => w.role && w.role.id === r.id).length : 0;

        return `<tr class="role-row" data-name="${escHtml(r.name)}" data-permissions="${permsText}">
            <td><strong>${escHtml(r.name)}</strong></td>
            <td style="font-size:0.85rem; color: var(--text-muted);">${escHtml(r.description || '\u2014')}</td>
            <td>${perms || '<span class="small" style="color: var(--text-muted);">Sin permisos</span>'}</td>
            <td><span class="badge" style="font-size:0.85rem; background-color: rgba(6,182,212,0.18); color: #22d3ee; border: 1px solid rgba(6,182,212,0.3);">${count} trabajador(es)</span></td>
            <td style="text-align:right">
                <button class="btn-icon" onclick="openRoleModal(${r.id})"><i class="bi bi-pencil"></i></button>
                <button class="btn-icon danger" onclick="deleteRole(${r.id})"><i class="bi bi-trash"></i></button>
            </td>
        </tr>`;
    }).join('');
}

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
                const isSpecial = p === 'ADMIN_ACCESS';
                const isChecked = role && role.permissions && role.permissions.includes(p);

                const div = document.createElement('div');
                div.className = 'form-check ' + (isSpecial ? 'mt-2 pt-2' : 'mb-2');
                if (isSpecial) div.style.borderTop = '1px solid var(--border)';

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
    const nameQuery = document.getElementById('workerFilterName').value.toLowerCase().trim();
    const roleId = document.getElementById('workerFilterRole').value;
    const status = document.getElementById('workerFilterStatus').value;

    const rows = document.querySelectorAll('.worker-row');
    let visibleCount = 0;

    rows.forEach(row => {
        const username = row.getAttribute('data-username').toLowerCase();
        const active = row.getAttribute('data-active');
        const rowRoleId = row.getAttribute('data-role-id');

        let matches = true;
        if (nameQuery && !username.includes(nameQuery)) matches = false;
        if (roleId && rowRoleId !== roleId) matches = false;
        if (status && active !== status) matches = false;

        row.style.display = matches ? '' : 'none';
        if (matches) visibleCount++;
    });

    const label = document.getElementById('workerCountLabel');
    if (nameQuery || roleId || status) {
        label.innerHTML = `Filtrado activo: <b>${visibleCount}</b> de <b>${rows.length}</b> trabajadores encontrados.`;
    } else {
        label.textContent = 'Mostrando todas las fichas de trabajadores.';
    }
}

function resetWorkerFilters() {
    document.getElementById('workerFilterName').value = '';
    document.getElementById('workerFilterRole').value = '';
    document.getElementById('workerFilterStatus').value = '';
    filterWorkers();
}

// ── ROLE FILTERING ──────────────────────────────────────────────────────────

function filterRoles() {
    const nameQuery = document.getElementById('roleFilterName').value.toLowerCase().trim();
    const selectedPerms = Array.from(document.querySelectorAll('.role-filter-perm:checked')).map(cb => cb.value);

    const rows = document.querySelectorAll('.role-row');
    rows.forEach(row => {
        const name = (row.getAttribute('data-name') || '').toLowerCase();
        const perms = (row.getAttribute('data-permissions') || '').split(',');

        let matches = true;
        if (nameQuery && !name.includes(nameQuery)) matches = false;

        if (selectedPerms.length > 0) {
            const hasAll = selectedPerms.every(p => perms.includes(p));
            if (!hasAll) matches = false;
        }

        row.style.display = matches ? '' : 'none';
    });
}

function resetRolePermFilters() {
    document.querySelectorAll('.role-filter-perm').forEach(cb => cb.checked = false);
    filterRoles();
    updateFilterPermLabel('roleFilterPermBtn', '.role-filter-perm', 'Seleccionar Permisos');
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

function filterInvoices() {
    const query = document.getElementById('invoiceFilterSearch').value.toLowerCase().trim();
    const type = document.getElementById('invoiceFilterType').value;
    const method = document.getElementById('invoiceFilterMethod').value;
    const date = document.getElementById('invoiceFilterDate').value;

    document.querySelectorAll('.invoice-row').forEach(row => {
        const id = (row.getAttribute('data-id') || '');
        const customer = (row.getAttribute('data-customer') || '').toLowerCase();
        const taxid = (row.getAttribute('data-taxid') || '').toLowerCase();
        const rowType = row.getAttribute('data-type');
        const rowMethod = row.getAttribute('data-method');
        const rowDate = row.getAttribute('data-date');

        let matches = true;
        if (query && !id.includes(query) && !customer.includes(query) && !taxid.includes(query)) matches = false;
        if (type && rowType !== type) matches = false;
        if (method && rowMethod !== method) matches = false;
        if (date && rowDate !== date) matches = false;

        row.style.display = matches ? '' : 'none';
    });
}
function resetInvoiceFilters() {
    document.getElementById('invoiceFilterSearch').value = '';
    document.getElementById('invoiceFilterType').value = '';
    document.getElementById('invoiceFilterMethod').value = '';
    document.getElementById('invoiceFilterDate').value = '';
    filterInvoices();
}

function filterCashClosures() {
    const query = document.getElementById('cashFilterWorker').value.toLowerCase().trim();
    const dateQuery = document.getElementById('cashFilterDate').value.trim();

    document.querySelectorAll('.cash-row').forEach(row => {
        const worker = (row.getAttribute('data-worker') || '').toLowerCase();
        const openDate = row.getAttribute('data-open-date');
        const closeDate = row.getAttribute('data-close-date');

        let matches = true;
        if (query && !worker.includes(query)) matches = false;
        if (dateQuery && openDate !== dateQuery && closeDate !== dateQuery) matches = false;

        row.style.display = matches ? '' : 'none';
    });
}
function resetCashFilters() {
    document.getElementById('cashFilterWorker').value = '';
    document.getElementById('cashFilterDate').value = '';
    filterCashClosures();
}

function filterCRM() {
    const query = document.getElementById('crmFilterSearch').value.toLowerCase().trim();
    const type = document.getElementById('crmFilterType').value;
    const re = document.getElementById('crmFilterRE').value;

    document.querySelectorAll('.crm-row').forEach(row => {
        const name = (row.getAttribute('data-name') || '').toLowerCase();
        const taxid = (row.getAttribute('data-taxid') || '').toLowerCase();
        const email = (row.getAttribute('data-email') || '').toLowerCase();
        const phone = (row.getAttribute('data-phone') || '').toLowerCase();
        const city = (row.getAttribute('data-city') || '').toLowerCase();
        const rowType = row.getAttribute('data-type');
        const rowRE = row.getAttribute('data-re');

        let matches = true;
        if (query && !name.includes(query) && !taxid.includes(query) && !email.includes(query) && !phone.includes(query) && !city.includes(query)) matches = false;
        if (type && rowType !== type) matches = false;
        if (re && rowRE !== re) matches = false;

        row.style.display = matches ? '' : 'none';
    });
}
function resetCRMFilters() {
    document.getElementById('crmFilterSearch').value = '';
    document.getElementById('crmFilterType').value = '';
    document.getElementById('crmFilterRE').value = '';
    filterCRM();
}

function filterBulkProductList() {
    const query = document.getElementById('bulkProductSearch').value.toLowerCase().trim();
    document.querySelectorAll('.bulk-product-item').forEach(item => {
        const text = item.textContent.toLowerCase();
        item.style.display = text.includes(query) ? 'block' : 'none';
    });
}

function filterActivity() {
    const user = document.getElementById('activityFilterUser').value.toLowerCase().trim();
    const action = document.getElementById('activityFilterAction').value.toLowerCase().trim();
    const date = document.getElementById('activityFilterDate').value; // YYYY-MM-DD

    document.querySelectorAll('.activity-item').forEach(item => {
        const itemUser = (item.getAttribute('data-user') || '').toLowerCase();
        const itemAction = (item.getAttribute('data-action') || '').toLowerCase();
        const itemDate = item.getAttribute('data-date'); // YYYY-MM-DD

        let matches = true;
        if (user && !itemUser.includes(user)) matches = false;
        if (action && !itemAction.includes(action)) matches = false;
        if (date && itemDate !== date) matches = false;

        item.style.display = matches ? 'block' : 'none';
    });
}
function resetActivityFilters() {
    document.getElementById('activityFilterUser').value = '';
    document.getElementById('activityFilterAction').value = '';
    document.getElementById('activityFilterDate').value = '';
    filterActivity();
}

// Categories filtering is now powered by shared/inventory-filter.js (runSharedBackendCategoryFilter)
function resetCategoryFilters() {
    const srch = document.getElementById('categoryFilterSearch');
    if (srch) srch.value = '';
    const globalSearch = document.getElementById('sharedFilterSearch');
    if (globalSearch) globalSearch.value = '';
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
    const query = document.getElementById('returnFilterSearch').value.toLowerCase().trim();
    const method = document.getElementById('returnFilterMethod').value;
    const date = document.getElementById('returnFilterDate').value;

    document.querySelectorAll('.return-row').forEach(row => {
        const number = (row.getAttribute('data-number') || '').toLowerCase();
        const reason = (row.getAttribute('data-reason') || '').toLowerCase();
        const rowMethod = row.getAttribute('data-method');
        const rowDate = row.getAttribute('data-date');

        let matches = true;
        if (query && !number.includes(query) && !reason.includes(query)) matches = false;
        if (method && rowMethod !== method) matches = false;
        if (date && rowDate !== date) matches = false;

        row.style.display = matches ? '' : 'none';
    });
}

function resetReturnFilters() {
    document.getElementById('returnFilterSearch').value = '';
    document.getElementById('returnFilterMethod').value = '';
    document.getElementById('returnFilterDate').value = '';
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



// -- IPC Update Integration (INE) ---------------------------------------------
var ipcUpdateModalEl = document.getElementById('ipcUpdateModal');
var ipcUpdateModal = ipcUpdateModalEl ? new bootstrap.Modal(ipcUpdateModalEl) : null;

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

// Add event listener for auto-preview on input change
document.getElementById('ipcValueInput').addEventListener('input', debounce(function () {
    updateIpcPreview();
}, 500));

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
                    <td class="text-end fw-bold text-accent">${parseFloat(p.newPrice).toFixed(2)} €</td>
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

// -- Settings Management (Mail & Security) ------------------------------
function loadMailSettings() {
    fetch('/api/admin/mail-settings')
        .then(function (r) { return r.json(); })
        .then(function (data) {
            document.getElementById('mailHost').value = data.host || '';
            document.getElementById('mailPort').value = data.port || '';
            document.getElementById('mailUsername').value = data.username || '';
            document.getElementById('mailPassword').value = ''; // Always clear on load for security
            if (data.username) {
                document.getElementById('mailPassword').placeholder = '•••••••• (Cifrado)';
            }
        })
        .catch(function (e) { console.error('Error fetching mail settings:', e); });
}

function saveMailSettings() {
    const host = document.getElementById('mailHost').value.trim();
    const port = document.getElementById('mailPort').value.trim();
    const username = document.getElementById('mailUsername').value.trim();
    const password = document.getElementById('mailPassword').value;

    if (!host || !port) {
        showToast('Host y puerto son obligatorios', 'error');
        return;
    }

    const body = {
        host: host,
        port: parseInt(port),
        username: username,
        password: password
    };

    fetch('/api/admin/mail-settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    }).then(function (res) {
        if (res.ok) {
            showToast('Configuración SMTP guardada');
            loadMailSettings();
        } else {
            showToast('Error al guardar configuración SMTP', 'error');
        }
    }).catch(function () {
        showToast('Error de red', 'error');
    });
}

function updateAdminPin() {
    const current = document.getElementById('currentPin').value;
    const newPin = document.getElementById('newPin').value;
    const confirm = document.getElementById('confirmPin').value;

    if (newPin !== confirm) {
        showToast('Los PINs no coinciden', 'error');
        return;
    }

    if (newPin.length < 4) {
        showToast('El PIN debe tener al menos 4 caracteres', 'error');
        return;
    }

    fetch('/api/admin/update-pin', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            currentPin: current,
            newPin: newPin
        })
    }).then(function (res) {
        if (res.ok) {
            showToast('PIN administrativo actualizado correctamente');
            document.getElementById('changePinForm').reset();
        } else {
            res.json().then(function (err) {
                showToast(err.message || 'Error al actualizar PIN', 'error');
            }).catch(function () {
                showToast('Error al actualizar PIN', 'error');
            });
        }
    }).catch(function () {
        showToast('Error de red', 'error');
    });
}
