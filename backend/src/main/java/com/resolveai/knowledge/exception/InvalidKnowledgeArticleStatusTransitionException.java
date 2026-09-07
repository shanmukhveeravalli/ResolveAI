package com.resolveai.knowledge.exception;

import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import lombok.Getter;

/**
 * Thrown when an illegal lifecycle transition is requested on a Knowledge Article.
 */
@Getter
public class InvalidKnowledgeArticleStatusTransitionException extends RuntimeException {

    private final KnowledgeArticleStatus fromStatus;
    private final KnowledgeArticleStatus toStatus;

    public InvalidKnowledgeArticleStatusTransitionException(KnowledgeArticleStatus fromStatus, KnowledgeArticleStatus toStatus) {
        super(String.format("Invalid knowledge article status transition from '%s' to '%s'. This transition is not permitted by lifecycle rules.",
                fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidKnowledgeArticleStatusTransitionException(String message) {
        super(message);
        this.fromStatus = null;
        this.toStatus = null;
    }
}
