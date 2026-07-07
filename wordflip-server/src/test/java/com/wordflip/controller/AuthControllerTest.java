package com.wordflip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordflip.dto.auth.AuthResponse;
import com.wordflip.dto.auth.LoginRequest;
import com.wordflip.dto.auth.RegisterRequest;
import com.wordflip.dto.auth.TokenRequest;
import com.wordflip.service.AuthService;
import com.wordflip.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth 端点 MockMvc 冒烟测试（Q-02 部分）。
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    /** 避免 @WebMvcTest 切片加载真实 JWT 依赖链 */
    @MockBean
    private com.wordflip.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void register_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(authService.register(any())).thenReturn(
                AuthResponse.of("access", "refresh", 900, buildUser())
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void login_returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("test@example.com");
        request.setPassword("password123");

        when(authService.login(any())).thenReturn(
                AuthResponse.of("access", "refresh", 900, buildUser())
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1));
    }

    @Test
    void refresh_returns200() throws Exception {
        TokenRequest request = new TokenRequest();
        request.setRefreshToken("old-refresh");

        when(authService.refresh("old-refresh")).thenReturn(
                AuthResponse.of("new-access", "new-refresh", 900, buildUser())
        );

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void logout_returns204() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(1L), null, List.of())
        );
        try {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNoContent());

            verify(authService).logout(eq(1L), eq(null));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static com.wordflip.domain.User buildUser() {
        com.wordflip.domain.User user = new com.wordflip.domain.User();
        user.setId(1L);
        user.setEmail("test@example.com");
        return user;
    }
}
