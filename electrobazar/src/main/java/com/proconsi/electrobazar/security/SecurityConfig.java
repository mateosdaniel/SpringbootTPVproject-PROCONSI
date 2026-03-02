package com.proconsi.electrobazar.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // login endpoint is public
                        .requestMatchers("/api/workers/login").permitAll()
                        // allow anybody to GET and POST customers (used by TPV for search and creation)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/customers/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/customers").permitAll()
                        // admin controllers require explicit authority
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN_ACCESS")
                        // the rest of the API needs a valid token/session
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll() // Keep web/UI parts permissive for transition
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
