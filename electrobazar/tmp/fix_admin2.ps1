$p = 'c:\Users\PracticasSoftware2\Desktop\TPV\electrobazar\src\main\resources\templates\admin\admin.html'
$bytes = [System.IO.File]::ReadAllBytes($p)
$content = [System.Text.Encoding]::UTF8.GetString($bytes)

# Sales total cell: add text-align:right
$oldSales = 'color:var(--text-main)"' + "`r`n" + '                                th:text="${#numbers.formatDecimal(sale.totalAmount,1,2)}'
$newSales = 'color:var(--text-main);text-align:right"' + "`r`n" + '                                th:text="${#numbers.formatDecimal(sale.totalAmount,1,2)}'
$content = $content.Replace($oldSales, $newSales)

# Returns total cell: add text-align:right
$oldRet = 'color:var(--danger)"' + "`r`n" + '                                th:text="''-'' + ${#numbers.formatDecimal(ret.totalRefunded,1,2)}'
$newRet = 'color:var(--danger);text-align:right"' + "`r`n" + '                                th:text="''-'' + ${#numbers.formatDecimal(ret.totalRefunded,1,2)}'
$content = $content.Replace($oldRet, $newRet)

$outBytes = [System.Text.Encoding]::UTF8.GetBytes($content)
[System.IO.File]::WriteAllBytes($p, $outBytes)
Write-Host "Done. Sales replaced: $($content.Contains('text-align:right'))"
