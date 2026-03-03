package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

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

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "worker_permissions", joinColumns = @JoinColumn(name = "worker_id"))
    @Column(name = "permission")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Returns the effective permissions by combining manual permissions with role permissions.
     * If a worker has a role, the role's permissions are added to any manual permissions.
     */
    public Set<String> getEffectivePermissions() {
        Set<String> effective = new HashSet<>(this.permissions);
        if (this.role != null) {
            effective.addAll(this.role.getPermissions());
        }
        return effective;
    }
}
