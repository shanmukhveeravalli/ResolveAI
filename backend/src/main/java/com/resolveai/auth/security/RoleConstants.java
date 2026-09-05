package com.resolveai.auth.security;

/**
 * Centralized constant definitions for system roles and Spring Security authority strings.
 */
public final class RoleConstants {

    private RoleConstants() {
        // Utility class
    }

    // Role Names (persisted in database and used in User/Role entities)
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String ENGINEER = "ENGINEER";
    public static final String MANAGER = "MANAGER";
    public static final String ADMIN = "ADMIN";

    // Spring Security GrantedAuthority Strings (ROLE_ prefix convention)
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_EMPLOYEE = ROLE_PREFIX + EMPLOYEE;
    public static final String ROLE_ENGINEER = ROLE_PREFIX + ENGINEER;
    public static final String ROLE_MANAGER = ROLE_PREFIX + MANAGER;
    public static final String ROLE_ADMIN = ROLE_PREFIX + ADMIN;
}
