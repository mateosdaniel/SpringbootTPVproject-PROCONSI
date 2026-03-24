package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing an activity log entry to track system actions.
 */
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

    /** Action performed (e.g., "CREATE_PRODUCT", "SALE", "CASH_CLOSE"). */
    @Column(nullable = false, length = 50)
    private String action;

    /** Log level (e.g., "INFO", "WARN", "ERROR"). */
    @Column(length = 20)
    @Builder.Default
    private String level = "INFO";

    /** Detailed description of the activity. */
    @Column(nullable = false, length = 255)
    private String description;

    /** Username of the worker who performed the action. */
    @Column(length = 100)
    private String username;

    /** Timestamp of the activity. */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Type of the entity affected (e.g., "PRODUCT", "SALE"). */
    @Column(length = 50)
    private String entityType;

    /** ID of the entity affected. */
    @Column
    private Long entityId;
}
