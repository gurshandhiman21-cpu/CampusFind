package com.campusfind.service;

import com.campusfind.dto.RecoveryCreateRequest;
import com.campusfind.dto.RecoveryResponse;
import com.campusfind.entity.Claim;
import com.campusfind.entity.RecoveryRecord;
import com.campusfind.entity.Report;
import com.campusfind.entity.User;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.entity.enums.UserRole;
import com.campusfind.exception.InvalidClaimStateException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.exception.UnauthorizedAccessException;
import com.campusfind.repository.ClaimRepository;
import com.campusfind.repository.RecoveryRecordRepository;
import com.campusfind.repository.ReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.impl.RecoveryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private RecoveryRecordRepository recoveryRecordRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private UserRepository userRepository;

    private RecoveryService recoveryService;

    private User studentFinder;
    private User studentClaimant;
    private User adminUser;
    private User unrelatedStudent;
    private Report foundReport;
    private Claim approvedClaim;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        recoveryService = new RecoveryServiceImpl(recoveryRecordRepository, reportRepository, claimRepository, userRepository);

        studentFinder = new User("Finder Bob", "bob@campusfind.test", "hash", UserRole.STUDENT);
        studentFinder.setId(20L);

        studentClaimant = new User("Claimant Alice", "alice@campusfind.test", "hash", UserRole.STUDENT);
        studentClaimant.setId(10L);

        adminUser = new User("Admin Carol", "carol@campusfind.test", "hash", UserRole.ADMIN);
        adminUser.setId(99L);

        unrelatedStudent = new User("Stranger Dave", "dave@campusfind.test", "hash", UserRole.STUDENT);
        unrelatedStudent.setId(50L);

        foundReport = new Report(
                "FND-20261026-REC",
                ReportType.FOUND,
                "Wristwatch",
                "accessories",
                "Silver wristwatch found near gym",
                "sports",
                LocalDate.of(2026, 10, 26)
        );
        foundReport.setId(200L);
        foundReport.setUser(studentFinder);
        foundReport.setStatus(ReportStatus.CLAIM_PENDING);

        approvedClaim = new Claim(foundReport, studentClaimant, "Explanation", "Proof");
        approvedClaim.setId(500L);
        approvedClaim.setStatus(ClaimStatus.APPROVED);
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
    @DisplayName("recordRecovery succeeds for approved claim, transitions report to RECOVERED, and auto-closes competing claims")
    void testRecordRecoverySuccess() {
        authenticateAs(studentFinder);

        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));
        when(recoveryRecordRepository.existsByReportId(200L)).thenReturn(false);
        when(claimRepository.findById(500L)).thenReturn(Optional.of(approvedClaim));
        when(recoveryRecordRepository.findByClaimId(500L)).thenReturn(Optional.empty());

        Claim competingClaim = new Claim(foundReport, unrelatedStudent, "Explanation 2", "Proof 2");
        competingClaim.setId(501L);
        competingClaim.setStatus(ClaimStatus.PENDING);
        when(claimRepository.findByReportIdAndStatus(200L, ClaimStatus.PENDING)).thenReturn(List.of(competingClaim));

        when(recoveryRecordRepository.save(any(RecoveryRecord.class))).thenAnswer(inv -> {
            RecoveryRecord r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        RecoveryCreateRequest req = new RecoveryCreateRequest(500L, LocalDate.of(2026, 10, 26), "Handed over at security office");
        RecoveryResponse response = recoveryService.recordRecovery("FND-20261026-REC", req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getClaimId()).isEqualTo(500L);
        assertThat(response.getRecordedByName()).isEqualTo("Finder Bob");
        assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.RECOVERED);
        assertThat(competingClaim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(competingClaim.getReviewNotes()).contains("Item physically recovered");

        verify(reportRepository).save(foundReport);
        verify(claimRepository).save(competingClaim);
        verify(recoveryRecordRepository).save(any(RecoveryRecord.class));
    }

    @Test
    @DisplayName("recordRecovery fails if report is already recovered")
    void testRecordRecoveryAlreadyRecoveredThrows() {
        authenticateAs(studentFinder);
        foundReport.setStatus(ReportStatus.RECOVERED);
        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));

        RecoveryCreateRequest req = new RecoveryCreateRequest(500L, LocalDate.of(2026, 10, 26), "Notes");
        assertThatThrownBy(() -> recoveryService.recordRecovery("FND-20261026-REC", req))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("A recovery record already exists");
    }

    @Test
    @DisplayName("recordRecovery fails if claim is not APPROVED")
    void testRecordRecoveryUnapprovedClaimThrows() {
        authenticateAs(studentFinder);
        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));
        when(recoveryRecordRepository.existsByReportId(200L)).thenReturn(false);

        approvedClaim.setStatus(ClaimStatus.PENDING);
        when(claimRepository.findById(500L)).thenReturn(Optional.of(approvedClaim));

        RecoveryCreateRequest req = new RecoveryCreateRequest(500L, LocalDate.of(2026, 10, 26), "Notes");
        assertThatThrownBy(() -> recoveryService.recordRecovery("FND-20261026-REC", req))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("Cannot complete handover for a claim that has not been approved");
    }

    @Test
    @DisplayName("recordRecovery rejects unauthorized student")
    void testRecordRecoveryUnauthorizedStudentThrows() {
        authenticateAs(unrelatedStudent);
        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));
        when(recoveryRecordRepository.existsByReportId(200L)).thenReturn(false);

        RecoveryCreateRequest req = new RecoveryCreateRequest(500L, LocalDate.of(2026, 10, 26), "Notes");
        assertThatThrownBy(() -> recoveryService.recordRecovery("FND-20261026-REC", req))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized to record item recovery");
    }

    @Test
    @DisplayName("getRecoveryByReportId succeeds for finder or approved claimant")
    void testGetRecoveryByReportIdAuthorized() {
        authenticateAs(studentClaimant);
        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));

        RecoveryRecord record = new RecoveryRecord(foundReport, approvedClaim, LocalDate.of(2026, 10, 26), studentFinder, "Notes");
        record.setId(1L);
        when(recoveryRecordRepository.findByReportId(200L)).thenReturn(Optional.of(record));

        RecoveryResponse response = recoveryService.getRecoveryByReportId("FND-20261026-REC");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getRecoveryByReportId rejects unrelated student")
    void testGetRecoveryByReportIdUnauthorizedThrows() {
        authenticateAs(unrelatedStudent);
        when(reportRepository.findByReportId("FND-20261026-REC")).thenReturn(Optional.of(foundReport));

        RecoveryRecord record = new RecoveryRecord(foundReport, approvedClaim, LocalDate.of(2026, 10, 26), studentFinder, "Notes");
        record.setId(1L);
        when(recoveryRecordRepository.findByReportId(200L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> recoveryService.getRecoveryByReportId("FND-20261026-REC"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized to view this recovery record");
    }

    @Test
    @DisplayName("RecoveryServiceImpl has exactly one explicit constructor for unambiguous Spring autowiring")
    void testRecoveryServiceImplHasSingleExplicitConstructor() {
        Constructor<?>[] constructors = RecoveryServiceImpl.class.getDeclaredConstructors();
        assertThat(constructors)
                .as("RecoveryServiceImpl must have a single constructor for unambiguous Spring autowiring")
                .hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(RecoveryRecordRepository.class, ReportRepository.class, ClaimRepository.class, UserRepository.class);
    }
}
