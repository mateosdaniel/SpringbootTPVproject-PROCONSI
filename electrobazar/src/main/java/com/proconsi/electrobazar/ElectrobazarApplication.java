package com.proconsi.electrobazar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
