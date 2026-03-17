package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final BigDecimal DEFAULT_VAT = new BigDecimal("0.21");

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;
    private final TaxRateRepository taxRateRepository;

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
        int pricesCreated = 0;
        int newCategoriesCreated = 0;
        int linesProcessed = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    continue;
                }

                String categoryName = columns[0].trim();
                String productName = columns[1].trim();
                String priceStr = columns[2].trim().replace("€", "").replace(",", ".");

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
                    imageUrl = columns.length >= 5 ? columns[4].trim() : "";
                }

                Category category = null;
                if (!categoryName.isEmpty()) {
                    List<Category> allCategories = categoryService.findAllActive();
                    category = allCategories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                            .findFirst()
                            .orElse(null);

                    if (category == null) {
                        category = Category.builder()
                                .name(categoryName)
                                .active(true)
                                .build();
                        category = categoryService.save(category);
                        newCategoriesCreated++;
                    }

                    if (!isNewFormat && category.getIvaRate() != null) {
                        ivaRate = category.getIvaRate();
                    }
                }

                if (!productName.isEmpty()) {
                    try {
                        BigDecimal price = new BigDecimal(priceStr);
                        Integer stock = Integer.parseInt(stockStr);

                        Product product = Product.builder()
                                .name(productName)
                                .stock(stock)
                                .imageUrl(imageUrl.isEmpty() ? null : imageUrl)
                                .category(category)
                                .active(true)
                                .build();

                        final BigDecimal targetVat = ivaRate;
                        TaxRate taxRate = taxRateRepository.findAll().stream()
                                .filter(t -> t.getVatRate().compareTo(targetVat) == 0)
                                .findFirst()
                                .orElse(taxRateRepository.findById(1L).orElse(null));
                        product.setTaxRate(taxRate);
                        product.setPrice(price);

                        Product saved = productService.save(product);
                        productsCreated++;

                        ProductPriceRequest priceRequest = new ProductPriceRequest();
                        priceRequest.setPrice(price);
                        priceRequest.setVatRate(ivaRate);
                        priceRequest.setStartDate(LocalDateTime.now());
                        priceRequest.setLabel("Tarifa inicial");

                        productPriceService.schedulePrice(saved.getId(), priceRequest);
                        pricesCreated++;

                    } catch (NumberFormatException e) {
                        System.err.println("Error decoding row for: " + productName);
                    }
                }
                linesProcessed++;
            }

            return String.format(
                    "CSV processed: %d products created, %d prices registered, %d categories in %d lines.",
                    productsCreated, pricesCreated, newCategoriesCreated, linesProcessed);

        } catch (Exception e) {
            return "Error processing CSV: " + e.getMessage();
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


