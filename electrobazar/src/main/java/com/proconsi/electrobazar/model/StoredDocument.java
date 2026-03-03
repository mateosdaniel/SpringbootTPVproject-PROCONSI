package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_documents", indexes = {
        @Index(name = "idx_doc_type_ref", columnList = "document_type, reference_id"),
        @Index(name = "idx_reference_id", columnList = "reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /**
     * ID of the associated entity (Sale ID for TICKET, Invoice ID for INVOICE,
     * CashRegister ID for CASH_CLOSE)
     */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false, length = 200)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 50)
    @Builder.Default
    private String contentType = "application/pdf";

    @Lob
    @Column(columnDefinition = "LONGBLOB", nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
