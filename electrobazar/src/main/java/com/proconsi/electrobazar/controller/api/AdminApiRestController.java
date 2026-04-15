package com.proconsi.electrobazar.controller.api;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.dto.*;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for administrative and management operations.
 * Handles sensitive features such as PDF generation, stats, company
 * configuration,
 * CSV imports, and worker management.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminApiRestController {

    private final AdminPinService adminPinService;
    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final ProductService productService;
    private final CsvImportService csvImportService;
    private final SaleService saleService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;
    private final WorkerService workerService;
    private final CompanySettingsService companySettingsService;
    private final InvoiceService invoiceService;
    private final TicketService ticketService;
    private final ReturnService returnService;
    private final CustomerService customerService;
    private final CategoryService categoryService;
    private final TariffService tariffService;
    private final TariffPriceHistoryService tariffPriceHistoryService;
    private final ActivityLogService activityLogService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TemplateEngine templateEngine;
    private final JwtService jwtService;
    private final ProductPriceService productPriceService;
    private final RoleService roleService;
    private final WorkerRepository workerRepository;

    /**
     * Retrieves aggregated statistics for the management dashboard.
     * 
     * @param period Time period (e.g., "today", "week", "month").
     * @return Dashboard statistics data.
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(cashRegisterService.getDashboardStats(period));
    }

    /**
     * Paginated list of sales with server-side filtering.
     * Used for the administration billing panel.
     */
    @GetMapping("/sales")
    public ResponseEntity<Map<String, Object>> getSalesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        // Whitelist allowed sort fields to prevent injection
        Set<String> allowedSort = Set.of("createdAt", "totalAmount", "id");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<Sale> salesPage = saleService.search(search, type, method, date, pageable);

        List<AdminSaleListingDTO> list = salesPage.getContent().stream().map(s -> AdminSaleListingDTO.builder()
                .id(s.getId())
                .displayId(s.getInvoice() != null ? s.getInvoice().getInvoiceNumber()
                        : (s.getTicket() != null ? s.getTicket().getTicketNumber() : "#" + s.getId()))
                .createdAt(s.getCreatedAt())
                .type(s.getInvoice() != null ? "factura" : "ticket")
                .status(s.getStatus() != null ? s.getStatus().name() : "ACTIVE")
                .customerName(s.getCustomer() != null ? s.getCustomer().getName() : null)
                .customerTaxId(s.getCustomer() != null ? s.getCustomer().getTaxId() : null)
                .workerUsername(s.getWorker() != null ? s.getWorker().getUsername() : null)
                .paymentMethod(s.getPaymentMethod() != null ? s.getPaymentMethod().name() : "CASH")
                .totalAmount(s.getTotalAmount())
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", salesPage.getTotalPages());
        response.put("totalElements", salesPage.getTotalElements());
        response.put("currentPage", salesPage.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of products with server-side filtering and sorting.
     */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProductsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "nameEs", "price", "stock", "category.nameEs");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<Product> productsPage = productService.getFilteredProducts(search, category, stock, active, unitId,
                pageable);

        List<AdminProductListingDTO> list = productsPage.getContent().stream().map(p -> AdminProductListingDTO.builder()
                .id(p.getId())
                .name(p.getNameEs())
                .description(p.getDescriptionEs())
                .price(p.getPrice())
                .stock(p.getStock())
                .categoryName(p.getCategory() != null ? p.getCategory().getNameEs() : null)
                .measurementUnit(p.getMeasurementUnit())
                .priceDecimals(
                        p.getMeasurementUnit() != null ? Math.max(2, p.getMeasurementUnit().getDecimalPlaces()) : 2)
                .vatRate(p.getTaxRate() != null ? p.getTaxRate().getVatRate() : null)
                .imageUrl(p.getImageUrl())
                .active(Boolean.TRUE.equals(p.getActive()))
                .build()).collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", productsPage.getTotalPages());
        response.put("totalElements", productsPage.getTotalElements());
        response.put("currentPage", productsPage.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of categories with server-side filtering and sorting.
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategoriesPage(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "nameEs", "descriptionEs");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<com.proconsi.electrobazar.model.Category> categoriesPage = categoryService.getFilteredCategories(search,
                pageable);

        List<AdminCategoryListingDTO> list = categoriesPage.getContent().stream()
                .map(c -> AdminCategoryListingDTO.builder()
                        .id(c.getId())
                        .name(c.getNameEs())
                        .description(c.getDescriptionEs())
                        .active(Boolean.TRUE.equals(c.getActive()))
                        .build())
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", categoriesPage.getTotalPages());
        response.put("totalElements", categoriesPage.getTotalElements());
        response.put("currentPage", categoriesPage.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cash-closings")
    public ResponseEntity<Map<String, Object>> getCashClosingsPage(
            @RequestParam(required = false) String worker,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "openingTime", "closedAt", "difference", "totalSales");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<CashRegister> pageData = cashRegisterService.getFilteredRegisters(worker, date, pageable);

        List<AdminCashClosingListingDTO> list = pageData.getContent().stream()
                .map(r -> AdminCashClosingListingDTO.builder()
                        .id(r.getId())
                        .openingTime(r.getOpeningTime())
                        .closedAt(r.getClosedAt())
                        .openingBalance(r.getOpeningBalance())
                        .totalSales(r.getTotalSales())
                        .totalCalculated((r.getOpeningBalance() != null ? r.getOpeningBalance() : BigDecimal.ZERO)
                                .add(r.getTotalSales() != null ? r.getTotalSales() : BigDecimal.ZERO))
                        .closingBalance(r.getClosingBalance())
                        .difference(r.getDifference())
                        .workerUsername(r.getWorker() != null ? r.getWorker().getUsername() : "Sistema")
                        .build())
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of returns with server-side filtering and sorting.
     */
    @GetMapping("/returns")
    public ResponseEntity<Map<String, Object>> getReturnsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "returnNumber", "createdAt", "totalRefunded", "type");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<SaleReturn> pageData = returnService.getFilteredReturns(search, method, date, pageable);

        List<AdminReturnListingDTO> list = pageData.getContent().stream().map(r -> AdminReturnListingDTO.builder()
                .id(r.getId())
                .returnNumber(r.getReturnNumber())
                .originalNumber(r.getOriginalSale().getInvoice() != null
                        ? r.getOriginalSale().getInvoice().getInvoiceNumber()
                        : (r.getOriginalSale().getTicket() != null ? r.getOriginalSale().getTicket().getTicketNumber()
                                : "#" + r.getOriginalSale().getId()))
                .createdAt(r.getCreatedAt())
                .type(r.getType() != null ? r.getType().name() : "Desconocido")
                .reason(r.getReason())
                .workerUsername(r.getWorker() != null ? r.getWorker().getUsername() : "—")
                .paymentMethod(r.getPaymentMethod() != null
                        ? (r.getPaymentMethod() == PaymentMethod.CASH ? "Efectivo" : "Tarjeta")
                        : "—")
                .amount(r.getTotalRefunded())
                .ticketUrl("/admin/return/" + r.getId())
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of workers with server-side filtering and sorting.
     */
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> getWorkersPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "username", "active");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "username";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<Worker> pageData = workerService.getFilteredWorkers(search, roleId, active, pageable);

        List<AdminWorkerListingDTO> list = pageData.getContent().stream().map(w -> AdminWorkerListingDTO.builder()
                .id(w.getId())
                .username(w.getUsername())
                .active(w.isActive())
                .roleId(w.getRole() != null ? w.getRole().getId() : null)
                .roleName(w.getRole() != null ? w.getRole().getName() : null)
                .permissions(w.getRole() != null ? new ArrayList<>(w.getRole().getPermissions()) : new ArrayList<>())
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of roles with server-side filtering and sorting.
     */
    @GetMapping("/roles")
    public ResponseEntity<Map<String, Object>> getRolesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> permissions,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "name");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<Role> pageData = roleService.getFilteredRoles(search, permissions, pageable);

        // Filter out ADMIN role from the management table listing to avoid accidental
        // deletion/modification
        List<AdminRoleListingDTO> list = pageData.getContent().stream()
                .filter(r -> !"ADMIN".equalsIgnoreCase(r.getName()))
                .map(r -> {
                    long count = workerRepository.findAll().stream()
                            .filter(w -> w.getRole() != null && w.getRole().getId().equals(r.getId()))
                            .count();
                    Set<String> perms = new HashSet<>(r.getPermissions());
                    return AdminRoleListingDTO.builder()
                            .id(r.getId())
                            .name(r.getName())
                            .description(r.getDescription())
                            .permissions(perms)
                            .workerCount(count)
                            .build();
                }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", pageData.getTotalPages());
        // Simple adjustment since ADMIN is usually just 1 role
        response.put("totalElements", Math.max(0, pageData.getTotalElements() - 1));
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of customers with server-side filtering and sorting.
     */
    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> getCustomersPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String re,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // Convert string filters to typed values
        Customer.CustomerType customerType = null;
        if (type != null && !type.isBlank()) {
            try {
                customerType = Customer.CustomerType.valueOf(type);
            } catch (Exception ignored) {
            }
        }
        Boolean hasRecargo = null;
        if ("yes".equalsIgnoreCase(re))
            hasRecargo = true;
        else if ("no".equalsIgnoreCase(re))
            hasRecargo = false;

        // Whitelist allowed sort fields
        Set<String> allowedSort = Set.of("id", "name", "taxId", "city");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "name";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<Customer> pageData = customerService.getFilteredCustomers(search, customerType, hasRecargo, pageable);

        List<AdminCustomerListingDTO> list = pageData.getContent().stream().map(c -> AdminCustomerListingDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .taxId(c.getTaxId())
                .email(c.getEmail())
                .phone(c.getPhone())
                .city(c.getCity())
                .type(c.getType())
                .hasRecargoEquivalencia(c.getHasRecargoEquivalencia() != null && c.getHasRecargoEquivalencia())
                .tariffId(c.getTariff() != null ? c.getTariff().getId() : null)
                .tariffName(c.getTariff() != null ? c.getTariff().getName() : null)
                .tariffColor(c.getTariff() != null ? c.getTariff().getColor() : null)
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated list of system activity logs with server-side filtering.
     * 
     * /**
     * Paginated list of system activity logs with server-side filtering.
     */
    @GetMapping("/activity-logs")
    public ResponseEntity<Map<String, Object>> getActivityLogsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "timestamp", "action", "level", "username");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "timestamp";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        Page<ActivityLog> pageData = activityLogService.getFilteredLogs(search, action, username, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", pageData.getContent());
        response.put("totalPages", pageData.getTotalPages());
        response.put("totalElements", pageData.getTotalElements());
        response.put("currentPage", pageData.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Verifies the super-admin master PIN before performing sensitive actions.
     * 
     * @param body Payload containing the "pin" string.
     * @return 200 OK if valid, 401 Unauthorized otherwise.
     */
    @PostMapping("/verify-pin")
    public ResponseEntity<?> verifyPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (adminPinService.verifyPin(pin)) {
            // Escalation logic for API/Mobile: Return a new token with ADMIN_ACCESS
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String username = auth.getName();
                Optional<Worker> workerOpt = workerService.findByUsername(username);

                if (workerOpt.isPresent()) {
                    Worker worker = workerOpt.get();
                    Set<String> permissions = worker.getEffectivePermissions();
                    permissions.add("ACCESO_TOTAL_ADMIN"); // Temporarily escalate for this token

                    String newToken = jwtService.generateToken(worker.getUsername(), worker.getId(), permissions);
                    return ResponseEntity.ok(Map.of(
                            "ok", true,
                            "token", newToken,
                            "worker", worker));
                }
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "PIN incorrecto"));
        }
    }

    /**
     * Generates and downloads the official PDF document for an existing sale
     * (Invoice or Ticket).
     * 
     * @param id The sale ID.
     * @return PDF binary resource tagged with appropriate filename.
     */
    @GetMapping("/download/invoice/{id}")
    public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable Long id) {
        Sale sale = saleService.findById(id);
        if (sale == null)
            return ResponseEntity.notFound().build();

        Context context = new Context();
        context.setVariable("sale", sale);
        context.setVariable("companySettings", companySettingsService.getSettings());
        context.setVariable("pdfMode", true); // Ensure we hide web UI elements in PDF

        Optional<Invoice> invoiceOpt = invoiceService.findBySaleId(id);
        invoiceOpt.ifPresent(inv -> context.setVariable("invoice", inv));

        if (invoiceOpt.isEmpty()) {
            ticketService.findBySaleId(id).ifPresent(t -> context.setVariable("ticket", t));
        }

        boolean applyRecargo = sale.getCustomer() != null
                && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());
        List<TaxBreakdown> breakdowns = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            breakdowns.add(recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(), line.getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo));
        }
        context.setVariable("taxBreakdowns", breakdowns);
        context.setVariable("applyRecargo", applyRecargo);
        context.setVariable("totalBase", breakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        context.setVariable("totalVat", breakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        context.setVariable("totalRecargo", breakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

        String template = invoiceOpt.isPresent() ? "tpv/invoice" : "tpv/receipt";
        byte[] pdfBytes = generatePdfFromTemplate(template, context);

        String filename = (invoiceOpt.isPresent() ? "Factura_" + invoiceOpt.get().getInvoiceNumber() : "Ticket_" + id)
                + ".pdf";
        return createPdfResponse(pdfBytes, filename);
    }

    /**
     * Generates and downloads the PDF receipt or rectificative invoice for a
     * return.
     * 
     * @param id The return ID.
     * @return PDF binary resource.
     */
    @GetMapping("/download/return/{id}")
    public ResponseEntity<Resource> downloadReturnPdf(@PathVariable Long id) {
        Optional<SaleReturn> returnOpt = returnService.findById(id);
        if (returnOpt.isEmpty())
            return ResponseEntity.notFound().build();
        SaleReturn saleReturn = returnOpt.get();

        Context context = new Context();
        context.setVariable("saleReturn", saleReturn);
        context.setVariable("companySettings", companySettingsService.getSettings());
        context.setVariable("pdfMode", true); // Ensure we hide web UI elements in PDF

        Sale originalSale = saleReturn.getOriginalSale();
        boolean applyRecargo = originalSale.isApplyRecargo();
        List<TaxBreakdown> standardBreakdowns = new ArrayList<>();

        for (ReturnLine line : saleReturn.getLines()) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            standardBreakdowns.add(recargoCalculator.calculateLineBreakdown(
                    line.getSaleLine().getProduct().getId(), line.getSaleLine().getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo));
        }

        String template;
        if (saleReturn.getRectificativeInvoice() != null) {
            template = "tpv/rectificative-invoice";
            List<TaxBreakdown> negativeBreakdowns = standardBreakdowns.stream().map(bd -> TaxBreakdown.builder()
                    .productId(bd.getProductId()).productName(bd.getProductName()).unitPrice(bd.getUnitPrice())
                    .quantity(bd.getQuantity() != null ? bd.getQuantity().negate() : BigDecimal.ZERO)
                    .baseAmount(bd.getBaseAmount().negate()).vatRate(bd.getVatRate())
                    .vatAmount(bd.getVatAmount().negate()).recargoRate(bd.getRecargoRate())
                    .recargoAmount(bd.getRecargoAmount().negate())
                    .totalAmount(bd.getTotalAmount().negate()).recargoApplied(applyRecargo).build())
                    .collect(Collectors.toList());
            context.setVariable("taxBreakdowns", negativeBreakdowns);
            context.setVariable("totalBase", negativeBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalVat", negativeBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalRecargo", negativeBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

            List<Map<String, Object>> negativeLines = new ArrayList<>();
            for (ReturnLine line : saleReturn.getLines()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", line.getSaleLine().getProduct().getName());
                map.put("unitPrice", line.getUnitPrice());
                map.put("quantity", line.getQuantity().negate());
                map.put("subtotal", line.getSubtotal().negate());
                map.put("vatRate", line.getVatRate());
                map.put("recargoRate", line.getSaleLine().getRecargoRate());
                negativeLines.add(map);
            }
            context.setVariable("negativeLines", negativeLines);
            context.setVariable("totalAmount", saleReturn.getTotalRefunded().negate());
        } else {
            template = "tpv/return-receipt";
            context.setVariable("taxBreakdowns", standardBreakdowns);
            context.setVariable("totalBase", standardBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalVat", standardBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalRecargo", standardBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        }
        context.setVariable("applyRecargo", applyRecargo);

        byte[] pdfBytes = generatePdfFromTemplate(template, context);
        String filename = "Devolucion_" + saleReturn.getReturnNumber() + ".pdf";
        return createPdfResponse(pdfBytes, filename);
    }

    /**
     * Exports a specific Tariff price list to PDF.
     * 
     * @param id   Tariff ID.
     * @param date The date for which prices should be calculated (defaults to now).
     * @return PDF resource.
     */
    @GetMapping("/tariffs/{id}/history/pdf")
    public ResponseEntity<Resource> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        Tariff tariff = tariffService.findById(id).orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalTime targetTime = time != null ? time : LocalTime.now();

        List<TariffPriceEntryDTO> history = tariffPriceHistoryService.getPricesForTariffAtExactDateTimeList(id,
                targetDate, targetTime);
        byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history, targetDate);
        String filename = String.format("Tarifa_%s_%s_%s.pdf", tariff.getName(), targetDate,
                targetTime.toString().replace(":", "-"));
        return createPdfResponse(pdfData, filename);
    }

    /**
     * Bulk imports products from a CSV file.
     * 
     * @param file Multipat CSV file.
     * @return Import result summary.
     */
    @PostMapping("/upload-csv")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) throws Exception {
        String result = csvImportService.importProductsCsv(file);
        activityLogService.logActivity("IMPORTAR_CSV", "Importación CSV realizada: " + result, "Admin", "IMPORT", null);
        return ResponseEntity.ok(Map.of("ok", true, "message", result));
    }

    /**
     * Manually triggers a tax rate transition across the product catalog.
     * 
     * @param newId ID of the new Tax Rate to apply.
     * @return 200 OK.
     */
    @PostMapping("/tax-rates/{newId}/apply-to-products")
    public ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId) {
        productService.applyNewTaxRate(newId);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves the master company settings (name, CIF, address, legal text).
     * 
     * @return {@link CompanySettings} entity.
     */
    @GetMapping("/settings")
    public ResponseEntity<CompanySettings> getSettings() {
        return ResponseEntity.ok(companySettingsService.getSettings());
    }

    /**
     * Updates the master company configuration.
     * 
     * @param companySettings New settings data.
     * @return Success message.
     */
    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody CompanySettings companySettings) {
        companySettingsService.save(companySettings);
        return ResponseEntity.ok(Map.of("message", "Configuración de empresa actualizada correctamente."));
    }

    /**
     * Retrieves the current mail settings (SMTP).
     * 
     * @return Map of mail configuration.
     */
    @GetMapping("/mail-settings")
    public ResponseEntity<Map<String, String>> getMailSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("host", appSettingRepository.findByKey("mail.host").map(AppSetting::getValue).orElse(""));
        settings.put("port", appSettingRepository.findByKey("mail.port").map(AppSetting::getValue).orElse("587"));
        settings.put("username", appSettingRepository.findByKey("mail.username").map(AppSetting::getValue).orElse(""));
        settings.put("password", appSettingRepository.findByKey("mail.password").isPresent() ? "••••••••" : "");
        return ResponseEntity.ok(settings);
    }

    /**
     * Updates mail settings (SMTP) and encrypts the password.
     * 
     * @param body Payload with host, port, username and password.
     * @return 200 OK.
     */
    @PostMapping("/mail-settings")
    public ResponseEntity<?> saveMailSettings(@RequestBody Map<String, String> body) {
        if (body.get("host") != null)
            saveAppSetting("mail.host", body.get("host"));
        if (body.get("port") != null)
            saveAppSetting("mail.port", body.get("port"));
        if (body.get("username") != null)
            saveAppSetting("mail.username", body.get("username"));
        if (body.get("password") != null && !body.get("password").isBlank()
                && !body.get("password").equals("••••••••")) {
            saveAppSetting("mail.password", aesEncryptionUtil.encrypt(body.get("password")));
        }
        return ResponseEntity.ok(Map.of("message", "Configuración guardada correctamente"));
    }

    /**
     * Updates the admin PIN securely.
     * 
     * @param body Payload with currentPin and newPin.
     * @return 200 OK or error message.
     */
    @PostMapping("/update-pin")
    public ResponseEntity<?> updatePin(@RequestBody Map<String, String> body) {
        try {
            adminPinService.updatePin(body.get("currentPin"), body.get("newPin"));
            return ResponseEntity.ok(Map.of("message", "PIN de administrador actualizado correctamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void saveAppSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findByKey(key)
                .orElse(AppSetting.builder().key(key).build());
        setting.setValue(value);
        appSettingRepository.save(setting);
    }

    /**
     * Deactivates a worker account (Soft Delete).
     * 
     * @param id Worker ID.
     * @return 200 OK.
     */
    @DeleteMapping("/workers/{id}")
    public ResponseEntity<?> deleteWorker(@PathVariable Long id) {
        workerService.findById(id).ifPresent(w -> {
            w.setActive(false);
            workerService.save(w);
            activityLogService.logActivity("DESACTIVAR_TRABAJADOR", "Trabajador desactivado: " + w.getUsername(),
                    "Admin", "WORKER", id);
        });
        return ResponseEntity.ok().build();
    }

    private byte[] generatePdfFromTemplate(String template, Context context) {
        try {
            String htmlContent = templateEngine.process(template, context);
            htmlContent = cleanHtmlForPdf(htmlContent);
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/static/");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("Error generating PDF from template: " + template, e);
            throw new RuntimeException("Error generating PDF: " + e.getMessage(), e);
        }
    }

    private String cleanHtmlForPdf(String html) {
        if (html == null)
            return "";
        // Replace unclosed tags common in HTML but invalid in strict XML/XHTML
        String cleaned = html.replaceAll("<(meta|br|hr|img|input|link)([^>]*?)(?<!/)>", "<$1$2 />");
        return cleaned.replace("&middot;", "&#183;")
                .replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;")
                .replace("&nbsp;", "&#160;")
                .replace("&euro;", "&#8364;")
                .replace("&ordm;", "&#186;")
                .replace("&ordf;", "&#170;")
                .replace("&mdash;", "&#8212;")
                .replace("&ndash;", "&#8211;")
                .replace("&aacute;", "&#225;")
                .replace("&eacute;", "&#233;")
                .replace("&iacute;", "&#237;")
                .replace("&oacute;", "&#243;")
                .replace("&uacute;", "&#250;")
                .replace("&ntilde;", "&#241;")
                .replace("&iquest;", "&#191;")
                .replace("&iexcl;", "&#161;")
                .replaceAll("&(?!(?:[a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");
    }

    /**
     * Bulk updates multiple prices (base and tariffs) in a single transaction.
     * 
     * @param request The price matrix update request.
     * @return 200 OK.
     */
    @PostMapping("/bulk-price-update")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody BulkPriceMatrixUpdateRequest request) {
        productPriceService.bulkMatrixUpdate(request);
        return ResponseEntity.ok(Map.of("message", "Precios procesados correctamente."));
    }

    /**
     * Lists all scheduled price changes that haven't taken effect yet.
     */
    @GetMapping("/price-updates/pending")
    public ResponseEntity<List<PriceMatrixSummaryDTO>> getPendingPriceUpdates() {
        return ResponseEntity.ok(productPriceService.getPendingMatrixUpdates());
    }

    /**
     * Lists recently applied price changes.
     */
    @GetMapping("/price-updates/history")
    public ResponseEntity<List<PriceMatrixSummaryDTO>> getPriceUpdateHistory() {
        return ResponseEntity.ok(productPriceService.getMatrixUpdateHistory());
    }

    /**
     * Cancels a scheduled price change.
     */
    @DeleteMapping("/price-updates/{id}")
    public ResponseEntity<?> deletePendingPriceUpdate(@PathVariable Long id) {
        productPriceService.deletePendingPrice(id);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Resource> createPdfResponse(byte[] pdfBytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdfBytes));
    }
}
