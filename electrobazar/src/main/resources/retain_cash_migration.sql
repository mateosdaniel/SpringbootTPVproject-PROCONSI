-- Migration: Add "retain cash for next shift" columns to cash_registers
-- Run this once against your electrobazar database if Hibernate ddl-auto is NOT set to update.
-- If spring.jpa.hibernate.ddl-auto=update (current default), Hibernate will add these columns automatically.

ALTER TABLE cash_registers
    ADD COLUMN IF NOT EXISTS retained_for_next_shift DECIMAL(10, 2) NULL,
    ADD COLUMN IF NOT EXISTS retained_by_worker_id   BIGINT           NULL,
    ADD CONSTRAINT fk_cr_retained_by_worker
        FOREIGN KEY (retained_by_worker_id)
        REFERENCES workers (id)
        ON DELETE SET NULL;
