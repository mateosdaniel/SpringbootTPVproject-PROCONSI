package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Tariff;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface defining operations for customer pricing tariffs.
 */
public interface TariffService {

    /**
     * Retrieves all tariffs, regardless of status.
     * @return List of all Tariff entities.
     */
    List<Tariff> findAll();

    /**
     * Retrieves currently active tariffs.
     * @return List of active tariffs.
     */
    List<Tariff> findAllActive();

    /**
     * Finds a tariff by ID.
     * @param id Primary key.
     * @return Optional containing the Tariff.
     */
    Optional<Tariff> findById(Long id);

    /**
     * Finds a tariff by its unique name.
     * @param name The tariff name.
     * @return Optional containing the Tariff.
     */
    Optional<Tariff> findByName(String name);

    /**
     * Returns the system's default retail tariff (MINORISTA).
     * @return The default Tariff entity.
     */
    Tariff getDefault();

    /**
     * Creates a new custom tariff.
     * @param name               Name of the tariff.
     * @param discountPercentage Discount to apply on gross prices.
     * @param description        Optional description.
     * @return The saved Tariff.
     */
    Tariff create(String name, BigDecimal discountPercentage, String description);

    /**
     * Updates an existing custom tariff.
     * @param id                 ID of the tariff.
     * @param discountPercentage New discount value.
     * @param description        New description.
     * @return The updated Tariff.
     */
    Tariff update(Long id, BigDecimal discountPercentage, String description);

    /**
     * Deactivates a tariff. Customers using it will revert to default.
     * @param id ID to deactivate.
     */
    void deactivate(Long id);

    /**
     * Activates a previously disabled tariff.
     * @param id ID to activate.
     */
    void activate(Long id);

    /**
     * Counts how many customers are currently assigned to each tariff.
     * @return A map of tariffId to customerCount.
     */
    Map<Long, Long> getCustomerCountPerTariff();

    /**
     * Regenerates historical price snapshots for products, usually after a tax change.
     * @param affectedProducts List of products to process.
     */
    void regenerateTariffHistoryForProducts(List<Product> affectedProducts);
}
