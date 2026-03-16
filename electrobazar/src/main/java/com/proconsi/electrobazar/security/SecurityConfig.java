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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final TpvTokenFilter tpvTokenFilter;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                // Public static resources
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**",
                                                                "/icons/**",
                                                                "/favicon.svg", "/favicon-light.svg")
                                                .permitAll()
                                                .requestMatchers("/login", "/register", "/error", "/logout").permitAll()
                                                .requestMatchers("/api/workers/login").permitAll()

                                                // TPV PUBLIC CATALOG (GET only)
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/categories/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/products/**")
                                                .permitAll()

                                                // TPV SECURED ACTION (Requires X-TPV-TOKEN or valid Worker JWT)
                                                .requestMatchers("/api/sales/with-tax/**").authenticated()
                                                .requestMatchers("/api/sales/stats/today").hasAnyAuthority("TPV_CLIENT", "ADMIN_ACCESS", "SALE_VIEW")

                                                // ADMIN
                                                .requestMatchers(HttpMethod.DELETE, "/admin/products/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/product-prices/bulk-schedule")
                                                .hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/product-prices/future")
                                                .hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/product-prices/**")
                                                .hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/suspended-sales/**").hasAnyAuthority("HOLD_SALES", "ADMIN_ACCESS")
                                                .requestMatchers("/api/roles/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/sales/range").hasAnyAuthority("SALE_VIEW", "ADMIN_ACCESS")
                                                .requestMatchers("/api/workers/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/activity-log/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/cash-registers/**").hasAnyAuthority("CASH_REGISTER", "ADMIN_ACCESS")
                                                .requestMatchers("/api/returns/**").hasAnyAuthority("RETURNS", "ADMIN_ACCESS")
                                                .requestMatchers("/api/tariffs/**").hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/customers/**").hasAnyAuthority("CRM_ACCESS", "ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/admin/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/admin/**").hasAuthority("ADMIN_ACCESS")

                                                // AUTHENTICATED
                                                .requestMatchers("/tpv/**").authenticated()
                                                .requestMatchers("/api/**").authenticated()

                                                // Catch-all
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                // API routes should return 401 instead of redirecting to /login
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                request -> request.getServletPath().startsWith("/api"))
                                                // Redirect unauthenticated HTML requests to login
                                                .defaultAuthenticationEntryPointFor(
                                                                new LoginUrlAuthenticationEntryPoint("/login"),
                                                                request -> request.getServletPath().startsWith("/tpv"))
                                                .defaultAuthenticationEntryPointFor(
                                                                new LoginUrlAuthenticationEntryPoint("/login"),
                                                                request -> request.getServletPath().startsWith("/admin"))
                                                // Redirect unauthorized HTML requests to TPV
                                                .defaultAccessDeniedHandlerFor(
                                                                (request, response, accessDeniedException) -> response.sendRedirect("/tpv"),
                                                                request -> request.getServletPath().startsWith("/admin")))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(tpvTokenFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

}