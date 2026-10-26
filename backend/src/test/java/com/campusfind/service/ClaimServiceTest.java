package com.campusfind.service;

import com.campusfind.dto.ClaimCreateRequest;
import com.campusfind.dto.ClaimResponse;
import com.campusfind.dto.ClaimReviewRequest;
import com.campusfind.entity.Claim;
import com.campusfind.entity.Report;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.AccountStatus;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.DuplicateClaimException;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.repository.ClaimRepository;
import com.campusfind.repository.ReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.impl.ClaimServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    private ClaimService claimService;

    private User studentClaimant;
    private User studentFinder;
    private User adminUser;
    private Report foundReport;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        claimService = new ClaimServiceImpl(claimRepository, reportRepository, userRepository);

        studentClaimant = new User("Alice Claimant", "alice@campusfind.test", "hash", UserRole.STUDENT);
        studentClaimant.setId(10L);

        studentFinder = new User("Bob Finder", "bob@campusfind.test", "hash", UserRole.STUDENT);
        studentFinder.setId(20L);

        adminUser = new User("Admin Carol", "carol@campusfind.test", "hash", UserRole.ADMIN);
        adminUser.setId(99L);

        foundReport = new Report(
                "FND-20261026-TEST",
                ReportType.FOUND,
                "Scientific Calculator",
                "electronics",
                "Found on table in library",
                "library",
                LocalDate.of(2026, 10, 26)
        );
        foundReport.setId(100L);
        foundReport.setUser(studentFinder);
        foundReport.setStatus(ReportStatus.OPEN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("submitClaim succeeds on valid found report and transitions report to CLAIM_PENDING")
    void testSubmitClaimSuccess() {
        authenticateAs(studentClaimant);

        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));
        when(claimRepository.existsByReportIdAndClaimantIdAndStatusIn(eq(100L), eq(10L), anyCollection()))
                .thenReturn(false);
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        ClaimCreateRequest request = new ClaimCreateRequest(
                "I lost this exact calculator in the library yesterday afternoon.",
                "My name is scratched in tiny letters under the solar panel."
        );

        ClaimResponse response = claimService.submitClaim("FND-20261026-TEST", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(ClaimStatus.PENDING);
        assertThat(response.getClaimantName()).isEqualTo("Alice Claimant");
        assertThat(response.getVerificationDetails()).isEqualTo("My name is scratched in tiny letters under the solar panel.");
        assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.CLAIM_PENDING);

        verify(reportRepository).save(foundReport);
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    @DisplayName("submitClaim rejects unauthenticated submission")
    void testSubmitClaimUnauthenticatedThrows() {
        ClaimCreateRequest request = new ClaimCreateRequest(
                "Valid explanation here for testing.",
                "Valid verification proof."
        );

        assertThatThrownBy(() -> claimService.submitClaim("FND-20261026-TEST", request))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    @DisplayName("submitClaim rejects claim on LOST item report")
    void testSubmitClaimOnLostItemThrows() {
        authenticateAs(studentClaimant);
        foundReport.setType(ReportType.LOST);
        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));

        ClaimCreateRequest request = new ClaimCreateRequest(
                "Valid explanation here for testing.",
                "Valid verification proof."
        );

        assertThatThrownBy(() -> claimService.submitClaim("FND-20261026-TEST", request))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("Claims can only be submitted for found items");
    }

    @Test
    @DisplayName("submitClaim rejects self-claim when claimant is the report finder")
    void testSubmitClaimByFinderThrows() {
        authenticateAs(studentFinder);
        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));

        ClaimCreateRequest request = new ClaimCreateRequest(
                "Valid explanation here for testing.",
                "Valid verification proof."
        );

        assertThatThrownBy(() -> claimService.submitClaim("FND-20261026-TEST", request))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("You cannot submit an ownership claim on an item that you reported finding");
    }

    @Test
    @DisplayName("submitClaim rejects duplicate active claim from same student")
    void testSubmitClaimDuplicateThrows() {
        authenticateAs(studentClaimant);
        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));
        when(claimRepository.existsByReportIdAndClaimantIdAndStatusIn(eq(100L), eq(10L), anyCollection()))
                .thenReturn(true);

        ClaimCreateRequest request = new ClaimCreateRequest(
                "Valid explanation here for testing.",
                "Valid verification proof."
        );

        assertThatThrownBy(() -> claimService.submitClaim("FND-20261026-TEST", request))
                .isInstanceOf(DuplicateClaimException.class)
                .hasMessageContaining("You already have an active claim");
    }

    @Test
    @DisplayName("getClaimsForReport succeeds for finder with confidential proof unmasked")
    void testGetClaimsForReportByFinder() {
        authenticateAs(studentFinder);
        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));

        Claim claim = new Claim(foundReport, studentClaimant, "Explanation", "Confidential Secret Proof");
        claim.setId(1L);
        when(claimRepository.findByReportIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(claim));

        List<ClaimResponse> claims = claimService.getClaimsForReport("FND-20261026-TEST");

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getVerificationDetails()).isEqualTo("Confidential Secret Proof");
    }

    @Test
    @DisplayName("getClaimsForReport rejects unauthorized student")
    void testGetClaimsForReportByUnauthorizedStudentThrows() {
        authenticateAs(studentClaimant);
        when(reportRepository.findByReportId("FND-20261026-TEST")).thenReturn(Optional.of(foundReport));

        assertThatThrownBy(() -> claimService.getClaimsForReport("FND-20261026-TEST"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized to view claims");
    }

    @Test
    @DisplayName("reviewClaim approves pending claim and records reviewer")
    void testReviewClaimApprovalSuccess() {
        authenticateAs(studentFinder);

        Claim claim = new Claim(foundReport, studentClaimant, "Explanation", "Proof");
        claim.setId(1L);
        claim.setStatus(ClaimStatus.PENDING);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

        ClaimReviewRequest reviewReq = new ClaimReviewRequest(ClaimStatus.APPROVED, "Proof matches description");
        ClaimResponse response = claimService.reviewClaim(1L, reviewReq);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.getReviewedByName()).isEqualTo("Bob Finder");
        assertThat(response.getReviewNotes()).isEqualTo("Proof matches description");
    }

    @Test
    @DisplayName("reviewClaim rejects pending claim and reverts report status to OPEN if no active claims remain")
    void testReviewClaimRejectionRevertsReportStatus() {
        authenticateAs(studentFinder);
        foundReport.setStatus(ReportStatus.CLAIM_PENDING);

        Claim claim = new Claim(foundReport, studentClaimant, "Explanation", "Proof");
        claim.setId(1L);
        claim.setStatus(ClaimStatus.PENDING);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimRepository.findByReportIdAndStatusIn(eq(100L), anyCollection())).thenReturn(Collections.emptyList());

        ClaimReviewRequest reviewReq = new ClaimReviewRequest(ClaimStatus.REJECTED, "Serial number does not match");
        ClaimResponse response = claimService.reviewClaim(1L, reviewReq);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.OPEN);
        verify(reportRepository).save(foundReport);
    }

    @Test
    @DisplayName("reviewClaim rejects review of non-pending claim")
    void testReviewClaimNonPendingThrows() {
        authenticateAs(studentFinder);

        Claim claim = new Claim(foundReport, studentClaimant, "Explanation", "Proof");
        claim.setId(1L);
        claim.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        ClaimReviewRequest reviewReq = new ClaimReviewRequest(ClaimStatus.REJECTED, "Notes");
        assertThatThrownBy(() -> claimService.reviewClaim(1L, reviewReq))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("Only claims in PENDING status can be reviewed");
    }

    @Test
    @DisplayName("withdrawClaim allows claimant to withdraw their pending claim")
    void testWithdrawClaimSuccess() {
        authenticateAs(studentClaimant);
        foundReport.setStatus(ReportStatus.CLAIM_PENDING);

        Claim claim = new Claim(foundReport, studentClaimant, "Explanation", "Proof");
        claim.setId(1L);
        claim.setStatus(ClaimStatus.PENDING);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimRepository.findByReportIdAndStatusIn(eq(100L), anyCollection())).thenReturn(Collections.emptyList());

        ClaimResponse response = claimService.withdrawClaim(1L);

        assertThat(response.getStatus()).isEqualTo(ClaimStatus.WITHDRAWN);
        assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    @DisplayName("ClaimServiceImpl has exactly one explicit constructor to prevent Spring autowiring ambiguity")
    void testClaimServiceImplHasSingleExplicitConstructor() {
        Constructor<?>[] constructors = ClaimServiceImpl.class.getDeclaredConstructors();
        assertThat(constructors)
                .as("ClaimServiceImpl must have a single constructor for unambiguous Spring autowiring")
                .hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(ClaimRepository.class, ReportRepository.class, UserRepository.class);
    }
}
