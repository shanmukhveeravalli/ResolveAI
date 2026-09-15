package com.resolveai.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Concrete Embedding Provider implementation communicating with OpenAI-compatible
 * embedding APIs (e.g. OpenAI, Azure OpenAI, Ollama, vLLM, TEI).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final RestClient aiRestClient;
    private final AiConfigProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Double> generateEmbedding(String text) {
        if (!properties.isEnabled()) {
            throw new AiServiceUnavailableException("AI subsystem is disabled (resolveai.ai.enabled=false)");
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiServiceUnavailableException("AI provider API key is not configured");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed cannot be null or blank");
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getEmbeddingModel(),
                "input", text
        );

        String rawResponseBody;
        try {
            log.debug("Dispatching text embedding request with model: {}", properties.getEmbeddingModel());

            rawResponseBody = aiRestClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

        } catch (ResourceAccessException e) {
            log.error("AI embedding provider timeout or network failure: {}", e.getMessage());
            throw new AiServiceUnavailableException("AI embedding provider timed out or connection failed", e);
        } catch (RestClientResponseException e) {
            log.error("AI embedding provider returned HTTP error {}: {}", e.getStatusCode(), e.getStatusText());
            throw new AiServiceUnavailableException("AI embedding provider returned an error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Unexpected failure contacting AI embedding provider: {}", e.getMessage());
            throw new AiServiceUnavailableException("Failed to reach AI embedding provider: " + e.getMessage(), e);
        }

        return parseEmbeddingResponse(rawResponseBody);
    }

    private List<Double> parseEmbeddingResponse(String rawResponseBody) {
        if (rawResponseBody == null || rawResponseBody.isBlank()) {
            throw new AiServiceUnavailableException("AI embedding provider returned empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponseBody);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                throw new AiServiceUnavailableException("AI embedding provider response missing 'data' array");
            }

            JsonNode embeddingNode = dataArray.get(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new AiServiceUnavailableException("AI embedding provider returned empty embedding vector");
            }

            List<Double> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode val : embeddingNode) {
                vector.add(val.asDouble());
            }

            return vector;
        } catch (AiServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse embedding response JSON: {}", e.getMessage());
            throw new AiServiceUnavailableException("Malformed response from AI embedding provider", e);
        }
    }
}
