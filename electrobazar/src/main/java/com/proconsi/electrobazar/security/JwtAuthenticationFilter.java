package com.proconsi.electrobazar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter that intercepts every HTTP request to check for a valid JWT in the 
 * Authorization header. If a valid JWT is found, it populates the Spring 
 * SecurityContext with the worker's user details and list of permissions.
 *
 * <p>This enables stateless authentication, allowing the server to 
 * verify the requester's identity without session storage.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /**
     * Extracts and validates the JWT from the Authorization header.
     * 1. Looks for the "Bearer " prefix.
     * 2. Extracts the username and granular permissions from the token.
     * 3. Sets the {@link UsernamePasswordAuthenticationToken} in the security context.
     * 
     * @param request The current HTTP request.
     * @param response The current HTTP response.
     * @param filterChain The Spring Security filter chain.
     */
    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Skip filter if header is missing or is not a Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7); // Remove "Bearer " prefix
        try {
            username = jwtService.extractUsername(jwt);

            // If user is valid and not already authenticated in this context
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // Extract custom permissions claim from JWT to populate authorities
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) jwtService.extractClaim(jwt,
                        claims -> claims.get("permissions", List.class));

                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // Build authentication token with permissions
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Finalize authentication for this request
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.error("JWT filter error: {}", e.getMessage());
            // Invalid or expired tokens will result in an unauthenticated context
        }

        filterChain.doFilter(request, response);
    }
}
