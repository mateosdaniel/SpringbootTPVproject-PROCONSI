/**
 * admin-unidades.js
 * Measurement units management functions.
 */

function loadMeasurementUnits() {
    fetch('/api/measurement-units')
        .then(res => res.json())
        .then(units => {
            const tbody = document.getElementById('measurementUnitsTableBody');
            if (!tbody) return;
            tbody.innerHTML = '';
            units.forEach(u => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${escHtml(u.name)}</td>
                    <td>${escHtml(u.symbol)}</td>
                    <td class="text-end">
                        <button class="btn-icon" onclick="openMeasurementUnitModal(${u.id}, '${escHtml(u.name)}', '${escHtml(u.symbol)}')" title="Editar"><i class="bi bi-pencil"></i></button>
                        <button class="btn-icon danger" onclick="deleteMeasurementUnit(${u.id})" title="Eliminar"><i class="bi bi-trash"></i></button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function openMeasurementUnitModal(id, name, symbol) {
    document.getElementById('measurementUnitId').value = id || '';
    document.getElementById('measurementUnitName').value = name || '';
    document.getElementById('measurementUnitSymbol').value = symbol || '';
    document.getElementById('measurementUnitModalLabel').textContent = id ? 'Editar Unidad' : 'Nueva Unidad';
    measurementUnitModal.show();
}

function saveMeasurementUnit() {
    const id = document.getElementById('measurementUnitId').value;
    const unit = {
        id: id ? parseInt(id) : null,
        name: document.getElementById('measurementUnitName').value.trim(),
        symbol: document.getElementById('measurementUnitSymbol').value.trim()
    };

    fetch('/api/measurement-units', {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(unit)
    }).then(res => {
        if (res.ok) {
            measurementUnitModal.hide();
            showToast('Unidad guardada');
            loadMeasurementUnits();
        }
    });
}

function deleteMeasurementUnit(id) {
    if (!confirm('¿Eliminar esta unidad?')) return;
    fetch('/api/measurement-units/' + id, { method: 'DELETE' })
        .then(res => {
            if (res.ok) {
                showToast('Unidad eliminada');
                loadMeasurementUnits();
            }
        });
}

// Global Exports
window.loadMeasurementUnits = loadMeasurementUnits;
window.openMeasurementUnitModal = openMeasurementUnitModal;
window.saveMeasurementUnit = saveMeasurementUnit;
window.deleteMeasurementUnit = deleteMeasurementUnit;
