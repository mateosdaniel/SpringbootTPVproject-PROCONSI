package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.Worker;

import java.util.List;
import java.util.Optional;

public interface SuspendedSaleService {

    /**
     * Persists a new suspended sale with the given cart lines.
     *
     * @param lines  Cart lines from the JS cart
     * @param label  Optional descriptive label
     * @param worker The worker who suspended the sale
     * @return The persisted SuspendedSale
     */
    SuspendedSale suspend(List<SuspendedSaleLineRequest> lines, String label, Worker worker);

    /**
     * Marks the suspended sale as RESUMED. The caller (JS) is responsible
     * for loading the lines back into the cart.
     *
     * @param id     ID of the suspended sale
     * @param worker The worker resuming it
     * @return The updated SuspendedSale with its lines for the JS cart to consume
     */
    SuspendedSale resume(Long id, Worker worker);

    /**
     * Marks the suspended sale as CANCELLED (discarded without completing).
     *
     * @param id     ID of the suspended sale
     * @param worker The worker cancelling it
     * @return The updated SuspendedSale
     */
    SuspendedSale cancel(Long id, Worker worker);

    /**
     * Returns all sales with status SUSPENDED, ordered by createdAt descending.
     */
    List<SuspendedSale> findAllSuspended();

    /**
     * Finds a suspended sale by ID.
     */
    Optional<SuspendedSale> findById(Long id);
}
