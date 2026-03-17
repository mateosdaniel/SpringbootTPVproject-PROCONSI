package com.proconsi.electrobazar.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing the response from the INE (National Statistics Institute) API for IPC (Consumer Price Index) data.
 */
@Data
public class IneIpcResponse {

    /** List of data points returned by the API. */
    @JsonProperty("Data")
    private List<IneDataPoint> data;

    /**
     * Represents a single data point from the INE API.
     */
    @Data
    public static class IneDataPoint {
        /** The value of the indicator. */
        @JsonProperty("Valor")
        private BigDecimal valor;

        /** The year of the data point. */
        @JsonProperty("Anyo")
        private Integer anyo;

        /** The month of the data point. */
        @JsonProperty("Mes")
        private Integer mes;
    }
}
