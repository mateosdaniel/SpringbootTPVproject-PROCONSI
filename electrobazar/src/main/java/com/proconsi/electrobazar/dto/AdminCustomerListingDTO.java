package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.Customer.CustomerType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminCustomerListingDTO {
    private Long id;
    private String name;
    private String taxId;
    private String email;
    private String phone;
    private String city;
    private CustomerType type;
    private boolean hasRecargoEquivalencia;
    private Long tariffId;
    private String tariffName;
    private String tariffColor;
}
