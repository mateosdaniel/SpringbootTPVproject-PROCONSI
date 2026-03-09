-- Migration for tax rates table
CREATE TABLE IF NOT EXISTS tax_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vat_rate DECIMAL(5,4) NOT NULL,
    re_rate DECIMAL(5,4) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE NOT NULL,
    valid_to DATE
);

-- Seed initial tax rates
INSERT INTO tax_rates (vat_rate, re_rate, description, active, valid_from) VALUES (0.21, 0.052, 'IVA General', TRUE, CURRENT_DATE);
INSERT INTO tax_rates (vat_rate, re_rate, description, active, valid_from) VALUES (0.10, 0.014, 'IVA Reducido', TRUE, CURRENT_DATE);
INSERT INTO tax_rates (vat_rate, re_rate, description, active, valid_from) VALUES (0.05, 0.0062, 'IVA Reducido Especial', TRUE, CURRENT_DATE);
INSERT INTO tax_rates (vat_rate, re_rate, description, active, valid_from) VALUES (0.04, 0.005, 'IVA Superreducido', TRUE, CURRENT_DATE);
INSERT INTO tax_rates (vat_rate, re_rate, description, active, valid_from) VALUES (0.02, 0.0026, 'IVA Superreducido Especial', TRUE, CURRENT_DATE);
