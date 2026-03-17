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
INSERT IGNORE INTO company_settings (id, name, cif, address, city, postal_code, phone, email, website, registro_mercantil, invoice_footer_text)
SELECT 1, 'ElectroBazar S.L.', 'B12345678', 'Calle Principal 123', 'León', '24001', '987654321', 'info@electrobazar.com', 'www.electrobazar.com', 'Registro Mercantil de León, Tomo 1234, Folio 56, Hoja LE-7890', 'Gracias por su compra. Plazo de devolución: 15 días con ticket original.'
WHERE NOT EXISTS (SELECT 1 FROM company_settings WHERE id = 1);

-- 4. CLEANUP: Fix potentially corrupted roles if needed
-- We ensure the ADMIN role ONLY has its core master permissions
DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'ADMIN_ACCESS' FROM roles WHERE name = 'ADMIN';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'MANAGE_PRODUCTS_TPV' FROM roles WHERE name = 'ADMIN';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'CASH_CLOSE' FROM roles WHERE name = 'ADMIN';
