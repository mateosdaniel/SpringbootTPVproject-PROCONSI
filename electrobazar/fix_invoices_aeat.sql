-- Script para corregir la falta de columnas AEAT (VeriFactu) en las tablas de documentos legales
-- Esto soluciona errores de "Unknown column 'aeat_last_error'" en historiales y cobros.

-- 1. CORRECCIÓN PARA INVOICES (Facturas)
ALTER TABLE invoices 
ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME,
ADD COLUMN IF NOT EXISTS aeat_last_error TEXT,
ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

UPDATE invoices SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE invoices SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE invoices SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;

-- 2. CORRECCIÓN PARA TICKETS (Facturas Simplificadas)
ALTER TABLE tickets 
ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME,
ADD COLUMN IF NOT EXISTS aeat_last_error TEXT,
ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

UPDATE tickets SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE tickets SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE tickets SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;

-- 3. CORRECCIÓN PARA RECTIFICATIVE_INVOICES (Facturas Rectificativas)
ALTER TABLE rectificative_invoices 
ADD COLUMN IF NOT EXISTS hash_previous_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS hash_current_invoice VARCHAR(64),
ADD COLUMN IF NOT EXISTS aeat_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS aeat_submission_date DATETIME,
ADD COLUMN IF NOT EXISTS aeat_last_error TEXT,
ADD COLUMN IF NOT EXISTS aeat_retry_count INT DEFAULT 0;

UPDATE rectificative_invoices SET hash_previous_invoice = '0000000000000000' WHERE hash_previous_invoice IS NULL;
UPDATE rectificative_invoices SET hash_current_invoice = '0000000000000000' WHERE hash_current_invoice IS NULL;
UPDATE rectificative_invoices SET aeat_retry_count = 0 WHERE aeat_retry_count IS NULL;
