package com.proconsi.electrobazar.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminReturnListingDTO {
    private Long id;
    private String returnNumber;
    private String originalNumber;
    private LocalDateTime createdAt;
    private String type; // TOTAL / PARCIAL
    private String reason;
    private String workerUsername;
    private String paymentMethod;
    private BigDecimal amount;
    private String ticketUrl;
}
