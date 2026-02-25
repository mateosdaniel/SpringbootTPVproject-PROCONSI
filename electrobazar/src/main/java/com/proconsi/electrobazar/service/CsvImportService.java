package com.proconsi.electrobazar.service;

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
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Transactional
    public String importProductsCsv(MultipartFile file) {
        if (file.isEmpty()) {
            return "El archivo está vacío.";
        }

        int productsCreated = 0;
        int newCategoriesCreated = 0;
        int linesProcessed = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Saltar la cabecera
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // Categoria, NombreProducto, Precio, Stock, ImagenURL
                String[] columns = line.split(",", -1);
                if (columns.length < 4) {
                    continue; // Saltar líneas mal formadas
                }

                String categoryName = columns[0].trim();
                String productName = columns[1].trim();
                String priceStr = columns[2].trim().replace("€", "");
                String stockStr = columns[3].trim();
                String imageUrl = columns.length >= 5 ? columns[4].trim() : "";

                // Buscar o crear la categoría
                Category category = null;
                if (!categoryName.isEmpty()) {
                    // Primero intentamos buscarla
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
                }

                // Validar y crear producto
                if (!productName.isEmpty()) {
                    try {
                        BigDecimal price = new BigDecimal(priceStr);
                        Integer stock = Integer.parseInt(stockStr);

                        Product product = Product.builder()
                                .name(productName)
                                .price(price)
                                .stock(stock)
                                .imageUrl(imageUrl)
                                .category(category)
                                .active(true)
                                .build();

                        productService.save(product);
                        productsCreated++;
                    } catch (NumberFormatException e) {
                        System.err.println(
                                "Error parseando números para el producto " + productName + ": " + e.getMessage());
                    }
                }
                linesProcessed++;
            }

            return String.format("CSV procesado: %d productos creados, %d nuevas categorías en %d líneas válidas.",
                    productsCreated, newCategoriesCreated, linesProcessed);

        } catch (Exception e) {
            e.printStackTrace();
            return "Error procesando el archivo CSV: " + e.getMessage();
        }
    }
}
