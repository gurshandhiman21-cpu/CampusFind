package com.campusfind.controller;

import com.campusfind.config.SecurityConfig;
import com.campusfind.config.WebCorsConfig;
import com.campusfind.dto.RecoveryCreateRequest;
import com.campusfind.dto.RecoveryResponse;
import com.campusfind.exception.GlobalExceptionHandler;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.service.RecoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecoveryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WebCorsConfig.class})
class RecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecoveryService recoveryService;

    private RecoveryResponse sampleRecoveryResponse() {
        RecoveryResponse dto = new RecoveryResponse();
        dto.setId(1L);
        dto.setReportId(10L);
        dto.setReportPublicId("FND-20261026-TEST");
        dto.setReportTitle("MacBook Air M2");
        dto.setClaimId(1L);
        dto.setRecoveryDate(LocalDate.now());
        dto.setRecordedById(2L);
        dto.setRecordedByName("Finder Student");
        dto.setNotes("Handover completed at security desk.");
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }

    @Test
    @DisplayName("RecoveryController has exactly one explicit constructor (Dependency Injection safety)")
    void testSingleConstructor() {
        Constructor<?>[] constructors = RecoveryController.class.getConstructors();
        assertThat(constructors).hasSize(1);
    }

    // --- Unauthenticated Access Tests ---

    @Test
    @DisplayName("POST /api/reports/{reportId}/recovery without authentication returns 401 Unauthorized")
    void testRecordRecoveryUnauthenticatedReturns401() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(1L, LocalDate.now(), "Handover note");

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/reports/{reportId}/recovery without authentication returns 401 Unauthorized")
    void testGetRecoveryUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/reports/FND-20261026-TEST/recovery"))
                .andExpect(status().isUnauthorized());
    }

    // --- CSRF Protection Tests ---

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/recovery without CSRF returns 403 Forbidden")
    void testRecordRecoveryWithoutCsrfReturns403() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(1L, LocalDate.now(), "Handover note");

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- Happy Paths ---

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/recovery with valid body and CSRF returns 201 Created")
    void testRecordRecoverySuccess() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(1L, LocalDate.now(), "Handover completed at security desk.");

        when(recoveryService.recordRecovery(eq("FND-20261026-TEST"), any(RecoveryCreateRequest.class)))
                .thenReturn(sampleRecoveryResponse());

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reportPublicId").value("FND-20261026-TEST"))
                .andExpect(jsonPath("$.claimId").value(1L))
                .andExpect(jsonPath("$.recordedByName").value("Finder Student"));
    }

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/reports/{reportId}/recovery returns 200 OK and recovery details")
    void testGetRecoverySuccess() throws Exception {
        when(recoveryService.getRecoveryByReportId("FND-20261026-TEST"))
                .thenReturn(sampleRecoveryResponse());

        mockMvc.perform(get("/api/reports/FND-20261026-TEST/recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reportPublicId").value("FND-20261026-TEST"))
                .andExpect(jsonPath("$.notes").value("Handover completed at security desk."));
    }

    // --- Validation and Exception Handling Tests ---

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/recovery with null claimId returns 400 Bad Request")
    void testRecordRecoveryValidationFailure() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(); // null claimId

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/recovery returns 400 Bad Request on invalid state (e.g. unapproved claim)")
    void testRecordRecoveryInvalidStateReturns400() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(1L, LocalDate.now(), "Handover note");

        when(recoveryService.recordRecovery(eq("FND-20261026-TEST"), any(RecoveryCreateRequest.class)))
                .thenThrow(new InvalidClaimStateException("Recovery can only be recorded for an APPROVED claim (current claim status: PENDING)."));

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Recovery can only be recorded for an APPROVED claim (current claim status: PENDING)."));
    }

    @Test
    @WithMockUser(username = "stranger@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/recovery returns 403 Forbidden when caller is unauthorized")
    void testRecordRecoveryUnauthorizedReturns403() throws Exception {
        RecoveryCreateRequest request = new RecoveryCreateRequest(1L, LocalDate.now(), "Handover note");

        when(recoveryService.recordRecovery(eq("FND-20261026-TEST"), any(RecoveryCreateRequest.class)))
                .thenThrow(new UnauthorizedAccessException("Only the finder who reported this item or an administrator can record recovery."));

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Only the finder who reported this item or an administrator can record recovery."));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/reports/{reportId}/recovery returns 404 Not Found when no recovery record exists")
    void testGetRecoveryNotFoundReturns404() throws Exception {
        when(recoveryService.getRecoveryByReportId("FND-20261026-TEST"))
                .thenThrow(new ResourceNotFoundException("No recovery record exists for report ID: FND-20261026-TEST"));

        mockMvc.perform(get("/api/reports/FND-20261026-TEST/recovery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No recovery record exists for report ID: FND-20261026-TEST"));
    }
}
