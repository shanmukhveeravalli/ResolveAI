package com.resolveai.ai.provider;

import java.util.List;

/**
 * Vendor-agnostic abstraction for vector embedding generation.
 * Decouples the RAG retrieval pipeline from specific embedding providers.
 */
public interface EmbeddingProvider {

    /**
     * Generates a dense vector embedding for the supplied text.
     *
     * @param text input text to embed
     * @return dense vector embedding as a list of floating-point values
     */
    List<Double> generateEmbedding(String text);
}
