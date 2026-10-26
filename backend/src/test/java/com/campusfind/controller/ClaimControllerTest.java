package com.campusfind.controller;

import com.campusfind.config.SecurityConfig;
import com.campusfind.config.WebCorsConfig;
import com.campusfind.dto.ClaimCreateRequest;
import com.campusfind.dto.ClaimResponse;
import com.campusfind.dto.ClaimReviewRequest;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.exception.DuplicateClaimException;
import com.campusfind.exception.GlobalExceptionHandler;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.service.ClaimService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WebCorsConfig.class})
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClaimService claimService;

    private ClaimResponse sampleClaimResponse(ClaimStatus status) {
        ClaimResponse dto = new ClaimResponse();
        dto.setId(1L);
        dto.setReportId(10L);
        dto.setReportPublicId("FND-20261026-TEST");
        dto.setReportTitle("MacBook Air M2");
        dto.setClaimantId(5L);
        dto.setClaimantName("John Doe");
        dto.setClaimantEmail("john@example.com");
        dto.setExplanation("This laptop belongs to me, left on library 2nd floor desk.");
        dto.setVerificationDetails("Has sticker with initials JD on bottom casing.");
        dto.setStatus(status);
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());
        return dto;
    }

    private ClaimResponse sampleClaimResponse() {
        return sampleClaimResponse(ClaimStatus.PENDING);
    }

    @Test
    @DisplayName("ClaimController has exactly one explicit constructor (Dependency Injection safety)")
    void testSingleConstructor() {
        Constructor<?>[] constructors = ClaimController.class.getConstructors();
        assertThat(constructors).hasSize(1);
    }

    // --- Unauthenticated Access Tests ---

    @Test
    @DisplayName("POST /api/reports/{reportId}/claims without authentication returns 401 Unauthorized")
    void testSubmitClaimUnauthenticatedReturns401() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest("Valid explanation text.", "Valid verification details.");

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/reports/{reportId}/claims without authentication returns 401 Unauthorized")
    void testGetReportClaimsUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/reports/FND-20261026-TEST/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/claims/my without authentication returns 401 Unauthorized")
    void testGetMyClaimsUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/claims/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/claims/{claimId} without authentication returns 401 Unauthorized")
    void testGetClaimByIdUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/claims/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/claims/{claimId}/review without authentication returns 401 Unauthorized")
    void testReviewClaimUnauthenticatedReturns401() throws Exception {
        ClaimReviewRequest request = new ClaimReviewRequest(ClaimStatus.APPROVED, "Verified");

        mockMvc.perform(patch("/api/claims/1/review")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/claims/{claimId}/withdraw without authentication returns 401 Unauthorized")
    void testWithdrawClaimUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/api/claims/1/withdraw")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // --- CSRF Protection Tests ---

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/claims without CSRF returns 403 Forbidden")
    void testSubmitClaimWithoutCsrfReturns403() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest("Valid explanation text.", "Valid verification details.");

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("PATCH /api/claims/{claimId}/review without CSRF returns 403 Forbidden")
    void testReviewClaimWithoutCsrfReturns403() throws Exception {
        ClaimReviewRequest request = new ClaimReviewRequest(ClaimStatus.APPROVED, "Verified");

        mockMvc.perform(patch("/api/claims/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- Happy Paths ---

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/claims with valid body and CSRF returns 201 Created")
    void testSubmitClaimSuccess() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest(
                "This laptop belongs to me, left on library 2nd floor desk.",
                "Has sticker with initials JD on bottom casing."
        );

        when(claimService.submitClaim(eq("FND-20261026-TEST"), any(ClaimCreateRequest.class)))
                .thenReturn(sampleClaimResponse());

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.reportPublicId").value("FND-20261026-TEST"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.claimantName").value("John Doe"));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/claims/my returns 200 OK and user claim list")
    void testGetMyClaimsSuccess() throws Exception {
        when(claimService.getMyClaims()).thenReturn(List.of(sampleClaimResponse()));

        mockMvc.perform(get("/api/claims/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].claimantName").value("John Doe"));
    }

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/reports/{reportId}/claims returns 200 OK and report claims list")
    void testGetClaimsForReportSuccess() throws Exception {
        when(claimService.getClaimsForReport("FND-20261026-TEST"))
                .thenReturn(List.of(sampleClaimResponse()));

        mockMvc.perform(get("/api/reports/FND-20261026-TEST/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportPublicId").value("FND-20261026-TEST"));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/claims/{claimId} returns 200 OK and single claim")
    void testGetClaimByIdSuccess() throws Exception {
        when(claimService.getClaimById(1L)).thenReturn(sampleClaimResponse());

        mockMvc.perform(get("/api/claims/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "finder@example.com", roles = {"STUDENT"})
    @DisplayName("PATCH /api/claims/{claimId}/review returns 200 OK on valid review decision")
    void testReviewClaimSuccess() throws Exception {
        ClaimReviewRequest request = new ClaimReviewRequest(ClaimStatus.APPROVED, "Verified ownership proof.");

        ClaimResponse reviewed = sampleClaimResponse(ClaimStatus.APPROVED);
        reviewed.setReviewNotes("Verified ownership proof.");

        when(claimService.reviewClaim(eq(1L), any(ClaimReviewRequest.class))).thenReturn(reviewed);

        mockMvc.perform(patch("/api/claims/1/review")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNotes").value("Verified ownership proof."));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("PATCH /api/claims/{claimId}/withdraw returns 200 OK on successful withdrawal")
    void testWithdrawClaimSuccess() throws Exception {
        ClaimResponse withdrawn = sampleClaimResponse(ClaimStatus.WITHDRAWN);

        when(claimService.withdrawClaim(1L)).thenReturn(withdrawn);

        mockMvc.perform(patch("/api/claims/1/withdraw")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    // --- Validation and Exception Handling Tests ---

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/claims with invalid body returns 400 Bad Request")
    void testSubmitClaimValidationFailure() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest("", "sh"); // explanation too short, verification too short

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/claims returns 409 Conflict on duplicate claim")
    void testSubmitDuplicateClaimReturns409() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest("Valid explanation text.", "Valid verification details.");

        when(claimService.submitClaim(eq("FND-20261026-TEST"), any(ClaimCreateRequest.class)))
                .thenThrow(new DuplicateClaimException("You already have an active claim on this item."));

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("You already have an active claim on this item."));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("POST /api/reports/{reportId}/claims returns 400 Bad Request on invalid claim state")
    void testSubmitClaimInvalidStateReturns400() throws Exception {
        ClaimCreateRequest request = new ClaimCreateRequest("Valid explanation text.", "Valid verification details.");

        when(claimService.submitClaim(eq("FND-20261026-TEST"), any(ClaimCreateRequest.class)))
                .thenThrow(new InvalidClaimStateException("Claims can only be submitted for found items."));

        mockMvc.perform(post("/api/reports/FND-20261026-TEST/claims")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Claims can only be submitted for found items."));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/reports/{reportId}/claims returns 403 Forbidden when caller is unauthorized")
    void testGetReportClaimsUnauthorizedReturns403() throws Exception {
        when(claimService.getClaimsForReport("FND-20261026-TEST"))
                .thenThrow(new UnauthorizedAccessException("You are not authorized to view claims for this report."));

        mockMvc.perform(get("/api/reports/FND-20261026-TEST/claims"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not authorized to view claims for this report."));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = {"STUDENT"})
    @DisplayName("GET /api/claims/{claimId} returns 404 Not Found when claim does not exist")
    void testGetClaimByIdNotFoundReturns404() throws Exception {
        when(claimService.getClaimById(999L))
                .thenThrow(new ResourceNotFoundException("Claim not found with ID: 999"));

        mockMvc.perform(get("/api/claims/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Claim not found with ID: 999"));
    }
}
