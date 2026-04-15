package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request DTO for calculating automatic promotions on a composing ticket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCalcRequest {
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private Long productId;
        private java.math.BigDecimal quantity;
        private java.math.BigDecimal unitPrice;
    }
}
