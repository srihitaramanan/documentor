package com.srihitaramanan.documentor.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and validates JWTs.
 *
 * <p>Tokens carry the user's UUID in the {@code sub} (subject) claim and
 * expire after {@code documentor.jwt.expiry-hours} hours. Signed with HMAC-SHA256
 * using a server-side secret. See ADR-006 for design rationale.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiry;

    public JwtService(
            @Value("${documentor.jwt.secret}") String secret,
            @Value("${documentor.jwt.expiry-hours:24}") int expiryHours
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "documentor.jwt.secret must be at least 32 chars (256 bits). " +
                            "Generate one with: openssl rand -hex 32");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofHours(expiryHours);
    }

    /** Issue a fresh JWT for the given user. */
    public String issueToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a token and extract the user ID.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired,
     *         or tampered with.
     */
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    /** Convenience for filters: returns {@code Duration.ZERO} when expired or about to expire. */
    public Duration timeUntilExpiry(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Duration.between(Instant.now(), claims.getExpiration().toInstant());
    }
}