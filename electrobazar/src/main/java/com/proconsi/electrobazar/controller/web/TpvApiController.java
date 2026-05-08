package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for TPV API endpoints (price lookups, cart updates).
 */
@Slf4j
@RestController
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvApiController {

    private final ProductService productService;
    private final ProductPriceService productPriceService;
    private final TariffService tariffService;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final AdminPinService adminPinService;
    private final ActivityLogService activityLogService;

    @GetMapping("/api/products/{productId}/price")
    public Map<String, BigDecimal> getProductEffectivePrice(
            @PathVariable String productId,
            @RequestParam(required = false) Long tariffId) {

        log.debug("[TPV] getProductEffectivePrice: productId string={}, tariffId={}", productId, tariffId);
        Long id;
        try {
            id = Long.parseLong(productId);
        } catch (NumberFormatException e) {
            log.error("[TPV] Invalid productId format: {}", productId);
            return Collections.emptyMap();
        }

        Product product = productService.findById(id);
        if (product == null) {
            return Collections.emptyMap();
        }

        Long effectiveTariffId = tariffId;
        if (effectiveTariffId == null) {
            effectiveTariffId = tariffService.findByName(Tariff.MINORISTA)
                    .map(Tariff::getId)
                    .orElse(null);
        }

        ProductPrice activePrice = productPriceService.getCurrentPrice(id, LocalDateTime.now());
        BigDecimal basePrice = (activePrice != null) ? activePrice.getPrice() : product.getPrice();
        BigDecimal vatRate = (activePrice != null) ? activePrice.getVatRate()
                : (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                        ? product.getTaxRate().getVatRate()
                        : new BigDecimal("0.21"));

        BigDecimal finalPrice = null;
        BigDecimal priceWithRe = null;

        if (effectiveTariffId != null) {
            var historyEntry = tariffPriceHistoryRepository.findCurrentByProductAndTariff(id, effectiveTariffId);
            if (historyEntry.isPresent()) {
                finalPrice = historyEntry.get().getPriceWithVat();
                priceWithRe = historyEntry.get().getPriceWithRe();
            }
        }

        if (finalPrice == null) {
            if (effectiveTariffId != null) {
                finalPrice = tariffService.findById(effectiveTariffId)
                        .map(tariff -> {
                            BigDecimal discount = tariff.getDiscountPercentage();
                            if (discount == null || discount.compareTo(BigDecimal.ZERO) == 0) {
                                return basePrice;
                            }
                            BigDecimal factor = BigDecimal.ONE.subtract(
                                    discount.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                            return basePrice.multiply(factor);
                        })
                        .orElse(basePrice);
            } else {
                finalPrice = basePrice;
            }

            finalPrice = finalPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);
            BigDecimal netPrice = finalPrice.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP);
            priceWithRe = netPrice.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, BigDecimal> response = new HashMap<>();
        response.put("price", finalPrice);
        response.put("priceWithRe", priceWithRe);
        response.put("vatRate", vatRate);
        return response;
    }

    @PostMapping("/cart/update-price")
    public ResponseEntity<Map<String, Object>> updateCartItemPrice(
            @RequestParam Long productId,
            @RequestParam String newPrice,
            @RequestParam(defaultValue = "SESSION") String saveMode,
            @RequestParam(required = false) String adminPin,
            HttpSession session) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "No hay sesión activa."));
        }

        BigDecimal newPriceDecimal;
        try {
            newPriceDecimal = new BigDecimal(newPrice.replace(",", ".")).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "Precio inválido."));
        }

        if (newPriceDecimal.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", "El precio no puede ser negativo."));
        }

        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Producto no encontrado."));
        }

        String username = worker.getUsername();
        BigDecimal oldPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal displayNewPrice = newPriceDecimal.setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();

        if ("DATABASE".equalsIgnoreCase(saveMode)) {
            if (!adminPinService.verifyPin(adminPin)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "PIN de administrador incorrecto."));
            }

            product.setPrice(newPriceDecimal);
            productService.save(product);
            log.info("[PRICE] DB price change: product={} ({}), oldPrice={}, newPrice={}, by={}",
                    productId, product.getName(), oldPrice, displayNewPrice, username);

            activityLogService.logFiscalEvent("CAMBIO_PRECIO",
                    String.format(
                            "PRECIO_DB | Producto: '%s' (ID: %d) | Precio anterior: %.2f€ | Nuevo precio: %.2f€ | Cajero: %s",
                            product.getName(), productId, oldPrice, displayNewPrice, username),
                    username);

            result.put("savedToDb", true);
            result.put("message",
                    String.format("Precio de '%s' actualizado en BD: %.2f€", product.getName(), displayNewPrice));
        } else {
            log.info("[PRICE] Session price override: product={} ({}), oldPrice={}, newPrice={}, by={}",
                    productId, product.getName(), oldPrice, displayNewPrice, username);

            activityLogService.logFiscalEvent("CAMBIO_PRECIO",
                    String.format(
                            "PRECIO_SESION | Producto: '%s' (ID: %d) | Precio anterior: %.2f€ | Nuevo precio: %.2f€ | Cajero: %s",
                            product.getName(), productId, oldPrice, displayNewPrice, username),
                    username);

            result.put("savedToDb", false);
            result.put("message", String.format("Precio de '%s' modificado para esta venta: %.2f€", product.getName(),
                    displayNewPrice));
        }

        result.put("newPrice", displayNewPrice);
        result.put("productId", productId);
        return ResponseEntity.ok(result);
    }
}
