package com.resolveai.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.ai.config.AiConfigProperties;
import com.resolveai.ai.dto.IncidentAnalysisPrompt;
import com.resolveai.ai.dto.IncidentAnalysisResponse;
import com.resolveai.ai.exception.AiServiceUnavailableException;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.entity.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Concrete AI Provider communicating with OpenAI-compatible chat completion APIs
 * (e.g. OpenAI, Azure, Groq, Ollama, vLLM) via Spring RestClient.
 * Enforces strict failure isolation, timeouts, and structured output parsing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiCompatibleAiProvider implements AiProvider {

    private final RestClient aiRestClient;
    private final AiConfigProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public IncidentAnalysisResponse analyzeIncident(IncidentAnalysisPrompt prompt) {
        if (!properties.isEnabled()) {
            throw new AiServiceUnavailableException("AI subsystem is disabled (resolveai.ai.enabled=false)");
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiServiceUnavailableException("AI provider API key is not configured");
        }

        String systemPrompt = "You are an expert ITIL incident management assistant for ResolveAI. " +
                "Analyze the provided incident and respond with STRICT JSON ONLY. Do not include markdown formatting or backticks. " +
                "The JSON must have the following schema:\n" +
                "{\n" +
                "  \"suggestedCategory\": \"string\",\n" +
                "  \"suggestedPriority\": \"P1\" | \"P2\" | \"P3\" | \"P4\",\n" +
                "  \"suggestedSeverity\": \"CRITICAL\" | \"HIGH\" | \"MEDIUM\" | \"LOW\",\n" +
                "  \"summary\": \"concise 1-2 sentence executive summary\",\n" +
                "  \"analysis\": \"brief operational analysis and troubleshooting suggestions\"\n" +
                "}";

        String userPrompt = String.format(
                "Incident ID: %d\nIncident Number: %s\nTitle: %s\nDescription: %s\nCurrent Category: %s\nCurrent Priority: %s\nCurrent Severity: %s",
                prompt.getIncidentId(),
                prompt.getIncidentNumber() != null ? prompt.getIncidentNumber() : "N/A",
                prompt.getTitle(),
                prompt.getDescription(),
                prompt.getCurrentCategory() != null ? prompt.getCurrentCategory() : "Uncategorized",
                prompt.getCurrentPriority() != null ? prompt.getCurrentPriority() : "P3",
                prompt.getCurrentSeverity() != null ? prompt.getCurrentSeverity() : "MEDIUM"
        );

        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String rawResponseBody;
        try {
            log.info("Dispatching incident analysis for incident ID: {} using model: {}", prompt.getIncidentId(), properties.getModel());

            rawResponseBody = aiRestClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

        } catch (ResourceAccessException e) {
            log.error("AI provider timeout or network failure for incident ID {}: {}", prompt.getIncidentId(), e.getMessage());
            throw new AiServiceUnavailableException("AI provider timed out or connection failed", e);
        } catch (RestClientResponseException e) {
            log.error("AI provider returned HTTP error {}: {}", e.getStatusCode(), e.getStatusText());
            throw new AiServiceUnavailableException("AI provider returned an error: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Unexpected failure contacting AI provider: {}", e.getMessage());
            throw new AiServiceUnavailableException("Failed to reach AI provider: " + e.getMessage(), e);
        }

        return parseStructuredResponse(prompt.getIncidentId(), rawResponseBody);
    }

    private IncidentAnalysisResponse parseStructuredResponse(Long incidentId, String rawResponseBody) {
        if (rawResponseBody == null || rawResponseBody.isBlank()) {
            throw new AiServiceUnavailableException("AI provider returned an empty response body");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawResponseBody);
            JsonNode choices = rootNode.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiServiceUnavailableException("AI provider response does not contain choices");
            }

            String content = choices.get(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new AiServiceUnavailableException("AI provider returned empty message content");
            }

            // Strip potential markdown code fence markers if model generated them
            String sanitizedContent = content.trim();
            if (sanitizedContent.startsWith("```json")) {
                sanitizedContent = sanitizedContent.substring(7);
            } else if (sanitizedContent.startsWith("```")) {
                sanitizedContent = sanitizedContent.substring(3);
            }
            if (sanitizedContent.endsWith("```")) {
                sanitizedContent = sanitizedContent.substring(0, sanitizedContent.length() - 3);
            }
            sanitizedContent = sanitizedContent.trim();

            JsonNode payloadNode = objectMapper.readTree(sanitizedContent);

            String suggestedCategory = payloadNode.path("suggestedCategory").asText("Software Applications");
            String priorityStr = payloadNode.path("suggestedPriority").asText("P3").toUpperCase();
            String severityStr = payloadNode.path("suggestedSeverity").asText("MEDIUM").toUpperCase();
            String summary = payloadNode.path("summary").asText("");
            String analysis = payloadNode.path("analysis").asText("");

            Priority priority;
            try {
                priority = Priority.valueOf(priorityStr);
            } catch (IllegalArgumentException e) {
                log.error("Invalid priority returned by AI: '{}'", priorityStr);
                throw new AiServiceUnavailableException("AI provider returned invalid priority: " + priorityStr, e);
            }

            Severity severity;
            try {
                severity = Severity.valueOf(severityStr);
            } catch (IllegalArgumentException e) {
                log.error("Invalid severity returned by AI: '{}'", severityStr);
                throw new AiServiceUnavailableException("AI provider returned invalid severity: " + severityStr, e);
            }

            return IncidentAnalysisResponse.builder()
                    .incidentId(incidentId)
                    .suggestedCategory(suggestedCategory)
                    .suggestedPriority(priority)
                    .suggestedSeverity(severity)
                    .summary(summary)
                    .analysis(analysis)
                    .build();

        } catch (AiServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse structured JSON from AI provider response: {}", e.getMessage());
            throw new AiServiceUnavailableException("Malformed response received from AI provider", e);
        }
    }
}
