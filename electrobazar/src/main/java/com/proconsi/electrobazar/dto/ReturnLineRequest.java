package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a single line in a return request, submitted from the return form.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnLineRequest {

    /** The ID of the original SaleLine being returned. */
    private Long saleLineId;

    /**
     * Number of units to return. Must be > 0 and ≤ original quantity minus
     * already-returned.
     */
    private int quantity;
}
