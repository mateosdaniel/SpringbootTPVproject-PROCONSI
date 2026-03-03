-- Migration script for Cash Withdrawals
CREATE TABLE cash_withdrawals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cash_register_id BIGINT NOT NULL,
    worker_id BIGINT,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(255),
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_withdrawal_register FOREIGN KEY (cash_register_id) REFERENCES cash_registers(id),
    CONSTRAINT fk_withdrawal_worker FOREIGN KEY (worker_id) REFERENCES workers(id)
);
