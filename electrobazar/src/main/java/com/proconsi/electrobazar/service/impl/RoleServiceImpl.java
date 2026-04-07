package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Role;
import com.proconsi.electrobazar.repository.RoleRepository;
import com.proconsi.electrobazar.repository.specification.RoleSpecification;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    public Page<Role> getFilteredRoles(String search, List<String> permissions, Pageable pageable) {
        Specification<Role> spec = RoleSpecification.filterRoles(search, permissions);
        return roleRepository.findAll(spec, pageable);
    }

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
        roleRepository.findById(id).ifPresent(role -> {
            roleRepository.deleteById(id);
            activityLogService.logActivity(
                    "ELIMINAR_ROL",
                    "Rol eliminado permanentemente: " + role.getName(),
                    "Admin",
                    "ROLE",
                    id);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAllPermissions() {
        java.util.Set<String> all = new java.util.TreeSet<>(java.util.Arrays.asList(
            "ACCESO_TPV", "VER_VENTAS", 
            "GESTION_INVENTARIO", "MANAGE_PRODUCTS_TPV", "GESTION_VENTAS_PAUSADAS", "HOLD_SALES",
            "GESTION_CAJA", "CASH_CLOSE", "CIERRE_CAJA", "GESTION_DEVOLUCIONES", "RETURNS",
            "GESTION_CLIENTES_CRM", "MODIFICAR_PREFERENCIAS", "PREFERENCES"
        ));
        try {
            all.addAll(roleRepository.findAllPermissions());
        } catch (Exception e) {
            // Role table might not have permissions yet or has no data
        }
        all.removeAll(java.util.Arrays.asList("ACCESO_TOTAL_ADMIN", "ADMIN_ACCESS"));
        return new java.util.ArrayList<>(all);
    }
}
