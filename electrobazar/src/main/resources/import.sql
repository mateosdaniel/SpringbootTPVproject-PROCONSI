-- ============================================
-- Electrobazar Database Schema Initialization
-- ============================================

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    iva_rate DECIMAL(5,4)
);

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    image_url VARCHAR(500),
    category_id BIGINT,
    iva_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2100,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- Workers table
CREATE TABLE IF NOT EXISTS workers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Worker permissions collection table
CREATE TABLE IF NOT EXISTS worker_permissions (
    worker_id BIGINT NOT NULL,
    permission VARCHAR(255) NOT NULL,
    PRIMARY KEY (worker_id, permission),
    FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE
);

-- Customers table
CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(50),
    postal_code VARCHAR(50),
    type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    has_recargo_equivalencia BOOLEAN NOT NULL DEFAULT FALSE
);

-- Sales table
CREATE TABLE IF NOT EXISTS sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    customer_id BIGINT,
    worker_id BIGINT,
    payment_method VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    received_amount DECIMAL(10,2),
    change_amount DECIMAL(10,2),
    notes VARCHAR(255),
    apply_recargo BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id)
);

-- Sale lines table
CREATE TABLE IF NOT EXISTS sale_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    vat_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2100,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Tickets table (Facturas simplificadas)
CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    serie VARCHAR(5) NOT NULL,
    year INT NOT NULL,
    sequence_number INT NOT NULL,
    sale_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    apply_recargo BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
);

-- Add vat_rate to sale_lines for existing databases (safe: no-op if column already exists)
ALTER TABLE sale_lines ADD COLUMN IF NOT EXISTS vat_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2100;

-- Product prices table
CREATE TABLE IF NOT EXISTS product_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    vat_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2100,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    label VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Cash registers table
CREATE TABLE IF NOT EXISTS cash_registers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    register_date DATE NOT NULL,
    opening_balance DECIMAL(10,2) NOT NULL DEFAULT 0,
    cash_sales DECIMAL(10,2) NOT NULL DEFAULT 0,
    card_sales DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_sales DECIMAL(10,2) NOT NULL DEFAULT 0,
    closing_balance DECIMAL(10,2) NOT NULL DEFAULT 0,
    difference DECIMAL(10,2),
    notes VARCHAR(255),
    opening_time TIMESTAMP,
    closed_at TIMESTAMP,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    worker_id BIGINT,
    FOREIGN KEY (worker_id) REFERENCES workers(id)
);

-- Activity logs table
CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    timestamp TIMESTAMP NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT
);

-- Create indexes for better performance
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_sales_created_at ON sales(created_at);
CREATE INDEX idx_sale_lines_sale ON sale_lines(sale_id);
CREATE INDEX idx_sale_lines_product ON sale_lines(product_id);
CREATE INDEX idx_product_prices_product ON product_prices(product_id);
CREATE INDEX idx_product_prices_dates ON product_prices(start_date, end_date);
CREATE INDEX idx_cash_registers_date ON cash_registers(register_date);
CREATE INDEX idx_activity_logs_timestamp ON activity_logs(timestamp);

-- ============================================
-- Invoice sequence counters table
-- Single source of truth for correlative numbering per serie + year
-- ============================================
CREATE TABLE IF NOT EXISTS invoice_sequences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serie VARCHAR(5) NOT NULL,
    year INT NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    CONSTRAINT uc_invoice_sequence_serie_year UNIQUE (serie, year)
);

-- ============================================
-- Invoices table
-- One invoice per sale (customer sales only)
-- ============================================
CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_number VARCHAR(20) NOT NULL,
    serie VARCHAR(5) NOT NULL,
    year INT NOT NULL,
    sequence_number INT NOT NULL,
    sale_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    rectified_by_id BIGINT,
    CONSTRAINT uc_invoices_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uc_invoices_sale_id UNIQUE (sale_id),
    FOREIGN KEY (sale_id) REFERENCES sales(id),
    FOREIGN KEY (rectified_by_id) REFERENCES invoices(id)
);

-- Safe additions for existing databases (no-op if tables already exist with these indexes)
CREATE INDEX IF NOT EXISTS idx_invoices_sale_id ON invoices(sale_id);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoice_sequences_serie_year ON invoice_sequences(serie, year);

-- ============================================
-- Returns table
-- Compensatory movements for sale returns
-- (sales are never deleted)
-- ============================================
CREATE TABLE IF NOT EXISTS returns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_number VARCHAR(20) NOT NULL,
    sale_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    worker_id BIGINT,
    reason VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    total_refunded DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    CONSTRAINT uc_returns_return_number UNIQUE (return_number),
    FOREIGN KEY (sale_id) REFERENCES sales(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id)
);

-- ============================================
-- Return lines table
-- Individual product lines within a return
-- ============================================
CREATE TABLE IF NOT EXISTS return_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    sale_line_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    vat_rate DECIMAL(5,4) NOT NULL,
    FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
    FOREIGN KEY (sale_line_id) REFERENCES sale_lines(id)
);

-- Safe indexes for return tables
CREATE INDEX IF NOT EXISTS idx_returns_sale_id ON returns(sale_id);
CREATE INDEX IF NOT EXISTS idx_return_lines_return ON return_lines(return_id);
CREATE INDEX IF NOT EXISTS idx_return_lines_sale_line ON return_lines(sale_line_id);

-- ============================================
-- Suspended sales table
-- Persists cart state across page reloads
-- ============================================
CREATE TABLE IF NOT EXISTS suspended_sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    worker_id BIGINT,
    label VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUSPENDED',
    FOREIGN KEY (worker_id) REFERENCES workers(id)
);

-- ============================================
-- Suspended sale lines table
-- Individual cart items within a suspended sale
-- ============================================
CREATE TABLE IF NOT EXISTS suspended_sale_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suspended_sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (suspended_sale_id) REFERENCES suspended_sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Safe indexes for suspended sales tables
CREATE INDEX IF NOT EXISTS idx_suspended_sales_status ON suspended_sales(status);
CREATE INDEX IF NOT EXISTS idx_suspended_sale_lines_sale ON suspended_sale_lines(suspended_sale_id);

