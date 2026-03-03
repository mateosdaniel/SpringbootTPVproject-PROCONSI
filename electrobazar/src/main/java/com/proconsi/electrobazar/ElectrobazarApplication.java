package com.proconsi.electrobazar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Electrobazar POS application.
 *
 * <p>Enabled features:</p>
 * <ul>
 *   <li>{@code @EnableCaching} — Activates Spring's annotation-driven cache management.
 *       Used by {@code ProductPriceService.getCurrentPrice()} via {@code @Cacheable}.</li>
 *   <li>{@code @EnableScheduling} — Activates Spring's scheduled task execution.
 *       Used by {@code PriceSchedulerTask} for daily price transition verification.</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ElectrobazarApplication {
	@jakarta.annotation.PostConstruct
	public void init() {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Madrid"));
	}

	public static void main(String[] args) {
		SpringApplication.run(ElectrobazarApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner initData(
			com.proconsi.electrobazar.service.WorkerService workerService) {
		return args -> {
			if (workerService.findAll().stream().noneMatch(w -> "root".equals(w.getUsername()))) {
				com.proconsi.electrobazar.model.Worker defaultWorker = new com.proconsi.electrobazar.model.Worker();
				defaultWorker.setUsername("root");
				defaultWorker.setPassword("root");
				defaultWorker.setActive(true);
				defaultWorker.setPermissions(java.util.Set.of("MANAGE_PRODUCTS_TPV", "CASH_CLOSE", "ADMIN_ACCESS"));
				workerService.save(defaultWorker);
				System.out.println(">>> Usuario ROOT creado por defecto (root/root)");
			}
		};
	}

}
