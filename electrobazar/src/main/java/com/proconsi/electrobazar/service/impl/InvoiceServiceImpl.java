package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.InvoiceSequenceRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.VerifactuService;
import com.proconsi.electrobazar.util.QrCodeGenerator;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String DEFAULT_SERIE = "F";
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final SaleRepository saleRepository;
    private final ActivityLogService activityLogService;
    private final com.proconsi.electrobazar.repository.CompanySettingsRepository companySettingsRepository;
    private final VerifactuHashCalculator hashCalculator;
    private final VerifactuService verifactuService;

    public InvoiceServiceImpl(
            InvoiceRepository invoiceRepository,
            InvoiceSequenceRepository invoiceSequenceRepository,
            SaleRepository saleRepository,
            ActivityLogService activityLogService,
            com.proconsi.electrobazar.repository.CompanySettingsRepository companySettingsRepository,
            VerifactuHashCalculator hashCalculator,
            @Lazy VerifactuService verifactuService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceSequenceRepository = invoiceSequenceRepository;
        this.saleRepository = saleRepository;
        this.activityLogService = activityLogService;
        this.companySettingsRepository = companySettingsRepository;
        this.hashCalculator = hashCalculator;
        this.verifactuService = verifactuService;
    }

    @Override
    @Transactional
    public Invoice createInvoice(Sale sale) {
        int invoiceYear = sale.getCreatedAt() != null ? sale.getCreatedAt().getYear() : LocalDate.now().getYear();
        String serie = DEFAULT_SERIE;

        InvoiceSequence sequence = invoiceSequenceRepository
                .findBySerieAndYearForUpdate(serie, invoiceYear)
                .orElseGet(() -> invoiceSequenceRepository.save(
                        InvoiceSequence.builder().serie(serie).year(invoiceYear).lastNumber(0).build()));

        String invoiceNumber;
        do {
            int nextNumber = sequence.getLastNumber() + 1;
            sequence.setLastNumber(nextNumber);
            invoiceSequenceRepository.save(sequence);
            invoiceNumber = String.format("%s-%d-%d", serie, invoiceYear, nextNumber);
        } while (invoiceRepository.findByInvoiceNumber(invoiceNumber).isPresent());

        String previousHash = invoiceRepository.findFirstBySerieOrderByYearDescSequenceNumberDesc(serie)
                .map(Invoice::getHashCurrentInvoice)
                .orElse(VerifactuHashCalculator.INITIAL_HASH);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .serie(serie)
                .year(invoiceYear)
                .sequenceNumber(sequence.getLastNumber())
                .sale(sale)
                .status(Invoice.InvoiceStatus.ACTIVE)
                .hashPreviousInvoice(previousHash)
                .aeatStatus(AeatStatus.PENDING_SEND)
                .build();

        if (invoice.getCreatedAt() == null) invoice.prePersist();

        invoice.setHashCurrentInvoice(calculateHash(invoice, previousHash));

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Factura generada: {} para Venta #{} [Huella: {}]",
                invoiceNumber, sale.getId(), saved.getHashCurrentInvoice());

        activityLogService.logActivity(
                "CREAR_FACTURA",
                String.format("Factura %s generada para Venta nº %d. Huella: %s",
                        invoiceNumber, sale.getId(), saved.getHashCurrentInvoice()),
                "System", "INVOICE", saved.getId());

        verifactuService.submitInvoiceAsync(saved.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findBySaleId(Long saleId) {
        return invoiceRepository.findBySaleId(saleId);
    }

    @Override
    @Transactional
    public Invoice generateRectificativeInvoice(Sale originalSale, String reason) {
        Invoice originalInvoice = originalSale.getInvoice();
        if (originalInvoice == null) return null;

        Sale negativeSale = Sale.builder()
                .paymentMethod(originalSale.getPaymentMethod())
                .customer(originalSale.getCustomer())
                .worker(originalSale.getWorker())
                .applyRecargo(originalSale.isApplyRecargo())
                .appliedTariff(originalSale.getAppliedTariff())
                .totalAmount(originalSale.getTotalAmount().negate())
                .totalBase(originalSale.getTotalBase().negate())
                .totalVat(originalSale.getTotalVat().negate())
                .totalRecargo(originalSale.getTotalRecargo().negate())
                .totalDiscount(originalSale.getTotalDiscount().negate())
                .appliedDiscountPercentage(originalSale.getAppliedDiscountPercentage())
                .notes("RECTIFICATIVA de " + originalInvoice.getInvoiceNumber() + ". Motivo: " + reason)
                .status(Sale.SaleStatus.CANCELLED)
                .build();

        List<SaleLine> negLines = originalSale.getLines().stream().map(l -> SaleLine.builder()
                .product(l.getProduct()).quantity(l.getQuantity().negate()).unitPrice(l.getUnitPrice())
                .basePriceNet(l.getBasePriceNet().negate()).baseAmount(l.getBaseAmount().negate())
                .vatRate(l.getVatRate()).vatAmount(l.getVatAmount().negate())
                .recargoRate(l.getRecargoRate()).recargoAmount(l.getRecargoAmount().negate())
                .subtotal(l.getSubtotal().negate()).sale(negativeSale)
                .build()).collect(Collectors.toList());
        negativeSale.setLines(negLines);

        Sale savedNegative = saleRepository.save(negativeSale);
        Invoice rectificative = createInvoice(savedNegative);

        originalInvoice.setStatus(Invoice.InvoiceStatus.RECTIFIED);
        originalInvoice.setRectifiedBy(rectificative);
        invoiceRepository.save(originalInvoice);
        return rectificative;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyChain(String serie) {
        log.info("Verificando cadena Verifactu para serie: {}", serie);
        List<Invoice> chain = invoiceRepository.findBySerieOrderByYearAscSequenceNumberAsc(serie);
        String expectedPrevHash = VerifactuHashCalculator.INITIAL_HASH;
        for (Invoice inv : chain) {
            if (!inv.getHashPreviousInvoice().equals(expectedPrevHash)) {
                log.warn("Cadena corrupta: Factura {} tiene prevHash {} pero se esperaba {}",
                        inv.getInvoiceNumber(), inv.getHashPreviousInvoice(), expectedPrevHash);
                return false;
            }
            String calculated = calculateHash(inv, expectedPrevHash);
            if (!inv.getHashCurrentInvoice().equals(calculated)) {
                log.warn("Cadena corrupta: Factura {} hash almacenado {} != recalculado {}",
                        inv.getInvoiceNumber(), inv.getHashCurrentInvoice(), calculated);
                return false;
            }
            expectedPrevHash = inv.getHashCurrentInvoice();
        }
        log.info("Cadena Verifactu para serie {} verificada y segura.", serie);
        return true;
    }

    // ---- calculateHash overloads (parte del contrato InvoiceService) ----

    @Override
    public String calculateHash(Invoice invoice, String previousHash) {
        String nif = getNif();
        BigDecimal cuotaTotal = invoice.getSale().getTotalVat()
                .add(invoice.getSale().getTotalRecargo());
        return hashCalculator.calculate(nif, invoice.getInvoiceNumber(),
                invoice.getCreatedAt(), "F1", cuotaTotal,
                invoice.getSale().getTotalAmount(), previousHash);
    }

    @Override
    public String calculateHash(Ticket ticket, String previousHash) {
        String nif = getNif();
        BigDecimal cuotaTotal = ticket.getSale().getTotalVat()
                .add(ticket.getSale().getTotalRecargo());
        return hashCalculator.calculate(nif, ticket.getTicketNumber(),
                ticket.getCreatedAt(), "F2", cuotaTotal,
                ticket.getSale().getTotalAmount(), previousHash);
    }

    @Override
    public String calculateHash(RectificativeInvoice rect, String previousHash) {
        String nif = getNif();
        BigDecimal totalRefunded = rect.getSaleReturn().getTotalRefunded().negate();
        // Cuota estimada proporcionalmente del original (negativa para rectificativas)
        Sale original = rect.getSaleReturn().getOriginalSale();
        BigDecimal cuotaTotal = BigDecimal.ZERO;
        if (original != null && original.getTotalAmount().compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal ratio = totalRefunded.abs().divide(
                    original.getTotalAmount().abs(), 10, java.math.RoundingMode.HALF_UP);
            cuotaTotal = original.getTotalVat().add(original.getTotalRecargo())
                    .multiply(ratio).negate().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return hashCalculator.calculate(nif, rect.getRectificativeNumber(),
                rect.getCreatedAt(), "R1", cuotaTotal, totalRefunded, previousHash);
    }

    // ---- QR code generation ----

    @Override
    public String generateQrCodeBase64(Invoice invoice) {
        String url = buildVerificacionUrl(getNif(), invoice.getInvoiceNumber(),
                invoice.getCreatedAt().format(DATE_SHORT),
                invoice.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    @Override
    public String generateQrCodeBase64(RectificativeInvoice rect) {
        String importe = "-" + rect.getSaleReturn().getTotalRefunded()
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String url = buildVerificacionUrl(getNif(), rect.getRectificativeNumber(),
                rect.getCreatedAt().format(DATE_SHORT), importe);
        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    @Override
    public String generateQrCodeBase64(Ticket ticket) {
        String url = buildVerificacionUrl(getNif(), ticket.getTicketNumber(),
                ticket.getCreatedAt().format(DATE_SHORT),
                ticket.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    // ---- helpers ----

    private String getNif() {
        return companySettingsRepository.findById(1L)
                .map(CompanySettings::getCif)
                .orElse("00000000A");
    }

    private String buildVerificacionUrl(String nif, String numSerie, String fecha, String importe) {
        return String.format(
                "https://www2.agenciatributaria.gob.es/static/v1/verifactu/verificacion?nif=%s&numserie=%s&fecha=%s&importe=%s",
                nif, numSerie, fecha, importe);
    }
}
