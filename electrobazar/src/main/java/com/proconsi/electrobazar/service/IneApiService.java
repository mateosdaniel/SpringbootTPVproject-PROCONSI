package com.proconsi.electrobazar.service;

import java.math.BigDecimal;

/**
 * Interface for interacting with external INE (Instituto Nacional de Estadística) APIs.
 * Primarily used to retrieve economic indicators like IPC.
 */
public interface IneApiService {

    /**
     * Fetches the latest annual IPC (Consumer Price Index) variation from INE.
     *
     * @return The latest IPC value as a percentage (e.g., 2.3), or null if the API call fails.
     */
    BigDecimal getLatestIpc();
}
