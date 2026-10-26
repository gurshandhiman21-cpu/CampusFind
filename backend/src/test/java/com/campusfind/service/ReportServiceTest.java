package com.campusfind.service;

import com.campusfind.dto.ReportCreateRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.Report;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.ReportRepository;
import com.campusfind.service.impl.ReportServiceImpl;
import com.campusfind.entity.User;
import com.campusfind.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportServiceImpl covering report creation, safe ID generation,
 * filtering delegation, identifier lookups, and exception handling.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        reportService = new ReportServiceImpl(reportRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createReport generates LST prefix for LOST type, sets status to OPEN, and saves report")
    void testCreateLostReport() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "MacBook Air M2",
                "electronics",
                "Midnight blue MacBook Air left in room 302",
                "main building",
                LocalDate.of(2026, 10, 15)
        );
        request.setIncidentTime(LocalTime.of(10, 15));
        request.setAdditionalInfo("Has a sticker on the lid");
        request.setImageUrl("https://example.com/macbook.jpg");

        when(reportRepository.existsByReportId(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report r = invocation.getArgument(0);
            r.setId(101L);
            return r;
        });

        ReportResponse response = reportService.createReport(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getReportId()).startsWith("LST-");
        assertThat(response.getType()).isEqualTo(ReportType.LOST);
        assertThat(response.getItemName()).isEqualTo("MacBook Air M2");
        assertThat(response.getCategory()).isEqualTo("electronics");
        assertThat(response.getLocation()).isEqualTo("main building");
        assertThat(response.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.getAdditionalInfo()).isEqualTo("Has a sticker on the lid");
        assertThat(response.getImageUrl()).isEqualTo("https://example.com/macbook.jpg");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report captured = captor.getValue();

        // Must verify security constraints: untrusted user identity must not be set
        assertThat(captured.getUser()).isNull();
        assertThat(captured.getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    @DisplayName("createReport generates FND prefix for FOUND type")
    void testCreateFoundReport() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.FOUND,
                "Titan Watch",
                "accessories",
                "Silver strap watch found near football ground",
                "sports complex",
                LocalDate.of(2026, 10, 16)
        );

        when(reportRepository.existsByReportId(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report r = invocation.getArgument(0);
            r.setId(102L);
            return r;
        });

        ReportResponse response = reportService.createReport(request);

        assertThat(response.getReportId()).startsWith("FND-");
        assertThat(response.getType()).isEqualTo(ReportType.FOUND);
    }

    @Test
    @DisplayName("createReport handles ID collision by regenerating until unique")
    void testCreateReportIdCollisionResolution() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "ID Card",
                "documents",
                "Student ID card lost",
                "library",
                LocalDate.now()
        );

        // First attempt collides, second attempt is unique
        when(reportRepository.existsByReportId(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.createReport(request);

        assertThat(response.getReportId()).startsWith("LST-");
        verify(reportRepository, times(2)).existsByReportId(anyString());
    }

    @Test
    @DisplayName("getReports passes cleaned filter criteria to repository")
    void testGetReportsWithFilters() {
        Report report = new Report();
        report.setId(1L);
        report.setReportId("LST-20261016-1234");
        report.setType(ReportType.LOST);
        report.setItemName("Keys");
        report.setCategory("other");
        report.setLocation("parking");
        report.setStatus(ReportStatus.OPEN);
        report.setIncidentDate(LocalDate.now());

        when(reportRepository.findWithFilters(ReportType.LOST, "other", "parking", ReportStatus.OPEN))
                .thenReturn(List.of(report));

        List<ReportResponse> results = reportService.getReports(
                ReportType.LOST, "  other  ", " parking ", ReportStatus.OPEN);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReportId()).isEqualTo("LST-20261016-1234");
        verify(reportRepository).findWithFilters(ReportType.LOST, "other", "parking", ReportStatus.OPEN);
    }

    @Test
    @DisplayName("getReports converts empty or whitespace filter strings to null")
    void testGetReportsEmptyStringsToNull() {
        when(reportRepository.findWithFilters(null, null, null, null))
                .thenReturn(Collections.emptyList());

        List<ReportResponse> results = reportService.getReports(null, "   ", "   ", null);

        assertThat(results).isEmpty();
        verify(reportRepository).findWithFilters(null, null, null, null);
    }

    @Test
    @DisplayName("getReportById successfully resolves by public reportId string")
    void testGetReportByIdByPublicId() {
        Report report = new Report();
        report.setId(5L);
        report.setReportId("LST-20261016-ABCD");
        report.setItemName("Backpack");
        report.setType(ReportType.LOST);

        when(reportRepository.findByReportId("LST-20261016-ABCD")).thenReturn(Optional.of(report));

        ReportResponse response = reportService.getReportById("LST-20261016-ABCD");

        assertThat(response.getReportId()).isEqualTo("LST-20261016-ABCD");
        assertThat(response.getItemName()).isEqualTo("Backpack");
        verify(reportRepository).findByReportId("LST-20261016-ABCD");
        verify(reportRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("getReportById falls back to numeric database primary key when reportId not found")
    void testGetReportByIdFallbackToNumericId() {
        Report report = new Report();
        report.setId(42L);
        report.setReportId("FND-20261016-9999");
        report.setItemName("Headphones");
        report.setType(ReportType.FOUND);

        when(reportRepository.findByReportId("42")).thenReturn(Optional.empty());
        when(reportRepository.findById(42L)).thenReturn(Optional.of(report));

        ReportResponse response = reportService.getReportById("42");

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getReportId()).isEqualTo("FND-20261016-9999");
        assertThat(response.getItemName()).isEqualTo("Headphones");
    }

    @Test
    @DisplayName("getReportById throws ResourceNotFoundException when report does not exist")
    void testGetReportByIdNotFoundThrowsException() {
        when(reportRepository.findByReportId("NON-EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReportById("NON-EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Report not found with identifier: NON-EXISTENT");
    }

    @Test
    @DisplayName("getReportById throws ResourceNotFoundException when identifier is blank or null")
    void testGetReportByIdBlankThrowsException() {
        assertThatThrownBy(() -> reportService.getReportById("   "))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Report identifier must not be empty");

        assertThatThrownBy(() -> reportService.getReportById(null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Report identifier must not be empty");
    }

    @Test
    @DisplayName("createReport with valid imageUrl persists imageUrl cleanly")
    void testCreateReportWithValidImageUrl() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "Umbrella",
                "accessories",
                "Black umbrella in library",
                "library",
                LocalDate.now()
        );
        request.setImageUrl("/api/images/img_umbrella123.jpg");

        when(reportRepository.existsByReportId(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.createReport(request);

        assertThat(response.getImageUrl()).isEqualTo("/api/images/img_umbrella123.jpg");
    }

    @Test
    @DisplayName("createReport with file URI, base64, or traversal imageUrl throws IllegalArgumentException")
    void testCreateReportWithUnsafeImageUrlThrowsException() {
        ReportCreateRequest fileUriReq = new ReportCreateRequest(
                ReportType.LOST,
                "Umbrella",
                "accessories",
                "Black umbrella",
                "library",
                LocalDate.now()
        );
        fileUriReq.setImageUrl("file:///C:/Users/test.jpg");

        assertThatThrownBy(() -> reportService.createReport(fileUriReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image URL");

        ReportCreateRequest base64Req = new ReportCreateRequest(
                ReportType.LOST,
                "Umbrella",
                "accessories",
                "Black umbrella",
                "library",
                LocalDate.now()
        );
        base64Req.setImageUrl("data:image/png;base64,abc");

        assertThatThrownBy(() -> reportService.createReport(base64Req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image URL");

        ReportCreateRequest traversalReq = new ReportCreateRequest(
                ReportType.LOST,
                "Umbrella",
                "accessories",
                "Black umbrella",
                "library",
                LocalDate.now()
        );
        traversalReq.setImageUrl("../../secret.jpg");

        assertThatThrownBy(() -> reportService.createReport(traversalReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid image URL");
    }

    @Test
    @DisplayName("createReport associates authenticated User entity when SecurityContext is populated")
    void testCreateReportAuthenticatedUserAssociated() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "Scientific Calculator",
                "electronics",
                "Casio fx-991EX left in math lab",
                "science block",
                LocalDate.of(2026, 10, 20)
        );

        User authenticatedUser = new User();
        authenticatedUser.setId(5L);
        authenticatedUser.setEmail("student@campus.edu");
        authenticatedUser.setFullName("Campus Student");

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(authenticatedUser));
        when(reportRepository.existsByReportId(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "student@campus.edu",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                )
        );

        ReportResponse response = reportService.createReport(request);

        assertThat(response).isNotNull();
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report captured = captor.getValue();

        assertThat(captured.getUser()).isNotNull();
        assertThat(captured.getUser().getId()).isEqualTo(5L);
        assertThat(captured.getUser().getEmail()).isEqualTo("student@campus.edu");
    }

    @Test
    @DisplayName("createReport leaves user null when user is anonymous")
    void testCreateReportAnonymousLeavesUserNull() {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.FOUND,
                "Water Bottle",
                "personal_items",
                "Blue bottle found on bench",
                "cafeteria",
                LocalDate.of(2026, 10, 21)
        );

        when(reportRepository.existsByReportId(anyString())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.createReport(request);

        assertThat(response).isNotNull();
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report captured = captor.getValue();

        assertThat(captured.getUser()).isNull();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("ReportServiceImpl has exactly one explicit constructor to satisfy Spring dependency injection")
    void testReportServiceImplHasSingleExplicitConstructor() {
        java.lang.reflect.Constructor<?>[] constructors = ReportServiceImpl.class.getDeclaredConstructors();
        assertThat(constructors)
                .as("ReportServiceImpl must have a single constructor for unambiguous Spring autowiring")
                .hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .as("Constructor must accept both ReportRepository and UserRepository")
                .containsExactly(ReportRepository.class, UserRepository.class);
    }
}
