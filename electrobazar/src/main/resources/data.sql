-- Seed initial tax rates (standard Spanish rates + special reduced ones)
INSERT IGNORE INTO tax_rates (id, vat_rate, re_rate, description, active, valid_from) VALUES
(1, 0.2100, 0.0520, 'IVA General', 1, '2024-01-01'),
(2, 0.1000, 0.0140, 'IVA Reducido', 1, '2024-01-01'),
(3, 0.0500, 0.0062, 'IVA Reducido Especial', 1, '2024-01-01'),
(4, 0.0400, 0.0050, 'IVA Superreducido', 1, '2024-01-01'),
(5, 0.0200, 0.0026, 'IVA Superreducido Especial', 1, '2024-01-01');

-- Update Admin role to only need ADMIN_ACCESS (remove old permissions)
DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'ADMIN_ACCESS' FROM roles WHERE name = 'ADMIN';

-- Encargado role
INSERT IGNORE INTO roles (name, description) VALUES ('ENCARGADO', 'Encargado de tienda');
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'MANAGE_PRODUCTS_TPV' FROM roles WHERE name = 'ENCARGADO';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'CASH_CLOSE' FROM roles WHERE name = 'ENCARGADO';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'RETURNS' FROM roles WHERE name = 'ENCARGADO';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'HOLD_SALES' FROM roles WHERE name = 'ENCARGADO';

-- Vendedor role
INSERT IGNORE INTO roles (name, description) VALUES ('VENDEDOR', 'Vendedor de tienda');
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'CASH_CLOSE' FROM roles WHERE name = 'VENDEDOR';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'RETURNS' FROM roles WHERE name = 'VENDEDOR';
INSERT IGNORE INTO role_permissions (role_id, permission)
SELECT id, 'HOLD_SALES' FROM roles WHERE name = 'VENDEDOR';
