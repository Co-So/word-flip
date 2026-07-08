package com.wordflip.controller;

import com.wordflip.dto.study.StudyGroupPayload;
import com.wordflip.dto.study.StudySessionReportResponse;
import com.wordflip.dto.today.TodayDashboard;
import com.wordflip.dto.today.TodayStats;
import com.wordflip.dto.today.TodayTask;
import com.wordflip.dto.today.TodayTasks;
import com.wordflip.security.UserPrincipal;
import com.wordflip.service.StudyService;
import com.wordflip.service.TodayService;
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

import java.time.LocalDate;
import java.time.ZoneId;
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
 * Today / Study 端点 MockMvc 冒烟（P1-B03~B12）。
 */
@WebMvcTest(controllers = {TodayController.class, StudyController.class})
@AutoConfigureMockMvc(addFilters = false)
class TodayStudyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodayService todayService;

    @MockBean
    private StudyService studyService;

    @MockBean
    private com.wordflip.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getToday_returns200() throws Exception {
        authenticate(1L);
        LocalDate today = LocalDate.of(2026, 7, 8);
        when(todayService.getDashboard(eq(1L), any(ZoneId.class)))
                .thenReturn(new TodayDashboard(
                        today,
                        3,
                        new TodayStats(0, 0, 0),
                        new TodayTasks(
                                TodayTask.of(10, "新词学习"),
                                TodayTask.of(0, "到期复习"),
                                TodayTask.of(0, "默写测验")
                        ),
                        null
                ));

        mockMvc.perform(get("/api/v1/today").header("X-Timezone", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakDays").value(3))
                .andExpect(jsonPath("$.tasks.newWords.count").value(10));
    }

    @Test
    void getStudyGroup_returns200() throws Exception {
        authenticate(1L);
        when(studyService.getStudyGroup(1L, 5L))
                .thenReturn(new StudyGroupPayload(
                        new StudyGroupPayload.StudyGroupInfo(5L, "第1组", com.wordflip.domain.GroupSource.auto),
                        List.of()
                ));

        mockMvc.perform(get("/api/v1/study/groups/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.name").value("第1组"));
    }

    @Test
    void reportSession_returns201() throws Exception {
        authenticate(1L);
        when(studyService.reportSession(eq(1L), any(), any(ZoneId.class)))
                .thenReturn(new StudySessionReportResponse(LocalDate.of(2026, 7, 8), 1));

        mockMvc.perform(post("/api/v1/study/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":5,\"durationSec\":60,\"wordsViewed\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streakDays").value(1));

        verify(studyService).reportSession(eq(1L), any(), any(ZoneId.class));
    }

    private static void authenticate(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of())
        );
    }
}
