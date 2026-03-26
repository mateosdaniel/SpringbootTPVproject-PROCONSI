package com.proconsi.electrobazar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.repository.RoleRepository;
import com.proconsi.electrobazar.model.Role;
import com.proconsi.electrobazar.model.Worker;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.TimeZone;

/**
 * Main entry point for the Electrobazar POS system.
 * 
 * This class initializes the Spring Boot context and configures core infrastructure:
 * 1. Caching: Enabled for high-performance pricing and catalog lookups.
 * 2. Scheduling: Enabled for daily automated tax changes and log maintenance.
 * 3. Environment: Loads localized settings (.env) and sets the systemic timezone to Europe/Madrid.
 * 4. Security: Auto-provisions the root administrator account.
 */
@SpringBootApplication(exclude = { 
    org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration.class 
})
@EnableCaching
@EnableScheduling
@EnableAsync
public class ElectrobazarApplication {

    /**
     * Standardizes the application timezone to Madrid (GMT+1/GMT+2).
     * This ensures all receipts, logs, and schedule calculations are consistent.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
    }

    /**
     * Bootstraps the application.
     * Manually loads .env variables into System properties for local development flexibility.
     */
    public static void main(String[] args) {
        try {
            File envFile = new File(".env");
            if (envFile.exists()) {
                Files.lines(envFile.toPath())
                    .filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        System.setProperty(parts[0].trim(), parts[1].trim());
                    });
            }
        } catch (IOException e) {
            // Silently fail if .env is missing or inaccessible
        }
        SpringApplication.run(ElectrobazarApplication.class, args);
    }

    /**
     * Ensures the presence of a 'root' user and 'ADMIN' role on every startup.
     * Use case: Automatic disaster recovery if the database is reset or compromised.
     */
    @Bean
    public CommandLineRunner initData(WorkerService workerService, RoleRepository roleRepository) {
        return args -> {
            // 1. Ensure ADMIN role with core permissions exists
            Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("ADMIN");
                newRole.setDescription("Administrador con acceso total al sistema");
                return newRole;
            });
            
            adminRole.setPermissions(Set.of(
                "ACCESO_TOTAL_ADMIN", 
                "ACCESO_TPV", 
                "VER_VENTAS", 
                "GESTION_INVENTARIO", 
                "GESTION_VENTAS_PAUSADAS", 
                "GESTION_CAJA", 
                "CIERRE_CAJA", 
                "GESTION_DEVOLUCIONES", 
                "GESTION_CLIENTES_CRM",
                "MODIFICAR_PREFERENCIAS"
            ));
            final Role finalAdminRole = roleRepository.save(adminRole);

            // 2. Ensure root worker is active and has the ADMIN role
            workerService.findAll().stream()
                .filter(w -> "root".equals(w.getUsername()))
                .findFirst()
                .ifPresentOrElse(w -> {
                    w.setRole(finalAdminRole);
                    w.setActive(true);
                    // Ensure the email is set to the user's test email if missing or using old placeholder
                    if (w.getEmail() == null || w.getEmail().isEmpty() || w.getEmail().equals("admin@electrobazar.com")) {
                        w.setEmail("danielmateos684@gmail.com");
                    }
                    workerService.save(w);
                }, () -> {
                    Worker defaultWorker = new Worker();
                    defaultWorker.setUsername("root");
                    defaultWorker.setPassword("r00t");
                    defaultWorker.setEmail("danielmateos684@gmail.com");
                    defaultWorker.setActive(true);
                    defaultWorker.setRole(finalAdminRole);
                    workerService.save(defaultWorker);
                });
        };
    }
}
