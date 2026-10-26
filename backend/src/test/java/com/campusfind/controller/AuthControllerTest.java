package com.campusfind.controller;

import com.campusfind.config.SecurityConfig;
import com.campusfind.config.WebCorsConfig;
import com.campusfind.dto.LoginRequest;
import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.enums.AccountStatus;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.EmailAlreadyExistsException;
import com.campusfind.exception.GlobalExceptionHandler;
import com.campusfind.exception.LoginRateLimitExceededException;
import com.campusfind.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WebCorsConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/register with valid body and CSRF returns 201 Created and safe UserResponse")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("student@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Student User");

        UserResponse userResponse = new UserResponse(
                1L,
                "Student User",
                "student@example.com",
                UserRole.STUDENT,
                AccountStatus.ACTIVE,
                null
        );

        when(authService.register(any())).thenReturn(userResponse);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.fullName").value("Student User"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register without CSRF returns 403 Forbidden")
    void testRegisterWithoutCsrfForbidden() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("student@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Student User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email returns 409 Conflict")
    void testRegisterDuplicateEmailReturns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Duplicate User");

        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid email syntax returns 400 Bad Request")
    void testRegisterInvalidEmailReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("SecurePass123!");
        request.setFullName("Invalid Email User");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 OK and safe UserResponse")
    void testLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@example.com");
        request.setPassword("ValidPassword123!");

        UserResponse userResponse = new UserResponse(
                1L,
                "Student User",
                "student@example.com",
                UserRole.STUDENT,
                AccountStatus.ACTIVE,
                null
        );

        when(authService.login(any(), any(), any())).thenReturn(userResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("POST /api/auth/login with bad credentials returns 401 Unauthorized")
    void testLoginBadCredentialsReturns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@example.com");
        request.setPassword("WrongPassword");

        when(authService.login(any(), any(), any()))
                .thenThrow(new BadCredentialsException("Invalid email or password."));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("POST /api/auth/login when rate limited returns 429 Too Many Requests")
    void testLoginRateLimitedReturns429() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@example.com");
        request.setPassword("Password");

        when(authService.login(any(), any(), any()))
                .thenThrow(new LoginRateLimitExceededException("Too many failed login attempts. Please try again in 5 minutes."));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Please try again in 5 minutes."));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 Unauthorized when unauthenticated")
    void testGetMeUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 OK and UserResponse when authenticated")
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    void testGetMeAuthenticatedReturns200() throws Exception {
        UserResponse userResponse = new UserResponse(
                1L,
                "Student User",
                "student@example.com",
                UserRole.STUDENT,
                AccountStatus.ACTIVE,
                null
        );
        when(authService.getCurrentUser(any())).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/logout without authentication returns 401 Unauthorized")
    void testLogoutUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout with authentication and CSRF returns 204 No Content")
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    void testLogoutAuthenticatedReturns204() throws Exception {
        doNothing().when(authService).logout(any(), any());

        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(), any());
    }

    @Test
    @DisplayName("GET /api/auth/csrf returns 200 OK with CSRF token details and sets XSRF-TOKEN cookie")
    void testGetCsrfToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("POST /api/auth/register succeeds with explicit X-CSRF-TOKEN header and XSRF-TOKEN cookie")
    void testRegisterWithExplicitCsrfHeaderAndCookie() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("explicit-csrf@example.com");
        request.setPassword("SecurePass123!");
        request.setFullName("Explicit CSRF User");

        UserResponse userResponse = new UserResponse(
                2L,
                "Explicit CSRF User",
                "explicit-csrf@example.com",
                UserRole.STUDENT,
                AccountStatus.ACTIVE,
                null
        );

        when(authService.register(any())).thenReturn(userResponse);

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", "sample-raw-token-123");

        mockMvc.perform(post("/api/auth/register")
                        .cookie(cookie)
                        .header("X-CSRF-TOKEN", "sample-raw-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("explicit-csrf@example.com"));
    }
}
