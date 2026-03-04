-- Migration to add refund and withdrawal tracking to cash registers
ALTER TABLE cash_registers ADD COLUMN cash_refunds DECIMAL(10, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE cash_registers ADD COLUMN card_refunds DECIMAL(10, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE cash_registers ADD COLUMN total_withdrawals DECIMAL(10, 2) NOT NULL DEFAULT 0.00;
