-- Script para adaptar la tabla de facturas al Reglamento Verifactu (Real Decreto 1007/2023)

ALTER TABLE invoices ADD COLUMN hash_previous_invoice VARCHAR(64);
ALTER TABLE invoices ADD COLUMN hash_current_invoice VARCHAR(64);

-- Inicialización para registros existentes (si los hubiera)
-- Se usa el valor inicial por defecto "0000000000000000" para cumplir la correlatividad inicial
UPDATE invoices SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE invoices SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;

-- Aplicar restricción de no nulidad
ALTER TABLE invoices MODIFY COLUMN hash_previous_invoice VARCHAR(64) NOT NULL;
ALTER TABLE invoices MODIFY COLUMN hash_current_invoice VARCHAR(64) NOT NULL;

-- Índice para acelerar la verificación de la cadena
CREATE INDEX idx_invoices_hash_chain ON invoices (serie, year, sequence_number);
