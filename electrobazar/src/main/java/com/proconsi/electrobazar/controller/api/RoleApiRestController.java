package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Role;
import com.proconsi.electrobazar.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleApiRestController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<List<Role>> getAll() {
        List<Role> roles = roleService.findAll();
        // Force master permission in response for consistent UI display
        roles.forEach(r -> {
            if ("ADMIN".equalsIgnoreCase(r.getName()) && r.getPermissions().isEmpty()) {
                r.getPermissions().add("ACCESO_TOTAL_ADMIN");
            }
        });
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> getById(@PathVariable Long id) {
        return roleService.findById(id)
                .map(r -> {
                    // Force master permission in response for consistent UI display
                    if ("ADMIN".equalsIgnoreCase(r.getName()) && r.getPermissions().isEmpty()) {
                        r.getPermissions().add("ACCESO_TOTAL_ADMIN");
                    }
                    return ResponseEntity.ok(r);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Role request) {
        // Prevent creating duplicate ADMIN role
        if ("ADMIN".equalsIgnoreCase(request.getName())) {
            return ResponseEntity.badRequest().body("El nombre 'ADMIN' está reservado y no se puede duplicar.");
        }
        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .permissions(request.getPermissions() != null ? request.getPermissions() : new java.util.HashSet<>())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.save(role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Role> update(@PathVariable Long id, @RequestBody Role request) {
        return roleService.findById(id)
                .map(existing -> {
                    if (request.getName() != null)
                        existing.setName(request.getName());
                    existing.setDescription(request.getDescription());
                    if (request.getPermissions() != null) {
                        existing.setPermissions(request.getPermissions());
                    } else {
                        existing.getPermissions().clear();
                    }
                    return ResponseEntity.ok(roleService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return roleService.findById(id)
                .map(role -> {
                    // Prevent deleting the master ADMIN role
                    if ("ADMIN".equalsIgnoreCase(role.getName())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("El rol de administrador principal no se puede eliminar.");
                    }
                    roleService.delete(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
