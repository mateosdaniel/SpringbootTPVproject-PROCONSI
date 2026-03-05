package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.model.Product;
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
 * Imports products from a 6-column CSV file.
 *
 * <p>
 * Expected header (skipped automatically):
 * 
 * <pre>
 * Categoria,NombreProducto,Precio,IVA,Stock,ImagenURL
 * </pre>
 *
 * <p>
 * Column layout:
 * <ul>
 * <li>0 – Categoria (looked up or created)</li>
 * <li>1 – NombreProducto</li>
 * <li>2 – Precio (e.g. 19.99 or 19,99€)</li>
 * <li>3 – IVA decimal rate (e.g. 0.21). Defaults to 0.21 if
 * missing/unparseable.</li>
 * <li>4 – Stock (integer)</li>
 * <li>5 – ImagenURL (optional)</li>
 * </ul>
 *
 * <p>
 * For each valid product row a
 * {@link com.proconsi.electrobazar.model.ProductPrice}
 * record is created via {@link ProductPriceService#schedulePrice} so that the
 * temporal
 * pricing system has a proper entry (instead of falling back to the hardcoded
 * 21% VAT).
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final BigDecimal DEFAULT_VAT = new BigDecimal("0.21");

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;

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
                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // Categoria, NombreProducto, Precio, IVA, Stock, ImagenURL
                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    continue; // Skip malformed lines (need at least Categoria, Nombre, Precio, old-Stock or
                              // new-IVA)
                }

                String categoryName = columns[0].trim();
                String productName = columns[1].trim();
                String priceStr = columns[2].trim().replace("€", "").replace(",", ".");

                // ── New 6-column format: col[3]=IVA, col[4]=Stock, col[5]=ImagenURL ──────
                // ── Old 5-column format: col[3]=Stock, col[4]=ImagenURL ──────────────────
                // Detect which format we have:
                // If columns[3] is a decimal < 1 (e.g. "0.21"), treat as new format.
                // Otherwise treat as the old format (columns[3] = stock integer).
                BigDecimal ivaRate = DEFAULT_VAT;
                String stockStr;
                String imageUrl;

                boolean isNewFormat = isIvaColumn(columns[3].trim());
                if (isNewFormat) {
                    // New format: 0=Cat, 1=Name, 2=Price, 3=IVA, 4=Stock, 5=ImageURL
                    ivaRate = parseVat(columns[3].trim());
                    stockStr = columns.length >= 5 ? columns[4].trim() : "0";
                    imageUrl = columns.length >= 6 ? columns[5].trim() : "";
                } else {
                    // Old format: 0=Cat, 1=Name, 2=Price, 3=Stock, 4=ImageURL
                    // Fall back to category IVA or default
                    stockStr = columns[3].trim();
                    imageUrl = columns.length >= 5 ? columns[4].trim() : "";
                }

                // ── Find or create category ────────────────────────────────────────────
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

                    // For old-format rows use the category's IVA if available
                    if (!isNewFormat && category.getIvaRate() != null) {
                        ivaRate = category.getIvaRate();
                    }
                }

                // ── Validate and create product + ProductPrice ─────────────────────────
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
                                .ivaRate(ivaRate)
                                .build();

                        // Use setPrice to handle Gross -> Net conversion automatically
                        product.setPrice(price);

                        Product saved = productService.save(product);
                        productsCreated++;

                        // Create the initial ProductPrice entry via the service so all
                        // business rules (cache eviction, open-ended close) are applied.
                        ProductPriceRequest priceRequest = new ProductPriceRequest();
                        priceRequest.setPrice(price);
                        priceRequest.setVatRate(ivaRate);
                        priceRequest.setStartDate(LocalDateTime.now());
                        priceRequest.setLabel("Tarifa inicial");

                        productPriceService.schedulePrice(saved.getId(), priceRequest);
                        pricesCreated++;

                    } catch (NumberFormatException e) {
                        System.err.println(
                                "Error parseando números para el producto '" + productName + "': " + e.getMessage());
                    }
                }
                linesProcessed++;
            }

            return String.format(
                    "CSV procesado: %d productos creados, %d precios registrados, %d nuevas categorías en %d líneas válidas.",
                    productsCreated, pricesCreated, newCategoriesCreated, linesProcessed);

        } catch (Exception e) {
            e.printStackTrace();
            return "Error procesando el archivo CSV: " + e.getMessage();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the raw column value looks like an IVA decimal rate
     * (i.e. parseable as a BigDecimal strictly between 0 and 1 inclusive).
     * This is used to distinguish the new 6-column format from the old 5-column
     * format.
     */
    private boolean isIvaColumn(String raw) {
        if (raw.isEmpty())
            return false;
        try {
            BigDecimal val = new BigDecimal(raw.replace(",", "."));
            // IVA rates are in range [0, 1]; stock integers are typically ≥ 1
            return val.compareTo(BigDecimal.ONE) <= 0 && val.compareTo(BigDecimal.ZERO) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Parses a VAT rate string, defaulting to 0.21 if unparseable.
     */
    private BigDecimal parseVat(String raw) {
        try {
            BigDecimal val = new BigDecimal(raw.replace(",", "."));
            if (val.compareTo(BigDecimal.ZERO) >= 0 && val.compareTo(BigDecimal.ONE) <= 0) {
                return val;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return DEFAULT_VAT;
    }
}
