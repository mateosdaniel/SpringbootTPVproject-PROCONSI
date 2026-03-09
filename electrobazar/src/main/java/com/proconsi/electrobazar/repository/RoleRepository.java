package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT permission FROM role_permissions", nativeQuery = true)
    java.util.List<String> findAllPermissions();
}
