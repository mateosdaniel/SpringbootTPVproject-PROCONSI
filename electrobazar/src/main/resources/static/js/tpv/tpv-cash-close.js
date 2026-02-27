const theoryAmountText = document.getElementById('theoryAmount').textContent.replace('\u20AC', '').trim();
// El formato español usa punto como separador de miles y coma como decimal (ej: 1.234,56)
// Primero eliminamos los puntos de miles, luego reemplazamos la coma decimal por punto
const theoryAmount = parseFloat(theoryAmountText.replace(/\./g, '').replace(',', '.')) || 0;
const closingBalanceInput = document.getElementById('closingBalance');

function updateDifference() {
    const realAmount = parseFloat(closingBalanceInput.value.replace(',', '.')) || 0;
    const difference = realAmount - theoryAmount;

    document.getElementById('realAmount').textContent = realAmount.toFixed(2) + '\u20AC';
    const diffAmountSpan = document.getElementById('diffAmount');

    // Cambiar color y texto según si hay diferencia
    if (Math.abs(difference) < 0.01) {
        diffAmountSpan.textContent = '0.00\u20AC';
        diffAmountSpan.style.color = 'var(--success)';
    } else if (difference > 0) {
        diffAmountSpan.textContent = '+' + difference.toFixed(2) + '\u20AC';
        diffAmountSpan.style.color = 'var(--success)';
    } else {
        diffAmountSpan.textContent = difference.toFixed(2) + '\u20AC';
        diffAmountSpan.style.color = 'var(--danger)';
    }
}

closingBalanceInput.addEventListener('input', updateDifference);
closingBalanceInput.addEventListener('change', updateDifference);
closingBalanceInput.addEventListener('keyup', updateDifference);

// -- updateDifference is called by event listeners defined above --
updateDifference();
