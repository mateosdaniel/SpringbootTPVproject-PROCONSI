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
                (isNew ? "Nuevo rol creado: " : "Rol actualizado: ") + saved.getName(),
                "Admin",
                "ROLE",
                saved.getId());

        return saved;
    }

    @Override
    public void delete(Long id) {
        roleRepository.findById(id).ifPresent(r -> {
            roleRepository.deleteById(id);
            activityLogService.logActivity(
                    "ELIMINAR_ROL",
                    "Rol eliminado definitivamente: " + r.getName(),
                    "Admin",
                    "ROLE",
                    id);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<String> findAllPermissions() {
        return roleRepository.findAllPermissions();
    }
}
