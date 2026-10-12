package com.resolveai.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare rate limiting boundaries on Spring MVC endpoints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of permitted requests within the window.
     */
    int limit() default 30;

    /**
     * Sliding / fixed window duration in seconds.
     */
    int periodSeconds() default 60;

    /**
     * Logical key prefix for distinguishing rate limiting buckets.
     */
    String keyPrefix() default "api";
}
