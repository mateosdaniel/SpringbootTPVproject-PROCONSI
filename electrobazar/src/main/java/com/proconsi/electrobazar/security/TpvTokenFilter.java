package com.proconsi.electrobazar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Secondary security filter for TPV client devices.
 * 
 * <p>Validates an "X-TPV-TOKEN" header with a configured static token. 
 * This allows specialized TPV hardware to bypass traditional worker 
 * login for a subset of catalog operations.</p>
 *
 * <p>Grants the 'TPV_CLIENT' authority if the token matches.</p>
 */
@Component
public class TpvTokenFilter extends OncePerRequestFilter {

    /**
     * Statically configured secret token for TPV integrations.
     * Usually loaded from system properties or environment variables.
     */
    @Value("${tpv.security.token}")
    private String tpvToken;

    /**
     * Intercepts requests to check for the X-TPV-TOKEN header.
     * 
     * @param request Inbound request.
     * @param response Outbound response.
     * @param filterChain Current filter chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Check for specific TPV custom header
        String requestToken = request.getHeader("X-TPV-TOKEN");

        // Simple string comparison for the security token
        if (tpvToken != null && tpvToken.equals(requestToken)) {
            // Build pseudo-user for the TPV client
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "TPV_CLIENT",
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("TPV_CLIENT")));
            
            // Populate SecurityContext with the TPV_CLIENT authority
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
