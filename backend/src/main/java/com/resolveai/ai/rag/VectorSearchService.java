package com.resolveai.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveai.ai.rag.dto.RagSourceItem;
import com.resolveai.ai.rag.dto.RagSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service managing persistence and similarity search for vector embeddings.
 * Supports native pgvector on PostgreSQL (HNSW index, <=> operator) with
 * seamless in-memory cosine similarity fallback for H2 in-memory testing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private Boolean isPostgres;

    private boolean isPostgreSql() {
        if (isPostgres == null) {
            try (Connection conn = dataSource.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                isPostgres = productName != null && productName.toLowerCase().contains("postgres");
            } catch (SQLException e) {
                log.warn("Could not determine database product name, defaulting to fallback: {}", e.getMessage());
                isPostgres = false;
            }
        }
        return isPostgres;
    }

    /**
     * Persists or updates the dense vector embedding for a knowledge article.
     */
    @Transactional
    public void storeArticleEmbedding(Long articleId, List<Double> embedding, String model) {
        if (articleId == null || embedding == null || embedding.isEmpty()) {
            return;
        }

        String vectorString = formatVector(embedding);
        if (isPostgreSql()) {
            String sql = """
                INSERT INTO article_embeddings (article_id, embedding, model, created_at, updated_at)
                VALUES (?, CAST(? AS vector), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (article_id) DO UPDATE 
                SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, updated_at = CURRENT_TIMESTAMP
            """;
            jdbcTemplate.update(sql, articleId, vectorString, model);
        } else {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM article_embeddings WHERE article_id = ?", Integer.class, articleId);
            if (count != null && count > 0) {
                jdbcTemplate.update(
                        "UPDATE article_embeddings SET embedding = ?, model = ?, updated_at = CURRENT_TIMESTAMP WHERE article_id = ?",
                        vectorString, model, articleId);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO article_embeddings (article_id, embedding, model, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        articleId, vectorString, model);
            }
        }
        log.debug("Stored embedding for article ID: {}", articleId);
    }

    /**
     * Persists or updates the dense vector embedding for an incident.
     */
    @Transactional
    public void storeIncidentEmbedding(Long incidentId, List<Double> embedding, String model) {
        if (incidentId == null || embedding == null || embedding.isEmpty()) {
            return;
        }

        String vectorString = formatVector(embedding);
        if (isPostgreSql()) {
            String sql = """
                INSERT INTO incident_embeddings (incident_id, embedding, model, created_at, updated_at)
                VALUES (?, CAST(? AS vector), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (incident_id) DO UPDATE 
                SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, updated_at = CURRENT_TIMESTAMP
            """;
            jdbcTemplate.update(sql, incidentId, vectorString, model);
        } else {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incident_embeddings WHERE incident_id = ?", Integer.class, incidentId);
            if (count != null && count > 0) {
                jdbcTemplate.update(
                        "UPDATE incident_embeddings SET embedding = ?, model = ?, updated_at = CURRENT_TIMESTAMP WHERE incident_id = ?",
                        vectorString, model, incidentId);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO incident_embeddings (incident_id, embedding, model, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        incidentId, vectorString, model);
            }
        }
        log.debug("Stored embedding for incident ID: {}", incidentId);
    }

    /**
     * Retrieves the top-K most similar Knowledge Articles using vector cosine distance.
     */
    @Transactional(readOnly = true)
    public List<RagSourceItem> searchSimilarArticles(List<Double> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }

        if (isPostgreSql()) {
            String vectorStr = formatVector(queryVector);
            String sql = """
                SELECT a.id, a.title, a.slug, a.problem, a.resolution, a.content,
                       (1 - (ae.embedding <=> CAST(? AS vector))) AS similarity
                FROM article_embeddings ae
                JOIN knowledge_articles a ON ae.article_id = a.id
                ORDER BY ae.embedding <=> CAST(? AS vector)
                LIMIT ?
            """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Long id = rs.getLong("id");
                String title = rs.getString("title");
                String slug = rs.getString("slug");
                String problem = rs.getString("problem");
                String resolution = rs.getString("resolution");
                double similarity = rs.getDouble("similarity");

                String contentSummary = (problem != null ? problem : "") + " " + (resolution != null ? resolution : "");
                return RagSourceItem.builder()
                        .sourceType(RagSourceType.KNOWLEDGE_ARTICLE)
                        .sourceId(id)
                        .title(title)
                        .reference(slug)
                        .content(contentSummary.trim())
                        .similarity(roundDouble(similarity))
                        .build();
            }, vectorStr, vectorStr, topK);
        } else {
            return searchSimilarArticlesInMemory(queryVector, topK);
        }
    }

    /**
     * Retrieves the top-K most similar resolved/closed Historical Incidents using vector cosine distance.
     */
    @Transactional(readOnly = true)
    public List<RagSourceItem> searchSimilarIncidents(List<Double> queryVector, int topK, Long excludeIncidentId) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }

        if (isPostgreSql()) {
            String vectorStr = formatVector(queryVector);
            String sql = """
                SELECT i.id, i.incident_number, i.title, i.description,
                       (1 - (ie.embedding <=> CAST(? AS vector))) AS similarity
                FROM incident_embeddings ie
                JOIN incidents i ON ie.incident_id = i.id
                WHERE i.status IN ('RESOLVED', 'CLOSED')
                  AND (? IS NULL OR i.id <> ?)
                ORDER BY ie.embedding <=> CAST(? AS vector)
                LIMIT ?
            """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Long id = rs.getLong("id");
                String incidentNumber = rs.getString("incident_number");
                String title = rs.getString("title");
                String description = rs.getString("description");
                double similarity = rs.getDouble("similarity");

                return RagSourceItem.builder()
                        .sourceType(RagSourceType.HISTORICAL_INCIDENT)
                        .sourceId(id)
                        .title(title)
                        .reference(incidentNumber)
                        .content(description != null ? description.trim() : "")
                        .similarity(roundDouble(similarity))
                        .build();
            }, vectorStr, excludeIncidentId, excludeIncidentId, vectorStr, topK);
        } else {
            return searchSimilarIncidentsInMemory(queryVector, topK, excludeIncidentId);
        }
    }

    private List<RagSourceItem> searchSimilarArticlesInMemory(List<Double> queryVector, int topK) {
        String sql = """
            SELECT a.id, a.title, a.slug, a.problem, a.resolution, ae.embedding
            FROM article_embeddings ae
            JOIN knowledge_articles a ON ae.article_id = a.id
        """;

        List<RagSourceItem> candidates = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Long id = rs.getLong("id");
            String title = rs.getString("title");
            String slug = rs.getString("slug");
            String problem = rs.getString("problem");
            String resolution = rs.getString("resolution");
            String embeddingJson = rs.getString("embedding");

            List<Double> storedVector = parseVectorString(embeddingJson);
            if (storedVector != null && storedVector.size() == queryVector.size()) {
                double similarity = computeCosineSimilarity(queryVector, storedVector);
                String contentSummary = (problem != null ? problem : "") + " " + (resolution != null ? resolution : "");
                candidates.add(RagSourceItem.builder()
                        .sourceType(RagSourceType.KNOWLEDGE_ARTICLE)
                        .sourceId(id)
                        .title(title)
                        .reference(slug)
                        .content(contentSummary.trim())
                        .similarity(roundDouble(similarity))
                        .build());
            }
        });

        candidates.sort(Comparator.comparingDouble(RagSourceItem::getSimilarity).reversed());
        return candidates.stream().limit(topK).toList();
    }

    private List<RagSourceItem> searchSimilarIncidentsInMemory(List<Double> queryVector, int topK, Long excludeIncidentId) {
        String sql = """
            SELECT i.id, i.incident_number, i.title, i.description, ie.embedding
            FROM incident_embeddings ie
            JOIN incidents i ON ie.incident_id = i.id
            WHERE i.status IN ('RESOLVED', 'CLOSED')
        """;

        List<RagSourceItem> candidates = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Long id = rs.getLong("id");
            if (excludeIncidentId != null && excludeIncidentId.equals(id)) {
                return;
            }

            String incidentNumber = rs.getString("incident_number");
            String title = rs.getString("title");
            String description = rs.getString("description");
            String embeddingJson = rs.getString("embedding");

            List<Double> storedVector = parseVectorString(embeddingJson);
            if (storedVector != null && storedVector.size() == queryVector.size()) {
                double similarity = computeCosineSimilarity(queryVector, storedVector);
                candidates.add(RagSourceItem.builder()
                        .sourceType(RagSourceType.HISTORICAL_INCIDENT)
                        .sourceId(id)
                        .title(title)
                        .reference(incidentNumber)
                        .content(description != null ? description.trim() : "")
                        .similarity(roundDouble(similarity))
                        .build());
            }
        });

        candidates.sort(Comparator.comparingDouble(RagSourceItem::getSimilarity).reversed());
        return candidates.stream().limit(topK).toList();
    }

    public static double computeCosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty() || v1.size() != v2.size()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String formatVector(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private List<Double> parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(vectorStr, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private double roundDouble(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }
}
