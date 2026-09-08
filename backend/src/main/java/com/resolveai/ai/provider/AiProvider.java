package com.resolveai.ai.provider;

import com.resolveai.ai.dto.IncidentAnalysisPrompt;
import com.resolveai.ai.dto.IncidentAnalysisResponse;

/**
 * Vendor-agnostic contract for AI capabilities.
 * Allows transparent swapping of LLM providers (e.g. OpenAI, Gemini, local mock/Ollama)
 * without modifying domain services or business logic.
 */
public interface AiProvider {

    /**
     * Performs structured incident classification, impact assessment, and operational summarization.
     *
     * @param prompt sanitized incident attributes
     * @return structured advisory suggestion
     */
    IncidentAnalysisResponse analyzeIncident(IncidentAnalysisPrompt prompt);
}
