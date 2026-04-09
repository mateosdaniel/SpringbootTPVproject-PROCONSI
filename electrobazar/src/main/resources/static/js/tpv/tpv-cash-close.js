document.addEventListener('DOMContentLoaded', function () {
    const theoryAmountElem = document.getElementById('theoryAmount');
    if (!theoryAmountElem) return;

    const theoryAmount = parseFloat(theoryAmountElem.getAttribute('data-amount')) || 0;

    const closingBalanceInput = document.getElementById('closingBalance');
    const realAmountDisplay = document.getElementById('realAmount');
    const diffAmountSpan = document.getElementById('diffAmount');
    const retainToggle = document.getElementById('retainToggle');
    const retainInput = document.getElementById('retainInput');
    const hiddenRetained = document.getElementById('retainedAmount');
    const cashCloseForm = document.getElementById('cashCloseForm');

    const csrfTokenMeta = document.querySelector('meta[name="_csrf"], meta[name="csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"], meta[name="csrf_header"]');

    const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : null;
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    if (retainInput) retainInput._userEdited = false;

    function updateDifference() {
        if (!closingBalanceInput) return;

        const val = closingBalanceInput.value.replace(',', '.');
        const realAmount = parseFloat(val) || 0;
        const difference = realAmount - theoryAmount;

        if (realAmountDisplay) {
            realAmountDisplay.textContent =
                realAmount.toLocaleString('es-ES', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                }) + '€';
        }

        if (diffAmountSpan) {
            if (Math.abs(difference) < 0.01) {
                diffAmountSpan.className = 'fw-bold text-success';
                diffAmountSpan.textContent = '0,00€';
            } else {
                diffAmountSpan.textContent =
                    (difference > 0 ? '+' : '') +
                    difference.toLocaleString('es-ES', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    }) +
                    '€';

                diffAmountSpan.className = difference > 0
                    ? 'fw-bold text-success'
                    : 'fw-bold text-danger';
            }
        }

        if (retainToggle && retainToggle.checked && retainInput && !retainInput._userEdited) {
            retainInput.value = realAmount > 0 ? realAmount.toFixed(2) : '';
        }
    }

    function toggleRetainVisibility() {
        if (!retainToggle || !retainInput) return;

        const inputGroup = retainInput.closest('.input-group');
        if (!inputGroup) return;

        if (retainToggle.checked) {
            inputGroup.style.display = 'flex';
            updateDifference();
        } else {
            inputGroup.style.display = 'none';
            retainInput.value = '';
            retainInput._userEdited = false;
        }
    }

    function setHiddenRetainedAmount() {
        if (!hiddenRetained) return;

        if (retainToggle && retainToggle.checked && retainInput && retainInput.value) {
            hiddenRetained.value = retainInput.value.replace(',', '.');
        } else {
            hiddenRetained.value = '0';
        }
    }

    if (closingBalanceInput) {
        closingBalanceInput.addEventListener('input', updateDifference);
    }

    if (retainToggle) {
        retainToggle.addEventListener('change', toggleRetainVisibility);
    }

    if (retainInput) {
        retainInput.addEventListener('input', () => {
            retainInput._userEdited = true;
        });
    }

    if (cashCloseForm) {
        cashCloseForm.addEventListener('submit', async function (e) {
            e.preventDefault();

            setHiddenRetainedAmount();

            const submitBtn = cashCloseForm.querySelector('button[type="submit"], input[type="submit"]');
            if (submitBtn) submitBtn.disabled = true;

            try {
                const formData = new FormData(cashCloseForm);
                const headers = {
                    'X-Requested-With': 'XMLHttpRequest'
                };

                if (csrfToken) {
                    headers[csrfHeader] = csrfToken;
                }

                const response = await fetch(cashCloseForm.action, {
                    method: 'POST',
                    body: formData,
                    headers: headers,
                    credentials: 'same-origin'
                });

                if (!response.ok) {
                    throw new Error('Error al cerrar caja');
                }

                if (response.redirected && response.url) {
                    window.location.href = response.url;
                    return;
                }

                window.location.href = '/tpv';
            } catch (error) {
                console.error('Error al cerrar caja:', error);
                alert('No se pudo cerrar la caja. Revisa los datos e inténtalo otra vez.');
                if (submitBtn) submitBtn.disabled = false;
            }
        });
    }

    toggleRetainVisibility();
    updateDifference();
});