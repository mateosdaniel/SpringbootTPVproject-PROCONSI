/**
 * admin-core.js
 * Core functions and initialization for the Admin Panel.
 */
 
// Helper for i18n in Admin JS
function getAdminI18n(key) {
    const el = document.getElementById('admin-js-translations');
    if (!el) return '';
    return el.dataset[key] || '';
}

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
        'tariffModal', 'editTariffModal', 'applyPricesModal', 'priceChangesHistoryModal',
        'addTariffValueModal', 'deleteTariffModal'
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
    function handleHashNavigation() {
        const hash = window.location.hash.substring(1);
        if (!hash || hash === 'dashboard') {
            switchView('dashboardView', null, true);
            return;
        }

        // Mapping hashes to View IDs
        const mapping = {
            'productos': 'productsView',
            'invoices': 'invoicesView',
            'cashClose': 'cashCloseView',
            'returns': 'returnsHistoryView',
            'categorias': 'categoriesView',
            'trabajadores': 'workersView',
            'roles': 'rolesView',
            'clientes': 'crmView',
            'precios': 'preciosTempView',
            'cupones': 'couponsView',
            'promociones': 'promotionsView',
            'tarifas': 'tarifasView',
            'unidades': 'measurementUnitsView',
            'logs': 'activityView',
            'config': 'settingsView',
            'abonos': 'abonosView'
        };

        const viewId = mapping[hash] || (hash + 'View');
        if (document.getElementById(viewId)) {
            switchView(viewId, null, true);
        }
    }

    window.addEventListener('hashchange', handleHashNavigation);
    handleHashNavigation(); // Initial check
});

// Navigation History
let viewHistory = [];

function switchView(viewId, btn, isBack = false) {
    // 0. History tracking: don't track if navigating back or if it's the same view
    const currentActiveView = Array.from(document.querySelectorAll('[id$="View"]'))
                                  .find(v => v.style.display !== 'none');
    
    if (!isBack && currentActiveView && currentActiveView.id !== viewId) {
        // Only push if not already at the top of history to avoid simple loops
        if (viewHistory.length === 0 || viewHistory[viewHistory.length - 1] !== currentActiveView.id) {
            viewHistory.push(currentActiveView.id);
        }
    }

    // 1. Hide all elements that end in "View"
    const allViews = document.querySelectorAll('[id$="View"]');
    allViews.forEach(v => v.style.display = 'none');

    // 2. Show the target view
    const target = document.getElementById(viewId);
    if (target) {
        target.style.display = 'block';
        // Update URL hash for better browser behavior
        const viewShortName = viewId.replace('View', '');
        if (viewShortName !== 'dashboard') {
            window.location.hash = viewShortName;
        } else {
            // Clear hash on dashboard
            if (window.location.hash) {
                history.replaceState(null, null, ' ');
            }
        }
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
    if (viewId === 'productsView') {
        const activeTab = document.querySelector('#mgmtTabs .nav-link.active');
        if (activeTab && activeTab.id === 'categories-tab') {
            if (typeof runSharedBackendCategoryFilter === 'function') runSharedBackendCategoryFilter();
        } else {
            if (typeof runSharedBackendFilter === 'function') runSharedBackendFilter();
        }
    }
    if (viewId === 'crmView' && typeof filterCRM === 'function') filterCRM();
    if (viewId === 'workersView' && typeof filterWorkers === 'function') filterWorkers();
    if (viewId === 'invoicesView' && typeof fetchSalesPage === 'function') fetchSalesPage(0);
    if (viewId === 'cashCloseView' && typeof filterCashClosures === 'function') filterCashClosures();
    if (viewId === 'measurementUnitsView' && typeof loadMeasurementUnits === 'function') loadMeasurementUnits();
    if (viewId === 'tarifasView' && typeof loadTariffs === 'function') loadTariffs();
    if (viewId === 'tiposIvaView' && typeof loadTaxRates === 'function') loadTaxRates();
    if (viewId === 'analyticsView' && typeof initDashboardCharts === 'function') initDashboardCharts();
    if (viewId === 'abonosView' && typeof filterAbonos === 'function') filterAbonos();
    if (viewId === 'preciosTempView' && typeof loadFuturePrices === 'function') loadFuturePrices();
    if (viewId === 'preciosMasivosView' && typeof loadBulkProducts === 'function') loadBulkProducts();

    // Scroll to top
    window.scrollTo(0, 0);
}

/**
 * Returns to the previous view in history.
 */
function backToPreviousView() {
    if (viewHistory.length > 0) {
        const lastView = viewHistory.pop();
        switchView(lastView, null, true);
    } else {
        // Fallback if history empty
        switchView('dashboardView');
    }
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
window.backToPreviousView = backToPreviousView;
window.showToast = showToast;
window.escHtml = escHtml;
window.escapeHtml = escapeHtml;
window.formatDateTime = formatDateTime;
window.formatDecimal = formatDecimal;
window.debounce = debounce;
window.previewImage = previewImage;
