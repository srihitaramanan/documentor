package com.srihitaramanan.documentor.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on every request.
 *
 * <p>If the token is valid, sets an authentication in the SecurityContext
 * with the user's UUID as the principal. If absent or invalid, the request
 * continues unauthenticated — downstream rules decide whether to allow it.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                UUID userId = jwtService.extractUserId(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        userId,                 // principal
                        null,                   // credentials (we don't carry the password)
                        Collections.emptyList() // authorities (we'll add roles later)
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ex) {
                // Token present but invalid — leave context unauthenticated.
                // Spring Security will reject the request at the next checkpoint.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}