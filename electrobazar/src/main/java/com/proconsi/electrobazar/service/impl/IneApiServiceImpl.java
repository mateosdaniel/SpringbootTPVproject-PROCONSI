package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.IneIpcResponse;
import com.proconsi.electrobazar.service.IneApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IneApiServiceImpl implements IneApiService {

    private final RestTemplate restTemplate;
    private static final String INE_URL = "https://servicios.ine.es/wstempus/js/ES/DATOS_SERIE/IPC251856?nult=2";

    // Manual cache
    private BigDecimal cachedIpc;
    private LocalDateTime cacheExpiry;

    @Override
    public BigDecimal getLatestIpc() {
        if (cachedIpc != null && cacheExpiry != null && cacheExpiry.isAfter(LocalDateTime.now())) {
            log.debug("Returning cached IPC value from INE: {}", cachedIpc);
            return cachedIpc;
        }

        try {
            log.info("Fetching latest IPC from INE... URL: {}", INE_URL);

            String rawJson = restTemplate.getForObject(INE_URL, String.class);
            if (rawJson == null || rawJson.isEmpty()) {
                log.warn("INE API returned empty response body");
                return null;
            }
            log.info("RAW JSON from INE (first 200 chars): {}", rawJson.substring(0, Math.min(200, rawJson.length())));

            IneIpcResponse response = null;
            if (rawJson.trim().startsWith("[")) {
                IneIpcResponse[] list = restTemplate.getForObject(INE_URL, IneIpcResponse[].class);
                if (list != null && list.length > 0)
                    response = list[0];
            } else {
                response = restTemplate.getForObject(INE_URL, IneIpcResponse.class);
            }

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                // Search for the most recent data point with a non-null value
                BigDecimal valor = response.getData().stream()
                        .map(IneIpcResponse.IneDataPoint::getValor)
                        .filter(v -> v != null)
                        .findFirst()
                        .orElse(null);

                if (valor != null) {
                    this.cachedIpc = valor;
                    this.cacheExpiry = LocalDateTime.now().plusHours(24);
                    log.info("Successfully fetched and cached IPC: {}%", valor);
                    return valor;
                }
            }
            log.warn("INE API returned valid structure but no valid data points found for IPC");
        } catch (Exception e) {
            log.error("CRITICAL error fetching IPC from INE. URL: {}. Error: {}", INE_URL, e.getMessage());
        }

        // Return the last cached value if available
        if (cachedIpc != null) {
            log.info("Returning expired cached IPC value due to API failure: {}", cachedIpc);
            return cachedIpc;
        }

        return null;
    }
}
