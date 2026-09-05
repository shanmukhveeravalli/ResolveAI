package com.resolveai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application context loading test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResolveAiApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context initializes cleanly
    }
}
