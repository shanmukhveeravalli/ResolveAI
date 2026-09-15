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

    /**
     * Performs evidence-grounded incident diagnosis and resolution suggestions using retrieved context.
     *
     * @param prompt sanitized incident attributes accompanied by retrieved grounding sources
     * @return grounded advisory suggestions citing specific source references
     */
    com.resolveai.ai.rag.dto.RagAnalysisResponse generateRagAnalysis(com.resolveai.ai.rag.dto.RagAnalysisPrompt prompt);
}
