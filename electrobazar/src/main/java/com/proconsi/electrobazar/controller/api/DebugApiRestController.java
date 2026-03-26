package com.proconsi.electrobazar.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * REST controller for system stress testing and debugging.
 * ONLY for development/test environments.
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugApiRestController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Seeds the database with a massive amount of sales to test performance and indexing.
     * Uses JdbcTemplate Batch updates for maximum performance, bypassing JPA overhead.
     * 
     * @param count Number of sales to generate (default 30,000)
     * @return Execution summary
     */
    @PostMapping("/stress-test-sales")
    @Transactional
    public String seedSales(@RequestParam(defaultValue = "30000") int count) {
        long startTime = System.currentTimeMillis();
        Random rand = new Random();

        // 1. Get some valid product IDs to make lines realistic
        List<Long> productIds = jdbcTemplate.queryForList("SELECT id FROM products WHERE active = true LIMIT 100", Long.class);
        if (productIds.isEmpty()) {
            return "Error: No hay productos activos en la BD para generar ventas.";
        }

        log.info("Starting stress test: Generating {} sales...", count);

        // 2. SQL statements for Batch Inserts
        String saleSql = "INSERT INTO sales (created_at, payment_method, total_amount, total_base, total_vat, total_recargo, applied_discount_percentage, total_discount, status, applied_tariff, apply_recargo) " +
                         "VALUES (?, ?, ?, ?, ?, 0, 0, 0, 'ACTIVE', 'MINORISTA', 0)";
        
        String lineSql = "INSERT INTO sale_lines (sale_id, product_id, product_name, quantity, unit_price, original_unit_price, base_price_net, vat_rate, subtotal, base_amount, vat_amount, recargo_rate, recargo_amount, discount_percentage) " +
                         "VALUES (?, ?, 'PRODUCTO PRUEBA', 1, ?, ?, ?, 0.21, ?, ?, ?, 0, 0, 0)";

        int batchSize = 1000;
        int totalLinesCount = 0;
        
        for (int i = 0; i < count; i += batchSize) {
            int currentBatch = Math.min(batchSize, count - i);
            
            for (int j = 0; j < currentBatch; j++) {
                LocalDateTime date = LocalDateTime.now().minusDays(rand.nextInt(365));
                BigDecimal total = BigDecimal.valueOf(rand.nextDouble() * 100 + 5).setScale(2, RoundingMode.HALF_UP);
                BigDecimal base = total.divide(BigDecimal.valueOf(1.21), 2, RoundingMode.HALF_UP);
                BigDecimal vat = total.subtract(base);

                // Insert Sale and get ID
                Object[] saleParams = new Object[]{
                    Timestamp.valueOf(date),
                    rand.nextBoolean() ? "CASH" : "CARD",
                    total, base, vat
                };
                
                // For simplicity and to avoid complex ID management in pure JDBC batch, 
                // we insert the sale first, then its line. 
                // In a REAL mass seeder, we'd use generated IDs, but this works for stress testing.
                jdbcTemplate.update(saleSql, saleParams);
                Long saleId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                
                Long productId = productIds.get(rand.nextInt(productIds.size()));
                
                jdbcTemplate.update(lineSql, saleId, productId, total, total, base, total, base, vat);
                totalLinesCount++;
            }
            log.info("Inserted batch {}/{}", i + currentBatch, count);
        }

        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;
        
        return String.format("Se han inyectado %d ventas y %d líneas correctamente en %.2f segundos.", count, totalLinesCount, seconds);
    }
}
