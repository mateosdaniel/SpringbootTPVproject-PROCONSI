
        var productModal = new bootstrap.Modal(document.getElementById('productModal'));
        var categoryModal = new bootstrap.Modal(document.getElementById('categoryModal'));
        var workerModal = new bootstrap.Modal(document.getElementById('workerModal'));

        // â”€â”€ View Switching â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        function switchView(viewId, btnElement) {
            // Hide all views
            document.getElementById('dashboardView').style.display = 'none';
            document.getElementById('productsView').style.display = 'none';
            document.getElementById('invoicesView').style.display = 'none';
            document.getElementById('cashCloseView').style.display = 'none';
            document.getElementById('workersView').style.display = 'none';
            document.getElementById('analyticsView').style.display = 'none';
            document.getElementById('crmView').style.display = 'none';
            document.getElementById('activityView').style.display = 'none';

            // Show selected view
            document.getElementById(viewId).style.display = 'block';

            // Update active sidebar button state
            document.querySelectorAll('.sidebar-menu-btn').forEach(function (btn) {
                btn.classList.remove('active');
            });
            if (btnElement) btnElement.classList.add('active');

            // Trigger chart update if needed
            if (viewId === 'analyticsView') {
                initCharts();
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

        // â”€â”€ Admin PIN Change â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        var pinModal = new bootstrap.Modal(document.getElementById('pinModal'));

        function openPinModal() {
            document.getElementById('oldPin').value = '';
            document.getElementById('newPin').value = '';
            pinModal.show();
        }

        function changePin() {
            var oldPin = document.getElementById('oldPin').value.trim();
            var newPin = document.getElementById('newPin').value.trim();

            if (!oldPin || !newPin) {
                showToast('Rellena ambos campos', 'error');
                return;
            }

            fetch('/admin/change-pin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ oldPin: oldPin, newPin: newPin })
            })
                .then(function (r) {
                    if (r.ok) {
                        pinModal.hide();
                        showToast('PIN cambiado correctamente');
                    } else {
                        r.json().then(function (data) {
                            showToast(data.message || 'Error al cambiar PIN', 'error');
                        });
                    }
                })
                .catch(function () { showToast('Error de red', 'error'); });
        }

        // â”€â”€ Product CRUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
                        previewImage(p.imageUrl);
                    })
                    .catch(function () { showToast('Error al cargar el producto', 'error'); });
            }

            productModal.show();
        }

        function saveProduct() {
            const name = document.getElementById('productName').value.trim();
            const price = document.getElementById('productPrice').value;
            if (!name || !price) { showToast('Nombre y precio son obligatorios', 'error'); return; }

            var id = document.getElementById('productId').value;
            var catId = document.getElementById('productCategory').value;

            const body = {
                name: name,
                description: document.getElementById('productDescription').value.trim() || null,
                price: parseFloat(price),
                stock: parseInt(document.getElementById('productStock').value) || 0,
                active: document.getElementById('productActive').checked,
                imageUrl: document.getElementById('productImageUrl').value.trim() || null,
                category: catId ? { id: parseInt(catId) } : null
            };

            var method = id ? 'PUT' : 'POST';
            var url = id ? '/api/products/' + id : '/api/products';

            fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
                .then(function (r) {
                    if (!r.ok) throw new Error();
                    productModal.hide();
                    showToast(id ? 'Producto actualizado correctamente' : 'Producto creado correctamente');
                    setTimeout(function () { location.reload(); }, 900);
                })
                .catch(function () { showToast('Error al guardar el producto', 'error'); });
        }

        function deleteProduct(id, name) {
            if (!confirm('Â¿Eliminar (desactivar) el producto "' + name + '"?')) return;
            fetch('/api/products/' + id, { method: 'DELETE' })
                .then(function (r) {
                    if (!r.ok) throw new Error();
                    showToast('Producto "' + name + '" desactivado');
                    setTimeout(function () { location.reload(); }, 900);
                })
                .catch(function () { showToast('Error al eliminar el producto', 'error'); });
        }


        // â”€â”€ Category CRUD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        function openCategoryModal(id) {
            document.getElementById('categoryId').value = '';
            document.getElementById('categoryName').value = '';
            document.getElementById('categoryDescription').value = '';
            document.getElementById('categoryActive').checked = true;
            document.getElementById('categoryModalLabel').textContent = id ? 'Editar CategorÃ­a' : 'Nueva CategorÃ­a';

            if (id) {
                fetch('/api/categories/' + id)
                    .then(function (r) { return r.json(); })
                    .then(function (c) {
                        document.getElementById('categoryId').value = c.id;
                        document.getElementById('categoryName').value = c.name || '';
                        document.getElementById('categoryDescription').value = c.description || '';
                        document.getElementById('categoryActive').checked = c.active !== false;
                    })
                    .catch(function () { showToast('Error al cargar la categorÃ­a', 'error'); });
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
                    if (!r.ok) return r.text().then(function (t) { throw new Error(t); });
                    categoryModal.hide();
                    showToast(id ? 'CategorÃ­a actualizada correctamente' : 'CategorÃ­a creada correctamente');
                    setTimeout(function () { location.reload(); }, 900);
                })
                .catch(function (e) {
                    var msg = e.message.indexOf('Ya existe') !== -1 ? e.message : 'Error al guardar la categorÃ­a';
                    showToast(msg, 'error');
                });
        }

        function deleteCategory(id, name) {
            if (!confirm('Â¿Desactivar la categorÃ­a "' + name + '"?')) return;
            fetch('/api/categories/' + id, { method: 'DELETE' })
                .then(function (r) {
                    if (!r.ok) throw new Error();
                    showToast('CategorÃ­a "' + name + '" desactivada');
                    setTimeout(function () { location.reload(); }, 900);
                })
                .catch(function () { showToast('Error al desactivar la categorÃ­a', 'error'); });
        }

        // â”€â”€ Importar Productos y CategorÃ­as por CSV â”€â”€
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

        // â”€â”€ Analytics & Charts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        let salesChart, categoryChart;

        function initCharts() {
            // Data injected via Thymeleaf from the model
            /*[# th:if="${sales != null or products != null}"]*/
            var salesDataRaw = /*[[${sales}]]*/[];
            var productsDataRaw = /*[[${products}]]*/[];
            /*[/]*/

            var now = new Date();
            var todayStr = now.toISOString().split('T')[0];

            // 1. Stats Calculation
            let todayRevenue = 0;
            let todaySalesCount = 0;
            const productSalesCount = {};
            let lowStockCount = 0;

            salesDataRaw.forEach(function (sale) {
                var saleDate = sale.createdAt ? sale.createdAt.split('T')[0] : '';
                if (saleDate === todayStr) {
                    todayRevenue += sale.totalAmount || 0;
                    todaySalesCount++;
                }

                // Track product counts if lines are available
                if (sale.lines) {
                    sale.lines.forEach(function (line) {
                        var pName = line.productName || 'Producto';
                        productSalesCount[pName] = (productSalesCount[pName] || 0) + line.quantity;
                    });
                }
            });

            productsDataRaw.forEach(function (p) {
                if (p.stock < 5) lowStockCount++;
            });

            // Find top product
            let topP = 'â€”';
            let maxQty = 0;
            for (const [name, qty] of Object.entries(productSalesCount)) {
                if (qty > maxQty) {
                    maxQty = qty;
                    topP = name;
                }
            }

            // Update DOM
            document.getElementById('statTodayRevenue').textContent = todayRevenue.toFixed(2) + ' â‚¬';
            document.getElementById('statTodaySales').textContent = todaySalesCount;
            document.getElementById('statTopProduct').textContent = topP.length > 15 ? topP.substring(0, 15) + '...' : topP;
            document.getElementById('statLowStock').textContent = lowStockCount;

            // 2. Sales Trend Chart (Last 7 Days)
            var days = [];
            var revenuePerDay = [];
            for (var i = 6; i >= 0; i--) {
                var d = new Date();
                d.setDate(d.getDate() - i);
                var dStr = d.toISOString().split('T')[0];
                days.push(d.toLocaleDateString('es-ES', { weekday: 'short' }));

                var total = salesDataRaw.reduce(function (sum, s) {
                    return (s.createdAt && s.createdAt.split('T')[0] === dStr) ? sum + (s.totalAmount || 0) : sum;
                }, 0);
                revenuePerDay.push(total);
            }

            var ctxSales = document.getElementById('salesChart').getContext('2d');
            if (salesChart) salesChart.destroy();
            salesChart = new Chart(ctxSales, {
                type: 'line',
                data: {
                    labels: days,
                    datasets: [{
                        label: 'Ventas (â‚¬)',
                        data: revenuePerDay,
                        borderColor: getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),
                        backgroundColor: 'rgba(245, 166, 35, 0.1)',
                        fill: true,
                        tension: 0.4
                    }]
                },
                options: {
                    responsive: true,
                    plugins: { legend: { display: false } },
                    scales: {
                        y: { grid: { color: 'rgba(255,255,255,0.05)' }, border: { display: false }, ticks: { color: '#8892a4' } },
                        x: { grid: { display: false }, border: { display: false }, ticks: { color: '#8892a4' } }
                    }
                }
            });

            // 3. Category Distribution Chart
            var catSummary = {};
            productsDataRaw.forEach(function (p) {
                var catName = p.category ? p.category.name : 'Sin CategorÃ­a';
                catSummary[catName] = (catSummary[catName] || 0) + 1;
            });

            var catLabels = Object.keys(catSummary);
            var catData = Object.values(catSummary);

            var ctxCat = document.getElementById('categoryChart').getContext('2d');
            if (categoryChart) categoryChart.destroy();
            categoryChart = new Chart(ctxCat, {
                type: 'doughnut',
                data: {
                    labels: catLabels,
                    datasets: [{
                        data: catData,
                        backgroundColor: ['#f5a623', '#3b82f6', '#22c55e', '#ef4444', '#a855f7', '#06b6d4'],
                        borderWidth: 0
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: { position: 'bottom', labels: { color: '#8892a4', usePointStyle: true, boxWidth: 6 } }
                    },
                    cutout: '75%'
                }
            });
        }

        // Theme application moved to <head>

        // â”€â”€ Worker Management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        function openWorkerModal(id, username, active, permissions) {
            document.getElementById('workerForm').reset();
            document.getElementById('workerId').value = id || '';
            document.getElementById('workerUsername').value = username || '';
            document.getElementById('workerActive').checked = active !== false;

            // Ajustar etiqueta de contraseÃ±a segÃºn si editamos o creamos
            var pwdLabel = document.getElementById('workerPasswordLabel');
            if (id) {
                pwdLabel.innerHTML = 'ContraseÃ±a <small class="text-muted font-normal">(opcional, blanco para mantener)</small>';
            } else {
                pwdLabel.innerHTML = 'ContraseÃ±a *';
            }

            // Permissions
            var perms = permissions || [];
            document.getElementById('permProd').checked = perms.indexOf('MANAGE_PRODUCTS_TPV') !== -1;
            document.getElementById('permCash').checked = perms.indexOf('CASH_CLOSE') !== -1;
            document.getElementById('permAdmin').checked = perms.indexOf('ADMIN_ACCESS') !== -1;

            workerModal.show();
        }

        function saveWorker() {
            var id = document.getElementById('workerId').value;
            var username = document.getElementById('workerUsername').value;
            var password = document.getElementById('workerPassword').value;
            var active = document.getElementById('workerActive').checked;

            var permissions = [];
            if (document.getElementById('permProd').checked) permissions.push('MANAGE_PRODUCTS_TPV');
            if (document.getElementById('permCash').checked) permissions.push('CASH_CLOSE');
            if (document.getElementById('permAdmin').checked) permissions.push('ADMIN_ACCESS');

            if (!username) { showToast('El nombre de usuario es obligatorio', 'error'); return; }
            if (!id && !password) { showToast('La contraseÃ±a es obligatoria para nuevos trabajadores', 'error'); return; }

            var worker = { id: id ? parseInt(id) : null, username: username, password: password, active: active, permissions: permissions };

            fetch('/admin/workers/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(worker)
            }).then(function (res) {
                if (res.ok) {
                    showToast('Trabajador guardado con Ã©xito');
                    setTimeout(function () { location.reload(); }, 1000);
                } else {
                    showToast('Error al guardar trabajador', 'error');
                }
            }).catch(function () {
                showToast('Error de red', 'error');
            });
        }

        function deleteWorker(id) {
            if (!confirm('Â¿Seguro que quieres eliminar a este trabajador?')) return;
            fetch('/admin/workers/delete/' + id, { method: 'DELETE' })
                .then(function (res) {
                    if (res.ok) {
                        showToast('Trabajador eliminado');
                        setTimeout(function () { location.reload(); }, 1000);
                    } else {
                        showToast('Error al eliminar', 'error');
                    }
                }).catch(function () {
                    showToast('Error de red', 'error');
                });
        }

        // â”€â”€ Filter Low Stock â”€â”€
        let showingLowStockOnly = false;
        function filterLowStock() {
            const btn = document.getElementById('btnFilterLowStock');
            showingLowStockOnly = !showingLowStockOnly;

            const rows = document.querySelectorAll('#productsView tbody tr:not(.empty-row)');
            rows.forEach(function (row) {
                var stockBadge = row.querySelector('.badge-stock-low');
                if (showingLowStockOnly) {
                    row.style.display = stockBadge ? '' : 'none';
                } else {
                    row.style.display = '';
                }
            });

            if (showingLowStockOnly) {
                btn.style.background = 'var(--danger)';
                btn.style.color = 'white';
                btn.innerHTML = '<i class="bi bi-x-lg"></i> Quitar Filtro';
            } else {
                btn.style.background = 'rgba(231, 76, 60, 0.1)';
                btn.style.color = 'var(--danger)';
                btn.innerHTML = '<i class="bi bi-filter"></i> Stock Bajo';
            }
        }
