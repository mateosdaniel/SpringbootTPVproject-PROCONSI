package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.util.NifCifValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for importing products from specialized CSV files.
 * Supports legacy and modern CSV formats with VAT mapping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final BigDecimal DEFAULT_VAT = new BigDecimal("0.21");

    private final CustomerService customerService;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;
    private final TaxRateRepository taxRateRepository;
    private final NifCifValidator nifCifValidator;

    /**
     * Parses a CSV file and creates products, categories, and initial prices.
     *
     * @param file The uploaded MultipartFile.
     * @return A status message summarizing the import results.
     */
    @Transactional
    public String importProductsCsv(MultipartFile file) {
        if (file.isEmpty()) {
            return "El archivo está vacío.";
        }

        int productsCreated = 0;
        int productsUpdated = 0;
        int linesProcessed = 0;
        int linesSkipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;
            // Pre-load all tax rates to avoid N+1 queries later.
            List<TaxRate> allTaxRates = taxRateRepository.findAll();

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] columns = line.split(",", -1);
                // Minimum valid row has at least 4 items (cat, name, price, stock).
                if (columns.length < 4) {
                    linesSkipped++;
                    continue;
                }

                String categoryName = columns[0].trim();
                String productName = columns[1].trim();
                String priceStr = columns[2].trim().replace("€", "").replace(",", ".");

                if (productName.isEmpty()) {
                    linesSkipped++;
                    continue;
                }

                BigDecimal ivaRate = DEFAULT_VAT;
                String stockStr;
                String imageUrl;

                boolean isNewFormat = isIvaColumn(columns[3].trim());
                if (isNewFormat) {
                    ivaRate = parseVat(columns[3].trim());
                    stockStr = columns.length >= 5 ? columns[4].trim() : "0";
                    imageUrl = columns.length >= 6 ? columns[5].trim() : "";
                } else {
                    stockStr = columns[3].trim();
                    imageUrl = columns.length >= 4 ? columns[4].trim() : "";
                }

                try {
                    Category category = null;
                    if (!categoryName.isEmpty()) {
                        category = categoryService.findAllActive().stream()
                                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                                .findFirst()
                                .orElse(null);

                        if (category == null) {
                            category = Category.builder()
                                    .nameEs(categoryName)
                                    .active(true)
                                    .build();
                            category = categoryService.save(category);
                        }
                    }

                    BigDecimal priceVal = new BigDecimal(priceStr);
                    Integer stockVal = Integer.parseInt(stockStr);

                    // SEARCH FOR EXISTING PRODUCT BY NAME (EXACT MATCH IGNORE CASE)
                    Product existing = productService.findByName(productName);
                    
                    if (existing != null) {
                        // UPDATE EXISTING
                        existing.setStock(stockVal);
                        existing.setImageUrl(imageUrl.isEmpty() ? existing.getImageUrl() : imageUrl);
                        existing.setCategory(category != null ? category : existing.getCategory());
                        // IMPORTANT: We update the gross price which recalculates the base price net.
                        existing.setPrice(priceVal);
                        
                        // We also need to re-find the tax rate if ivaRate in CSV differs from current one.
                        final BigDecimal targetVat = ivaRate;
                        TaxRate taxRate = allTaxRates.stream()
                                .filter(t -> t.getVatRate().compareTo(targetVat) == 0)
                                .findFirst()
                                .orElse(existing.getTaxRate());
                        existing.setTaxRate(taxRate);
                        
                        productService.save(existing);
                        productsUpdated++;
                    } else {
                        // CREATE NEW
                        Product product = Product.builder()
                                .nameEs(productName)
                                .stock(stockVal)
                                .imageUrl(imageUrl.isEmpty() ? null : imageUrl)
                                .category(category)
                                .active(true)
                                .build();

                        final BigDecimal targetVat = ivaRate;
                        TaxRate taxRate = allTaxRates.stream()
                                .filter(t -> t.getVatRate().compareTo(targetVat) == 0)
                                .findFirst()
                                .orElse(allTaxRates.isEmpty() ? null : allTaxRates.get(0));
                        product.setTaxRate(taxRate);
                        product.setPrice(priceVal);

                        Product saved = productService.save(product);
                        productsCreated++;

                        // Initialize price history for new products
                        ProductPriceRequest priceRequest = new ProductPriceRequest();
                        priceRequest.setPrice(priceVal);
                        priceRequest.setVatRate(ivaRate);
                        priceRequest.setStartDate(LocalDateTime.now());
                        priceRequest.setLabel("Tarifa inicial (import)");

                        productPriceService.schedulePrice(saved.getId(), priceRequest);
                    }
                    linesProcessed++;
                } catch (Exception e) {
                    log.error("Error importing product line for {}: {}", productName, e.getMessage());
                    linesSkipped++;
                }
            }

            return String.format(
                    "Productos procesados: %d creados, %d actualizados, %d omitidos (%d filas en total).",
                    productsCreated, productsUpdated, linesSkipped, linesProcessed + linesSkipped);

        } catch (Exception e) {
            log.error("Global error parsing products CSV: {}", e.getMessage(), e);
            return "Error al analizar el CSV de productos: " + e.getMessage();
        }
    }

    /**
     * Determines if a column contains an IVA rate based on its value range.
     */
    private boolean isIvaColumn(String raw) {
        if (raw.isEmpty())
            return false;
        try {
            BigDecimal val = new BigDecimal(raw.replace(",", "."));
            return val.compareTo(BigDecimal.ONE) <= 0 && val.compareTo(BigDecimal.ZERO) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Parses a CSV file and imports or updates customer records.
     * Expected columns (first row is header, skipped automatically):
     * name, taxId, email, phone, address, city, postalCode, type
     * <p>
     * If a customer with the same taxId already exists, it will be updated.
     * Otherwise a new customer is created.
     *
     * @param file The uploaded MultipartFile.
     * @return A status message summarising the import results.
     */
    @Transactional
    public String importCustomersCsv(MultipartFile file) {
        if (file.isEmpty()) {
            return "El archivo está vacío.";
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int linesProcessed = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // skip header row
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // Support both comma and semicolon delimiters
                String delimiter = line.contains(";") ? ";" : ",";
                String[] cols = line.split(delimiter, -1);

                // Minimum: name (index 0)
                if (cols.length < 1 || cols[0].trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                String name      = cols.length > 0 ? cols[0].trim() : "";
                String taxId     = cols.length > 1 ? cols[1].trim() : "";
                String email     = cols.length > 2 ? cols[2].trim() : "";
                String phone     = cols.length > 3 ? cols[3].trim() : "";
                String address   = cols.length > 4 ? cols[4].trim() : "";
                String city      = cols.length > 5 ? cols[5].trim() : "";
                String postalCode = cols.length > 6 ? cols[6].trim() : "";
                String typeStr   = cols.length > 7 ? cols[7].trim().toUpperCase() : "INDIVIDUAL";

                // Validate NIF before proceeding to avoid transaction rollback marking
                if (!taxId.isEmpty() && !nifCifValidator.isValid(taxId)) {
                    skipped++;
                    continue;
                }

                Customer.CustomerType type;
                try {
                    type = Customer.CustomerType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    type = Customer.CustomerType.INDIVIDUAL;
                }

                // Try to find existing customer by taxId
                Customer existing = null;
                if (!taxId.isEmpty()) {
                    existing = customerService.findAll().stream()
                            .filter(c -> taxId.equalsIgnoreCase(c.getTaxId()))
                            .findFirst()
                            .orElse(null);
                }

                if (existing != null) {
                    // Update existing
                    existing.setName(name);
                    existing.setEmail(email.isEmpty() ? existing.getEmail() : email);
                    existing.setPhone(phone.isEmpty() ? existing.getPhone() : phone);
                    existing.setAddress(address.isEmpty() ? existing.getAddress() : address);
                    existing.setCity(city.isEmpty() ? existing.getCity() : city);
                    existing.setPostalCode(postalCode.isEmpty() ? existing.getPostalCode() : postalCode);
                    existing.setType(type);
                    customerService.update(existing.getId(), existing);
                    updated++;
                } else {
                    // Create new
                    Customer newCustomer = Customer.builder()
                            .name(name)
                            .taxId(taxId.isEmpty() ? null : taxId)
                            .email(email.isEmpty() ? null : email)
                            .phone(phone.isEmpty() ? null : phone)
                            .address(address.isEmpty() ? null : address)
                            .city(city.isEmpty() ? null : city)
                            .postalCode(postalCode.isEmpty() ? null : postalCode)
                            .type(type)
                            .active(true)
                            .hasRecargoEquivalencia(false)
                            .build();
                    customerService.save(newCustomer);
                    created++;
                }

                linesProcessed++;
            }

            return String.format(
                    "CSV de clientes procesado: %d creados, %d actualizados, %d omitidos (%d filas procesadas).",
                    created, updated, skipped, linesProcessed);

        } catch (Exception e) {
            return "Error al procesar el CSV de clientes: " + e.getMessage();
        }
    }

    /**
     * Parses a VAT string safely.
     */
    private BigDecimal parseVat(String raw) {
        try {
            BigDecimal val = new BigDecimal(raw.replace(",", "."));
            if (val.compareTo(BigDecimal.ZERO) >= 0 && val.compareTo(BigDecimal.ONE) <= 0) {
                return val;
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_VAT;
    }
}


