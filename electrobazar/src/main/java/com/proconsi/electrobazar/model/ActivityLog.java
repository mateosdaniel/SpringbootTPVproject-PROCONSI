package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action; // e.g. "CREATE_PRODUCT", "SALE", "CASH_CLOSE"

    @Column(nullable = false, length = 255)
    private String description;

    @Column(length = 100)
    private String username;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(length = 50)
    private String entityType; // e.g. "PRODUCT", "SALE", "CASH_DRAWER"

    @Column
    private Long entityId;
}
