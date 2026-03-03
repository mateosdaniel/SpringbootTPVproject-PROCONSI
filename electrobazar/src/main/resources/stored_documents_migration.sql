-- Migration for PDF storage migration
-- This table replaces the file-system-based PDF storage for invoices and cash closures.

CREATE TABLE IF NOT EXISTS stored_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_type VARCHAR(20) NOT NULL,
    reference_id BIGINT NOT NULL,
    filename VARCHAR(200) NOT NULL,
    content_type VARCHAR(50) NOT NULL DEFAULT 'application/pdf',
    data LONGBLOB NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_doc_type_ref (document_type, reference_id),
    INDEX idx_reference_id (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
