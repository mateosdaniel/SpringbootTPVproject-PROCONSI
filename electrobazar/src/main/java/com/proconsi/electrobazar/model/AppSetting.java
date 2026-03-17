package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity for simple key-value application settings.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    /** Unique identifier/key for the setting. */
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    /** Value associated with the key. */
    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;
}
