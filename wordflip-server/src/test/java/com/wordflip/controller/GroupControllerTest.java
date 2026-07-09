package com.wordflip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.domain.GroupSource;
import com.wordflip.domain.GroupStatus;
import com.wordflip.dto.group.CreateCustomGroupRequest;
import com.wordflip.dto.group.GroupDetail;
import com.wordflip.dto.group.GroupListResponse;
import com.wordflip.dto.group.GroupStats;
import com.wordflip.dto.group.GroupWordsResponse;
import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.dto.word.UnassignedWordsResponse;
import com.wordflip.dto.word.WordProgressSnapshot;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.security.UserPrincipal;
import com.wordflip.service.GroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Groups / Words 端点 MockMvc 冒烟测试（P0-B31~35）。
 */
@WebMvcTest(controllers = GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private com.wordflip.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listGroups_returns200() throws Exception {
        authenticate(1L);
        when(groupService.listGroups(1L, null, "createdAt"))
                .thenReturn(new GroupListResponse(List.of(sampleDetail())));

        mockMvc.perform(get("/api/v1/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].name").value("第1组"));
    }

    @Test
    void getGroup_returns200() throws Exception {
        authenticate(1L);
        when(groupService.getGroup(1L, 10L)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/groups/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void listGroupWords_returns200() throws Exception {
        authenticate(1L);
        when(groupService.listGroupWords(1L, 10L, 0, 20))
                .thenReturn(new GroupWordsResponse(
                        0,
                        20,
                        1,
                        1,
                        List.of(GroupWordsResponse.GroupWordItem.from(
                                new WordSummary("apple", "apple", "苹果", null, null),
                                WordProgressSnapshot.empty(HeatDisplayMode.combined)
                        ))
                ));

        mockMvc.perform(get("/api/v1/groups/10/words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.words[0].wordKey").value("apple"));
    }

    @Test
    void listUnassignedWords_returns200() throws Exception {
        authenticate(1L);
        when(groupService.listUnassignedWords(1L, false, null, 0, 20))
                .thenReturn(new UnassignedWordsResponse(
                        0,
                        20,
                        1,
                        1,
                        List.of(new WordSummary("apple", "apple", "苹果", null, null))
                ));

        mockMvc.perform(get("/api/v1/words/unassigned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.words[0].en").value("apple"));
    }

    @Test
    void createCustomGroup_returns201() throws Exception {
        authenticate(1L);
        CreateCustomGroupRequest request = new CreateCustomGroupRequest();
        request.setWordKeys(List.of("apple"));
        request.setName("我的组");

        when(groupService.createCustomGroup(eq(1L), any())).thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/groups/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("auto"));

        verify(groupService).createCustomGroup(eq(1L), any());
    }

    private static void authenticate(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of())
        );
    }

    private static GroupDetail sampleDetail() {
        return new GroupDetail(
                10L,
                "第1组",
                GroupSource.auto,
                GroupStatus.not_started,
                Instant.parse("2026-07-08T00:00:00Z"),
                new GroupStats(5, 0, 0, 0, 0, 5),
                0f
        );
    }
}
