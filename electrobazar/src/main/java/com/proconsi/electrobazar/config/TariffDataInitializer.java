package com.proconsi.electrobazar.config;

import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.repository.TariffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Master data initializer for Tariffs.
 * Runs on application startup (CommandLineRunner).
 * Creates default tariffs (Retail, Wholesale, Employee) if they don't exist in the database.
 * If they already exist, it ensures the 'systemTariff' flag is active.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TariffDataInitializer implements CommandLineRunner {

    private final TariffRepository tariffRepository;

    @Override
    public void run(String... args) {
        seedSystemTariff(Tariff.MINORISTA, BigDecimal.ZERO,
                "Tarifa estándar para clientes minoristas. Sin descuento.");
        seedSystemTariff(Tariff.MAYORISTA, new BigDecimal("15.00"),
                "Tarifa mayorista con descuento del 15% sobre precio de catálogo.");
        seedSystemTariff(Tariff.EMPLEADO, new BigDecimal("10.00"),
                "Tarifa empleado con descuento del 10% sobre precio de catálogo.");
        log.info(">>> System tariffs initialized: MINORISTA (0%), MAYORISTA (15%), EMPLEADO (10%)");
    }

    private void seedSystemTariff(String name, BigDecimal defaultDiscount, String description) {
        tariffRepository.findByName(name).ifPresentOrElse(
                t -> {
                    // Enforce the system flag even if someone removed it
                    if (!Boolean.TRUE.equals(t.getSystemTariff())) {
                        t.setSystemTariff(true);
                        tariffRepository.save(t);
                    }
                },
                () -> {
                    Tariff tariff = Tariff.builder()
                            .name(name)
                            .discountPercentage(defaultDiscount)
                            .description(description)
                            .active(true)
                            .systemTariff(true)
                            .build();
                    tariffRepository.save(tariff);
                    log.info("  → Created system tariff: {} ({}%)", name, defaultDiscount);
                });
    }
}
