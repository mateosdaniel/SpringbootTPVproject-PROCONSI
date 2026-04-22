package com.proconsi.electrobazar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

/**
 * Registry for custom properties used via @Value to remove IDE warnings 
 * about unknown keys in application.properties.
 */
@Configuration
public class ExtraPropertiesConfig {

    @Configuration
    @ConfigurationProperties(prefix = "tpv.security")
    @Getter @Setter
    public static class TpvSecurityProperties {
        private String token;
    }

    @Configuration
    @ConfigurationProperties(prefix = "admin")
    @Getter @Setter
    public static class AdminProperties {
        private String pin;
    }

    @Configuration
    @ConfigurationProperties(prefix = "backup")
    @Getter @Setter
    public static class BackupProperties {
        private String localPath;
    }

    @Configuration
    @ConfigurationProperties(prefix = "mysqldump")
    @Getter @Setter
    public static class MysqldumpProperties {
        private String path;
    }
}
