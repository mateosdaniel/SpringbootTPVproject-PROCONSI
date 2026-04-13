package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduler to update the sales rank of products based on historical sales volume.
 * Optimized with a native query for bulk reset.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesRankScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        updateSalesRank();
    }

    @Scheduled(fixedRate = 21600000)
    public void updateSalesRank() {
        log.info("Starting automated sales rank update...");
        
        // 1. Get Top 100 selling products
        List<Product> topSelling = productRepository.findTopSellingProducts(PageRequest.of(0, 100));
        
        if (topSelling.isEmpty()) {
            log.warn("No top selling products found. Skipping ranking update.");
            return;
        }

        // 2. Identify and update Top 100
        for (int i = 0; i < topSelling.size(); i++) {
            Product p = topSelling.get(i);
            p.setSalesRank(i + 1);
            productRepository.save(p);
        }

        // 3. Optimized native query to reset others to 0 using JdbcTemplate
        String idsCsv = topSelling.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
        jdbcTemplate.update("UPDATE products SET sales_rank = 0 WHERE id NOT IN (" + idsCsv + ")");

        log.info("Sales rank update completed.");
    }
}
