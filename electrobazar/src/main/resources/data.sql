-- Seed initial tax rates (standard Spanish rates + special reduced ones)
INSERT IGNORE INTO tax_rates (id, vat_rate, re_rate, description, active, valid_from) VALUES
(1, 0.2100, 0.0520, 'IVA General', 1, '2024-01-01'),
(2, 0.1000, 0.0140, 'IVA Reducido', 1, '2024-01-01'),
(3, 0.0500, 0.0062, 'IVA Reducido Especial', 1, '2024-01-01'),
(4, 0.0400, 0.0050, 'IVA Superreducido', 1, '2024-01-01'),
(5, 0.0200, 0.0026, 'IVA Superreducido Especial', 1, '2024-01-01');
