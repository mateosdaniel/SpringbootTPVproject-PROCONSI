package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Main UI Controller for the Point of Sale (TPV) interface.
 * Handles the main catalog view, sale processing, and receipt display.
 */
@Slf4j
@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SaleService saleService;
    private final CustomerService customerService;
    private final ProductPriceService productPriceService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final InvoiceService invoiceService;
    private final TicketService ticketService;
    private final TariffService tariffService;
    private final CompanySettingsService companySettingsService;
    private final CashRegisterService cashRegisterService;

    @GetMapping
    public String index(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            HttpSession session,
            Model model) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("selectedCategoryId", categoryId);

        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productService.search(search);
        } else if (categoryId != null) {
            products = productService.findByCategory(categoryId);
        } else {
            products = productService.getTopProductsByRank(100);
        }

        Optional<CashRegister> activeRegisterOpt = cashRegisterService.getOpenRegister();
        boolean isRegisterOpen = activeRegisterOpt.isPresent();
        model.addAttribute("isRegisterOpen", isRegisterOpen);

        model.addAttribute("products", products);
        model.addAttribute("search", search);

        SaleSummaryResponse summary = saleService.getSummaryToday();
        model.addAttribute("totalToday", summary.getTotalSalesAmount());
        model.addAttribute("countToday", summary.getTotalSalesCount());
        model.addAttribute("tariffs", tariffService.findAllActive());

        if (isRegisterOpen) {
            model.addAttribute("activeRegister", activeRegisterOpt.get());
        } else {
            CashRegisterOpenSuggestion suggestion = cashRegisterService.getOpenSuggestion();
            model.addAttribute("hasSuggestion", suggestion.isHasSuggestion());
            model.addAttribute("suggestedOpeningBalance", suggestion.getSuggestedBalance());
        }

        Map<Long, String> formattedPrices = new LinkedHashMap<>();
        for (Product p : products) {
            BigDecimal price = p.getPrice();
            if (price != null) {
                BigDecimal stripped = price.stripTrailingZeros();
                int decimals = Math.max(2, stripped.scale());
                formattedPrices.put(p.getId(), price.setScale(decimals, RoundingMode.HALF_UP).toPlainString());
            }
        }
        model.addAttribute("formattedPrices", formattedPrices);
        model.addAttribute("currentCash", cashRegisterService.getCurrentCashBalance());

        return "tpv/index";
    }

    @PostMapping("/sale")
    public String processSale(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerType,
            @RequestParam List<Long> productIds,
            @RequestParam List<BigDecimal> quantities,
            @RequestParam(required = false) List<String> unitPrices,
            @RequestParam(required = false) List<String> originalUnitPrices,
            @RequestParam(required = false) List<String> lineTariffNames,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String receivedAmount,
            @RequestParam(required = false) String cashAmount,
            @RequestParam(required = false) String cardAmount,
            @RequestParam(required = false, defaultValue = "false") Boolean requestInvoice,
            @RequestParam(required = false) Long tariffId,
            @RequestParam(required = false) String couponCode,
            @RequestParam(required = false) List<String> productNames,
            @RequestParam(required = false) List<String> vatRates,
            @RequestParam(required = false) List<Long> abonoIds,
            @RequestParam(required = false) BigDecimal manualAbonoAmount,
            @RequestParam(required = false) String tipoDocumentoParam,
            @RequestParam(required = false) String clientePuntualJson,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        Customer customer = null;
        if (customerId != null) {
            customer = customerService.findById(customerId);
        } else if (customerName != null && !customerName.isBlank()) {
            Customer.CustomerType type = (customerType != null && customerType.equals("COMPANY"))
                    ? Customer.CustomerType.COMPANY
                    : Customer.CustomerType.INDIVIDUAL;
            customer = customerService.save(Customer.builder()
                    .name(customerName)
                    .type(type)
                    .build());
        }

        Tariff tariffOverride = null;
        if (tariffId != null) {
            tariffOverride = tariffService.findById(tariffId).orElse(null);
        }

        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        List<SaleLine> lines = new ArrayList<>();

        List<Long> distinctIds = productIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        Map<Long, Product> productMap = productService.findAllByIds(distinctIds).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        for (int i = 0; i < productIds.size(); i++) {
            Long pid = productIds.get(i);
            Product product = (pid != null && pid > 0) ? productMap.get(pid) : null;
            BigDecimal qty = quantities.get(i);

            BigDecimal unitPrice = BigDecimal.ZERO;
            BigDecimal vatRate = new BigDecimal("0.21");

            if (unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null
                    && !unitPrices.get(i).isBlank()) {
                unitPrice = new BigDecimal(unitPrices.get(i).replace(",", "."));

                if (product != null) {
                    vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                            ? product.getTaxRate().getVatRate()
                            : new BigDecimal("0.21");
                } else if (vatRates != null && i < vatRates.size() && vatRates.get(i) != null
                        && !vatRates.get(i).isBlank()) {
                    vatRate = new BigDecimal(vatRates.get(i).replace(",", "."));
                }
            } else if (product != null) {
                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), LocalDateTime.now());
                if (activePrice != null) {
                    unitPrice = activePrice.getPrice();
                    vatRate = activePrice.getVatRate();
                } else {
                    unitPrice = product.getPrice();
                    vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                            ? product.getTaxRate().getVatRate()
                            : new BigDecimal("0.21");
                }
            }

            BigDecimal origUnitPrice = unitPrice;
            if (originalUnitPrices != null && i < originalUnitPrices.size()
                    && originalUnitPrices.get(i) != null && !originalUnitPrices.get(i).isBlank()) {
                try {
                    origUnitPrice = new BigDecimal(originalUnitPrices.get(i).replace(",", "."));
                } catch (NumberFormatException ignored) {
                }
            } else if (product != null) {
                origUnitPrice = product.getPrice();
            }

            String customName = (productNames != null && i < productNames.size() && productNames.get(i) != null
                    && !productNames.get(i).isBlank())
                            ? productNames.get(i)
                            : (product != null ? product.getName() : "Producto Comodín");

            BigDecimal discountPercentage = BigDecimal.ZERO;
            if (origUnitPrice.compareTo(BigDecimal.ZERO) > 0
                    && unitPrice.compareTo(origUnitPrice) < 0) {
                discountPercentage = origUnitPrice.subtract(unitPrice)
                        .divide(origUnitPrice, 10, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            lines.add(SaleLine.builder()
                    .product(product)
                    .productName(customName)
                    .quantity(qty)
                    .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                    .originalUnitPrice(origUnitPrice.setScale(2, RoundingMode.HALF_UP))
                    .discountPercentage(discountPercentage)
                    .vatRate(vatRate)
                    .build());
        }

        if (tariffId == null && tariffOverride == null && lineTariffNames != null) {
            String firstNonMinorista = lineTariffNames.stream()
                    .filter(t -> t != null && !t.isBlank() && !"MINORISTA".equalsIgnoreCase(t))
                    .findFirst().orElse(null);
            if (firstNonMinorista != null) {
                tariffOverride = tariffService.findByName(firstNonMinorista).orElse(null);
            }
        }

        Worker worker = (Worker) session.getAttribute("worker");
        Sale sale = null;
        try {
            BigDecimal receivedAmountDecimal = null;
            if (paymentMethod == PaymentMethod.CASH && receivedAmount != null && !receivedAmount.isBlank()) {
                try {
                    receivedAmountDecimal = new BigDecimal(receivedAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            BigDecimal cashAmountDecimal = null;
            if (cashAmount != null && !cashAmount.isBlank()) {
                try {
                    cashAmountDecimal = new BigDecimal(cashAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            BigDecimal cardAmountDecimal = null;
            if (cardAmount != null && !cardAmount.isBlank()) {
                try {
                    cardAmountDecimal = new BigDecimal(cardAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            sale = saleService.createSaleWithAbonos(lines, paymentMethod, notes, receivedAmountDecimal,
                    cashAmountDecimal, cardAmountDecimal, customer,
                    worker, tariffOverride, couponCode, abonoIds, manualAbonoAmount);
        } catch (IllegalStateException | IllegalArgumentException
                | com.proconsi.electrobazar.exception.InsufficientCashException e) {
            log.error("Error creating sale: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tpv";
        }

        TipoDocumento tipoDocumento;
        String puntualJson = null;
        boolean hasPuntual = clientePuntualJson != null && !clientePuntualJson.isBlank();

        if (hasPuntual) {
            tipoDocumento = TipoDocumento.FACTURA_COMPLETA;
            puntualJson = clientePuntualJson;
        } else if (customer != null) {
            tipoDocumento = "FACTURA_SIMPLIFICADA".equals(tipoDocumentoParam)
                    ? TipoDocumento.FACTURA_SIMPLIFICADA
                    : TipoDocumento.FACTURA_COMPLETA;
        } else {
            tipoDocumento = TipoDocumento.FACTURA_SIMPLIFICADA;
        }

        try {
            saleService.setDocumentType(sale, tipoDocumento, puntualJson);
        } catch (Exception e) {
            log.warn("Could not persist tipoDocumento for sale {}: {}", sale.getId(), e.getMessage());
        }

        try {
            Invoice invoice = null;
            if (tipoDocumento == TipoDocumento.FACTURA_COMPLETA) {
                invoice = invoiceService.createInvoice(sale);
                redirectAttributes.addFlashAttribute("invoice", invoice);
            }

            if (invoice != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Invoice " + invoice.getInvoiceNumber() + " generated.");
            } else {
                ticketService.createTicket(sale, applyRecargo);
            }
        } catch (Exception e) {
            log.error("Error creating document record for sale " + sale.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sale completed but there was an error generating the PDF document.");
        }

        return "redirect:/tpv/receipt/" + sale.getId() + "?autoPrint=true";
    }

    @GetMapping("/receipt/{saleId}")
    public String showReceipt(
            @PathVariable Long saleId,
            @RequestParam(required = false, defaultValue = "false") Boolean autoPrint,
            HttpSession session,
            Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);
        model.addAttribute("autoPrint", autoPrint);
        model.addAttribute("companySettings", companySettingsService.getSettings());

        if (!model.containsAttribute("invoice")) {
            invoiceService.findBySaleId(saleId)
                    .ifPresent(inv -> model.addAttribute("invoice", inv));
        }

        if (!model.containsAttribute("invoice") && !model.containsAttribute("ticket")) {
            ticketService.findBySaleId(saleId)
                    .ifPresent(t -> {
                        model.addAttribute("ticket", t);
                        model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(t));
                    });
        }

        if (!model.containsAttribute("taxBreakdowns")) {
            boolean applyRecargo = sale.getCustomer() != null
                    && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());
            List<TaxBreakdown> breakdowns = new ArrayList<>();
            for (SaleLine line : sale.getLines()) {
                BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
                Long pId = (line.getProduct() != null) ? line.getProduct().getId() : null;
                String pName = (line.getProductName() != null) ? line.getProductName()
                        : (line.getProduct() != null ? line.getProduct().getName() : "Producto Comodín");
                TaxBreakdown bd = recargoCalculator.calculateLineBreakdown(
                        pId, pName, line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo);
                breakdowns.add(bd);
            }
            model.addAttribute("lineBreakdowns", breakdowns);

            TreeMap<BigDecimal, TaxBreakdown> groupedTax = new TreeMap<>(BigDecimal::compareTo);
            for (TaxBreakdown tb : breakdowns) {
                groupedTax.merge(tb.getVatRate(), TaxBreakdown.builder()
                        .vatRate(tb.getVatRate())
                        .vatAmount(tb.getVatAmount())
                        .baseAmount(tb.getBaseAmount())
                        .recargoRate(tb.getRecargoRate())
                        .recargoAmount(tb.getRecargoAmount())
                        .totalAmount(tb.getTotalAmount())
                        .recargoApplied(tb.isRecargoApplied())
                        .build(),
                        (existing, newTb) -> {
                            existing.setBaseAmount(existing.getBaseAmount().add(newTb.getBaseAmount()));
                            existing.setVatAmount(existing.getVatAmount().add(newTb.getVatAmount()));
                            existing.setRecargoAmount(existing.getRecargoAmount().add(newTb.getRecargoAmount()));
                            existing.setTotalAmount(existing.getTotalAmount().add(newTb.getTotalAmount()));
                            return existing;
                        });
            }
            model.addAttribute("taxBreakdowns", new ArrayList<>(groupedTax.values()));
            model.addAttribute("applyRecargo", applyRecargo);
            BigDecimal totalBase = breakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalVat = breakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRecargo = breakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            model.addAttribute("totalBase", totalBase);
            model.addAttribute("totalVat", totalVat);
            model.addAttribute("totalRecargo", totalRecargo);

            BigDecimal totalOriginalBase = BigDecimal.ZERO;
            BigDecimal totalTariffDiscountNet = BigDecimal.ZERO;
            BigDecimal tariffPct = (sale.getAppliedDiscountPercentage() != null)
                    ? sale.getAppliedDiscountPercentage().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            for (SaleLine line : sale.getLines()) {
                BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
                BigDecimal divisor = BigDecimal.ONE.add(vatRate);

                BigDecimal catalogNet = (line.getOriginalUnitPrice() != null
                        && line.getOriginalUnitPrice().compareTo(BigDecimal.ZERO) > 0)
                                ? line.getOriginalUnitPrice().divide(divisor, 10, RoundingMode.HALF_UP)
                                : line.getUnitPrice().divide(divisor, 10, RoundingMode.HALF_UP);

                totalOriginalBase = totalOriginalBase.add(catalogNet.multiply(line.getQuantity()));

                BigDecimal lineTariffSavingNet = catalogNet.multiply(tariffPct).multiply(line.getQuantity());
                totalTariffDiscountNet = totalTariffDiscountNet.add(lineTariffSavingNet);
            }

            BigDecimal aggregateDiscountNet = totalOriginalBase.subtract(totalBase);
            BigDecimal couponDiscountNet = aggregateDiscountNet.subtract(totalTariffDiscountNet);

            model.addAttribute("totalOriginalBase", totalOriginalBase.setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalTariffDiscountNet", totalTariffDiscountNet.setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalCouponDiscountNet",
                    (couponDiscountNet.compareTo(BigDecimal.ZERO) > 0 ? couponDiscountNet : BigDecimal.ZERO).setScale(2,
                            RoundingMode.HALF_UP));
        }

        if (model.containsAttribute("invoice")) {
            Invoice invoice = (Invoice) model.asMap().get("invoice");
            model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(invoice));
            return "tpv/invoice";
        }

        return "tpv/receipt";
    }

    @GetMapping("/preferences")
    public String preferences(HttpSession session) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        return "tpv/preferences";
    }
}
