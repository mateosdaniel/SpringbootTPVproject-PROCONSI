package com.proconsi.electrobazar.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main Web Security Configuration.
 * 
 * <p>Centralizes the configuration of HTTP filters, authorization rules, 
 * password encryption, and exception handling strategies.
 * Implements a hybrid approach where:
 * 1. Web browser requests (/tpv, /admin) are redirected to a login page.
 * 2. API requests (/api) return HTTP 401 instead of redirects.</p>
 *
 * <p>Authentication is processed via two filters before reaching 
 * the standard {@link UsernamePasswordAuthenticationFilter}:
 * <ul>
 *     <li>{@link JwtAuthenticationFilter}: Verifies standard worker tokens.</li>
 *     <li>{@link TpvTokenFilter}: Verifies static TPV device tokens.</li>
 * </ul></p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TpvTokenFilter tpvTokenFilter;

    /**
     * Bean for password hashing.
     * Uses BCrypt for secure, one-way cryptographic hashing of worker credentials.
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the main HTTP Filter Chain.
     * 
     * @param http The Spring Security object to configure.
     * @return A built security filter chain.
     * @throws Exception If an error occurs during chain building.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF as authentication is mostly stateless (Bearer tokens) 
            // and handled by customized filters
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Authorization Rules by Path and Method
            .authorizeHttpRequests(auth -> auth
                // Public static resources (CSS, JS, Images, Favicon)
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/icons/**", "/favicon.svg", "/favicon-light.svg").permitAll()
                
                // Public authentication and generic informational pages
                .requestMatchers("/login", "/register", "/error", "/logout").permitAll()
                .requestMatchers("/api/workers/login").permitAll()

                // TPV PUBLIC CATALOG (Allow public access to read categories and products)
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()

                // SECURED ACTIONS (Require specific authorities granted during token filtering)
                .requestMatchers("/api/sales/with-tax/**").authenticated()
                .requestMatchers("/api/sales/stats/today").hasAnyAuthority("TPV_CLIENT", "ADMIN_ACCESS", "SALE_VIEW")

                // ADMIN PANEL ONLY (Protected by ADMIN_ACCESS authority)
                .requestMatchers(HttpMethod.DELETE, "/admin/products/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers(HttpMethod.POST, "/api/product-prices/bulk-schedule").hasAuthority("ADMIN_ACCESS")
                .requestMatchers(HttpMethod.GET, "/api/product-prices/future").hasAuthority("ADMIN_ACCESS")
                
                // MULTI-AUTHORITY ACTIONS (Standardized worker access)
                .requestMatchers("/api/product-prices/**").hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                .requestMatchers("/api/suspended-sales/**").hasAnyAuthority("HOLD_SALES", "ADMIN_ACCESS")
                .requestMatchers("/api/roles/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/permissions/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/sales/range").hasAnyAuthority("SALE_VIEW", "ADMIN_ACCESS")
                .requestMatchers("/api/sales/**").hasAnyAuthority("SALE_VIEW", "ADMIN_ACCESS", "TPV_CLIENT")
                .requestMatchers("/api/workers/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/activity-log/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/cash-registers/**").hasAnyAuthority("CASH_REGISTER", "ADMIN_ACCESS")
                .requestMatchers("/api/cash-withdrawals/**").hasAnyAuthority("CASH_REGISTER", "ADMIN_ACCESS")
                .requestMatchers("/api/returns/**").hasAnyAuthority("RETURNS", "ADMIN_ACCESS")
                .requestMatchers("/api/tariffs/**").hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                .requestMatchers("/api/ipc/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/customers/**").hasAnyAuthority("CRM_ACCESS", "ADMIN_ACCESS", "TPV_CLIENT")
                
                // CATCH-ALL FOR ADMIN AND USER INTERFACES
                .requestMatchers("/admin/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN_ACCESS")
                .requestMatchers("/admin/api/**").hasAuthority("ADMIN_ACCESS")

                // GENERAL AUTHENTICATED ACCESS (Requires valid token for all /tpv and general /api calls)
                .requestMatchers("/tpv/**").authenticated()
                .requestMatchers("/api/**").authenticated()

                // Strict catch-all for any other request
                .anyRequest().authenticated())
            
            // 3. Stateless and Interactive Exception Handling Strategies
            .exceptionHandling(exceptions -> exceptions
                
                // For API requests, return 401 UNAUTHORIZED status instead of redirects
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getServletPath().startsWith("/api"))
                
                // For HTML requests, redirect the browser to the login page
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    request -> request.getServletPath().startsWith("/tpv"))
                
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    request -> request.getServletPath().startsWith("/admin"))
                
                // Redirect user to the TPV dashboard if they try to access restricted admin pages without permission
                .defaultAccessDeniedHandlerFor(
                    (request, response, accessDeniedException) -> response.sendRedirect("/tpv"),
                    request -> request.getServletPath().startsWith("/admin")))
            
            // 4. Session Management Strategy
            // Using standard session policy for web browser interactions while keeping API authentication stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            
            // 5. Custom Filter Registration
            // Order is important: JWT and TPV tokens are validated before the default UsernamePassword filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(tpvTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}