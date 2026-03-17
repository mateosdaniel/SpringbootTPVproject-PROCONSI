package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Role;
import com.proconsi.electrobazar.repository.RoleRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link RoleService}.
 * Manages user roles and their associated system permissions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(Long id) {
        return roleRepository.findById(id);
    }

    @Override
    public Role save(Role role) {
        boolean isNew = role.getId() == null;
        Role saved = roleRepository.save(role);

        activityLogService.logActivity(
                isNew ? "CREAR_ROL" : "ACTUALIZAR_ROL",
                (isNew ? "New role created: " : "Role updated: ") + saved.getName(),
                "Admin",
                "ROLE",
                saved.getId());

        return saved;
    }

    @Override
    public void delete(Long id) {
        roleRepository.findById(id).ifPresent(role -> {
            roleRepository.deleteById(id);
            activityLogService.logActivity(
                    "ELIMINAR_ROL",
                    "Role permanently deleted: " + role.getName(),
                    "Admin",
                    "ROLE",
                    id);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAllPermissions() {
        return roleRepository.findAllPermissions();
    }
}


