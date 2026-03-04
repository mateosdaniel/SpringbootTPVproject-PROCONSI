package com.proconsi.electrobazar.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth

                                                // ── PUBLIC ENDPOINTS ─────────────────────────────────────────────
                                                // Web login / logout pages (form-based session auth)
                                                .requestMatchers("/login", "/logout").permitAll()
                                                // API login endpoint (returns JWT / creates session)
                                                .requestMatchers("/api/workers/login").permitAll()

                                                // ── ADMIN-ONLY ENDPOINTS (RBAC) ──────────────────────────────────
                                                // Audit / activity log — sensitive operational data
                                                .requestMatchers("/api/activity-log/**")
                                                .hasAuthority("ADMIN_ACCESS")
                                                // Bulk price scheduling — mass price mutation, admin only
                                                .requestMatchers(org.springframework.http.HttpMethod.POST,
                                                                "/api/product-prices/bulk-schedule")
                                                .hasAuthority("ADMIN_ACCESS")
                                                // Price history and future prices — admin dashboard read operations
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/product-prices/future")
                                                .hasAuthority("ADMIN_ACCESS")
                                                // PIN escalation — any authenticated worker can attempt it;
                                                // the AdminPinService validates the PIN itself
                                                .requestMatchers(org.springframework.http.HttpMethod.POST,
                                                                "/admin/login")
                                                .authenticated()
                                                // Admin web interface and logout (must come after the POST /admin/login
                                                // rule)
                                                .requestMatchers("/admin/**").hasAuthority("ADMIN_ACCESS")
                                                // Admin REST API namespace
                                                .requestMatchers("/api/admin/**").hasAuthority("ADMIN_ACCESS")

                                                // ── AUTHENTICATED ENDPOINTS ───────────────────────────────────────
                                                // TPV web interface — any logged-in worker
                                                .requestMatchers("/tpv/**").authenticated()
                                                // Catalog reads needed by the TPV frontend and JavaFX client
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/products/**")
                                                .authenticated()
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/categories/**")
                                                .authenticated()
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/cash-registers/**")
                                                .authenticated()
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/product-prices/**")
                                                .authenticated()
                                                // Customer management — search and creation during a sale
                                                .requestMatchers("/api/customers/**").authenticated()
                                                // Roles, workers, sales
                                                .requestMatchers("/api/roles/**").authenticated()
                                                .requestMatchers("/api/workers/**").authenticated()
                                                .requestMatchers("/api/sales/**").authenticated()
                                                // Catch-all for any remaining API route
                                                .requestMatchers("/api/**").authenticated()

                                                // ── SECURE BY DEFAULT ─────────────────────────────────────────────
                                                // Every request not matched above requires authentication.
                                                // This replaces the previous .anyRequest().permitAll() hole.
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                // Redirect unauthenticated HTML requests to login, while returning 401
                                                // for API
                                                .defaultAuthenticationEntryPointFor(
                                                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                                                                                "/login"),
                                                                request -> request.getServletPath().startsWith("/tpv"))
                                                .defaultAuthenticationEntryPointFor(
                                                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                                                                                "/login"),
                                                                request -> request.getServletPath()
                                                                                .startsWith("/admin"))
                                                // Redirect unauthorized HTML requests to TPV or login
                                                .defaultAccessDeniedHandlerFor(
                                                                (request, response, accessDeniedException) -> response
                                                                                .sendRedirect("/tpv"),
                                                                request -> request.getServletPath()
                                                                                .startsWith("/admin")))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
