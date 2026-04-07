package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a system user with a specific role and set of permissions.
 */
@Entity
@Table(name = "workers", indexes = {
        @Index(name = "idx_workers_active_username", columnList = "active, username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique login username. */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Column(nullable = false, unique = true)
    private String username;

    /** Hashed password. */
    @NotBlank(message = "La contraseña es obligatoria")
    @Column(nullable = false)
    private String password;

    /** Worker email (Used for password reset). */
    @Column(nullable = true, unique = true)
    private String email;

    /** Current temporary 6-digit PIN for password reset. */
    @Column(nullable = true)
    private String resetPin;

    /** Expiration date for the reset PIN. */
    @Column(nullable = true)
    private java.time.LocalDateTime resetPinExpiration;

    /** Main role assigned to the worker. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    /** Whether the account is active. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Resolves the effective set of permissions from the assigned role.
     * If the role name is 'ADMIN' or contains 'ACCESO_TOTAL_ADMIN', it acts as a 
     * master key and returns all available system permissions for UI visibility.
     * @return A set of permission keys.
     */
    public Set<String> getEffectivePermissions() {
        Set<String> perms = new HashSet<>();
        if (this.role != null) {
            if (this.role.getPermissions() != null) {
                perms.addAll(this.role.getPermissions());
            }
            
            // Master key check: either by specific permission OR by role name
            if (perms.contains("ACCESO_TOTAL_ADMIN") || "ADMIN".equals(this.role.getName())) {
                perms.addAll(java.util.Arrays.asList(
                    "ACCESO_TOTAL_ADMIN", "ADMIN_ACCESS", "ACCESO_TPV", "VER_VENTAS", 
                    "GESTION_INVENTARIO", "MANAGE_PRODUCTS_TPV", "GESTION_VENTAS_PAUSADAS", "HOLD_SALES",
                    "GESTION_CAJA", "CASH_CLOSE", "CIERRE_CAJA", "GESTION_DEVOLUCIONES", "RETURNS",
                    "GESTION_CLIENTES_CRM", "MODIFICAR_PREFERENCIAS", "PREFERENCES"
                ));
            }
        }
        return perms;
    }
}
