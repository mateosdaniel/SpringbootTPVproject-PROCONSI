/**
 * admin-roles.js
 * Role management functions.
 */

let rolesCache = [];
let _rolesListenerAttached = false;

function loadRoles() {
    return Promise.all([
        fetch('/api/admin/roles?size=200').then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); }),
        fetch('/api/roles').then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); }),
        fetch('/api/workers?size=500').then(res => { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
    ])
        .then(function ([adminRoles, allRoles, workersPage]) {
            // adminRoles has workerCount but filters out ADMIN.
            // allRoles has ADMIN. We merge: use adminRoles list + add ADMIN from allRoles with computed count.
            const adminList = adminRoles.content || adminRoles;
            const workers = workersPage.content || workersPage;

            // Build a map of roleId -> count from the workers list for roles not in adminList
            const countMap = {};
            workers.forEach(w => { if (w.role && w.role.id) countMap[w.role.id] = (countMap[w.role.id] || 0) + 1; });

            // Find ADMIN role and add it at the top if present
            const adminRole = allRoles.find(r => r.name && r.name.toUpperCase() === 'ADMIN');
            const merged = [...adminList];
            if (adminRole && !merged.find(r => r.name && r.name.toUpperCase() === 'ADMIN')) {
                merged.unshift({
                    id: adminRole.id,
                    name: adminRole.name,
                    description: adminRole.description || null,
                    permissions: adminRole.permissions || [],
                    workerCount: countMap[adminRole.id] || 0,
                    isAdmin: true  // flag to disable edit/delete in UI
                });
            }

            rolesCache = allRoles;
            renderRolesTable(merged);
            populateRoleSelect(allRoles);
            return merged;
        })
        .catch(function (err) {
            console.error('Error loading roles:', err);
            const el = document.getElementById('rolesTableBody');
            if (el) {
                el.innerHTML = '<tr><td colspan="5" class="text-center text-danger">Error al cargar datos: ' + err.message + '</td></tr>';
            }
        });
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
    document.getElementById('rolemodalLabel').textContent = id ? 'Editar Rol' : 'Nuevo Rol';

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

            const labelEl = document.getElementById('roleCountLabel');
            if (labelEl) {
                if (search || permissions.length > 0) {
                    labelEl.textContent = `Mostrando ${data.totalElements || (data.content || data).length} roles coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los roles.';
                }
            }
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
        tr.style.cursor = 'pointer';
        tr.setAttribute('data-role-id', r.id);
        tr.setAttribute('data-role-name', r.name);
        
        const count = r.workerCount !== undefined ? r.workerCount : 0;
        const isAdmin = r.isAdmin === true || (r.name && r.name.toUpperCase() === 'ADMIN');
        tr.innerHTML = `
            <td><strong>${escHtml(r.name)}</strong></td>
            <td class="small" style="color: var(--text-muted);">${escHtml(r.description || '\u2014')}</td>
            <td>${perms}</td>
            <td>
                <span class="badge" style="background-color: rgba(var(--accent-rgb), 0.1); color: var(--accent); border: 1px solid var(--accent);">
                    ${count} trabajador(es)
                </span>
            </td>
            <td style="text-align:right">
                ${isAdmin ? '<span class="small" style="color:var(--text-muted)">Protegido</span>' : `
                <button class="btn-icon" data-action="edit" data-role-id="${r.id}" title="Editar">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn-icon danger" data-action="delete" data-role-id="${r.id}" title="Eliminar">
                    <i class="bi bi-trash"></i>
                </button>`}
            </td>
        `;
        tbody.appendChild(tr);
    });

    // Register delegated listener once — tbody is guaranteed to exist here
    if (!_rolesListenerAttached) {
        _rolesListenerAttached = true;
        tbody.addEventListener('click', function(e) {
            const row = e.target.closest('.role-row');
            const editBtn = e.target.closest('[data-action="edit"]');
            const deleteBtn = e.target.closest('[data-action="delete"]');

            if (editBtn) {
                e.stopPropagation();
                openRoleModal(parseInt(editBtn.dataset.roleId));
                return;
            }
            if (deleteBtn) {
                e.stopPropagation();
                deleteRole(parseInt(deleteBtn.dataset.roleId));
                return;
            }
            if (row) {
                showRoleWorkers(parseInt(row.dataset.roleId), null, row.dataset.roleName);
            }
        });
    }
}

/**
 * Shows a modal listing workers assigned to a role.
 */
function showRoleWorkers(roleId, _unused, roleName) {
    const list = document.getElementById('roleUsersList');
    const title = document.getElementById('roleUsersTitle');
    
    title.textContent = `Trabajadores con el Rol "${roleName}"`;
    list.innerHTML = '<div class="text-center py-4"><div class="spinner-border spinner-border-sm text-accent"></div></div>';
    
    roleUsersModal.show();

    fetch(`/api/admin/roles/${roleId}/workers`)
        .then(res => res.json())
        .then(workers => {
            if (!workers || workers.length === 0) {
                list.innerHTML = '<div class="text-center py-3 text-muted small">Sin trabajadores asignados.</div>';
                return;
            }
            list.innerHTML = workers.map(w => `
                <div class="d-flex align-items-center gap-3 p-2 rounded" style="background:var(--surface); border:1px solid var(--border)">
                    <div class="rounded-circle d-flex align-items-center justify-content-center" style="width:36px; height:36px; background:rgba(var(--accent-rgb), 0.1)">
                        <i class="bi bi-person text-accent"></i>
                    </div>
                    <div class="flex-grow-1">
                        <div class="fw-bold small" style="color:var(--text-main)">${escHtml(w.username)}</div>
                        <div class="text-muted" style="font-size:0.7rem">${escHtml(w.email || 'Sin email')}</div>
                    </div>
                    ${w.active 
                        ? '<span class="badge" style="background:rgba(34,197,94,0.1);color:#22c55e;border:1px solid rgba(34,197,94,0.3);font-size:0.6rem">Activo</span>'
                        : '<span class="badge" style="background:rgba(239,68,68,0.1);color:#ef4444;border:1px solid rgba(239,68,68,0.3);font-size:0.6rem">Inactivo</span>'}
                </div>
            `).join('');
        })
        .catch(() => {
            list.innerHTML = '<div class="text-center py-3 text-danger small">Error al cargar trabajadores.</div>';
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

/**
 * Updates a button label with the count of selected items.
 */
function updateFilterPermLabel(btnId, checkboxClass, defaultText) {
    const checked = document.querySelectorAll(checkboxClass + ':checked').length;
    const btn = document.getElementById(btnId);
    if (!btn) return;
    if (checked > 0) {
        // Find if defaultText is a translation key or plain text
        const label = checked === 1 ? '1 seleccionado' : checked + ' seleccionados';
        btn.innerHTML = `<i class="bi bi-shield-check me-2"></i> ${label}`;
    } else {
        btn.textContent = defaultText;
    }
}

// Global Exports
window.loadRoles = loadRoles;
window.populateRoleSelect = populateRoleSelect;
window.openRoleModal = openRoleModal;
window.saveRole = saveRole;
window.deleteRole = deleteRole;
window.filterRoles = filterRoles;
window.renderRolesTable = renderRolesTable;
window.showRoleWorkers = showRoleWorkers;
window.resetRolePermFilters = resetRolePermFilters;
window.resetRoleFilters = resetRoleFilters;
window.updateFilterPermLabel = updateFilterPermLabel;
