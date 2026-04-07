package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryListingDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;
}
