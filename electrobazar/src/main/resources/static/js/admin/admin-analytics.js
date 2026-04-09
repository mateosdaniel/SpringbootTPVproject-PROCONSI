/**
 * admin-analytics.js
 * Analytics and dashboard charts logic.
 */

let salesChart = null;
let categoryChart = null;
let hourlyChartInstance = null;
let topProductsChartInstance = null;

function onAnalyticsPeriodChange() {
    const val = document.getElementById('analyticsPeriod').value;
    const dateInput = document.getElementById('analyticsDate');
    if (dateInput) {
        dateInput.style.display = (val === 'custom') ? 'block' : 'none';
        if (val === 'custom' && !dateInput.value) {
            dateInput.value = new Date().toISOString().split('T')[0];
        }
    }
    updateAnalytics();
}

function updateAnalytics() {
    const periodSelect = document.getElementById('analyticsPeriod');
    const period = periodSelect ? periodSelect.value : '7days';
    const periodText = periodSelect ? periodSelect.options[periodSelect.selectedIndex].text : '';
    const now = new Date();
    let fromDate = new Date();
    let toDate = new Date();
    let chartTitle = 'Tendencia de Ventas (' + periodText + ')';

    const toLocalISO = (d) => {
        const off = d.getTimezoneOffset() * 60000;
        return new Date(d.getTime() - off).toISOString().slice(0, 19);
    };

    toDate.setHours(23, 59, 59, 999);

    if (period === 'today') {
        fromDate.setHours(0, 0, 0, 0);
    } else if (period === '7days') {
        fromDate.setDate(now.getDate() - 6);
        fromDate.setHours(0, 0, 0, 0);
    } else if (period === '1month') {
        fromDate.setMonth(now.getMonth() - 1);
        fromDate.setHours(0, 0, 0, 0);
    } else if (period === '6months') {
        fromDate.setMonth(now.getMonth() - 6);
        fromDate.setHours(0, 0, 0, 0);
    } else if (period === '1year') {
        fromDate.setFullYear(now.getFullYear() - 1);
        fromDate.setHours(0, 0, 0, 0);
    } else if (period === 'custom') {
        const dVal = document.getElementById('analyticsDate').value;
        if (dVal) {
            fromDate = new Date(dVal);
            fromDate.setHours(0, 0, 0, 0);
            toDate = new Date(dVal);
            toDate.setHours(23, 59, 59, 999);
            chartTitle = 'Análisis del día ' + fromDate.toLocaleDateString();
        }
    } else if (period === 'all') {
        fromDate = new Date(0);
    }

    // Update title in UI
    const titleEl = document.getElementById('salesChartTitle');
    if (titleEl) {
        const span = titleEl.querySelector('span');
        if (span) span.textContent = chartTitle;
        else titleEl.textContent = chartTitle;
    }

    const url = `/api/sales/analytics?from=${toLocalISO(fromDate)}&to=${toLocalISO(toDate)}`;

    fetch(url)
        .then(r => { if (!r.ok) throw new Error('Status: ' + r.status); return r.json(); })
        .then(analytics => {
            initCharts(analytics, period, chartTitle);
        })
        .catch(err => {
            console.error('Error updating analytics:', err);
            showToast('Error al cargar datos de análisis', 'error');
        });
}

function initCharts(analytics, period, chartLabel) {
    if (!analytics) return;

    // KPI Counters
    if (document.getElementById('statTodayRevenue')) {
        document.getElementById('statTodayRevenue').textContent = 
            (analytics.totalRevenue || 0).toLocaleString('es-ES', { minimumFractionDigits: 2 }) + ' €';
    }
    if (document.getElementById('statTodaySales')) {
        document.getElementById('statTodaySales').textContent = analytics.totalSales || 0;
    }
    if (document.getElementById('statTopProduct')) {
        const topP = analytics.topProductName || '—';
        document.getElementById('statTopProduct').textContent = 
            topP.length > 20 ? topP.substring(0, 20) + '...' : topP;
    }
    if (document.getElementById('statLowStock')) {
        document.getElementById('statLowStock').textContent = analytics.lowStockCount || 0;
    }
    if (document.getElementById('statAvgTicket')) {
        document.getElementById('statAvgTicket').textContent = 
            (analytics.averageTicket || 0).toLocaleString('es-ES', { minimumFractionDigits: 2 }) + ' €';
    }
    if (document.getElementById('statCancellationRate')) {
        document.getElementById('statCancellationRate').textContent = 
            (analytics.cancellationRate || 0).toFixed(1) + '%';
    }

    // Trend Chart
    const trendData = analytics.revenueTrend || {};
    let labels = [];
    let datasetsData = [];

    Object.keys(trendData).sort().forEach(dateStr => {
        const d = new Date(dateStr);
        labels.push(d.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' }));
        datasetsData.push(trendData[dateStr]);
    });

    const ctxSales = document.getElementById('salesChart');
    if (ctxSales) {
        if (salesChart) salesChart.destroy();
        salesChart = new Chart(ctxSales.getContext('2d'), {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Ventas (€)',
                    data: datasetsData,
                    borderColor: '#f5a623',
                    backgroundColor: 'rgba(245, 166, 35, 0.1)',
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } }, // Hide legend to emphasize title
                scales: {
                    y: { grid: { color: 'rgba(255,255,255,0.05)' } },
                    x: { grid: { display: false } }
                }
            }
        });
    }

    // Category Distribution
    const catSummary = analytics.categoryDistribution || {};
    const ctxCat = document.getElementById('categoryChart');
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
            options: { cutout: '75%', plugins: { legend: { position: 'bottom' } } }
        });
    }

    // Hourly Trend
    const hourlyTrend = analytics.hourlyTrend || {};
    const ctxHourly = document.getElementById('hourlyChart');
    if (ctxHourly) {
        if (hourlyChartInstance) hourlyChartInstance.destroy();
        
        let hourlyLabels = [];
        let hourlyData = [];
        for (let i = 0; i < 24; i++) {
            hourlyLabels.push(i + ':00');
            hourlyData.push(hourlyTrend[i] || 0);
        }

        hourlyChartInstance = new Chart(ctxHourly.getContext('2d'), {
            type: 'bar',
            data: {
                labels: hourlyLabels,
                datasets: [{
                    label: 'Ventas (€)',
                    data: hourlyData,
                    backgroundColor: '#10b981' // Greenish
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { display: false } },
                    y: { grid: { color: 'rgba(255,255,255,0.05)' } }
                }
            }
        });
    }

    // Top Products Chart
    const topProds = analytics.topProducts || {};
    const ctxTop = document.getElementById('topProductsChart');
    if (ctxTop) {
        if (topProductsChartInstance) topProductsChartInstance.destroy();
        topProductsChartInstance = new Chart(ctxTop.getContext('2d'), {
            type: 'bar',
            data: {
                labels: Object.keys(topProds),
                datasets: [{
                    label: 'Ventas (€)',
                    data: Object.values(topProds),
                    backgroundColor: '#3b82f6'
                }]
            },
            options: {
                indexAxis: 'y', // Better for top list
                responsive: true,
                plugins: { legend: { display: false } }
            }
        });
    }
}

function initDashboardCharts() {
    updateAnalytics();
}

// Global Exports
window.onAnalyticsPeriodChange = onAnalyticsPeriodChange;
window.updateAnalytics = updateAnalytics;
window.initCharts = initCharts;
window.initDashboardCharts = initDashboardCharts;
