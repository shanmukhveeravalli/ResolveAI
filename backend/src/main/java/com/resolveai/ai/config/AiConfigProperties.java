package com.resolveai.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for ResolveAI AI Foundation.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "resolveai.ai")
public class AiConfigProperties {

    /**
     * Master toggle enabling or disabling AI integration features.
     */
    private boolean enabled = false;

    /**
     * Target AI provider identifier (e.g. openai, gemini, mock).
     */
    private String provider = "openai";

    /**
     * Provider API key for authentication.
     */
    private String apiKey = "";

    /**
     * LLM model name.
     */
    private String model = "gpt-4o-mini";

    /**
     * Base URL for the AI provider endpoint.
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * HTTP connection and read timeout in seconds.
     */
    private int timeoutSeconds = 10;
}
