package com.resolveai.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Environment-backed JWT configuration properties.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "resolveai.jwt")
public class JwtProperties {

    /**
     * Base64 or plain secret key (must be at least 256 bits / 32 bytes for HMAC-SHA256).
     */
    private String secret = "c2VjdXJlX2VudGVycHJpc2VfamF2YV8yMV9zcHJpbmdfYm9vdF9qd3Rfc2VjcmV0X2tleV9leGFtcGxl";

    /**
     * Access token expiration in milliseconds (default 15 minutes = 900,000 ms).
     */
    private long accessExpirationMs = 900000L;

    /**
     * Refresh token expiration in milliseconds (default 7 days = 604,800,000 ms).
     */
    private long refreshExpirationMs = 604800000L;
}
