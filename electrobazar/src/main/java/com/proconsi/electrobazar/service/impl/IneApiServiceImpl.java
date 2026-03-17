package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.IneIpcResponse;
import com.proconsi.electrobazar.service.IneApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Implementation of {@link IneApiService}.
 * Encapsulates communication with the Spanish INE (Instituto Nacional de Estadística) API.
 * Includes a 24-hour manual cache to minimize external network calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IneApiServiceImpl implements IneApiService {

    private final RestTemplate restTemplate;
    private static final String INE_URL = "https://servicios.ine.es/wstempus/js/ES/DATOS_SERIE/IPC251856?nult=2";

    // Lightweight in-memory cache
    private BigDecimal cachedIpc;
    private LocalDateTime cacheExpiry;

    @Override
    public BigDecimal getLatestIpc() {
        if (cachedIpc != null && cacheExpiry != null && cacheExpiry.isAfter(LocalDateTime.now())) {
            log.debug("Returning cached IPC value: {}%", cachedIpc);
            return cachedIpc;
        }

        try {
            log.info("Requesting latest IPC from INE...");

            String rawJson = restTemplate.getForObject(INE_URL, String.class);
            if (rawJson == null || rawJson.isEmpty()) {
                log.warn("INE API returned an empty response.");
                return cachedIpc; // Return stale value if available
            }

            IneIpcResponse response = null;
            if (rawJson.trim().startsWith("[")) {
                IneIpcResponse[] list = restTemplate.getForObject(INE_URL, IneIpcResponse[].class);
                if (list != null && list.length > 0) response = list[0];
            } else {
                response = restTemplate.getForObject(INE_URL, IneIpcResponse.class);
            }

            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                BigDecimal value = response.getData().stream()
                        .map(IneIpcResponse.IneDataPoint::getValor)
                        .filter(v -> v != null)
                        .findFirst()
                        .orElse(null);

                if (value != null) {
                    this.cachedIpc = value;
                    this.cacheExpiry = LocalDateTime.now().plusHours(24);
                    log.info("IPC successfully updated and cached for 24h: {}%", value);
                    return value;
                }
            }
            log.warn("INE API response was valid but contained no data points.");
        } catch (Exception e) {
            log.error("Failed to fetch IPC from INE. Error: {}", e.getMessage());
        }

        // Fallback to last successful value even if expired
        return cachedIpc;
    }
}


