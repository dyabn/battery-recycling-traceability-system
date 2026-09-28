package com.batteryrecycling.traceability.common.security;

import com.batteryrecycling.traceability.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-seconds}") long expirationSeconds
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be provided and contain at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public TokenResult issueToken(CurrentUser currentUser) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(currentUser.id()))
                .claim("enterpriseId", currentUser.enterpriseId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new TokenResult(token, "Bearer", expirationSeconds, tokenId);
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtPrincipal(
                    Long.valueOf(claims.getSubject()),
                    Long.valueOf(String.valueOf(claims.get("enterpriseId"))),
                    claims.getId()
            );
        } catch (ExpiredJwtException exception) {
            throw ApiException.unauthenticated("TOKEN_EXPIRED", "登录凭证已过期");
        } catch (RuntimeException exception) {
            throw ApiException.unauthenticated("UNAUTHENTICATED", "无效登录凭证");
        }
    }

    public record JwtPrincipal(Long userId, Long enterpriseId, String tokenId) {
    }

    public record TokenResult(String accessToken, String tokenType, long expiresIn, String tokenId) {
    }
}

