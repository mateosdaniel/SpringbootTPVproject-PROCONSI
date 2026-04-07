package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.PromotionCalcRequest;
import com.proconsi.electrobazar.dto.PromotionCalcResponse;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Promotion;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.PromotionRepository;
import com.proconsi.electrobazar.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the automatic promotion engine.
 * Specifically handles NxM logic (e.g., 3x2) by identifying the cheapest items
 * in matching sets and applying a 100% discount on those specific units.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository repository;
    private final ProductRepository productRepository;

    @Override
    public PromotionCalcResponse calculateTotals(PromotionCalcRequest request) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            return PromotionCalcResponse.builder()
                    .totalDiscount(BigDecimal.ZERO)
                    .appliedPromotions(new ArrayList<>())
                    .build();
        }

        // Convert request into temporary SaleLines for the engine
        List<SaleLine> tempLines = new ArrayList<>();
        for (PromotionCalcRequest.Line l : request.getLines()) {
            Product p = productRepository.findById(l.getProductId()).orElse(null);
            if (p != null) {
                tempLines.add(SaleLine.builder()
                        .product(p)
                        .quantity(l.getQuantity())
                        .unitPrice(p.getPrice())
                        .originalUnitPrice(p.getPrice())
                        .build());
            }
        }

        BigDecimal totalOriginal = tempLines.stream()
                .map(l -> l.getUnitPrice().multiply(l.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply engine logic
        List<SaleLine> withPromos = applyNxMPromotions(tempLines);

        BigDecimal totalAfterPromos = withPromos.stream()
                .map(l -> l.getUnitPrice().multiply(l.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = totalOriginal.subtract(totalAfterPromos);
        
        // Count unique promos applied (rough estimate based on names if we track them, 
        // but for now let's just return a generic list or enhance applyNxM to return it)
        List<String> promoNames = repository.findAllActive().stream()
                .filter(Promotion::isValid)
                .map(Promotion::getName)
                .collect(Collectors.toList()); // Simplified: showing all potentials or we should refine

        return PromotionCalcResponse.builder()
                .totalDiscount(totalDiscount)
                .appliedPromotions(promoNames.stream().filter(n -> totalDiscount.compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<SaleLine> applyNxMPromotions(List<SaleLine> lines) {
        if (lines == null || lines.isEmpty()) return lines;

        List<Promotion> activePromos = repository.findAllActive().stream()
                .filter(Promotion::isValid)
                .collect(Collectors.toList());

        if (activePromos.isEmpty()) return lines;

        // Working copy to allow modification
        List<SaleLine> result = new ArrayList<>(lines);

        for (Promotion promo : activePromos) {
            // 1. Identify all lines that fall under this promotion and are NOT fractional
            List<SaleLine> promoLines = result.stream()
                    .filter(l -> {
                        Product p = l.getProduct();
                        if (!promo.isApplicableTo(p)) return false;
                        
                        if (p.getMeasurementUnit() != null && p.getMeasurementUnit().getDecimalPlaces() > 0) {
                            log.info("Promoción NxM omitida: producto fraccionario '{}'", p.getName());
                            return false;
                        }
                        return true;
                    })
                    // Sort ascending by unit price so the CHEAPEST items are the ones discounted (free)
                    .sorted((l1, l2) -> l1.getUnitPrice().compareTo(l2.getUnitPrice()))
                    .collect(Collectors.toList());

            // 2. Count total units
            BigDecimal totalUnits = promoLines.stream().map(SaleLine::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (promo.getNValue() <= 0) continue;

            // 3. Calculate how many items are "free" using BigDecimal precision
            BigDecimal nVal = BigDecimal.valueOf(promo.getNValue());
            BigDecimal mVal = BigDecimal.valueOf(promo.getMValue());
            
            BigDecimal timesApplicable;
            
            if (promo.getRestrictedProducts() != null && promo.getRestrictedProducts().size() > 1) {
                // Combo logic: must have at least 1 of EACH restricted product to form a group.
                BigDecimal minQty = null;
                for (Product rp : promo.getRestrictedProducts()) {
                    BigDecimal qtyInCart = promoLines.stream()
                            .filter(l -> l.getProduct() != null && l.getProduct().getId().equals(rp.getId()))
                            .map(SaleLine::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (minQty == null || qtyInCart.compareTo(minQty) < 0) {
                        minQty = qtyInCart;
                    }
                }
                BigDecimal setsByProducts = minQty != null ? minQty.divideToIntegralValue(BigDecimal.ONE) : BigDecimal.ZERO;
                BigDecimal setsByUnits = totalUnits.divideToIntegralValue(nVal);
                // The number of promotions applied is the minimum of "available complete combos" and "N-sized groups"
                timesApplicable = setsByProducts.compareTo(setsByUnits) < 0 ? setsByProducts : setsByUnits;
            } else {
                timesApplicable = totalUnits.divideToIntegralValue(nVal);
            }

            BigDecimal freeUnitsNeededB = timesApplicable.multiply(nVal.subtract(mVal));

            if (freeUnitsNeededB.compareTo(BigDecimal.ZERO) <= 0) continue;

            log.info("Applying Promotion '{}': Found {} free units out of {} qualifying items.", 
                     promo.getName(), freeUnitsNeededB, totalUnits);

            BigDecimal remainingToDiscount = freeUnitsNeededB;
            
            for (SaleLine line : promoLines) {
                if (remainingToDiscount.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal qtyInLine = line.getQuantity();
                
                if (qtyInLine.compareTo(remainingToDiscount) <= 0) {
                    // Entire line becomes free (100% discount)
                    line.setUnitPrice(BigDecimal.ZERO);
                    line.setDiscountPercentage(new BigDecimal("100.00"));
                    remainingToDiscount = remainingToDiscount.subtract(qtyInLine);
                } else {
                    // Split the line: part of it is free, part stays full price
                    BigDecimal freeInThisLine = remainingToDiscount;
                    BigDecimal paidInThisLine = qtyInLine.subtract(freeInThisLine);

                    // Update existing line for the PAID items
                    line.setQuantity(paidInThisLine);
                    
                    // Create NEW line for the FREE items
                    SaleLine freeLine = SaleLine.builder()
                            .product(line.getProduct())
                            .productName(line.getProductName())
                            .quantity(freeInThisLine)
                            .unitPrice(BigDecimal.ZERO)
                            .originalUnitPrice(line.getOriginalUnitPrice())
                            .discountPercentage(new BigDecimal("100.00"))
                            .vatRate(line.getVatRate())
                            .sale(line.getSale())
                            .build();
                    
                    result.add(freeLine);
                    remainingToDiscount = BigDecimal.ZERO;
                }
            }
        }

        return result;
    }

    @Override
    public List<Promotion> findAll() {
        return repository.findAll();
    }

    @Override
    public Promotion findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public Promotion save(Promotion promotion) {
        if (promotion.getRestrictedProducts() != null) {
            for (Product pRef : promotion.getRestrictedProducts()) {
                if (pRef.getId() != null) {
                    Product dbProduct = productRepository.findById(pRef.getId()).orElse(null);
                    if (dbProduct != null && dbProduct.getMeasurementUnit() != null && dbProduct.getMeasurementUnit().getDecimalPlaces() > 0) {
                        throw new IllegalArgumentException("El producto '" + dbProduct.getName() + "' es fraccionario y no puede incluirse en promociones NxM.");
                    }
                }
            }
        }
        return repository.save(promotion);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
