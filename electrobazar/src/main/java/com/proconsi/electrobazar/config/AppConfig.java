package com.proconsi.electrobazar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * General application configuration.
 * This class defines beans and global configurations necessary for the application's operation.
 */
@Configuration
public class AppConfig {

    /**
     * Defines a RestTemplate HTTP client for making requests to external APIs.
     * An interceptor is configured to add an identifying User-Agent to each request,
     * allowing external services to identify the source of the requests.
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "Electrobazar-TPV/1.0 (Java Client)");
            return execution.execute(request, body);
        });
        return rt;
    }
}
