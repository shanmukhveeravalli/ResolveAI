package com.resolveai.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Foundation Health Check Controller.
 * Verifies that the modular monolith backend is operational and accessible.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "System liveness and operational readiness checks")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check backend service health", description = "Returns service status, version, and server timestamp")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "ResolveAI Backend");
        health.put("version", "0.1.0-SNAPSHOT");
        health.put("environment", "development");
        health.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(health);
    }
}
