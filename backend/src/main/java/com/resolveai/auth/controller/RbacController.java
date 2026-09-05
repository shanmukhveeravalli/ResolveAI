package com.resolveai.auth.controller;

import com.resolveai.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller exposing demonstration endpoints to verify Role-Based Access Control (RBAC).
 */
@RestController
@RequestMapping("/api/rbac")
@Tag(name = "RBAC Demonstration", description = "Role-based access control verification endpoints")
public class RbacController {

    @GetMapping("/employee")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(
        summary = "Employee resource endpoint",
        description = "Accessible by EMPLOYEE and ADMIN roles. Returns 403 Forbidden for ENGINEER and MANAGER roles.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Employee resource accessed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions for EMPLOYEE resource")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmployeeResource() {
        return ResponseEntity.ok(ApiResponse.success("Employee resource accessed successfully", Map.of(
                "resource", "employee",
                "accessGranted", true
        )));
    }

    @GetMapping("/engineer")
    @PreAuthorize("hasAnyRole('ENGINEER', 'ADMIN')")
    @Operation(
        summary = "Engineer resource endpoint",
        description = "Accessible by ENGINEER and ADMIN roles. Returns 403 Forbidden for EMPLOYEE and MANAGER roles.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Engineer resource accessed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions for ENGINEER resource")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEngineerResource() {
        return ResponseEntity.ok(ApiResponse.success("Engineer resource accessed successfully", Map.of(
                "resource", "engineer",
                "accessGranted", true
        )));
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(
        summary = "Manager resource endpoint",
        description = "Accessible by MANAGER and ADMIN roles. Returns 403 Forbidden for EMPLOYEE and ENGINEER roles.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager resource accessed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions for MANAGER resource")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getManagerResource() {
        return ResponseEntity.ok(ApiResponse.success("Manager resource accessed successfully", Map.of(
                "resource", "manager",
                "accessGranted", true
        )));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Admin resource endpoint",
        description = "Accessible exclusively by ADMIN role. Returns 403 Forbidden for EMPLOYEE, ENGINEER, and MANAGER roles.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Admin resource accessed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions for ADMIN resource")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminResource() {
        return ResponseEntity.ok(ApiResponse.success("Admin resource accessed successfully", Map.of(
                "resource", "admin",
                "accessGranted", true
        )));
    }
}
