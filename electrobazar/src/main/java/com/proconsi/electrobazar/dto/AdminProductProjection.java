package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.MeasurementUnit;
import java.math.BigDecimal;

public interface AdminProductProjection {
    Long getId();
    String getNameEs();
    String getDescriptionEs();
    BigDecimal getPrice();
    BigDecimal getStock();
    String getCategoryName();
    MeasurementUnit getMeasurementUnit();
    BigDecimal getTaxVatRate();
    String getImageUrl();
    Boolean getActive();
    Integer getSalesRank();
}
