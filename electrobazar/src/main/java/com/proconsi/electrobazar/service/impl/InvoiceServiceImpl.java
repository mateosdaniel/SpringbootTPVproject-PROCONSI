package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.InvoiceSequence;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.InvoiceSequenceRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.proconsi.electrobazar.model.CompanySettings;
import com.proconsi.electrobazar.repository.CompanySettingsRepository;
import com.proconsi.electrobazar.util.QrCodeGenerator;

/**
 * Implementation of {@link InvoiceService}.
 * Manages the generation of legal invoices with strict sequential numbering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String DEFAULT_SERIE = "F";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final SaleRepository saleRepository;
    private final ActivityLogService activityLogService;
    private final CompanySettingsRepository companySettingsRepository;

    private static final String INITIAL_HASH = "0000000000000000";
    private static final DateTimeFormatter VERIFACTU_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Atomically increments the invoice sequence for the current year and serie.
     * Uses PESSIMISTIC_WRITE locking to prevent race conditions and duplicate
     * numbering.
     *
     * @param sale The sale entity to link with the invoice.
     * @return The newly generated Invoice.
     */
    @Override
    @Transactional
    public Invoice createInvoice(Sale sale) {
        int invoiceYear = sale.getCreatedAt() != null ? sale.getCreatedAt().getYear() : LocalDate.now().getYear();
        String serie = DEFAULT_SERIE;

        // Fetch and lock the sequence row.
        InvoiceSequence sequence = invoiceSequenceRepository
                .findBySerieAndYearForUpdate(serie, invoiceYear)
                .orElseGet(() -> {
                    InvoiceSequence newSeq = InvoiceSequence.builder()
                            .serie(serie)
                            .year(invoiceYear)
                            .lastNumber(0)
                            .build();
                    return invoiceSequenceRepository.save(newSeq);
                });

        String invoiceNumber;
        do {
            int nextNumber = sequence.getLastNumber() + 1;
            sequence.setLastNumber(nextNumber);
            invoiceSequenceRepository.save(sequence);

            // Format example: F-2026-1
            invoiceNumber = String.format("%s-%d-%d", serie, invoiceYear, nextNumber);
        } while (invoiceRepository.findByInvoiceNumber(invoiceNumber).isPresent());

        // Verifactu Chaining: Get previous hash
        String previousHash = invoiceRepository.findFirstBySerieOrderByYearDescSequenceNumberDesc(serie)
                .map(Invoice::getHashCurrentInvoice)
                .orElse(INITIAL_HASH);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .serie(serie)
                .year(invoiceYear)
                .sequenceNumber(sequence.getLastNumber())
                .sale(sale)
                .status(Invoice.InvoiceStatus.ACTIVE)
                .hashPreviousInvoice(previousHash)
                .build();

        // Calculate current hash (must be done after setting fields, especially date)
        if (invoice.getCreatedAt() == null) {
            invoice.prePersist(); // Ensure date is set for hash calculation
        }
        invoice.setHashCurrentInvoice(calculateHash(invoice, previousHash));

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice generated: {} for Sale ID {} [Hash: {}]", invoiceNumber, sale.getId(),
                saved.getHashCurrentInvoice());

        activityLogService.logActivity(
                "CREAR_FACTURA",
                String.format("Factura %s generada para Venta nº %d. Hash Verifactu: %s",
                        invoiceNumber, sale.getId(), saved.getHashCurrentInvoice()),
                "System",
                "INVOICE",
                saved.getId());

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
        if (originalInvoice == null)
            return null;

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
                .notes("RECTIFICATIVA de " + originalInvoice.getInvoiceNumber() + ". Motivo: " + reason)
                .status(Sale.SaleStatus.CANCELLED)
                .build();

        List<SaleLine> negLines = originalSale.getLines().stream().map(l -> SaleLine.builder()
                .product(l.getProduct()).quantity(-l.getQuantity()).unitPrice(l.getUnitPrice())
                .basePriceNet(l.getBasePriceNet().negate()).baseAmount(l.getBaseAmount().negate())
                .vatAmount(l.getVatAmount().negate()).recargoAmount(l.getRecargoAmount().negate())
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
        log.info("Starting Verifactu chain verification for serie: {}", serie);
        List<Invoice> chain = invoiceRepository.findBySerieOrderByYearAscSequenceNumberAsc(serie);

        String expectedPrevHash = INITIAL_HASH;
        for (Invoice inv : chain) {
            if (!inv.getHashPreviousInvoice().equals(expectedPrevHash)) {
                log.warn("Corrupt chain: Invoice {} has prevHash {} but expected {}",
                        inv.getInvoiceNumber(), inv.getHashPreviousInvoice(), expectedPrevHash);
                return false;
            }

            String calculated = calculateHash(inv, expectedPrevHash);
            if (!inv.getHashCurrentInvoice().equals(calculated)) {
                log.warn("Corrupt chain: Invoice {} has hash {} but recalculation yielded {}",
                        inv.getInvoiceNumber(), inv.getHashCurrentInvoice(), calculated);
                return false;
            }
            expectedPrevHash = inv.getHashCurrentInvoice();
        }

        log.info("Verifactu chain for serie {} is verified and secure.", serie);
        return true;
    }

    private String calculateHash(Invoice invoice, String previousHash) {
        try {
            String issuerNif = companySettingsRepository.findById(1L)
                    .map(CompanySettings::getCif)
                    .orElse("00000000A");

            String data = issuerNif +
                    invoice.getInvoiceNumber() +
                    invoice.getCreatedAt().format(VERIFACTU_DATE_FORMAT) +
                    invoice.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString() +
                    previousHash;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("Internal error during Verifactu hash calculation", e);
            throw new RuntimeException("Verifactu hash error", e);
        }
    }

    @Override
    public String generateQrCodeBase64(Invoice invoice) {
        String issuerNif = companySettingsRepository.findById(1L)
                .map(CompanySettings::getCif)
                .orElse("00000000A");

        // Format for AEAT Verifactu Portal:
        // https://www2.agenciatributaria.gob.es/static/v1/verifactu/verificacion?nif=...&numserie=...&fecha=...&importe=...

        String nif = issuerNif;
        String numSerie = invoice.getInvoiceNumber();
        String fecha = invoice.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String importe = invoice.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString();

        String url = String.format(
                "https://www2.agenciatributaria.gob.es/static/v1/verifactu/verificacion?nif=%s&numserie=%s&fecha=%s&importe=%s",
                nif, numSerie, fecha, importe);

        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    @Override
    public String generateQrCodeBase64(com.proconsi.electrobazar.model.RectificativeInvoice rect) {
        String issuerNif = companySettingsRepository.findById(1L)
                .map(CompanySettings::getCif)
                .orElse("00000000A");

        String nif = issuerNif;
        String numSerie = rect.getRectificativeNumber();
        String fecha = rect.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String importe = rect.getSaleReturn().getTotalRefunded().setScale(2, java.math.RoundingMode.HALF_UP).toString();

        String url = String.format(
                "https://www2.agenciatributaria.gob.es/static/v1/verifactu/verificacion?nif=%s&numserie=%s&fecha=%s&importe=-%s",
                nif, numSerie, fecha, importe);

        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    @Override
    public String calculateHash(com.proconsi.electrobazar.model.RectificativeInvoice rect, String previousHash) {
        try {
            String issuerNif = companySettingsRepository.findById(1L)
                    .map(CompanySettings::getCif)
                    .orElse("00000000A");

            String data = issuerNif +
                    rect.getRectificativeNumber() +
                    rect.getCreatedAt().format(VERIFACTU_DATE_FORMAT) +
                    "-" + rect.getSaleReturn().getTotalRefunded().setScale(2, java.math.RoundingMode.HALF_UP).toString()
                    +
                    previousHash;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("Internal error during Verifactu hash calculation for Rectificative", e);
            throw new RuntimeException("Verifactu hash error", e);
        }
    }

    @Override
    public String generateQrCodeBase64(com.proconsi.electrobazar.model.Ticket ticket) {
        String issuerNif = companySettingsRepository.findById(1L)
                .map(CompanySettings::getCif)
                .orElse("00000000A");

        String nif = issuerNif;
        String numSerie = ticket.getTicketNumber();
        String fecha = ticket.getCreatedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String importe = ticket.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString();

        String url = String.format(
                "https://www2.agenciatributaria.gob.es/static/v1/verifactu/verificacion?nif=%s&numserie=%s&fecha=%s&importe=%s",
                nif, numSerie, fecha, importe);

        return QrCodeGenerator.generateQrBase64(url, 250, 250);
    }

    @Override
    public String calculateHash(com.proconsi.electrobazar.model.Ticket ticket, String previousHash) {
        try {
            String issuerNif = companySettingsRepository.findById(1L)
                    .map(CompanySettings::getCif)
                    .orElse("00000000A");

            String data = issuerNif +
                    ticket.getTicketNumber() +
                    ticket.getCreatedAt().format(VERIFACTU_DATE_FORMAT) +
                    ticket.getSale().getTotalAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString() +
                    previousHash;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("Internal error during Verifactu hash calculation for Ticket", e);
            throw new RuntimeException("Verifactu hash error", e);
        }
    }
}
