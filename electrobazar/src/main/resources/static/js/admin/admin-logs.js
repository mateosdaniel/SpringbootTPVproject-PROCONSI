/**
 * admin-logs.js
 * Activity log management functions.
 */

function loadActivityLog() {
    fetchActivityLogs(0);
}

function fetchActivityLogs(page) {
    const search = document.getElementById('activityFilterSearch')?.value || '';
    const action = document.getElementById('activityFilterAction')?.value || '';
    const username = document.getElementById('activityFilterUsername')?.value || '';
    const sortBy = document.getElementById('activitySortBy')?.value || 'timestamp';
    const sortDir = document.getElementById('activitySortDir')?.value || 'desc';
    
    fetch(`/api/admin/activity-logs?page=${page}&search=${encodeURIComponent(search)}&action=${encodeURIComponent(action)}&username=${encodeURIComponent(username)}&sortBy=${sortBy}&sortDir=${sortDir}`)
        .then(res => res.json())
        .then(data => {
            renderActivityLogs(data.content);
            
            const labelEl = document.getElementById('activityCountLabel');
            if (labelEl) {
                if (search || action || username) {
                    labelEl.textContent = `Mostrando ${data.totalElements || data.content.length} registros coincidentes.`;
                } else {
                    labelEl.textContent = 'Mostrando todos los registros de actividad.';
                }
            }

            renderActivityPagination(data);
        });
}

function renderActivityLogs(logs) {
    const tbody = document.getElementById('activityTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-5 text-muted">No hay registros de actividad que coincidan con los filtros.</td></tr>';
        return;
    }

    logs.forEach(log => {
        const tr = document.createElement('tr');
        
        let displayUser = (log.username || 'Sistema').trim();
        if (displayUser === 'Admin') displayUser = 'Sistema';

        let levelBadge = '<span class="badge-active yes">INFO</span>';
        if (log.level === 'WARN') levelBadge = '<span class="badge-active" style="background:rgba(241,196,15,0.15); color:#f1c40f;">WARN</span>';
        if (log.level === 'ERROR' || log.level === 'CRITICAL') levelBadge = '<span class="badge-active no">ERROR</span>';
        
        const timestamp = log.timestamp ? new Date(log.timestamp).toLocaleString() : '—';
        
        tr.innerHTML = `
            <td class="text-center">${levelBadge}</td>
            <td style="white-space:nowrap; color:var(--text-muted); font-size:0.8rem;">${timestamp}</td>
            <td><strong style="color:var(--text-main);">${escHtml(displayUser)}</strong></td>
            <td><span class="badge bg-secondary text-main border border-border" style="font-size:0.7rem; font-weight:700;">${escHtml(log.action || 'GENERAL')}</span></td>
            <td style="font-size:0.85rem; color:var(--text-main); opacity:0.8;">${escHtml(log.description)}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderActivityPagination(data) {
    const container = document.getElementById('activityPaginationContainer');
    if (!container) return;
    container.innerHTML = '';
    
    const totalPages = data.totalPages;
    const currentPage = data.currentPage;
    if (totalPages <= 1) return;

    const wrap = document.createElement('div');
    wrap.className = 'pagination-wrap rounded-bottom mt-0';
    wrap.innerHTML = `
        <button class="pagination-btn" ${currentPage === 0 ? 'disabled' : ''} onclick="fetchActivityLogs(${currentPage - 1})">
            <i class="bi bi-chevron-left"></i> <span>Anterior</span>
        </button>
        
        <div class="pagination-info">
            Página <strong>${currentPage + 1}</strong> de <strong>${totalPages}</strong>
        </div>
        
        <div class="pagination-jump">
            <input type="number" value="${currentPage + 1}" min="1" max="${totalPages}" onchange="if(this.value > 0 && this.value <= ${totalPages}) fetchActivityLogs(this.value - 1)">
        </div>

        <button class="pagination-btn" ${currentPage === totalPages - 1 ? 'disabled' : ''} onclick="fetchActivityLogs(${currentPage + 1})">
            <span>Siguiente</span> <i class="bi bi-chevron-right"></i>
        </button>
    `;
    container.appendChild(wrap);
}

function filterActivity() {
    fetchActivityLogs(0);
}

function resetActivityFilters() {
    document.getElementById('activityFilterSearch').value = '';
    document.getElementById('activityFilterAction').value = '';
    document.getElementById('activityFilterUsername').value = '';
    document.getElementById('activitySortBy').value = 'timestamp';
    document.getElementById('activitySortDir').value = 'desc';
    fetchActivityLogs(0);
}

function formatTimeAgo(dateStr) {
    if (!dateStr) return '—';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = Math.floor((now - date) / 1000);
    
    if (diff < 60) return 'hace un momento';
    if (diff < 3600) return `hace ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `hace ${Math.floor(diff / 3600)} h`;
    return date.toLocaleDateString();
}

// Global Exports
window.loadActivityLog = loadActivityLog;
window.fetchActivityLogs = fetchActivityLogs;
window.renderActivityLogs = renderActivityLogs;
window.renderActivityPagination = renderActivityPagination;
window.formatTimeAgo = formatTimeAgo;
window.filterActivity = filterActivity;
window.resetActivityFilters = resetActivityFilters;
