package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.MeasurementUnitRepository;
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
    private final MeasurementUnitRepository measurementUnitRepository;
    private final NifCifValidator nifCifValidator;

    @Transactional
    public String importProductsCsv(MultipartFile file) {
        if (file.isEmpty()) {
            return "El archivo está vacío.";
        }

        int productsCreated = 0;
        int productsUpdated = 0;
        int linesSkipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            // Pre-load lookup data to avoid N+1 queries
            List<TaxRate> allTaxRates = taxRateRepository.findAll();
            List<Category> allCategories = categoryService.findAll(); // Assuming this exists or using service
            List<com.proconsi.electrobazar.model.MeasurementUnit> allUnits = measurementUnitRepository.findAll();

            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] cols = parseSimpleCsvLine(line);
                // Modern format: Categoría, Nombre ES, Nombre EN, Precio, Stock, IVA, Imagen,
                // Unidad, Desc ES, Desc EN, Activo
                if (cols.length < 4) {
                    linesSkipped++;
                    continue;
                }

                try {
                    String catName = cols[0].trim();
                    String nameEs = cols[1].trim();
                    String nameEn = cols.length > 2 ? cols[2].trim() : "";
                    String priceStr = cols.length > 3 ? cols[3].trim().replace("€", "").replace(",", ".") : "0";
                    String stockStr = cols.length > 4 ? cols[4].trim().replace(",", ".") : "0";
                    String ivaStr = cols.length > 5 ? cols[5].trim().replace(",", ".") : "0.21";
                    String imageUrl = cols.length > 6 ? cols[6].trim() : "";
                    String unitSymbol = cols.length > 7 ? cols[7].trim() : "";
                    String descEs = cols.length > 8 ? cols[8].trim() : "";
                    String descEn = cols.length > 9 ? cols[9].trim() : "";
                    String activeStr = cols.length > 10 ? cols[10].trim() : "true";

                    if (nameEs.isEmpty()) {
                        linesSkipped++;
                        continue;
                    }

                    BigDecimal priceVal = new BigDecimal(priceStr);
                    BigDecimal stockVal = new BigDecimal(stockStr);
                    BigDecimal ivaVal = parseVat(ivaStr);
                    boolean isActive = !activeStr.equalsIgnoreCase("false");

                    // 1. Resolve Category
                    Category category = allCategories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(catName))
                            .findFirst()
                            .orElse(null);

                    if (category == null && !catName.isEmpty()) {
                        category = Category.builder().nameEs(catName).active(true).build();
                        category = categoryService.save(category);
                        allCategories.add(category);
                    }

                    // 2. Resolve TaxRate
                    TaxRate taxRate = allTaxRates.stream()
                            .filter(t -> t.getVatRate().compareTo(ivaVal) == 0)
                            .findFirst()
                            .orElse(allTaxRates.isEmpty() ? null : allTaxRates.get(0));

                    // 3. Resolve Unit
                    com.proconsi.electrobazar.model.MeasurementUnit unit = allUnits.stream()
                            .filter(u -> u.getSymbol().equalsIgnoreCase(unitSymbol))
                            .findFirst()
                            .orElse(null);

                    // 4. Upsert Product
                    Product p = productService.findByName(nameEs);
                    boolean isNew = (p == null);

                    if (isNew) {
                        p = new Product();
                        p.setNameEs(nameEs);
                    }

                    p.setNameEn(nameEn.isEmpty() ? p.getNameEn() : nameEn);
                    p.setDescriptionEs(descEs.isEmpty() ? p.getDescriptionEs() : descEs);
                    p.setDescriptionEn(descEn.isEmpty() ? p.getDescriptionEn() : descEn);
                    p.setCategory(category);
                    p.setTaxRate(taxRate);
                    p.setMeasurementUnit(unit);
                    p.setStock(stockVal);
                    p.setActive(isActive);
                    p.setImageUrl(imageUrl.isEmpty() ? p.getImageUrl() : imageUrl);

                    // Set price logic handles net/gross conversion internnally if taxRate is set
                    // first
                    p.setPrice(priceVal);

                    productService.save(p);

                    if (isNew) {
                        productsCreated++;
                        // Price History Log
                        ProductPriceRequest ppr = new ProductPriceRequest();
                        ppr.setPrice(priceVal);
                        ppr.setVatRate(ivaVal);
                        ppr.setStartDate(LocalDateTime.now());
                        ppr.setLabel("Importación CSV");
                        productPriceService.schedulePrice(p.getId(), ppr);
                    } else {
                        productsUpdated++;
                    }

                } catch (Exception e) {
                    log.error("Error en línea CSV: {}", e.getMessage());
                    linesSkipped++;
                }
            }

            return String.format("Importación finalizada: %d creados, %d actualizados, %d omitidos.",
                    productsCreated, productsUpdated, linesSkipped);

        } catch (Exception e) {
            log.error("Error crítico en importación CSV", e);
            return "Error fatal: " + e.getMessage();
        }
    }

    /**
     * Specialized CSV parser that handles quoted values containing commas.
     */
    private String[] parseSimpleCsvLine(String line) {
        List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
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

                String name = cols.length > 0 ? cols[0].trim() : "";
                String taxId = cols.length > 1 ? cols[1].trim() : "";
                String email = cols.length > 2 ? cols[2].trim() : "";
                String phone = cols.length > 3 ? cols[3].trim() : "";
                String address = cols.length > 4 ? cols[4].trim() : "";
                String city = cols.length > 5 ? cols[5].trim() : "";
                String postalCode = cols.length > 6 ? cols[6].trim() : "";
                String typeStr = cols.length > 7 ? cols[7].trim().toUpperCase() : "INDIVIDUAL";

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
