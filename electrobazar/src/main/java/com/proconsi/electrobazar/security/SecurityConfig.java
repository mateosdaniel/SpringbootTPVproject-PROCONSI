package com.proconsi.electrobazar.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Main Web Security Configuration.
 * 
 * <p>
 * Centralizes the configuration of HTTP filters, authorization rules,
 * password encryption, and exception handling strategies.
 * Implements a hybrid approach where:
 * 1. Web browser requests (/tpv, /admin) are redirected to a login page.
 * 2. API requests (/api) return HTTP 401 instead of redirects.
 * </p>
 *
 * <p>
 * Authentication is processed via two filters before reaching
 * the standard {@link UsernamePasswordAuthenticationFilter}:
 * <ul>
 * <li>{@link JwtAuthenticationFilter}: Verifies standard worker tokens.</li>
 * <li>{@link TpvTokenFilter}: Verifies static TPV device tokens.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final TpvTokenFilter tpvTokenFilter;

        /**
         * Disables the 'Using generated security password' log by providing a custom
         * UserDetailsService. Since the app uses JWT/Custom PIN auth, we provide
         * an empty manager to satisfy Spring Boot's requirements.
         */
        @Bean
        public UserDetailsService userDetailsService() {
                return new InMemoryUserDetailsManager();
        }

        /**
         * Bean for password hashing.
         * Uses BCrypt for secure, one-way cryptographic hashing of worker credentials.
         * 
         * @return A BCryptPasswordEncoder instance.
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * Bean to manage security context persistence across requests.
         */
        @Bean
        public SecurityContextRepository securityContextRepository() {
                return new HttpSessionSecurityContextRepository();
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
                                // 1. Enabling selective CSRF protection (Security Requirement)
                                // Enabling it for browser sessions (/admin, /tpv, /login)
                                // While ignoring it for API calls that use Bearer tokens
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers("/api/**", "/admin/login",
                                                                "/admin/products/**", "/admin/upload-csv", "/admin/upload-customers-csv",
                                                                "/admin/settings/pin", "/forgot-password", "/reset-password"))

                                // 2. Authorization Rules by Path and Method
                                .authorizeHttpRequests(auth -> auth
                                                // Public static resources (CSS, JS, Images, Favicon)
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**",
                                                                "/icons/**", "/favicon.svg",
                                                                "/favicon-light.svg")
                                                .permitAll()

                                                // Public authentication and generic informational pages
                                                .requestMatchers("/login", "/register", "/error", "/logout", "/forgot-password", "/reset-password").permitAll()
                                                .requestMatchers("/api/workers/login").permitAll()

                                                // TPV PUBLIC CATALOG (Allow public access to read categories and
                                                // products)
                                                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()

                                                // SECURED ACTIONS (Require specific authorities granted during token
                                                // filtering)
                                                .requestMatchers("/api/sales/with-tax/**").authenticated()
                                                .requestMatchers("/api/sales/stats/today")
                                                .hasAnyAuthority("TPV_CLIENT", "ADMIN_ACCESS", "SALE_VIEW")

                                                // ADMIN PANEL ONLY (Protected by ADMIN_ACCESS authority)
                                                .requestMatchers(HttpMethod.DELETE, "/admin/products/**")
                                                .hasAnyAuthority("ADMIN_ACCESS", "MANAGE_PRODUCTS_TPV")
                                                .requestMatchers(HttpMethod.POST, "/api/product-prices/bulk-schedule")
                                                .hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers(HttpMethod.GET, "/api/product-prices/future")
                                                .hasAuthority("ADMIN_ACCESS")

                                                // MULTI-AUTHORITY ACTIONS (Standardized worker access)
                                                .requestMatchers("/api/product-prices/**")
                                                .hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/suspended-sales/**")
                                                .hasAnyAuthority("HOLD_SALES", "ADMIN_ACCESS")
                                                .requestMatchers("/api/roles/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/permissions/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/sales/range")
                                                .hasAnyAuthority("SALE_VIEW", "ADMIN_ACCESS")
                                                .requestMatchers("/api/sales/**")
                                                .hasAnyAuthority("SALE_VIEW", "ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/workers/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/activity-log/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/cash-registers/**")
                                                .hasAnyAuthority("CASH_REGISTER", "CASH_CLOSE", "ADMIN_ACCESS")
                                                .requestMatchers("/api/cash-withdrawals/**")
                                                .hasAnyAuthority("CASH_REGISTER", "CASH_CLOSE", "ADMIN_ACCESS")
                                                .requestMatchers("/api/returns/**")
                                                .hasAnyAuthority("RETURNS", "ADMIN_ACCESS")
                                                .requestMatchers("/api/tariffs/**")
                                                .hasAnyAuthority("ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/ipc/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/customers/**")
                                                .hasAnyAuthority("CRM_ACCESS", "ADMIN_ACCESS", "TPV_CLIENT")
                                                .requestMatchers("/api/email/**")
                                                .hasAnyAuthority("CRM_ACCESS", "ADMIN_ACCESS", "TPV_CLIENT")

                                                // CATCH-ALL FOR ADMIN AND USER INTERFACES
                                                .requestMatchers("/tpv/**", "/admin/**", "/api/admin/**")
                                                .authenticated()
                                                .requestMatchers("/api/**").authenticated()

                                                // Strict catch-all for any other request
                                                .anyRequest().authenticated())

                                // 3. Exception Handling (Redirects vs 401)
                                .exceptionHandling(exceptions -> exceptions
                                                // For API requests, return 401 UNAUTHORIZED status instead of redirects
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                request -> request.getServletPath().startsWith("/api"))
                                                // For HTML requests, redirect the browser to the login page
                                                .defaultAuthenticationEntryPointFor(
                                                                new LoginUrlAuthenticationEntryPoint("/login"),
                                                                request -> !request.getServletPath().startsWith("/api")))

                                // 4. Session Management Strategy
                                // Using standard session policy for web browser interactions while keeping API
                                // authentication stateless
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                                // 5. Custom Filter Registration
                                // Order is important: JWT and TPV tokens are validated before the default
                                // UsernamePassword filter
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(tpvTokenFilter, UsernamePasswordAuthenticationFilter.class)

                                // 6. Frame Options - Allow same origin for invoice/receipt previews
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin()))

                                // 7. Persist context manually (Spring Security 6 Requirement)
                                .securityContext(context -> context
                                                .securityContextRepository(securityContextRepository()));

                return http.build();
        }
}