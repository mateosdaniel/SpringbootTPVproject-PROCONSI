package com.proconsi.electrobazar.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStatsScheduler {

    private final JdbcTemplate jdbcTemplate;

    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(10)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        updateDailyStats();
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void updateDailyStats() {
        checkSchema();
        log.info("Iniciando actualización de estadísticas diarias...");
        
        // 1. Determinar el rango de fechas faltantes
        LocalDate lastDate = jdbcTemplate.queryForObject("SELECT MAX(date) FROM daily_sales_stats", LocalDate.class);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        if (lastDate == null) {
            log.warn("No se encontró fecha máxima, buscando primera venta registrada...");
            LocalDate firstSale = jdbcTemplate.queryForObject("SELECT DATE(MIN(created_at)) FROM sales", LocalDate.class);
            lastDate = (firstSale != null) ? firstSale : LocalDate.now();
        }

        // 2. Procesar desde el último registro hasta ayer inclusive
        LocalDate current = lastDate;
        while (!current.isAfter(yesterday)) {
            processUpsertForDate(current);
            current = current.plusDays(1);
        }

        // 3. Procesar siempre HOY para mantener datos frescos
        processUpsertForDate(LocalDate.now());
        
        log.info("Actualización de estadísticas completada.");
    }

    private void processUpsertForDate(LocalDate targetDate) {
        log.info("Procesando estadísticas para la fecha: {}", targetDate);
        
        // BLOQUE 0: Resumen Global
        String sqlSales = """
            INSERT INTO daily_sales_stats 
              (date, total_revenue, cash_total, card_total, mixed_total,
               sales_count, cancelled_count, cancelled_total, returns_count, returns_total, total_units_sold)
            SELECT 
              DATE(s.created_at),
              COALESCE(SUM(CASE WHEN s.status='ACTIVE' THEN s.total_amount END), 0),
              COALESCE(SUM(CASE WHEN s.status='ACTIVE' AND s.payment_method='CASH' THEN s.total_amount END), 0),
              COALESCE(SUM(CASE WHEN s.status='ACTIVE' AND s.payment_method='CARD' THEN s.total_amount END), 0),
              COALESCE(SUM(CASE WHEN s.status='ACTIVE' AND s.payment_method='MIXED' THEN s.total_amount END), 0),
              COUNT(CASE WHEN s.status='ACTIVE' THEN 1 END),
              COUNT(CASE WHEN s.status='CANCELLED' THEN 1 END),
              COALESCE(SUM(CASE WHEN s.status='CANCELLED' THEN s.total_amount END), 0),
              (SELECT COUNT(*) FROM returns r WHERE DATE(r.created_at) = DATE(s.created_at)),
              (SELECT COALESCE(SUM(r.total_refunded), 0) FROM returns r WHERE DATE(r.created_at) = DATE(s.created_at)),
              COALESCE((SELECT SUM(sl.quantity) FROM sale_lines sl 
                JOIN sales s2 ON sl.sale_id = s2.id 
                WHERE DATE(s2.created_at) = ? 
                AND s2.status='ACTIVE'), 0)
            FROM sales s
            WHERE DATE(s.created_at) = ?
            GROUP BY DATE(s.created_at)
            ON DUPLICATE KEY UPDATE
              total_revenue = VALUES(total_revenue),
              cash_total = VALUES(cash_total),
              card_total = VALUES(card_total),
              mixed_total = VALUES(mixed_total),
              sales_count = VALUES(sales_count),
              cancelled_count = VALUES(cancelled_count),
              cancelled_total = VALUES(cancelled_total),
              returns_count = VALUES(returns_count),
              returns_total = VALUES(returns_total),
              total_units_sold = VALUES(total_units_sold)
        """;
        jdbcTemplate.update(sqlSales, targetDate, targetDate);

        // BLOQUE 1: Desglose por Producto
        String sqlProducts = """
            INSERT INTO daily_product_stats 
              (date, product_id, product_name, category_id, category_name, units_sold, revenue)
            SELECT 
              DATE(s.created_at),
              p.id,
              p.name_es,
              c.id,
              c.name_es,
              SUM(sl.quantity),
              SUM(sl.subtotal)
            FROM sales s
            JOIN sale_lines sl ON sl.sale_id = s.id
            JOIN products p ON p.id = sl.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE DATE(s.created_at) = ? AND s.status = 'ACTIVE'
            GROUP BY p.id, p.name_es, c.id, c.name_es
            ON DUPLICATE KEY UPDATE
              units_sold = VALUES(units_sold),
              revenue = VALUES(revenue),
              product_name = VALUES(product_name),
              category_name = VALUES(category_name)
        """;
        jdbcTemplate.update(sqlProducts, targetDate);

        // BLOQUE 2: Desglose por Hora
        String sqlHourly = """
            INSERT INTO hourly_sales_stats (date, hour, total_revenue, sales_count)
            SELECT 
              DATE(s.created_at),
              HOUR(s.created_at),
              SUM(s.total_amount),
              COUNT(*)
            FROM sales s
            WHERE DATE(s.created_at) = ? AND s.status = 'ACTIVE'
            GROUP BY DATE(s.created_at), HOUR(s.created_at)
            ON DUPLICATE KEY UPDATE
              total_revenue = VALUES(total_revenue),
              sales_count = VALUES(sales_count)
        """;
        jdbcTemplate.update(sqlHourly, targetDate);
    }

    private void checkSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE daily_sales_stats ADD COLUMN IF NOT EXISTS returns_count BIGINT DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE daily_sales_stats ADD COLUMN IF NOT EXISTS returns_total DECIMAL(15,2) DEFAULT 0.00");
            
            // Monthly stats might be a table or a view
            jdbcTemplate.execute("ALTER TABLE monthly_sales_stats ADD COLUMN IF NOT EXISTS returns_count BIGINT DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE monthly_sales_stats ADD COLUMN IF NOT EXISTS returns_total DECIMAL(15,2) DEFAULT 0.00");
        } catch (Exception e) {
            log.debug("Stats table schema update skipped (columns may exist or table is a view): {}", e.getMessage());
        }
    }
}
