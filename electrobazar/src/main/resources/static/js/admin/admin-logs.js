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
            renderActivityPagination(data);
        });
}

function renderActivityLogs(logs) {
    const container = document.getElementById('activityFeedContainer');
    if (!container) return;
    container.innerHTML = '';

    if (logs.length === 0) {
        container.innerHTML = '<div class="text-center py-4 text-muted">No hay registros de actividad.</div>';
        return;
    }

    logs.forEach(log => {
        const div = document.createElement('div');
        div.className = 'log-item';
        div.innerHTML = `
            <div class="log-icon"><i class="bi bi-info-circle"></i></div>
            <div class="log-content">
                <div class="log-header">
                    <span class="log-worker">${escHtml(log.username || '-')}</span>
                    <span class="log-time">${formatTimeAgo(log.timestamp)}</span>
                </div>
                <div class="log-message">${escHtml(log.description)}</div>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderActivityPagination(data) {
    const container = document.getElementById('activityFeedContainer');
    if (!container) return;
    
    const totalPages = data.totalPages;
    const currentPage = data.currentPage;
    if (totalPages <= 1) return;

    const pagination = document.createElement('nav');
    pagination.innerHTML = `
        <ul class="pagination justify-content-center mt-3">
            <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
                <button class="page-link" onclick="fetchActivityLogs(${currentPage - 1})">Anterior</button>
            </li>
            ${Array.from({length: totalPages}, (_, i) => `
                <li class="page-item ${i === currentPage ? 'active' : ''}">
                    <button class="page-link" onclick="fetchActivityLogs(${i})">${i + 1}</button>
                </li>
            `).join('')}
            <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
                <button class="page-link" onclick="fetchActivityLogs(${currentPage + 1})">Siguiente</button>
            </li>
        </ul>
    `;
    container.appendChild(pagination);
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
