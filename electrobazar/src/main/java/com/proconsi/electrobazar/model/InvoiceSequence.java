package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks the last used sequence number for a given invoice serie + year
 * combination.
 * Used as the single source of truth for correlative invoice numbering.
 *
 * <p>
 * The row for (serie="F", year=2026) is locked pessimistically when a new
 * invoice
 * is created to guarantee no duplicate sequence numbers under concurrent
 * requests.
 * </p>
 */
@Entity
@Table(name = "invoice_sequences", uniqueConstraints = @UniqueConstraint(name = "uc_invoice_sequence_serie_year", columnNames = {
        "serie", "year" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Invoice series prefix, e.g. "F". */
    @Column(nullable = false, length = 5)
    private String serie;

    /** The year this sequence counter belongs to, e.g. 2026. */
    @Column(nullable = false)
    private int year;

    /**
     * The last number issued in this serie/year.
     * Starts at 0; first invoice in the series will be 1.
     */
    @Column(name = "last_number", nullable = false)
    @Builder.Default
    private int lastNumber = 0;
}
