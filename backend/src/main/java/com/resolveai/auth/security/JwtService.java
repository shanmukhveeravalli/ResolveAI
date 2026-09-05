package com.resolveai.auth.security;

import com.resolveai.auth.config.JwtProperties;
import com.resolveai.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Dedicated JWT service for token generation, signature validation, and claims extraction.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    /**
     * Generates a short-lived HMAC-SHA256 access token.
     */
    public String generateAccessToken(User user) {
        return generateAccessToken(user, jwtProperties.getAccessExpirationMs());
    }

    /**
     * Generates an HMAC-SHA256 access token with a custom expiration in milliseconds.
     */
    public String generateAccessToken(User user, long customExpirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole() != null ? user.getRole().getName() : RoleConstants.EMPLOYEE)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date(now))
                .expiration(new Date(now + customExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Generates a long-lived HMAC-SHA256 refresh token.
     */
    public String generateRefreshToken(User user) {
        return generateRefreshToken(user, jwtProperties.getRefreshExpirationMs());
    }

    /**
     * Generates an HMAC-SHA256 refresh token with a custom expiration in milliseconds.
     */
    public String generateRefreshToken(User user, long customExpirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(new Date(now))
                .expiration(new Date(now + customExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extracts all claims from a signed JWT. Throws JwtException if signature is invalid or expired.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        return claims.get(CLAIM_ROLE, String.class);
    }

    public String extractTokenType(String token) {
        Claims claims = extractClaims(token);
        return claims.get(CLAIM_TYPE, String.class);
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE)) &&
                    !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TYPE)) &&
                    !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessExpirationMs() {
        return jwtProperties.getAccessExpirationMs();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        } catch (Exception e) {
            keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
