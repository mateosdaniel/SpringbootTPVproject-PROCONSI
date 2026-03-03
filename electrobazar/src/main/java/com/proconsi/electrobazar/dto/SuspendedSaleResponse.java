package com.proconsi.electrobazar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe serialisation view of a
 * {@link com.proconsi.electrobazar.model.SuspendedSale}.
 * Contains only primitive/value fields; avoids returning lazy-loaded JPA
 * proxies
 * or the full {@link com.proconsi.electrobazar.model.Worker} entity.
 */
@Data
@Builder
public class SuspendedSaleResponse {

    private Long id;
    private String label;

    /** Enum name: SUSPENDED, RESUMED, or CANCELLED. */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /** Username of the worker who suspended the sale, or null if unknown. */
    private String workerUsername;

    /** The individual cart lines included in the suspended sale. */
    private List<SuspendedSaleLineResponse> lines;

    // ── Nested line DTO ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class SuspendedSaleLineResponse {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
