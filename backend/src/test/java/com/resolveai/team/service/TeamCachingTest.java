package com.resolveai.team.service;

import com.resolveai.common.config.CacheConfig;
import com.resolveai.notification.service.NotificationService;
import com.resolveai.team.dto.TeamResponse;
import com.resolveai.team.dto.TeamUpdateRequest;
import com.resolveai.team.entity.Team;
import com.resolveai.team.repository.TeamMemberRepository;
import com.resolveai.team.repository.TeamRepository;
import com.resolveai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class TeamCachingTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private TeamRepository teamRepository;

    @MockBean
    private TeamMemberRepository teamMemberRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private NotificationService notificationService;

    private Team testTeam;

    @BeforeEach
    void setUp() {
        Cache teamCache = cacheManager.getCache(CacheConfig.CACHE_TEAMS);
        if (teamCache != null) {
            teamCache.clear();
        }
        Cache memberCache = cacheManager.getCache(CacheConfig.CACHE_TEAM_MEMBERS);
        if (memberCache != null) {
            memberCache.clear();
        }

        testTeam = Team.builder()
                .id(1L)
                .name("DevOps")
                .description("DevOps and Platform Engineering")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getTeamById caches response and avoids redundant repository calls")
    void getTeamByIdCaching() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(teamMemberRepository.findByTeamId(1L)).thenReturn(Collections.emptyList());

        TeamResponse resp1 = teamService.getTeamById(1L);
        assertThat(resp1.getName()).isEqualTo("DevOps");
        verify(teamRepository, times(1)).findById(1L);

        // Second call should come from cache
        TeamResponse resp2 = teamService.getTeamById(1L);
        assertThat(resp2.getName()).isEqualTo("DevOps");
        verify(teamRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getTeams caches list and avoids second repository call")
    void getTeamsCaching() {
        when(teamRepository.findByIsActiveTrue()).thenReturn(List.of(testTeam));
        when(teamMemberRepository.findByTeamId(1L)).thenReturn(Collections.emptyList());

        List<TeamResponse> list1 = teamService.getTeams(true);
        assertThat(list1).hasSize(1);
        verify(teamRepository, times(1)).findByIsActiveTrue();

        // Second call hits cache
        List<TeamResponse> list2 = teamService.getTeams(true);
        assertThat(list2).hasSize(1);
        verify(teamRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    @DisplayName("updateTeam evicts teams cache")
    void updateTeamEvictsCache() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(teamMemberRepository.findByTeamId(1L)).thenReturn(Collections.emptyList());
        when(teamRepository.save(any(Team.class))).thenReturn(testTeam);

        // Warm cache
        teamService.getTeamById(1L);
        Cache teamCache = cacheManager.getCache(CacheConfig.CACHE_TEAMS);
        assertThat(teamCache.get("team_1")).isNotNull();

        // Update team -> should evict
        TeamUpdateRequest updateReq = new TeamUpdateRequest();
        updateReq.setDescription("Updated DevOps description");
        teamService.updateTeam(1L, updateReq);

        assertThat(teamCache.get("team_1")).isNull();
    }
}