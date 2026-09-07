package com.resolveai.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeAnalyticsResponse {
    private long totalArticles;
    private long publishedArticles;
    private long draftArticles;
    private long archivedArticles;
}
