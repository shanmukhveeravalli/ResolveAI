package com.resolveai;

import com.resolveai.auth.entity.Role;
import com.resolveai.auth.repository.RoleRepository;
import com.resolveai.incident.entity.Priority;
import com.resolveai.incident.repository.CategoryRepository;
import com.resolveai.sla.entity.SlaPolicy;
import com.resolveai.sla.repository.SlaPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("Verify Flyway migration seeds initial roles")
    void flywaySeedsInitialRoles() {
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).isNotEmpty();
        assertThat(roleRepository.existsByName("EMPLOYEE")).isTrue();
        assertThat(roleRepository.existsByName("ENGINEER")).isTrue();
        assertThat(roleRepository.existsByName("MANAGER")).isTrue();
        assertThat(roleRepository.existsByName("ADMIN")).isTrue();
    }

    @Test
    @DisplayName("Verify Flyway migration seeds initial SLA policies")
    void flywaySeedsInitialSlaPolicies() {
        List<SlaPolicy> policies = slaPolicyRepository.findAll();
        assertThat(policies).hasSize(4);

        Optional<SlaPolicy> p1 = slaPolicyRepository.findByPriority(Priority.P1);
        assertThat(p1).isPresent();
        assertThat(p1.get().getResponseTimeMinutes()).isEqualTo(15);
        assertThat(p1.get().getResolutionTimeMinutes()).isEqualTo(120);

        Optional<SlaPolicy> p2 = slaPolicyRepository.findByPriority(Priority.P2);
        assertThat(p2).isPresent();
        assertThat(p2.get().getResponseTimeMinutes()).isEqualTo(30);

        Optional<SlaPolicy> p3 = slaPolicyRepository.findByPriority(Priority.P3);
        assertThat(p3).isPresent();
        assertThat(p3.get().getResponseTimeMinutes()).isEqualTo(120);

        Optional<SlaPolicy> p4 = slaPolicyRepository.findByPriority(Priority.P4);
        assertThat(p4).isPresent();
        assertThat(p4.get().getResponseTimeMinutes()).isEqualTo(240);
    }

    @Test
    @DisplayName("Verify Flyway migration seeds initial ITIL categories")
    void flywaySeedsInitialCategories() {
        assertThat(categoryRepository.existsBySlug("network-connectivity")).isTrue();
        assertThat(categoryRepository.existsBySlug("hardware-peripherals")).isTrue();
        assertThat(categoryRepository.existsBySlug("software-applications")).isTrue();
        assertThat(categoryRepository.existsBySlug("identity-access")).isTrue();
        assertThat(categoryRepository.existsBySlug("security-incidents")).isTrue();
    }
}
