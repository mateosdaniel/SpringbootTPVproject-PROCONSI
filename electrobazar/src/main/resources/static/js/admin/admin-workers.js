/**
 * admin-workers.js
 * Worker management functions.
 */

function openWorkerModal(id, username, active, permissions, roleId) {
    document.getElementById('workerForm').reset();
    document.getElementById('workerId').value = id || '';
    document.getElementById('workerUsername').value = username || '';
    document.getElementById('workerActive').checked = active !== false;
    
    // Reset password visibility
    resetPasswordVisibility('workerPassword');
    resetPasswordVisibility('workerPin');

    // Password label adjustment based on edit or create
    var pwdLabel = document.getElementById('workerPasswordLabel');
    var pinLabel = document.getElementById('workerPinLabel');
    if (id) {
        pwdLabel.innerHTML = 'Contraseña <small class="font-normal" style="color: var(--text-muted);">(opcional, blanco para mantener)</small>';
        pinLabel.innerHTML = 'PIN de acceso <small class="font-normal" style="color: var(--text-muted);">(opcional, blanco para mantener)</small>';
    } else {
        pwdLabel.innerHTML = 'Contraseña *';
        pinLabel.innerHTML = 'PIN de acceso *';
    }

    // Load roles into select if not already there, then select current
    if (typeof loadRoles === 'function') {
        loadRoles().then(() => {
            document.getElementById('workerRole').value = roleId || '';
        });
    }

    workerModal.show();
}

function saveWorker() {
    var id = document.getElementById('workerId').value;
    var username = document.getElementById('workerUsername').value;
    var password = document.getElementById('workerPassword').value;
    var pinCode = document.getElementById('workerPin').value;
    var active = document.getElementById('workerActive').checked;
    var roleId = document.getElementById('workerRole').value;

    if (pinCode && pinCode.length !== 4) {
        showToast("El PIN de acceso debe tener exactamente 4 dígitos.", "error");
        return;
    }

    var worker = {
        id: id ? parseInt(id) : null,
        username: username,
        password: password || null,
        pinCode: pinCode || null,
        active: active,
        role: roleId ? { id: parseInt(roleId) } : null
    };

    fetch('/admin/workers/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(worker)
    }).then(function (res) {
        if (res.ok) {
            showToast(getAdminI18n('successSave'));
            setTimeout(function () { location.reload(); }, 1000);
        } else {
            res.json().then(function (err) {
                showToast(getAdminI18n('errorSave') + ': ' + (err.error || err.message || 'Desconocido'), 'error');
            }).catch(function () {
                showToast(getAdminI18n('errorSave'), 'error');
            });
        }
    }).catch(function () {
        showToast(getAdminI18n('errorNetwork'), 'error');
    });
}

function deleteWorker(id) {
    if (!confirm(getAdminI18n('confirmDelete'))) return;
    fetch('/admin/workers/delete/' + id, { method: 'DELETE' })
        .then(function (res) {
            if (res.ok) {
                showToast(getAdminI18n('successDelete'));
                setTimeout(function () { location.reload(); }, 1000);
            } else {
                res.json().then(function (err) {
                    showToast(getAdminI18n('errorDelete') + ': ' + (err.error || err.message || 'Desconocido'), 'error');
                }).catch(function () {
                    showToast(getAdminI18n('errorDelete'), 'error');
                });
            }
        }).catch(function () {
            showToast(getAdminI18n('errorNetwork'), 'error');
        });
}

const filterWorkers = debounce(function (page = 0) {
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
    queryParams.append('page', page);
    queryParams.append('size', 10);

    fetch(`/api/admin/workers?${queryParams.toString()}`)
        .then(res => res.json())
        .then(data => {
            renderWorkersTable(data.content || data);
            
            const total = data.totalElements || data.length;
            const label = document.getElementById('workerCountLabel');
            if (label) {
                if (search || roleId || active !== '') {
                    label.innerHTML = `Mostrando trabajadores encontrados con los filtros aplicados.`;
                } else {
                    label.textContent = 'Mostrando todas las fichas de trabajadores.';
                }
            }
            
            if (typeof renderInventoryPagination === 'function') {
                renderInventoryPagination('workersPagination', data, filterWorkers);
            }
        })
        .catch(err => console.error("Error filtering workers:", err));
}, 250);

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

        const tr = document.createElement('tr');
        tr.className = 'worker-row';
        
        let actionsHtml = '';
        if (w.username === 'root') {
            actionsHtml = `
                <span class="badge" style="background: rgba(245, 158, 11, 0.1); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.2); font-size: 0.7rem;">
                    <i class="bi bi-lock-fill me-1"></i> Protegido
                </span>
            `;
        } else {
                actionsHtml = `
                <div style="display:flex;gap:0.4rem;justify-content:flex-end">
                    <button class="btn-icon btn-edit" title="Editar" 
                        onclick="openWorkerModal(${w.id}, '${w.username}', ${w.active}, null, ${w.roleId || 'null'})">
                        <i class="bi bi-pencil"></i>
                    </button>
            `;
            if (!w.hasSales) {
                actionsHtml += `
                    <button class="btn-icon btn-delete" title="Eliminar" onclick="deleteWorker(${w.id})">
                        <i class="bi bi-trash"></i>
                    </button>
                `;
            }
            actionsHtml += `</div>`;
        }

        tr.innerHTML = `
            <td><strong>${w.username}</strong></td>
            <td>${badgeRole}</td>
            <td>${badgeActive}</td>
            <td style="text-align:right">
                ${actionsHtml}
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

function onWorkerRoleChange() {
    // No longer auto-checking individual permissions as they are removed
}

function togglePasswordVisibility(inputId, btn) {
    const input = document.getElementById(inputId);
    const icon = btn.querySelector('i');
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.remove('bi-eye');
        icon.classList.add('bi-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.remove('bi-eye-slash');
        icon.classList.add('bi-eye');
    }
}

function resetPasswordVisibility(inputId) {
    const input = document.getElementById(inputId);
    input.type = 'password';
    const btn = input.parentElement.querySelector('button');
    if (btn) {
        const icon = btn.querySelector('i');
        if (icon) {
            icon.classList.remove('bi-eye-slash');
            icon.classList.add('bi-eye');
        }
    }
}

// Global Exports
window.openWorkerModal = openWorkerModal;
window.saveWorker = saveWorker;
window.deleteWorker = deleteWorker;
window.filterWorkers = filterWorkers;
window.renderWorkersTable = renderWorkersTable;
window.resetWorkerFilters = resetWorkerFilters;
window.onWorkerRoleChange = onWorkerRoleChange;
window.togglePasswordVisibility = togglePasswordVisibility;
window.resetPasswordVisibility = resetPasswordVisibility;
