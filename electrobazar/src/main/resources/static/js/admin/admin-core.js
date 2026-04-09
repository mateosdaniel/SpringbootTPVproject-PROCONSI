/**
 * admin-core.js
 * Core functions and initialization for the Admin Panel.
 */

// Global Modal Instances
window.productModal = null;
window.categoryModal = null;
window.workerModal = null;
window.roleModal = null;
window.customerModal = null;
window.schedulePriceModal = null;
window.ipcUpdateModal = null;
window.couponModal = null;
window.promotionModal = null;
window.measurementUnitModal = null;
window.abonoModal = null;
window.tariffModal = null;
window.addTariffValueModal = null;
window.deleteTariffModal = null;

// Initialization
document.addEventListener('DOMContentLoaded', function () {
    // Initialize standard modals
    const modalIds = [
        'productModal', 'categoryModal', 'workerModal', 'roleModal',
        'customerModal', 'schedulePriceModal', 'ipcUpdateModal', 'couponModal',
        'promotionModal', 'measurementUnitModal', 'abonoModal',
        'tariffModal', 'addTariffValueModal', 'deleteTariffModal'
    ];

    modalIds.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            window[id] = new bootstrap.Modal(el);
        }
    });

    // Check for "View All" buttons and their behavior
    const viewAllBtns = document.querySelectorAll('.view-all-btn');
    viewAllBtns.forEach(btn => {
        btn.addEventListener('click', function () {
            const targetView = this.getAttribute('data-view');
            if (targetView) switchView(targetView);
        });
    });

    // Hash-based navigation
    const hash = window.location.hash.substring(1);
    const viewNames = [
        'dashboard', 'productos', 'categorias', 'trabajadores', 'roles',
        'clientes', 'precios', 'cupones', 'promociones', 'ventas',
        'cajas', 'tarifas', 'unidades', 'logs', 'config', 'abonos'
    ];
    if (viewNames.includes(hash)) {
        switchView(hash + 'View');
    }

    // Auto-load tasks based on view
    if (document.getElementById('invoicesView') && document.getElementById('invoicesView').style.display !== 'none') {
        if (typeof fetchSalesPage === 'function') fetchSalesPage(0);
    }
});

function switchView(viewId, btn) {
    // 1. Hide all elements that end in "View"
    // Using a more specific selector if possible, or just the suffix
    const allViews = document.querySelectorAll('[id$="View"]');
    allViews.forEach(v => v.style.display = 'none');

    // 2. Show the target view
    const target = document.getElementById(viewId);
    if (target) {
        target.style.display = 'block';
    } else {
        console.warn('View not found:', viewId);
    }

    // 3. Update Sidebar active state
    document.querySelectorAll('.sidebar-nav .nav-link').forEach(link => {
        link.classList.remove('active');
    });
    if (btn) {
        btn.classList.add('active');
    } else {
        // Fallback: try to find button by onclick
        document.querySelectorAll('.sidebar-nav .nav-link').forEach(link => {
            const oc = link.getAttribute('onclick');
            if (oc && oc.includes(`'${viewId}'`)) {
                link.classList.add('active');
            }
        });
    }

    // 4. Specific logic for views (initial data-loading)
    if (viewId === 'activityView' && typeof loadActivityLog === 'function') loadActivityLog();
    if (viewId === 'rolesView' && typeof loadRoles === 'function') loadRoles();
    if (viewId === 'crmView' && typeof filterCRM === 'function') filterCRM();
    if (viewId === 'workersView' && typeof filterWorkers === 'function') filterWorkers();
    if (viewId === 'invoicesView' && typeof fetchSalesPage === 'function') fetchSalesPage(0);
    if (viewId === 'cashCloseView' && typeof filterCashClosures === 'function') filterCashClosures();
    if (viewId === 'measurementUnitsView' && typeof loadMeasurementUnits === 'function') loadMeasurementUnits();
    if (viewId === 'tarifasView' && typeof loadTariffs === 'function') loadTariffs();
    if (viewId === 'tiposIvaView' && typeof loadTaxRates === 'function') loadTaxRates();
    if (viewId === 'analyticsView' && typeof initDashboardCharts === 'function') initDashboardCharts();
    if (viewId === 'abonosView' && typeof loadAbonos === 'function') loadAbonos();

    // Scroll to top
    window.scrollTo(0, 0);
}

function showToast(message, type = 'success') {
    const toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        const container = document.createElement('div');
        container.id = 'toastContainer';
        container.style = 'position: fixed; top: 20px; right: 20px; z-index: 9999;';
        document.body.appendChild(container);
    }
    
    const toast = document.createElement('div');
    const colors = {
        'success': '#22c55e',
        'error': '#ef4444',
        'warning': '#f5a623',
        'info': '#3b82f6'
    };
    
    toast.style = `
        background: var(--surface);
        color: var(--text-main);
        padding: 12px 20px;
        border-radius: 8px;
        margin-bottom: 10px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        border-left: 4px solid ${colors[type] || colors.success};
        display: flex;
        align-items: center;
        gap: 10px;
        animation: slideIn 0.3s ease forwards;
        min-width: 250px;
    `;
    
    const icon = type === 'error' ? 'bi-exclamation-circle' : 'bi-check-circle';
    toast.innerHTML = `<i class="bi ${icon}"></i> <span>${message}</span>`;
    
    document.getElementById('toastContainer').appendChild(toast);
    
    setTimeout(() => {
        toast.style.animation = 'slideOut 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function escHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeHtml(text) {
    if (!text) return "";
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatDateTime(dt) {
    if (!dt) return '—';
    try {
        var d = new Date(dt);
        return d.toLocaleDateString('es-ES') + ' ' + d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
    } catch (e) { return dt; }
}

function formatDecimal(val, minFrac = 2, maxFrac = 4) {
    if (val === null || val === undefined) return '0,00';
    return new Intl.NumberFormat('es-ES', {
        minimumFractionDigits: minFrac,
        maximumFractionDigits: maxFrac
    }).format(parseFloat(val));
}

function debounce(func, wait) {
    let timeout;
    return function () {
        const context = this, args = arguments;
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(context, args), wait);
    };
}

function previewImage(event) {
    var reader = new FileReader();
    reader.onload = function () {
        var output = document.getElementById('imagePreview');
        output.src = reader.result;
        output.style.display = 'block';
    };
    reader.readAsDataURL(event.target.files[0]);
}

// Global Exports
window.switchView = switchView;
window.showToast = showToast;
window.escHtml = escHtml;
window.escapeHtml = escapeHtml;
window.formatDateTime = formatDateTime;
window.formatDecimal = formatDecimal;
window.debounce = debounce;
window.previewImage = previewImage;
