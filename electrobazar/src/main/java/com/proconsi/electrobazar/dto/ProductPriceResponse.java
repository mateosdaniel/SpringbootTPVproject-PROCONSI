package com.proconsi.electrobazar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing a product price record returned by the API.
 */
@Data
@Builder(toBuilder = true)
public class ProductPriceResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private BigDecimal vatRate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endDate;

    private String label;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /** Convenience flag: true if this is the currently active price at query time. */
    private boolean currentlyActive;

    /** Price change compared to the previous price (absolute amount). Positive = increase, Negative = decrease. */
    private BigDecimal priceChange;

    /** Price change as percentage compared to the previous price. Positive = increase, Negative = decrease. */
    private BigDecimal priceChangePct;
}
