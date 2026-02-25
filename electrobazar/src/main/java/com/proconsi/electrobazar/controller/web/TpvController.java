package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SaleService saleService;
    private final CustomerService customerService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;

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
            products = productService.findAllActiveWithCategory();
        }

        model.addAttribute("products", products);
        model.addAttribute("search", search);
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());
        model.addAttribute("todayRegister", cashRegisterService.getTodayRegister());

        return "tpv/index";
    }

    @PostMapping("/sale")
    public String processSale(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerType,
            @RequestParam List<Long> productIds,
            @RequestParam List<Integer> quantities,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        // Procesar cliente
        Customer customer = null;
        if (customerId != null) {
            customer = customerService.findById(customerId);
        } else if (customerName != null && !customerName.isBlank()) {
            // Crear cliente rápido (fallback for legacy or simplified non-invoice flow with
            // name)
            Customer.CustomerType type = (customerType != null && customerType.equals("COMPANY"))
                    ? Customer.CustomerType.COMPANY
                    : Customer.CustomerType.INDIVIDUAL;
            customer = customerService.save(Customer.builder()
                    .name(customerName)
                    .type(type)
                    .build());
        }

        // Procesar líneas de venta
        List<SaleLine> lines = java.util.stream.IntStream.range(0, productIds.size())
                .mapToObj(i -> {
                    Product product = productService.findById(productIds.get(i));
                    return SaleLine.builder()
                            .product(product)
                            .quantity(quantities.get(i))
                            .unitPrice(product.getPrice())
                            .build();
                }).collect(Collectors.toList());

        Sale sale = saleService.createSale(lines, paymentMethod, notes, customer);

        if (customer != null) {
            try {
                java.io.File pdfFile = pdfReportService.generateInvoiceReport(sale);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Factura generada y guardada en: " + pdfFile.getAbsolutePath());
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Venta completada pero hubo un error al generar el PDF de la factura.");
            }
        }

        return "redirect:/tpv/receipt/" + sale.getId();
    }

    @GetMapping("/receipt/{saleId}")
    public String showReceipt(@PathVariable Long saleId, HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);
        return "tpv/receipt";
    }

    @GetMapping("/cash-close")
    public String cashCloseForm(HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());
        model.addAttribute("todayRegister", cashRegisterService.getTodayRegister());
        model.addAttribute("cashSalesToday", saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH));
        model.addAttribute("cardSalesToday", saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD));
        return "tpv/cash-close";
    }

    @GetMapping("/preferences")
    public String preferences(HttpSession session) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        return "tpv/preferences";
    }

    @PostMapping("/cash-close")
    public String processCashClose(
            @RequestParam String closingBalance,
            @RequestParam(required = false) String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        // Convertir closingBalance, reemplazando coma por punto si es necesario
        String normalizedBalance = closingBalance.replace(",", ".");
        java.math.BigDecimal closingBalanceDecimal = new java.math.BigDecimal(normalizedBalance);

        CashRegister register = cashRegisterService.closeCashRegister(closingBalanceDecimal, notes);

        // Generar el PDF del cierre
        java.io.File pdfFile = pdfReportService.generateCashCloseReport(register);

        redirectAttributes.addFlashAttribute("successMessage",
                "Cierre de caja realizado. Diferencia: " + register.getDifference() + "€. PDF guardado en: "
                        + pdfFile.getAbsolutePath());

        return "redirect:/tpv";
    }
}
