    -- =============================================================================
    -- ELECTOBAZAR INITIAL SEED DATA
    -- =============================================================================

    -- 1. SEED TAX RATES (Spanish standard and reduced rates)
    INSERT IGNORE INTO tax_rates (vat_rate, re_rate, description, active, valid_from) 
    SELECT 0.2100, 0.0520, 'IVA General', 1, '2024-01-01' 
    WHERE NOT EXISTS (SELECT 1 FROM tax_rates WHERE description = 'IVA General');

    INSERT IGNORE INTO tax_rates (vat_rate, re_rate, description, active, valid_from) 
    SELECT 0.1000, 0.0140, 'IVA Reducido', 1, '2024-01-01' 
    WHERE NOT EXISTS (SELECT 1 FROM tax_rates WHERE description = 'IVA Reducido');

    INSERT IGNORE INTO tax_rates (vat_rate, re_rate, description, active, valid_from) 
    SELECT 0.0500, 0.0062, 'IVA Reducido Especial', 1, '2024-01-01' 
    WHERE NOT EXISTS (SELECT 1 FROM tax_rates WHERE description = 'IVA Reducido Especial');

    INSERT IGNORE INTO tax_rates (vat_rate, re_rate, description, active, valid_from) 
    SELECT 0.0400, 0.0050, 'IVA Superreducido', 1, '2024-01-01' 
    WHERE NOT EXISTS (SELECT 1 FROM tax_rates WHERE description = 'IVA Superreducido');

    INSERT IGNORE INTO tax_rates (vat_rate, re_rate, description, active, valid_from) 
    SELECT 0.0200, 0.0026, 'IVA Superreducido Especial', 1, '2024-01-01' 
    WHERE NOT EXISTS (SELECT 1 FROM tax_rates WHERE description = 'IVA Superreducido Especial');

    -- 2. ROLES PROVISIONING (Non-admin defaults)
    INSERT IGNORE INTO roles (name, description) VALUES ('ENCARGADO', 'Encargado de tienda');
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'MANAGE_PRODUCTS_TPV' FROM roles WHERE name = 'ENCARGADO';
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'CASH_CLOSE' FROM roles WHERE name = 'ENCARGADO';
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'RETURNS' FROM roles WHERE name = 'ENCARGADO';
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'HOLD_SALES' FROM roles WHERE name = 'ENCARGADO';

    INSERT IGNORE INTO roles (name, description) VALUES ('VENDEDOR', 'Vendedor de tienda');
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'CASH_CLOSE' FROM roles WHERE name = 'VENDEDOR';
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'RETURNS' FROM roles WHERE name = 'VENDEDOR';
    INSERT IGNORE INTO role_permissions (role_id, permission)
    SELECT id, 'HOLD_SALES' FROM roles WHERE name = 'VENDEDOR';

    -- 3. COMPANY INITIAL CONFIGURATION
    INSERT INTO company_settings (id, name, app_name, cif, address, city, postal_code, phone, email, website, registro_mercantil, invoice_footer_text)
    VALUES (1, 'CERTIFICADO FISICA PRUEBAS', '(VERI*FACTU) CERTIFICADO FISICA PRUEBAS', '99999910G', 'Calle Prueba 123', 'Madrid', '28001', '912345678', 'test@aeat.es', 'www.aeat.es', 'Registro de Pruebas AEAT', 'Documento generado en entorno de pruebas VeriFactu.')
    ON DUPLICATE KEY UPDATE 
    name = VALUES(name), app_name = VALUES(app_name), cif = VALUES(cif);

    -- 4. ADMIN ROLE PROVISIONING
    -- The ADMIN role has a single master permission: ACCESO_TOTAL_ADMIN.
    -- That permission is accepted as a master key in all SecurityConfig rules.
    -- There is no need for individual per-feature permissions on this role.
    INSERT IGNORE INTO roles (name, description) VALUES ('ADMIN', 'Administrador del sistema');
    DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');
    INSERT IGNORE INTO role_permissions (role_id, permission) SELECT id, 'ACCESO_TOTAL_ADMIN' FROM roles WHERE name = 'ADMIN';

    -- 5. ENSURE ROOT USER HAS ADMIN ROLE
    -- Mandatory for existing accounts during refactor
    UPDATE workers SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE username = 'root';

    -- 6. MIGRATE PRODUCT IMAGE URLs from /uploads/products/ to /img/
    UPDATE products SET image_url = REPLACE(image_url, '/uploads/products/', '/img/')
    WHERE image_url LIKE '/uploads/products/%';

    -- 7. CLEAN UP IMAGE URLs: Remove UUID prefixes (e.g., /img/UUID_name.png -> /img/name.png)
    -- We look for /img/ followed by 36 chars of UUID and an underscore
    UPDATE products 
    SET image_url = CONCAT('/img/', SUBSTRING(image_url, 43))
    WHERE image_url LIKE '/img/%_%' AND LENGTH(SUBSTRING_INDEX(SUBSTRING(image_url, 6), '_', 1)) = 36;

-- 8. FIX ABONOS TABLE SCHEMA
ALTER TABLE abonos ADD COLUMN IF NOT EXISTS requires_full_use TINYINT(1) DEFAULT 1;
ALTER TABLE abonos ADD COLUMN IF NOT EXISTS code VARCHAR(20) UNIQUE;

-- 9. FIX SALES TABLE SCHEMA
ALTER TABLE sales ADD COLUMN IF NOT EXISTS abono_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00;

-- 10. FIX INVOICES TABLE SCHEMA (VeriFactu / AEAT)
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS aeat_last_error TEXT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

-- Initializing values for existing records to avoid NULL constraints issues
UPDATE invoices SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE invoices SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE invoices SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;

-- 11. FIX TICKETS TABLE SCHEMA (VeriFactu / AEAT)
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS aeat_last_error TEXT;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

UPDATE tickets SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE tickets SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE tickets SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;

-- 12. FIX RECTIFICATIVE_INVOICES TABLE SCHEMA (VeriFactu / AEAT)
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64);
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64);
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30);
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME;
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS aeat_last_error TEXT;
ALTER TABLE rectificative_invoices ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

UPDATE rectificative_invoices SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE rectificative_invoices SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE rectificative_invoices SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;

-- 13. Return deadline columns (snapshotted per-ticket at sale time)
ALTER TABLE company_settings ADD COLUMN IF NOT EXISTS return_deadline_days INT DEFAULT 15;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS return_deadline_days INT DEFAULT 15;
