package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.dto.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interface defining operations for product catalog management and stock
 * control.
 */
public interface ProductService {

    /**
     * Retrieves all products, including inactive ones.
     * 
     * @return A list of all Product entities.
     */
    List<Product> findAll();

    /**
     * Retrieves a paginated list of all products.
     * 
     * @param pageable Pagination and sorting criteria.
     * @return A page of products.
     */
    Page<Product> findAll(Pageable pageable);

    /**
     * Retrieves only products marked as active.
     * 
     * @return A list of active products.
     */
    List<Product> findAllActive();

    /**
     * Retrieves active products with their category information eagerly loaded.
     * 
     * @return A list of active products with categories.
     */
    List<Product> findAllActiveWithCategory();

    /**
     * Retrieves all products with their category information eagerly loaded.
     * 
     * @return A list of all products with categories.
     */
    List<Product> findAllWithCategory();

    /**
     * Retrieves products belonging to a specific category.
     * 
     * @param categoryId The category ID.
     * @return A list of products in that category.
     */
    List<Product> findByCategory(Long categoryId);

    /**
     * Searches for products by name.
     * 
     * @param name The name fragment to search for.
     * @return A list of matching products.
     */
    List<Product> search(String name);

    /**
     * Finds a specific product by its exact name.
     * 
     * @param name Exact name.
     * @return The found product or null.
     */
    Product findByName(String name);

    /**
     * Advanced filtering for products based on multiple criteria.
     *
     * @param search   Name fragment.
     * @param category Category name filter.
     * @param stock    Stock level filter (e.g., "low").
     * @param active   Active status filter.
     * @return A filtered list of products.
     */
    List<Product> getFilteredProducts(String search, String category, String stock, Boolean active, Long measurementUnitId);

    /**
     * Advanced filtering for products with pagination and sorting.
     */
    Page<Product> getFilteredProducts(String search, String category, String stock, Boolean active, Long measurementUnitId, Pageable pageable);

    /**
     * Finds a specific product by ID.
     * 
     * @param id The primary key.
     * @return The found Product entity.
     */
    Product findById(Long id);

    /**
     * Efficiently fetches multiple products by their IDs.
     * 
     * @param ids List of product IDs.
     * @return A list of products.
     */
    List<Product> findAllByIds(List<Long> ids);

    /**
     * Persists a new product record.
     * 
     * @param product The entity data.
     * @return The saved Product.
     */
    Product save(Product product);

    /**
     * Updates an existing product using a DTO request.
     * 
     * @param id      The product ID.
     * @param request The updated data in DTO format.
     * @return The updated Product entity.
     */
    Product update(Long id, ProductRequest request);

    /**
     * Performs a soft delete (active = false).
     * 
     * @param id The product ID.
     */
    void delete(Long id);

    /**
     * Physically removes the product from the database if no constraints prevent
     * it.
     * 
     * @param id The product ID.
     */
    void hardDeleteProduct(Long id);

    /**
     * Reduces the stock quantity for a product.
     * 
     * @param productId The ID of the product.
     * @param quantity  Amount to subtract.
     */
    void decreaseStock(Long productId, BigDecimal quantity);

    /**
     * Increases the stock quantity for a product.
     * 
     * @param productId The ID of the product.
     * @param quantity  Amount to add.
     */
    void increaseStock(Long productId, BigDecimal quantity);

    /**
     * Sets the stock to a specific absolute value.
     * 
     * @param productId The ID of the product.
     * @param quantity  The new stock level.
     */
    void adjustStock(Long productId, BigDecimal quantity);

    /**
     * Retrieves the most sold or most relevant products up to a limit.
     * 
     * @param limit Maximum number of products to return.
     * @return A list of top products.
     */
    List<Product> getTopProducts(int limit);

    /**
     * Updates all products with a new tax rate.
     * 
     * @param newTaxRateId The ID of the TaxRate to apply globally.
     */
    void applyNewTaxRate(Long newTaxRateId);

    /**
     * Applies a specific tax rate to a subset of products.
     * 
     * @param productIds List of affected product IDs.
     * @param taxRate    The TaxRate entity to apply.
     */
    void applyTaxRateToProducts(List<Long> productIds, TaxRate taxRate);

    /**
     * Recalculates the gross labels for all products associated with a tax rate.
     * 
     * @param taxRateId  The tax rate ID.
     * @param newVatRate The new VAT value.
     */
    void recalculatePricesForTaxRate(Long taxRateId, BigDecimal newVatRate);

    Page<Product> findAllWithCategoryPaged(int page, int size);

    List<Product> getTopSellingProducts(int limit);

    List<Product> getTopProductsByRank(int limit);
}

