package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.Customer.CustomerType;
import com.proconsi.electrobazar.model.Customer.IdDocumentType;

public interface AdminCustomerProjection {
    Long getId();
    String getName();
    String getTaxId();
    String getEmail();
    String getPhone();
    String getCity();
    CustomerType getType();
    Boolean getHasRecargoEquivalencia();
    Long getTariffId();
    String getTariffName();
    String getTariffColor();
    IdDocumentType getIdDocumentType();
    String getIdDocumentNumber();
    Boolean getActive();
}
