package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.Worker;
import java.util.List;
import java.util.Optional;

/**
 * Interface defining operations for managing suspended sales (pending carts).
 * Allows workers to pause a transaction and resume it later.
 */
public interface SuspendedSaleService {

    /**
     * Persists a new suspended sale with the current cart state.
     *
     * @param lines  The product lines currently in the cart.
     * @param label  Optional descriptive label for easy identification.
     * @param worker The worker who is suspending the transaction.
     * @return The newly persisted SuspendedSale entity.
     */
    SuspendedSale suspend(List<SuspendedSaleLineRequest> lines, String label, Worker worker);

    /**
     * Marks a suspended sale as RESUMED.
     * The front-end is expected to reconstruct the cart using the returned data.
     *
     * @param id     Target ID.
     * @param worker The worker resuming the sale.
     * @return The updated SuspendedSale entity.
     */
    SuspendedSale resume(Long id, Worker worker);

    /**
     * Marks a suspended sale as CANCELLED, effectively discarding the cart.
     *
     * @param id     Target ID.
     * @param worker The worker performing the cancellation.
     * @return The updated SuspendedSale entity.
     */
    SuspendedSale cancel(Long id, Worker worker);

    /**
     * Retrieves all sales currently in SUSPENDED status.
     * @return A list of pending suspended sales history.
     */
    List<SuspendedSale> findAllSuspended();

    /**
     * Finds a specific suspended sale by ID.
     * @param id Primary key.
     * @return An Optional containing the SuspendedSale.
     */
    Optional<SuspendedSale> findById(Long id);
}
