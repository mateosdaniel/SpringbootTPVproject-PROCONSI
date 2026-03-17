package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Role;
import java.util.List;
import java.util.Optional;

/**
 * Interface for managing worker roles and their associated permissions.
 */
public interface RoleService {

    /**
     * Retrieves all roles defined in the system.
     * @return A list of Role entities.
     */
    List<Role> findAll();

    /**
     * Finds a role by ID.
     * @param id Primary key.
     * @return An Optional containing the Role.
     */
    Optional<Role> findById(Long id);

    /**
     * Persists or updates a role.
     * @param role The entity data.
     * @return The saved Role.
     */
    Role save(Role role);

    /**
     * Deletes a role from the system.
     * @param id The ID to delete.
     */
    void delete(Long id);

    /**
     * Retrieves a list of all raw permission strings available in the system.
     * @return A list of permission keys.
     */
    List<String> findAllPermissions();
}
