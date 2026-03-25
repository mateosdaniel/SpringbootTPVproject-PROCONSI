import os

path = r'c:\Users\PracticasSoftware2\Desktop\TPV\electrobazar\src\main\resources\templates\admin\admin.html'

with open(path, 'r', encoding='utf-8', errors='replace') as f:
    content = f.read()

# Fix Sales row
old_sales = """                            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--text-main)"
                                th:text="${#numbers.formatDecimal(sale.totalAmount,1,2)} + ' """
new_sales = """                            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--text-main);text-align:right"
                                th:text="${#numbers.formatDecimal(sale.totalAmount,1,2)} + ' €"""

# Fix Returns row
old_returns = """                            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--danger)"
                                th:text="'-' + ${#numbers.formatDecimal(ret.totalRefunded,1,2)} + ' """
new_returns = """                            <td style="font-family:'Barlow Condensed',sans-serif;font-size:1rem;font-weight:700;color:var(--danger);text-align:right"
                                th:text="'-' + ${#numbers.formatDecimal(ret.totalRefunded,1,2)} + ' €"""

# Headers (already done? check index.html)
# Already done in previous successful multi_replace

content = content.replace(old_sales, new_sales)
content = content.replace(old_returns, new_returns)

# Replace any remaining corrupted euro placeholders
content = content.replace(" ' '", " ' €'")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed!")
