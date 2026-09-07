package com.resolveai.knowledge.service;

import com.resolveai.auth.security.AuthorizationService;
import com.resolveai.auth.security.RoleConstants;
import com.resolveai.common.dto.PageResponse;
import com.resolveai.common.exception.ResourceNotFoundException;
import com.resolveai.incident.entity.Category;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.knowledge.dto.KnowledgeArticleCreateRequest;
import com.resolveai.knowledge.dto.KnowledgeArticleResponse;
import com.resolveai.knowledge.dto.KnowledgeArticleUpdateRequest;
import com.resolveai.knowledge.entity.KnowledgeArticle;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.exception.InvalidKnowledgeArticleStatusTransitionException;
import com.resolveai.knowledge.repository.KnowledgeArticleRepository;
import com.resolveai.user.entity.User;
import com.resolveai.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing Knowledge Base articles, publication lifecycle state machine,
 * database-backed search, and role-based visibility.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    /**
     * Creates a new knowledge base article with default status DRAFT.
     * Authenticated user becomes the author.
     */
    @Transactional
    public KnowledgeArticleResponse createArticle(KnowledgeArticleCreateRequest request, String currentUserEmail) {
        if (!authorizationService.canCreateKnowledgeArticle()) {
            throw new AccessDeniedException("Access denied: only ENGINEER, MANAGER, or ADMIN roles can create knowledge articles");
        }

        User author = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserEmail));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        }

        String title = request.getTitle().trim();
        String slug = generateUniqueSlug(title);

        String content = request.getContent() != null ? request.getContent().trim() : null;
        String problem = request.getProblem() != null ? request.getProblem().trim() : (content != null ? content : title);
        String symptoms = request.getSymptoms() != null ? request.getSymptoms().trim() : "See description";
        String rootCause = request.getRootCause() != null ? request.getRootCause().trim() : null;
        String resolution = request.getResolution() != null ? request.getResolution().trim() : (content != null ? content : title);
        if (content == null) {
            content = resolution;
        }

        KnowledgeArticleStatus status = request.getStatus() != null ? request.getStatus() : KnowledgeArticleStatus.DRAFT;
        Instant publishedAt = (status == KnowledgeArticleStatus.PUBLISHED) ? Instant.now() : null;

        KnowledgeArticle article = KnowledgeArticle.builder()
                .title(title)
                .slug(slug)
                .content(content)
                .problem(problem)
                .symptoms(symptoms)
                .rootCause(rootCause)
                .resolution(resolution)
                .category(category)
                .author(author)
                .status(status)
                .tags(request.getTags() != null ? request.getTags().trim() : null)
                .publishedAt(publishedAt)
                .build();

        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        log.info("Knowledge article created with ID {} and slug '{}' by {}", saved.getId(), saved.getSlug(), currentUserEmail);

        return KnowledgeArticleResponse.fromEntity(saved);
    }

    /**
     * Updates an existing knowledge article.
     */
    @Transactional
    public KnowledgeArticleResponse updateArticle(Long id, KnowledgeArticleUpdateRequest request, String currentUserEmail) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", id));

        if (!authorizationService.canManageKnowledgeArticle(article)) {
            throw new AccessDeniedException("Access denied: you do not have permission to modify this article");
        }

        // Validate lifecycle transition if status change is requested
        if (request.getStatus() != null && request.getStatus() != article.getStatus()) {
            validateAndApplyTransition(article, request.getStatus());
        }

        if (request.getTitle() != null && !request.getTitle().isBlank() && !request.getTitle().equals(article.getTitle())) {
            article.setTitle(request.getTitle().trim());
            article.setSlug(generateUniqueSlug(request.getTitle().trim()));
        }

        if (request.getContent() != null) {
            article.setContent(request.getContent().trim());
        }

        if (request.getProblem() != null) {
            article.setProblem(request.getProblem().trim());
        }

        if (request.getSymptoms() != null) {
            article.setSymptoms(request.getSymptoms().trim());
        }

        if (request.getRootCause() != null) {
            article.setRootCause(request.getRootCause().trim());
        }

        if (request.getResolution() != null) {
            article.setResolution(request.getResolution().trim());
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            article.setCategory(category);
        }

        if (request.getTags() != null) {
            article.setTags(request.getTags().trim());
        }

        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        log.info("Knowledge article ID {} updated by {}", saved.getId(), currentUserEmail);

        return KnowledgeArticleResponse.fromEntity(saved);
    }

    /**
     * Transitions an article to PUBLISHED and records publishedAt timestamp.
     * Allowed transition: DRAFT -> PUBLISHED.
     */
    @Transactional
    public KnowledgeArticleResponse publishArticle(Long id, String currentUserEmail) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", id));

        if (!authorizationService.canManageKnowledgeArticle(article)) {
            throw new AccessDeniedException("Access denied: you do not have permission to publish this article");
        }

        if (article.getStatus() != KnowledgeArticleStatus.DRAFT) {
            throw new InvalidKnowledgeArticleStatusTransitionException(article.getStatus(), KnowledgeArticleStatus.PUBLISHED);
        }

        article.setStatus(KnowledgeArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.now());

        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        log.info("Knowledge article ID {} published by {}", saved.getId(), currentUserEmail);

        return KnowledgeArticleResponse.fromEntity(saved);
    }

    /**
     * Transitions an article to ARCHIVED.
     * Allowed transitions: DRAFT -> ARCHIVED, PUBLISHED -> ARCHIVED.
     */
    @Transactional
    public KnowledgeArticleResponse archiveArticle(Long id, String currentUserEmail) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", id));

        if (!authorizationService.canManageKnowledgeArticle(article)) {
            throw new AccessDeniedException("Access denied: you do not have permission to archive this article");
        }

        if (article.getStatus() == KnowledgeArticleStatus.ARCHIVED) {
            throw new InvalidKnowledgeArticleStatusTransitionException(article.getStatus(), KnowledgeArticleStatus.ARCHIVED);
        }

        article.setStatus(KnowledgeArticleStatus.ARCHIVED);

        KnowledgeArticle saved = knowledgeArticleRepository.save(article);
        log.info("Knowledge article ID {} archived by {}", saved.getId(), currentUserEmail);

        return KnowledgeArticleResponse.fromEntity(saved);
    }

    /**
     * Retrieves a single knowledge article by ID subject to visibility rules.
     */
    @Transactional(readOnly = true)
    public KnowledgeArticleResponse getArticleById(Long id) {
        KnowledgeArticle article = knowledgeArticleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", id));

        if (!authorizationService.canViewKnowledgeArticle(article)) {
            throw new AccessDeniedException("Access denied: you do not have permission to view this article");
        }

        return KnowledgeArticleResponse.fromEntity(article);
    }

    /**
     * Paginated database-backed search and filtering for knowledge base articles.
     */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeArticleResponse> getArticles(String search,
                                                              Long categoryId,
                                                              KnowledgeArticleStatus status,
                                                              Pageable pageable) {
        User currentUser = authorizationService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Full authentication required"));

        Specification<KnowledgeArticle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Role-based visibility scoping
            if (authorizationService.isAdmin() || authorizationService.hasRole(RoleConstants.MANAGER)) {
                // Full management access: filter by requested status if specified
                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            } else if (authorizationService.hasRole(RoleConstants.ENGINEER)) {
                // Engineers see PUBLISHED or own articles
                if (status != null) {
                    if (status == KnowledgeArticleStatus.PUBLISHED) {
                        predicates.add(cb.equal(root.get("status"), KnowledgeArticleStatus.PUBLISHED));
                    } else {
                        predicates.add(cb.and(
                                cb.equal(root.get("status"), status),
                                cb.equal(root.get("author").get("id"), currentUser.getId())
                        ));
                    }
                } else {
                    predicates.add(cb.or(
                            cb.equal(root.get("status"), KnowledgeArticleStatus.PUBLISHED),
                            cb.equal(root.get("author").get("id"), currentUser.getId())
                    ));
                }
            } else {
                // EMPLOYEE: strictly PUBLISHED articles
                if (status != null && status != KnowledgeArticleStatus.PUBLISHED) {
                    throw new AccessDeniedException("Access denied: only published articles are viewable");
                }
                predicates.add(cb.equal(root.get("status"), KnowledgeArticleStatus.PUBLISHED));
            }

            // 2. Category filtering
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // 3. Database-backed case-insensitive search
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                List<Predicate> searchPredicates = new ArrayList<>();

                searchPredicates.add(cb.like(cb.lower(root.get("title")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("content")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("problem")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("symptoms")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("resolution")), pattern));
                searchPredicates.add(cb.like(cb.lower(root.get("tags")), pattern));

                Join<KnowledgeArticle, Category> categoryJoin = root.join("category", JoinType.LEFT);
                searchPredicates.add(cb.like(cb.lower(categoryJoin.get("name")), pattern));

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable effectivePageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Page<KnowledgeArticle> page = knowledgeArticleRepository.findAll(spec, effectivePageable);
        return PageResponse.of(page, KnowledgeArticleResponse::fromEntity);
    }

    private void validateAndApplyTransition(KnowledgeArticle article, KnowledgeArticleStatus targetStatus) {
        KnowledgeArticleStatus currentStatus = article.getStatus();

        if (currentStatus == KnowledgeArticleStatus.DRAFT) {
            if (targetStatus == KnowledgeArticleStatus.PUBLISHED) {
                article.setStatus(KnowledgeArticleStatus.PUBLISHED);
                article.setPublishedAt(Instant.now());
                return;
            } else if (targetStatus == KnowledgeArticleStatus.ARCHIVED) {
                article.setStatus(KnowledgeArticleStatus.ARCHIVED);
                return;
            }
        } else if (currentStatus == KnowledgeArticleStatus.PUBLISHED) {
            if (targetStatus == KnowledgeArticleStatus.ARCHIVED) {
                article.setStatus(KnowledgeArticleStatus.ARCHIVED);
                return;
            }
        }

        throw new InvalidKnowledgeArticleStatusTransitionException(currentStatus, targetStatus);
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = title.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");

        if (baseSlug.isBlank()) {
            baseSlug = "article";
        }
        if (baseSlug.length() > 250) {
            baseSlug = baseSlug.substring(0, 250);
        }

        String slug = baseSlug;
        int counter = 1;
        while (knowledgeArticleRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }
}
