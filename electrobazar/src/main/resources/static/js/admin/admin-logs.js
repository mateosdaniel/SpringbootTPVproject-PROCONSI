/**
 * admin-logs.js
 * Activity log management functions.
 */

function loadActivityLog() {
    fetchActivityLogs(0);
}

function fetchActivityLogs(page) {
    const type = document.getElementById('logFilterType').value;
    const worker = document.getElementById('logFilterWorker').value;
    
    fetch(`/api/admin/logs?page=${page}&type=${type}&workerId=${worker}`)
        .then(res => res.json())
        .then(data => {
            renderActivityLogs(data.content);
            renderActivityPagination(data);
        });
}

function renderActivityLogs(logs) {
    const container = document.getElementById('activityLogList');
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
                    <span class="log-worker">${escHtml(log.workerName)}</span>
                    <span class="log-time">${formatTimeAgo(log.createdAt)}</span>
                </div>
                <div class="log-message">${escHtml(log.message)}</div>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderActivityPagination(data) {
    // Implementation for logs pagination
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
