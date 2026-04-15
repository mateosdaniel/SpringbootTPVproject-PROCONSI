package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.AnalyticsSummaryDTO;
import com.proconsi.electrobazar.dto.SaleSummaryProjection;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.dto.WorkerSaleStatsDTO;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.CouponRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.PromotionService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;

/**
 * Implementation of {@link SaleService}.
 * Central service for processing TPV sales, managing tax breakdowns, stock
 * deductions,
 * and linking sales with customers, workers, and tariffs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final CashRegisterRepository cashRegisterRepository;
    private final ActivityLogService activityLogService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TariffRepository tariffRepository;
    private final InvoiceService invoiceService;
    private final CouponRepository couponRepository;
    private final CashRegisterService cashRegisterService;
    private final MessageSource messageSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "analyticsSummary", key = "#from.toLocalDate().toString() + '-' + #to.toLocalDate().toString()")
    public AnalyticsSummaryDTO getAnalyticsSummary(LocalDateTime from, LocalDateTime to) {
        LocalDate startDate = from.toLocalDate();
        LocalDate endDate = to.toLocalDate();

        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        boolean useMonthly = days > 31;

        BigDecimal totalRevenue;
        long totalSalesCount;
        long totalCancelledCount;
        BigDecimal totalCancelledAmount;
        BigDecimal cashTotal;
        BigDecimal cardTotal;
        BigDecimal mixedTotal;
        BigDecimal totalUnitsSold;
        Map<String, BigDecimal> trend = new TreeMap<>();
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        Map<Integer, BigDecimal> hourly = new TreeMap<>();
        Map<String, BigDecimal> topProds = new LinkedHashMap<>();
        String topProduct;

        if (useMonthly) {
            // ── 1. Totales desde monthly_sales_stats ──────────────────────────
            String summarySql = """
                        SELECT
                            COALESCE(SUM(total_revenue),0), COALESCE(SUM(sales_count),0),
                            COALESCE(SUM(cancelled_count),0), COALESCE(SUM(cancelled_total),0),
                            COALESCE(SUM(cash_total),0), COALESCE(SUM(card_total),0),
                            COALESCE(SUM(mixed_total),0), COALESCE(SUM(total_units_sold),0)
                        FROM monthly_sales_stats
                        WHERE stat_month BETWEEN DATE_FORMAT(?, '%Y-%m-01')
                        AND DATE_FORMAT(?, '%Y-%m-01')
                    """;
            Object[] summaryData = jdbcTemplate.queryForObject(summarySql, (rs, rowNum) -> new Object[] {
                    rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4),
                    rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)
            }, startDate, endDate);

            totalRevenue = (BigDecimal) summaryData[0];
            totalSalesCount = (long) summaryData[1];
            totalCancelledCount = (long) summaryData[2];
            totalCancelledAmount = (BigDecimal) summaryData[3];
            cashTotal = (BigDecimal) summaryData[4];
            cardTotal = (BigDecimal) summaryData[5];
            mixedTotal = (BigDecimal) summaryData[6];
            totalUnitsSold = (BigDecimal) summaryData[7];

            // ── 2. Tendencia mensual ───────────────────────────────────────────
            String trendSql = """
                        SELECT stat_month, total_revenue FROM monthly_sales_stats
                        WHERE stat_month BETWEEN DATE_FORMAT(?, '%Y-%m-01')
                        AND DATE_FORMAT(?, '%Y-%m-01')
                        ORDER BY stat_month ASC
                    """;
            List<Object[]> trendRows = jdbcTemplate.query(trendSql, (rs, rowNum) -> new Object[] {
                    rs.getDate("stat_month").toString(), rs.getBigDecimal("total_revenue")
            }, startDate, endDate);
            for (Object[] row : trendRows)
                trend.put((String) row[0], (BigDecimal) row[1]);

            // ── 3. Categorías ─────────────────────────────────────────────────
            String catSql = """
                        SELECT category_name, SUM(total_amount) as total
                        FROM monthly_category_stats
                        WHERE stat_month BETWEEN DATE_FORMAT(?, '%Y-%m-01')
                        AND DATE_FORMAT(?, '%Y-%m-01')
                        GROUP BY category_name ORDER BY total DESC
                    """;
            List<Object[]> catRowsShared = jdbcTemplate.query(catSql, (rs, rowNum) -> new Object[] {
                    rs.getString("category_name"), rs.getBigDecimal("total")
            }, startDate, endDate);
            for (Object[] row : catRowsShared)
                categories.put((String) row[0], (BigDecimal) row[1]);

            // ── 5. Top productos ──────────────────────────────────────────────
            String topProdsSql = """
                        SELECT product_name, SUM(revenue) as total
                        FROM monthly_product_stats
                        WHERE stat_month BETWEEN DATE_FORMAT(?, '%Y-%m-01')
                        AND DATE_FORMAT(?, '%Y-%m-01')
                        GROUP BY product_id, product_name
                        ORDER BY total DESC LIMIT 5
                    """;
            List<Object[]> prodRows = jdbcTemplate.query(topProdsSql, (rs, rowNum) -> new Object[] {
                    rs.getString("product_name"), rs.getBigDecimal("total")
            }, startDate, endDate);
            for (Object[] row : prodRows)
                topProds.put((String) row[0], (BigDecimal) row[1]);

            // ── 6. Top producto nombre ────────────────────────────────────────
            String topNameSql = """
                        SELECT product_name FROM monthly_product_stats
                        WHERE stat_month BETWEEN DATE_FORMAT(?, '%Y-%m-01')
                        AND DATE_FORMAT(?, '%Y-%m-01')
                        GROUP BY product_id, product_name
                        ORDER BY SUM(units_sold) DESC LIMIT 1
                    """;
            List<String> topNames = jdbcTemplate.queryForList(topNameSql, String.class, startDate, endDate);
            topProduct = topNames.isEmpty() ? null : topNames.get(0);

        } else {
            // ── Tablas diarias (lógica original) ─────────────────────────────
            String summarySql = """
                        SELECT
                            COALESCE(SUM(total_revenue), 0), COALESCE(SUM(sales_count), 0),
                            COALESCE(SUM(cancelled_count), 0), COALESCE(SUM(cancelled_total), 0),
                            COALESCE(SUM(cash_total), 0), COALESCE(SUM(card_total), 0),
                            COALESCE(SUM(mixed_total), 0), COALESCE(SUM(total_units_sold), 0)
                        FROM daily_sales_stats WHERE date BETWEEN ? AND ?
                    """;
            Object[] summaryData = jdbcTemplate.queryForObject(summarySql, (rs, rowNum) -> new Object[] {
                    rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4),
                    rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)
            }, startDate, endDate);

            totalRevenue = (BigDecimal) summaryData[0];
            totalSalesCount = (long) summaryData[1];
            totalCancelledCount = (long) summaryData[2];
            totalCancelledAmount = (BigDecimal) summaryData[3];
            cashTotal = (BigDecimal) summaryData[4];
            cardTotal = (BigDecimal) summaryData[5];
            mixedTotal = (BigDecimal) summaryData[6];
            totalUnitsSold = (BigDecimal) summaryData[7];

            topProduct = findTopProductNameBetween(from, to);

            String trendSql = "SELECT date, total_revenue FROM daily_sales_stats WHERE date BETWEEN ? AND ? ORDER BY date ASC";
            List<Object[]> trendRowsDaily = jdbcTemplate.query(trendSql, (rs, rowNum) -> new Object[] {
                    rs.getDate("date").toString(), rs.getBigDecimal("total_revenue")
            }, startDate, endDate);
            for (Object[] row : trendRowsDaily)
                trend.put((String) row[0], (BigDecimal) row[1]);

            String catSql = """
                        SELECT category_name, SUM(total_amount) as total, SUM(units_sold)
                        FROM daily_category_stats
                        WHERE date BETWEEN ? AND ?
                        GROUP BY category_name ORDER BY total DESC
                    """;
            List<Object[]> catRowsSharedDaily = jdbcTemplate.query(catSql, (rs, rowNum) -> new Object[] {
                    rs.getString("category_name"), rs.getBigDecimal("total")
            }, startDate, endDate);
            for (Object[] row : catRowsSharedDaily)
                categories.put((String) row[0], (BigDecimal) row[1]);

            List<Object[]> topProdsData = getTopProducts(from, to, 5);
            for (Object[] row : topProdsData) {
                topProds.put((String) row[0], (BigDecimal) row[1]);
            }
        }

        // ── Horario: siempre desde hourly_sales_stats ────────────────────────
        List<Object[]> hourlyRows = jdbcTemplate.query(
                "SELECT hour, SUM(total_revenue) FROM hourly_sales_stats WHERE date BETWEEN ? AND ? GROUP BY hour ORDER BY hour ASC",
                (rs, rowNum) -> new Object[] { rs.getInt(1), rs.getBigDecimal(2) },
                startDate, endDate);
        for (Object[] row : hourlyRows)
            hourly.put((Integer) row[0], (BigDecimal) row[1]);

        Long lowStockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE stock < 5 AND active = true", Long.class);

        return AnalyticsSummaryDTO.builder()
                .totalSales(totalSalesCount)
                .totalRevenue(totalRevenue)
                .cashRevenue(cashTotal.add(mixedTotal))
                .cardRevenue(cardTotal)
                .cancelledSales(totalCancelledCount)
                .cancelledRevenue(totalCancelledAmount)
                .topProductName(topProduct != null ? topProduct : "—")
                .lowStockCount(lowStockCount != null ? lowStockCount : 0L)
                .revenueTrend(trend)
                .categoryDistribution(categories)
                .averageTicket(totalSalesCount > 0
                        ? totalRevenue.divide(BigDecimal.valueOf(totalSalesCount), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .cancellationRate(totalSalesCount > 0
                        ? (double) totalCancelledCount / totalSalesCount * 100
                        : 0.0)
                .hourlyTrend(hourly)
                .topProducts(topProds)
                .build();
    }

    @org.springframework.cache.annotation.Cacheable(value = "topProductName", key = "#from.toLocalDate().toString() + '-' + #to.toLocalDate().toString()")
    public String findTopProductNameBetween(LocalDateTime from, LocalDateTime to) {
        return saleRepository.findTopProductNameBetween(from, to);
    }

    @org.springframework.cache.annotation.Cacheable(value = "hourlyRevenue", key = "#from.toLocalDate().toString() + '-' + #to.toLocalDate().toString()")
    public List<Object[]> getHourlyRevenue(LocalDateTime from, LocalDateTime to) {
        return saleRepository.getHourlyRevenue(from, to);
    }

    @org.springframework.cache.annotation.Cacheable(value = "topProducts", key = "#from.toLocalDate().toString() + '-' + #to.toLocalDate().toString() + '-' + #limit")
    public List<Object[]> getTopProducts(LocalDateTime from, LocalDateTime to, int limit) {
        return saleRepository.getTopProducts(from, to, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findAll() {
        return saleRepository.findAllWithDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Sale> findAll(Pageable pageable) {
        return saleRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Sale> search(String search, String type, String method, LocalDate date, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Sale> spec = com.proconsi.electrobazar.repository.specification.SaleSpecification
                .filterSales(search, type, method, date);
        return saleRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<Sale> searchSlice(String search, String type, String method, LocalDate date, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Sale> spec = com.proconsi.electrobazar.repository.specification.SaleSpecification
                .filterSales(search, type, method, date);
        return saleRepository.findSliceBy(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findToday() {
        return saleRepository.findToday();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findBetween(LocalDateTime from, LocalDateTime to) {
        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Sale> findBetween(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return saleRepository.findByCreatedAtBetween(from, to, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Sale> findBetween(LocalDateTime from, LocalDateTime to, Long workerId, Pageable pageable) {
        return saleRepository.findByCreatedAtBetweenAndWorkerId(from, to, workerId, pageable);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
            BigDecimal cashAmount, BigDecimal cardAmount, Worker worker) {
        return createSale(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, null, worker);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
            BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker) {
        return createSaleWithTariff(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, customer,
                worker, null);
    }

    private final PromotionService promotionService;

    // 2. Initial logic
    @Override
    @CacheEvict(value = "analyticsSummary", key = "T(java.time.LocalDate).now().toString() + '-' + T(java.time.LocalDate).now().toString()")
    public Sale createSaleWithCoupon(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer,
            Worker worker, Tariff tariffOverride, String couponCode) {

        // 1. Automatic NxM Promotions (Apply before calculating totals)
        lines = promotionService.applyNxMPromotions(lines);

        // 2. Resolve and Validate Coupon
        Coupon coupon = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Cupón no válido o inexistente: " + couponCode));

            if (!coupon.isValid()) {
                throw new IllegalStateException("El cupón ha expirado o ha alcanzado su límite de uso.");
            }
        }

        // 2. Initial logic
        cashRegisterService.checkOpenRegisterForToday();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one line.");
        }

        Tariff effective = (tariffOverride != null) ? tariffOverride
                : ((customer != null && customer.getTariff() != null) ? customer.getTariff()
                        : tariffRepository.findByName(Tariff.MINORISTA).orElse(null));

        String tariffName = (effective != null) ? effective.getName() : Tariff.MINORISTA;
        BigDecimal tariffDiscountPct = (effective != null && effective.getDiscountPercentage() != null)
                ? effective.getDiscountPercentage()
                : BigDecimal.ZERO;

        // 3. Calculation Loop for Subtotals
        BigDecimal subtotalBeforeCoupon = BigDecimal.ZERO;
        BigDecimal eligibleSubtotal = BigDecimal.ZERO;
        boolean applyRE = (customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia()));

        for (SaleLine line : lines) {
            BigDecimal lineTotal = line.getUnitPrice().multiply(line.getQuantity()).setScale(SCALE, ROUNDING);
            subtotalBeforeCoupon = subtotalBeforeCoupon.add(lineTotal);

            if (coupon != null) {
                boolean isEligible = false;
                if (line.getProduct() != null) {
                    isEligible = coupon.isApplicableTo(line.getProduct());
                } else {
                    isEligible = (coupon.getRestrictedProducts() == null || coupon.getRestrictedProducts().isEmpty()) &&
                            (coupon.getRestrictedCategories() == null || coupon.getRestrictedCategories().isEmpty());
                }

                if (isEligible) {
                    eligibleSubtotal = eligibleSubtotal.add(lineTotal);
                }
            }
        }

        // Calculate actual coupon discount amount based on ELIGIBLE items
        if (coupon != null && eligibleSubtotal.compareTo(BigDecimal.ZERO) > 0) {
            if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
                couponDiscount = eligibleSubtotal
                        .multiply(coupon.getDiscountValue().divide(new BigDecimal("100"), 10, ROUNDING));
            } else {
                couponDiscount = coupon.getDiscountValue();
            }
            if (couponDiscount.compareTo(eligibleSubtotal) > 0) {
                couponDiscount = eligibleSubtotal;
            }
        }

        // 4. Distribute coupon discount among ELIGIBLE lines and calculate totals
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;

        for (int i = 0; i < lines.size(); i++) {
            SaleLine line = lines.get(i);
            if (line.getProduct() != null) {
                validarStock(line.getProduct(), line.getQuantity());
                productService.decreaseStock(line.getProduct().getId(), line.getQuantity());
            }

            BigDecimal lineGrossBeforeCoupon = line.getUnitPrice();
            BigDecimal lineTotalBeforeCoupon = lineGrossBeforeCoupon.multiply(line.getQuantity());

            BigDecimal lineCouponDiscount = BigDecimal.ZERO;
            if (coupon != null && eligibleSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                boolean isEligible = false;
                if (line.getProduct() != null) {
                    isEligible = coupon.isApplicableTo(line.getProduct());
                } else {
                    isEligible = (coupon.getRestrictedProducts() == null || coupon.getRestrictedProducts().isEmpty()) &&
                            (coupon.getRestrictedCategories() == null || coupon.getRestrictedCategories().isEmpty());
                }

                if (isEligible) {
                    lineCouponDiscount = couponDiscount.multiply(lineTotalBeforeCoupon)
                            .divide(eligibleSubtotal, 10, ROUNDING);
                }
            }

            BigDecimal finalLineTotal = lineTotalBeforeCoupon.subtract(lineCouponDiscount);
            if (finalLineTotal.compareTo(BigDecimal.ZERO) < 0)
                finalLineTotal = BigDecimal.ZERO;

            BigDecimal finalQuantity = line.getQuantity();
            BigDecimal effectiveUnitPrice = finalQuantity.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : finalLineTotal.divide(finalQuantity, 10, ROUNDING);

            BigDecimal vatRate = (line.getVatRate() != null) ? line.getVatRate()
                    : (line.getProduct() != null && line.getProduct().getTaxRate() != null
                            ? line.getProduct().getTaxRate().getVatRate()
                            : new BigDecimal("0.21"));

            Long pId = (line.getProduct() != null) ? line.getProduct().getId() : null;
            String pName = (line.getProductName() != null) ? line.getProductName()
                    : (line.getProduct() != null ? line.getProduct().getName() : "Producto Comodín");

            TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(pId, pName, effectiveUnitPrice,
                    line.getQuantity(), vatRate, applyRE);

            line.setOriginalUnitPrice(lineGrossBeforeCoupon.setScale(SCALE, ROUNDING));
            line.setUnitPrice(effectiveUnitPrice.setScale(SCALE, ROUNDING));
            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount()); // Rounded to 2 in calculator
            line.setVatAmount(breakdown.getVatAmount()); // Rounded to 2 in calculator
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount()); // Rounded to 2 in calculator
            // Subtotal must be the sum of rounded components to avoid 1-cent mismatch
            line.setSubtotal(breakdown.getTotalAmount());

            total = total.add(line.getSubtotal());
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal finalTotal = total.setScale(SCALE, ROUNDING);

        // 5. Fiscal
        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal limit = (customer != null && customer.getType() == Customer.CustomerType.COMPANY)
                    ? new BigDecimal("1000")
                    : new BigDecimal("10000");
            if (finalTotal.compareTo(limit) > 0) {
                if (customer != null && customer.getType() == Customer.CustomerType.COMPANY)
                    throw new IllegalStateException("Excede el límite de efectivo real decreto.");
            }
        }

        BigDecimal change = null;
        BigDecimal actualCashAmt = BigDecimal.ZERO;
        BigDecimal actualCardAmt = BigDecimal.ZERO;

        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal rAmt = receivedAmount != null ? receivedAmount : finalTotal;
            change = rAmt.subtract(finalTotal);
            actualCashAmt = finalTotal;
        } else if (paymentMethod == PaymentMethod.CARD) {
            actualCardAmt = finalTotal;
        } else if (paymentMethod == PaymentMethod.MIXED) {
            BigDecimal totalReceived = (cardAmount != null ? cardAmount : BigDecimal.ZERO)
                    .add(cashAmount != null ? cashAmount : BigDecimal.ZERO);
            change = totalReceived.subtract(finalTotal);
            actualCardAmt = cardAmount != null ? cardAmount : BigDecimal.ZERO;
            actualCashAmt = (cashAmount != null ? cashAmount : BigDecimal.ZERO).subtract(change);
        }

        // --- NEW: Check if there's enough cash for change ---
        if (change != null && change.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentCash = cashRegisterService.getCurrentCashBalance();
            if (change.compareTo(currentCash) > 0) {
                String localizedMsg = messageSource.getMessage("error.insufficient_cash_change",
                        new Object[] { change, currentCash }, LocaleContextHolder.getLocale());
                throw new com.proconsi.electrobazar.exception.InsufficientCashException(localizedMsg);
            }
        }

        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod).totalAmount(finalTotal).totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING)).totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(couponDiscount.setScale(SCALE, ROUNDING)).applyRecargo(applyRE)
                .receivedAmount(receivedAmount).changeAmount(change)
                .cashAmount(actualCashAmt).cardAmount(actualCardAmt)
                .notes(notes).customer(customer).worker(worker).lines(lines).appliedTariff(tariffName)
                .coupon(coupon)
                .appliedDiscountPercentage(tariffDiscountPct.setScale(SCALE, ROUNDING)).build();

        lines.forEach(l -> l.setSale(sale));

        if (coupon != null) {
            coupon.setTimesUsed(coupon.getTimesUsed() + 1);
            couponRepository.save(coupon);
        }

        Sale saved = saleRepository.save(sale);
        String username = (worker != null) ? worker.getUsername() : "Anonymous";
        activityLogService
                .logActivity(
                        "VENTA", String.format("Venta procesada por %s. Total: %.2f € (Cupón: %s)", username,
                                finalTotal, (coupon != null ? coupon.getCode() : "Ninguno")),
                        username, "SALE", saved.getId());

        return saved;
    }

    @Override
    public Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker,
            Tariff tariffOverride) {
        return createSaleWithCoupon(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, customer,
                worker, tariffOverride, null);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalToday() {
        LocalDateTime start = getShiftStart();
        return saleRepository.sumTotalBetween(start, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long countToday() {
        return saleRepository.countByCreatedAtBetween(getShiftStart(), LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalByPaymentMethodToday(PaymentMethod method) {
        return saleRepository.sumTotalBetweenByPaymentMethod(getShiftStart(), LocalDateTime.now(), method)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleSummaryResponse getSummaryToday() {
        return mapProjection(saleRepository.getSummaryBetween(getShiftStart(), LocalDateTime.now()));
    }

    private SaleSummaryResponse mapProjection(SaleSummaryProjection p) {
        if (p == null)
            return new SaleSummaryResponse();
        return SaleSummaryResponse.builder()
                .totalSalesCount(p.getTotalSalesCount() != null ? p.getTotalSalesCount() : 0L)
                .totalSalesAmount(p.getTotalSalesAmount() != null ? p.getTotalSalesAmount() : BigDecimal.ZERO)
                .totalCashAmount(p.getTotalCashAmount() != null ? p.getTotalCashAmount() : BigDecimal.ZERO)
                .totalCardAmount(p.getTotalCardAmount() != null ? p.getTotalCardAmount() : BigDecimal.ZERO)
                .totalCancelledCount(p.getTotalCancelledCount() != null ? p.getTotalCancelledCount() : 0L)
                .totalCancelledAmount(
                        p.getTotalCancelledAmount() != null ? p.getTotalCancelledAmount() : BigDecimal.ZERO)
                .build();
    }

    private LocalDateTime getShiftStart() {
        return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(LocalDate.now().atStartOfDay());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerSaleStatsDTO> getWorkerStatsBetween(LocalDateTime from, LocalDateTime to) {
        return saleRepository.getWorkerStatsBetween(from, to);
    }

    @Override
    @Transactional
    public void cancelSale(Long id, Worker worker, String reason) {
        Sale sale = findById(id);
        if (sale.getStatus() == Sale.SaleStatus.CANCELLED)
            throw new IllegalStateException("Sale already cancelled.");

        if (sale.getInvoice() != null) {
            invoiceService.generateRectificativeInvoice(sale, reason);
        }

        sale.getLines().stream()
                .filter(l -> l.getProduct() != null)
                .forEach(l -> productService.increaseStock(l.getProduct().getId(), l.getQuantity()));

        sale.setStatus(Sale.SaleStatus.CANCELLED);
        sale.setNotes((sale.getNotes() != null ? sale.getNotes() + " | " : "") + "ANNULLED: " + reason);
        saleRepository.save(sale);

        String username = (worker != null) ? worker.getUsername() : "System";
        activityLogService.logActivity("ANULAR_VENTA",
                String.format("Venta nº %d anulada por %s. Motivo: %s", id, username, reason), username, "SALE", id);
    }

    private void validarStock(Product product, BigDecimal cantidad) {
        if (product != null && cantidad != null && cantidad.compareTo(product.getStock()) > 0) {
            throw new IllegalStateException(String.format("Stock insuficiente para %s. Disponible: %s, Requerido: %s",
                    product.getName(), product.getStock(), cantidad));
        }
    }
}