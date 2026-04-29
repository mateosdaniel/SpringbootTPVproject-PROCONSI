package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubsanarRequest {
    private String nombreRazon;
    private String nif;
    private String address;
    private String postalCode;
    private String city;
}
