package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.exception.InsufficientCashException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Controller for managing returns in the TPV.
 */
@Slf4j
@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvReturnController {

    private final SaleService saleService;
    private final ReturnService returnService;
    private final TicketService ticketService;
    private final InvoiceService invoiceService;
    private final MessageSource messageSource;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final CompanySettingsService companySettingsService;

    @GetMapping("/return/check")
    @ResponseBody
    public ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
        try {
            Long saleId = null;
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                Sale sale = saleService.findById(id);
                if (sale != null) {
                    saleId = id;
                }
            }

            if (saleId == null) {
                saleId = ticketService.findByTicketNumber(query)
                        .map(t -> t.getSale().getId())
                        .orElse(null);
            }

            if (saleId == null) {
                saleId = invoiceService.findByInvoiceNumber(query)
                        .map(i -> i.getSale().getId())
                        .orElse(null);
            }

            if (saleId != null) {
                return ResponseEntity
                        .ok(Collections.singletonMap("redirectUrl", "/tpv/return/" + saleId));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("errorMessage", getMessage("tpv.error.ticket_not_found", query)));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("errorMessage", getMessage("tpv.error.ticket_search", e.getMessage())));
        }
    }

    @GetMapping("/return/search")
    public String searchTicketForReturn(@RequestParam String query, RedirectAttributes redirectAttributes) {
        try {
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                if (saleService.findById(id) != null) {
                    return "redirect:/tpv/return/" + id;
                }
            }

            Optional<Ticket> ticketOpt = ticketService.findByTicketNumber(query);
            if (ticketOpt.isPresent()) {
                return "redirect:/tpv/return/" + ticketOpt.get().getSale().getId();
            }

            Optional<Invoice> invoiceOpt = invoiceService.findByInvoiceNumber(query);
            if (invoiceOpt.isPresent()) {
                return "redirect:/tpv/return/" + invoiceOpt.get().getSale().getId();
            }

            redirectAttributes.addFlashAttribute("errorMessage", getMessage("tpv.error.ticket_invoice_not_found", query));
            return "redirect:/tpv";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", getMessage("tpv.error.ticket_search", e.getMessage()));
            return "redirect:/tpv";
        }
    }

    @GetMapping("/return/{saleId}")
    public String showReturnForm(@PathVariable Long saleId, HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);
        model.addAttribute("paymentMethods", PaymentMethod.values());

        Map<Long, BigDecimal> alreadyReturned = new HashMap<>();
        List<SaleReturn> existingReturns = returnService.findByOriginalSaleId(saleId);

        for (SaleLine line : sale.getLines()) {
            BigDecimal returned = existingReturns.stream()
                    .flatMap(r -> r.getLines().stream())
                    .filter(rl -> rl.getSaleLine().getId().equals(line.getId()))
                    .map(ReturnLine::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            alreadyReturned.put(line.getId(), returned);
        }
        model.addAttribute("alreadyReturned", alreadyReturned);
        return "tpv/return-form";
    }

    @PostMapping("/return")
    public String processReturn(
            @RequestParam Long saleId,
            @RequestParam List<Long> saleLineIds,
            @RequestParam List<BigDecimal> quantities,
            @RequestParam(required = false) String reason,
            @RequestParam PaymentMethod paymentMethod,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return "redirect:/login";
        }

        if (saleLineIds == null || quantities == null || saleLineIds.size() != quantities.size()
                || saleLineIds.isEmpty()) {
            String localizedMsg = messageSource.getMessage("tpv.error.return_items",
                    null, LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("errorMessage", localizedMsg);
            return "redirect:/tpv/return/" + saleId;
        }

        List<ReturnLineRequest> lineRequests = new ArrayList<>();
        for (int i = 0; i < saleLineIds.size(); i++) {
            lineRequests.add(new ReturnLineRequest(
                    saleLineIds.get(i), quantities.get(i)));
        }

        try {
            SaleReturn saleReturn = returnService.processReturn(
                    saleId, lineRequests, reason, paymentMethod, worker);
            redirectAttributes.addFlashAttribute("saleReturn", saleReturn);
            return "redirect:/tpv/return-receipt/" + saleReturn.getId();
        } catch (IllegalArgumentException | InsufficientCashException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tpv/return/" + saleId;
        }
    }

    @GetMapping("/return-receipt/{returnId}")
    public String showReturnReceipt(
            @PathVariable Long returnId,
            @RequestParam(required = false, defaultValue = "false") Boolean autoPrint,
            HttpSession session,
            Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        SaleReturn saleReturn = (SaleReturn) model.getAttribute("saleReturn");
        if (saleReturn == null) {
            saleReturn = returnService.findById(returnId)
                    .orElseThrow(() -> new IllegalArgumentException("Return not found: " + returnId));
            model.addAttribute("saleReturn", saleReturn);
        }
        model.addAttribute("autoPrint", autoPrint);
        model.addAttribute("companySettings", companySettingsService.getSettings());

        Sale originalSale = saleReturn.getOriginalSale();
        boolean applyRecargo = originalSale.isApplyRecargo();
        List<TaxBreakdown> standardBreakdowns = new ArrayList<>();

        for (ReturnLine line : saleReturn.getLines()) {
            if (line.getSaleLine() == null || line.getSaleLine().getProduct() == null)
                continue;

            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            TaxBreakdown bd = recargoCalculator.calculateLineBreakdown(
                    line.getSaleLine().getProduct().getId(), line.getSaleLine().getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo);
            standardBreakdowns.add(bd);
        }

        model.addAttribute("applyRecargo", applyRecargo);
        model.addAttribute("totalBase", standardBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("totalVat", standardBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("totalRecargo", standardBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

        if (saleReturn.getRectificativeInvoice() != null) {
            List<TaxBreakdown> negativeBreakdowns = new ArrayList<>();
            for (TaxBreakdown bd : standardBreakdowns) {
                negativeBreakdowns.add(TaxBreakdown.builder()
                        .productId(bd.getProductId())
                        .productName(bd.getProductName())
                        .unitPrice(bd.getUnitPrice())
                        .quantity(bd.getQuantity().negate())
                        .baseAmount(bd.getBaseAmount().negate())
                        .vatRate(bd.getVatRate())
                        .vatAmount(bd.getVatAmount().negate())
                        .recargoRate(bd.getRecargoRate())
                        .recargoAmount(bd.getRecargoAmount().negate())
                        .totalAmount(bd.getTotalAmount().negate())
                        .recargoApplied(applyRecargo)
                        .build());
            }
            model.addAttribute("taxBreakdowns", negativeBreakdowns);
            model.addAttribute("totalBase", negativeBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalVat", negativeBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalRecargo", negativeBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

            List<Map<String, Object>> negativeLines = new ArrayList<>();
            for (ReturnLine line : saleReturn.getLines()) {
                if (line.getSaleLine() == null || line.getSaleLine().getProduct() == null)
                    continue;
                Map<String, Object> map = new HashMap<>();
                map.put("name", line.getSaleLine().getProduct().getName());
                map.put("unitPrice", line.getUnitPrice());
                map.put("quantity", line.getQuantity().negate());
                map.put("subtotal", line.getSubtotal().negate());
                map.put("vatRate", line.getVatRate());
                map.put("recargoRate", line.getSaleLine().getRecargoRate());
                negativeLines.add(map);
            }
            model.addAttribute("negativeLines", negativeLines);
            model.addAttribute("totalAmount", saleReturn.getTotalRefunded().negate());
            model.addAttribute("qrCodeBase64",
                    invoiceService.generateQrCodeBase64(saleReturn.getRectificativeInvoice()));

            return "tpv/rectificative-invoice";
        }

        model.addAttribute("taxBreakdowns", standardBreakdowns);
        
        if (originalSale.getTicket() != null) {
            model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(originalSale.getTicket()));
        }
        
        return "tpv/return-receipt";
    }

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
