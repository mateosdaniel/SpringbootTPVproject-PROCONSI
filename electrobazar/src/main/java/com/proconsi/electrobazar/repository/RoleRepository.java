package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Role} entities.
 * Manages authorization roles and system permission metadata.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    /**
     * Finds a role by its unique name (e.g., "ROLE_ADMIN").
     */
    Optional<Role> findByName(String name);

    /**
     * Validates if a role name is already in use.
     */
    boolean existsByName(String name);

    /**
     * Retrieves a distinct list of all granular permission strings defined across all roles.
     * Uses a native query on the role_permissions join table.
     * 
     * @return List of unique permission tokens available in the system.
     */
    @Query(value = "SELECT DISTINCT permission FROM role_permissions", nativeQuery = true)
    List<String> findAllPermissions();
}


