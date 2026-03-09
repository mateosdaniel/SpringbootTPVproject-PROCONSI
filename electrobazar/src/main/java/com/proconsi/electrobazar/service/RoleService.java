package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleService {
    List<Role> findAll();

    Optional<Role> findById(Long id);

    Role save(Role role);

    void delete(Long id);

    java.util.List<String> findAllPermissions();
}
