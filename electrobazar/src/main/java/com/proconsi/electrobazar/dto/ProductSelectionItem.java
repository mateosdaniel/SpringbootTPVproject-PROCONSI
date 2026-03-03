package com.proconsi.electrobazar.dto;

import java.math.BigDecimal;

public record ProductSelectionItem(
    Long id, 
    String name, 
    BigDecimal currentPrice, 
    BigDecimal currentVat,
    Long categoryId,
    String categoryName
) {}
