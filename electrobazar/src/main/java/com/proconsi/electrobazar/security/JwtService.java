package com.proconsi.electrobazar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Service for managing JSON Web Tokens (JWT).
 * Handles the creation, parsing, and validation of tokens used for stateless authentication.
 * 
 * <p>Tokens contain worker IDs and their specific permissions as claims to avoid
 * frequent database lookups during the request lifecycle.</p>
 */
@Service
public class JwtService {

    /**
     * Secret key for signing the JWT. 
     * In a production environment, this should be loaded from an external 
     * configuration or environment variable.
     */
    private static final String SECRET_KEY_STRING = "eb_super_secret_key_2024_electrobazar_pos_system_long_string";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET_KEY_STRING.getBytes());

    /**
     * Extracts the subject (username) from the token.
     * @param token JWT string.
     * @return The username embedded in the token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic method to extract a specific claim from the token using a resolver function.
     * @param <T> The type of the claim.
     * @param token JWT string.
     * @param claimsResolver Function to extract the desired claim from the {@link Claims} object.
     * @return The extracted claim value.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a new JWT for a worker.
     * @param username The worker's username.
     * @param workerId The internal database ID of the worker.
     * @param permissions Set of granular permission names granted to the worker.
     * @return A signed JWT valid for 10 hours.
     */
    public String generateToken(String username, Long workerId, Set<String> permissions) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("workerId", workerId);
        extraClaims.put("permissions", permissions);
        return createToken(extraClaims, username);
    }

    /**
     * Builds and signs the JWT with the provided claims and subject.
     */
    private String createToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validates if the token belongs to the given user and has not expired.
     * @param token JWT string.
     * @param username The username to compare against.
     * @return True if valid, false otherwise.
     */
    public Boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * Checks if the token's expiration date has passed.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from the token metadata.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses the token and retrieves all embedded claims.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
