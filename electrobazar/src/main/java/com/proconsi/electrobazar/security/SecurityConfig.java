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
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/categories/**")
                                                .permitAll()
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/products/**")
                                                .permitAll()

                                                // TPV SECURED ACTION (Requires X-TPV-TOKEN or valid Worker JWT)
                                                .requestMatchers("/api/sales/with-tax/**").authenticated()
                                                .requestMatchers("/api/sales/stats/today").hasAnyAuthority("TPV_CLIENT", "ADMIN_ACCESS", "SALE_VIEW")

                                                // ADMIN
                                                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/admin/products/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers("/api/activity-log/**").hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers(org.springframework.http.HttpMethod.POST,
                                                                "/api/product-prices/bulk-schedule")
                                                .hasAuthority("ADMIN_ACCESS")
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/product-prices/future")
                                                .hasAuthority("ADMIN_ACCESS")
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
                                                                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                                                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**"))
                                                // Redirect unauthenticated HTML requests to login
                                                .defaultAuthenticationEntryPointFor(
                                                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                                                                                "/login"),
                                                                request -> request.getServletPath().startsWith("/tpv"))
                                                .defaultAuthenticationEntryPointFor(
                                                                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                                                                                "/login"),
                                                                request -> request.getServletPath()
                                                                                .startsWith("/admin"))
                                                // Redirect unauthorized HTML requests to TPV
                                                .defaultAccessDeniedHandlerFor(
                                                                (request, response, accessDeniedException) -> response
                                                                                .sendRedirect("/tpv"),
                                                                request -> request.getServletPath()
                                                                                .startsWith("/admin")))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(tpvTokenFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

}