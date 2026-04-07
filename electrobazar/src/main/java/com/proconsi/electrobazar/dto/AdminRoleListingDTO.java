package com.proconsi.electrobazar.dto;

import lombok.*;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRoleListingDTO {
    private Long id;
    private String name;
    private String description;
    private Set<String> permissions;

    @com.fasterxml.jackson.annotation.JsonProperty("workerCount")
    private long workerCount;
}
