-- Migration script to add database indexes for query optimization

-- sales
CREATE INDEX IF NOT EXISTS idx_sales_created_at ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sales_payment_method ON sales(payment_method);

-- sale_lines
CREATE INDEX IF NOT EXISTS idx_sale_lines_sale_id ON sale_lines(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_lines_product_id ON sale_lines(product_id);

-- product_prices
CREATE INDEX IF NOT EXISTS idx_product_prices_lookup ON product_prices(product_id, start_date, end_date);

-- invoices
CREATE INDEX IF NOT EXISTS idx_invoices_sale_id ON invoices(sale_id);
CREATE INDEX IF NOT EXISTS idx_invoices_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);

-- suspended_sales
CREATE INDEX IF NOT EXISTS idx_suspended_sales_status ON suspended_sales(status);
CREATE INDEX IF NOT EXISTS idx_suspended_sales_worker_id ON suspended_sales(worker_id);


-- returns
CREATE INDEX IF NOT EXISTS idx_returns_sale_id ON returns(sale_id);

-- cash_withdrawals
CREATE INDEX IF NOT EXISTS idx_withdrawals_register_id ON cash_withdrawals(cash_register_id);

-- customers
CREATE INDEX IF NOT EXISTS idx_customers_tax_id ON customers(tax_id);
CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
