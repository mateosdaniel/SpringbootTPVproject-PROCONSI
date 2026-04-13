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
                    <td class="text-end">${u.decimalPlaces}</td>
                    <td>${u.promptOnAdd ? '<i class="bi bi-check-circle-fill text-success"></i>' : '<i class="bi bi-x-circle text-muted"></i>'}</td>
                    <td>${u.active ? '<i class="bi bi-check-circle-fill text-success"></i>' : '<i class="bi bi-x-circle text-muted"></i>'}</td>
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
    if (id && id > 0) {
        fetch('/api/measurement-units/' + id)
            .then(res => res.json())
            .then(u => {
                document.getElementById('measurementUnitId').value = u.id || '';
                document.getElementById('measurementUnitName').value = u.name || '';
                document.getElementById('measurementUnitSymbol').value = u.symbol || '';
                document.getElementById('measurementUnitDecimals').value = u.decimalPlaces || 0;
                document.getElementById('measurementUnitActive').checked = u.active !== false;
                document.getElementById('measurementUnitPromptOnAdd').checked = u.promptOnAdd === true;
                document.getElementById('measurementUnitModalLabel').textContent = 'Editar Unidad';
                measurementUnitModal.show();
            });
    } else {
        document.getElementById('measurementUnitId').value = '';
        document.getElementById('measurementUnitName').value = '';
        document.getElementById('measurementUnitSymbol').value = '';
        document.getElementById('measurementUnitDecimals').value = 0;
        document.getElementById('measurementUnitActive').checked = true;
        document.getElementById('measurementUnitPromptOnAdd').checked = false;
        document.getElementById('measurementUnitModalLabel').textContent = 'Nueva Unidad';
        measurementUnitModal.show();
    }
}

function saveMeasurementUnit() {
    const id = document.getElementById('measurementUnitId').value;
    const unit = {
        id: id ? parseInt(id) : null,
        name: document.getElementById('measurementUnitName').value.trim(),
        symbol: document.getElementById('measurementUnitSymbol').value.trim(),
        decimalPlaces: parseInt(document.getElementById('measurementUnitDecimals').value) || 0,
        active: document.getElementById('measurementUnitActive').checked,
        promptOnAdd: document.getElementById('measurementUnitPromptOnAdd').checked
    };

    const method = id && id > 0 ? 'PUT' : 'POST';
    const url = id && id > 0 ? '/api/measurement-units/' + id : '/api/measurement-units';

    fetch(url, {
        method: method,
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
