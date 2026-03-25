$p = 'c:\Users\PracticasSoftware2\Desktop\TPV\electrobazar\src\main\resources\templates\admin\admin.html'
$lines = [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8)

# Fix line 1702 (0-indexed: 1701) - Sales total cell style
$lines[1701] = "                            <td style=""font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--text-main);text-align:right"""
$lines[1702] = "                                th:text=""`${#numbers.formatDecimal(sale.totalAmount,1,2)} + ' ' + [char]0x20AC + """"

# Fix line 1824 (0-indexed: 1823) - Returns total cell style
$lines[1823] = "                            <td style=""font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--danger);text-align:right"""
$lines[1824] = "                                th:text=""'-' + `${#numbers.formatDecimal(ret.totalRefunded,1,2)} + ' ' + [char]0x20AC + """"

[System.IO.File]::WriteAllLines($p, $lines, [System.Text.Encoding]::UTF8)
Write-Host "Done"
