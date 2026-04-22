package com.proconsi.electrobazar.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Ensures critical database indexes are present for high-performance analytics.
 * With 1M+ records, Hibernate ddl-auto=update may fail to create complex indexes
 * due to lock timeouts. This component forces creation at startup if missing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(1)
public class DatabaseIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexesExist() {
        initializeTables();
        log.info("Checking critical database indexes for performance...");

        try {
            // 1. Sales Composite Index (Optimization Step 1) - Critical for filtering by date and status
            createIndexIfMissing("sales", "idx_sales_created_at_status", "created_at, status");

            // 2. Sale Lines Join Index (Optimization Step 2) - Critical for category/product joins
            // Including sale_id first then product_id covers the JOIN sales -> sale_lines
            createIndexIfMissing("sale_lines", "idx_sale_lines_sale_product", "sale_id, product_id");

            // 3. Payment Method Optimization - For daily/periodic revenue breakdown
            createIndexIfMissing("sales", "idx_sales_payment_created", "payment_method, created_at");

            // 4. Initial Statistics Population - If the summary table is empty, seed it from historical sales
            populateDailyStatsIfEmpty();

            log.info("Database performance indexing check completed.");
        } catch (Exception e) {

            log.error("Failed to ensure database indexes: {}", e.getMessage());
        }
    }

    private void populateDailyStatsIfEmpty() {
        // 1. Seed Global Daily Stats
        Long countSales = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_sales_stats", Long.class);
        if (countSales != null && countSales == 0) {
            log.warn("Daily stats table is empty. Seeding from historical sales data... (1M+ rows)");
            String seedSalesSql = """
                INSERT INTO daily_sales_stats (date, total_revenue, sales_count, cash_total, card_total, cancelled_count, cancelled_total)
                SELECT 
                    DATE(created_at) as date,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN total_amount ELSE 0 END), 0) as total_revenue,
                    COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) as sales_count,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND payment_method = 'CASH' THEN total_amount ELSE 0 END), 0) as cash_total,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND payment_method = 'CARD' THEN total_amount ELSE 0 END), 0) as card_total,
                    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) as cancelled_count,
                    COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN total_amount ELSE 0 END), 0) as cancelled_total
                FROM sales
                GROUP BY DATE(created_at)
            """;
            jdbcTemplate.execute(seedSalesSql);
            log.info("Daily global stats seeded.");
        }

        // 2. Seed Category Daily Stats
        Long countCats = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_category_stats", Long.class);
        if (countCats != null && countCats == 0) {
            log.warn("Daily category stats table is empty. Seeding from historical lines... (This may take a minute)");
            String seedCatsSql = """
                INSERT INTO daily_category_stats (date, category_name, total_amount)
                SELECT 
                    DATE(s.created_at) as date,
                    COALESCE(c.name_es, 'Sin Categoría') as category_name,
                    SUM(sl.subtotal) as total_amount
                FROM sales s
                JOIN sale_lines sl ON sl.sale_id = s.id
                JOIN products p ON p.id = sl.product_id
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE s.status = 'ACTIVE'
                GROUP BY DATE(s.created_at), category_name
            """;
            jdbcTemplate.execute(seedCatsSql);
            log.info("Daily category stats seeded.");
        }
    }

    private void initializeTables() {
        log.info("Ensuring analytics and favorite tables exist...");
        
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS daily_product_stats (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                date DATE NOT NULL,
                product_id BIGINT NOT NULL,
                product_name VARCHAR(255) NOT NULL,
                category_id BIGINT,
                category_name VARCHAR(255),
                units_sold DECIMAL(15,3) NOT NULL DEFAULT 0,
                revenue DECIMAL(15,2) NOT NULL DEFAULT 0,
                INDEX idx_prod_stats_date (date),
                INDEX idx_prod_stats_product (product_id),
                UNIQUE KEY uk_prod_stats_date_product (date, product_id)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS hourly_sales_stats (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                date DATE NOT NULL,
                hour TINYINT NOT NULL,
                total_revenue DECIMAL(15,2) NOT NULL DEFAULT 0,
                sales_count INT NOT NULL DEFAULT 0,
                INDEX idx_hourly_date (date),
                UNIQUE KEY uk_hourly_date_hour (date, hour)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_favorite_products (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                UNIQUE KEY uk_user_favorite (user_id, product_id)
            )
        """);

        // Ensure returns columns exist for analytics
        try {
            jdbcTemplate.execute("ALTER TABLE daily_sales_stats ADD COLUMN IF NOT EXISTS returns_count BIGINT DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE daily_sales_stats ADD COLUMN IF NOT EXISTS returns_total DECIMAL(15,2) DEFAULT 0.00");
            
            // Try to update monthly stats too if it exists as a table
            jdbcTemplate.execute("ALTER TABLE monthly_sales_stats ADD COLUMN IF NOT EXISTS returns_count BIGINT DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE monthly_sales_stats ADD COLUMN IF NOT EXISTS returns_total DECIMAL(15,2) DEFAULT 0.00");
        } catch (Exception e) {
            log.warn("Could not add returns columns to stats tables (might be views): {}", e.getMessage());
        }

        // Return deadline feature: ensure columns exist (idempotent on every restart)
        try {
            jdbcTemplate.execute(
                "ALTER TABLE company_settings ADD COLUMN IF NOT EXISTS return_deadline_days INT DEFAULT 15");
            log.info("company_settings.return_deadline_days column ensured.");
        } catch (Exception e) {
            log.warn("Could not add return_deadline_days to company_settings: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "ALTER TABLE tickets ADD COLUMN IF NOT EXISTS return_deadline_days INT DEFAULT 15");
            log.info("tickets.return_deadline_days column ensured.");
        } catch (Exception e) {
            log.warn("Could not add return_deadline_days to tickets: {}", e.getMessage());
        }
    }



    private void createIndexIfMissing(String tableName, String indexName, String columnList) {
        String checkSql = "SHOW INDEX FROM " + tableName + " WHERE Key_name = ?";
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(checkSql, indexName);

        if (indexes.isEmpty()) {
            log.warn("Index {} missing on table {}. Creating it now... (This may take a minute for 1M+ rows)", indexName, tableName);
            String createSql = String.format("CREATE INDEX %s ON %s (%s)", indexName, tableName, columnList);
            jdbcTemplate.execute(createSql);
            log.info("Index {} created successfully.", indexName);
        } else {
            log.debug("Index {} already exists on table {}.", indexName, tableName);
        }
    }
}
