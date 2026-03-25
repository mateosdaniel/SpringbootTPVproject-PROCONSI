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
@Table(name = "workers")
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
     * Resolves the effective set of permissions based on the assigned role.
     * @return A set of permission keys.
     */
    public Set<String> getEffectivePermissions() {
        if (this.role != null && this.role.getPermissions() != null) {
            if (this.role.getPermissions().contains("ADMIN_ACCESS")) {
                return new HashSet<>(java.util.Arrays.asList(
                        "ADMIN_ACCESS", "CASH_CLOSE", "MANAGE_PRODUCTS_TPV",
                        "RETURNS", "HOLD_SALES", "PREFERENCES"));
            }
            return new HashSet<>(this.role.getPermissions());
        }
        return new HashSet<>();
    }
}
