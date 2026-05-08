package com.proconsi.electrobazar.dto;

import java.util.Set;

public interface AdminWorkerProjection {
    Long getId();
    String getUsername();
    boolean isActive();
    Long getRoleId();
    String getRoleName();
    Set<String> getRolePermissions();
    boolean getHasSales();
}
