package com.resolveai.knowledge.controller;

import com.resolveai.common.dto.ApiResponse;
import com.resolveai.common.dto.PageResponse;
import com.resolveai.knowledge.dto.KnowledgeArticleCreateRequest;
import com.resolveai.knowledge.dto.KnowledgeArticleResponse;
import com.resolveai.knowledge.dto.KnowledgeArticleUpdateRequest;
import com.resolveai.knowledge.entity.KnowledgeArticleStatus;
import com.resolveai.knowledge.service.KnowledgeArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller providing Knowledge Base article operations:
 * browsing, search, authoring, editing, publishing, and archiving.
 */
@RestController
@RequestMapping({"/api/knowledge/articles", "/api/knowledge"})
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "ITIL Knowledge Base article management, search, and lifecycle transitions")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeArticleController {

    private final KnowledgeArticleService knowledgeArticleService;

    @PostMapping
    @Operation(summary = "Create knowledge article", description = "Creates a new article in DRAFT status. Author is set to the authenticated user.")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> createArticle(
            @Valid @RequestBody KnowledgeArticleCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        KnowledgeArticleResponse response = knowledgeArticleService.createArticle(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Knowledge article created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List and search knowledge articles", description = "Paginated listing and database-backed search filtered by role, category, and status.")
    public ResponseEntity<ApiResponse<PageResponse<KnowledgeArticleResponse>>> getArticles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) KnowledgeArticleStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<KnowledgeArticleResponse> response = knowledgeArticleService.getArticles(search, categoryId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get knowledge article by ID", description = "Retrieves an article by ID subject to role-based visibility rules.")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> getArticleById(@PathVariable Long id) {
        KnowledgeArticleResponse response = knowledgeArticleService.getArticleById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update knowledge article", description = "Updates title, content, taxonomy, or status of an existing article.")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> updateArticle(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeArticleUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        KnowledgeArticleResponse response = knowledgeArticleService.updateArticle(id, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Knowledge article updated successfully", response));
    }

    @PatchMapping("/{id}/publish")
    @Operation(summary = "Publish knowledge article", description = "Transitions an article from DRAFT to PUBLISHED and sets publication timestamp.")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> publishArticle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        KnowledgeArticleResponse response = knowledgeArticleService.publishArticle(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Knowledge article published successfully", response));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive knowledge article", description = "Transitions an article to ARCHIVED.")
    public ResponseEntity<ApiResponse<KnowledgeArticleResponse>> archiveArticle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        KnowledgeArticleResponse response = knowledgeArticleService.archiveArticle(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Knowledge article archived successfully", response));
    }
}
