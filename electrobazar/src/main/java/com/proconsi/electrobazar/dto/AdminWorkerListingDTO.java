package com.proconsi.electrobazar.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminWorkerListingDTO {
    private Long id;
    private String username;
    private boolean active;
    private Long roleId;
    private String roleName;
    private List<String> permissions;
}
