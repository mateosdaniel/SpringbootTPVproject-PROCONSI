/**
 * admin-workers.js
 * Worker management functions.
 */

function openWorkerModal(id, username, active, permissions, roleId) {
    document.getElementById('workerForm').reset();
    document.getElementById('workerId').value = id || '';
    document.getElementById('workerUsername').value = username || '';
    document.getElementById('workerActive').checked = active !== false;

    // Password label adjustment based on edit or create
    var pwdLabel = document.getElementById('workerPasswordLabel');
    if (id) {
        pwdLabel.innerHTML = 'Contraseña <small class="font-normal" style="color: var(--text-muted);">(opcional, blanco para mantener)</small>';
    } else {
        pwdLabel.innerHTML = 'Contraseña *';
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

function onWorkerRoleChange() {
    // No longer auto-checking individual permissions as they are removed
}

// Global Exports
window.openWorkerModal = openWorkerModal;
window.saveWorker = saveWorker;
window.deleteWorker = deleteWorker;
window.filterWorkers = filterWorkers;
window.renderWorkersTable = renderWorkersTable;
window.resetWorkerFilters = resetWorkerFilters;
window.onWorkerRoleChange = onWorkerRoleChange;
