package com.campusfind.controller;

import com.campusfind.dto.ReportCreateRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests for /api/reports endpoints.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportService reportService;

    @Test
    @DisplayName("POST /api/reports with valid payload returns 201 Created and JSON response")
    void testCreateReportSuccess() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "Casio Calculator",
                "electronics",
                "Black Casio FX-991EX left in central library",
                "library",
                LocalDate.now()
        );
        request.setIncidentTime(LocalTime.of(14, 30));

        ReportResponse response = new ReportResponse();
        response.setId(1L);
        response.setReportId("LST-20261016-A1B2");
        response.setType(ReportType.LOST);
        response.setItemName("Casio Calculator");
        response.setCategory("electronics");
        response.setDescription("Black Casio FX-991EX left in central library");
        response.setLocation("library");
        response.setIncidentDate(LocalDate.now());
        response.setIncidentTime(LocalTime.of(14, 30));
        response.setStatus(ReportStatus.OPEN);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(reportService.createReport(any(ReportCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").value("LST-20261016-A1B2"))
                .andExpect(jsonPath("$.itemName").value("Casio Calculator"))
                .andExpect(jsonPath("$.type").value("LOST"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST /api/reports with invalid payload returns 400 Bad Request with field errors")
    void testCreateReportValidationFailure() throws Exception {
        ReportCreateRequest invalidRequest = new ReportCreateRequest();
        // Missing required fields: type, itemName, category, description, location, incidentDate

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.type").exists())
                .andExpect(jsonPath("$.fieldErrors.itemName").exists())
                .andExpect(jsonPath("$.fieldErrors.category").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.location").exists())
                .andExpect(jsonPath("$.fieldErrors.incidentDate").exists());
    }

    @Test
    @DisplayName("POST /api/reports with future incident date returns 400 Bad Request")
    void testCreateReportFutureDateRejected() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.FOUND,
                "Blue Umbrella",
                "accessories",
                "Blue umbrella found in canteen entrance",
                "canteen",
                LocalDate.now().plusDays(2) // Future date
        );

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.incidentDate").value("Incident date cannot be in the future"));
    }

    @Test
    @DisplayName("POST /api/reports with Base64 data URL image returns 400 Bad Request")
    void testCreateReportBase64ImageRejected() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "Keys",
                "other",
                "Keys with blue lanyard",
                "parking",
                LocalDate.now()
        );
        request.setImageUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.imageUrl").value("Base64 data URLs are not permitted. Provide a valid URL or path."));
    }

    @Test
    @DisplayName("GET /api/reports retrieves list of reports with 200 OK")
    void testGetReports() throws Exception {
        ReportResponse report1 = new ReportResponse();
        report1.setId(1L);
        report1.setReportId("LST-20261016-A1B2");
        report1.setItemName("Calculator");
        report1.setType(ReportType.LOST);

        ReportResponse report2 = new ReportResponse();
        report2.setId(2L);
        report2.setReportId("FND-20261016-C3D4");
        report2.setItemName("Water Bottle");
        report2.setType(ReportType.FOUND);

        when(reportService.getReports(null, null, null, null)).thenReturn(List.of(report1, report2));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reportId").value("LST-20261016-A1B2"))
                .andExpect(jsonPath("$[1].reportId").value("FND-20261016-C3D4"));
    }

    @Test
    @DisplayName("GET /api/reports with query filters passes criteria to service")
    void testGetReportsWithFilters() throws Exception {
        ReportResponse report = new ReportResponse();
        report.setId(1L);
        report.setReportId("LST-20261016-A1B2");
        report.setItemName("Casio Calculator");
        report.setType(ReportType.LOST);
        report.setCategory("electronics");
        report.setLocation("library");

        when(reportService.getReports(eq(ReportType.LOST), eq("electronics"), eq("library"), eq(ReportStatus.OPEN)))
                .thenReturn(List.of(report));

        mockMvc.perform(get("/api/reports")
                        .param("type", "LOST")
                        .param("category", "electronics")
                        .param("location", "library")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportId").value("LST-20261016-A1B2"));
    }

    @Test
    @DisplayName("GET /api/reports/{id} with existing reportId returns 200 OK")
    void testGetReportByIdSuccess() throws Exception {
        ReportResponse response = new ReportResponse();
        response.setId(1L);
        response.setReportId("LST-20261016-A1B2");
        response.setItemName("Casio Calculator");
        response.setType(ReportType.LOST);

        when(reportService.getReportById("LST-20261016-A1B2")).thenReturn(response);

        mockMvc.perform(get("/api/reports/LST-20261016-A1B2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").value("LST-20261016-A1B2"))
                .andExpect(jsonPath("$.itemName").value("Casio Calculator"));
    }

    @Test
    @DisplayName("GET /api/reports/{id} with non-existent ID returns 404 Not Found")
    void testGetReportByIdNotFound() throws Exception {
        when(reportService.getReportById("NON-EXISTENT-ID"))
                .thenThrow(new ResourceNotFoundException("Report not found with identifier: NON-EXISTENT-ID"));

        mockMvc.perform(get("/api/reports/NON-EXISTENT-ID"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Report not found with identifier: NON-EXISTENT-ID"));
    }

    @Test
    @DisplayName("GET /api/reports?status=RESOLVED returns 400 Bad Request because schema uses RECOVERED")
    void testGetReportsInvalidStatusResolvedReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports").param("status", "RESOLVED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Parameter 'status' has invalid value 'RESOLVED'"));
    }

    @Test
    @DisplayName("GET /api/reports?type=INVALID returns 400 Bad Request")
    void testGetReportsInvalidTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports").param("type", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Parameter 'type' has invalid value 'INVALID'"));
    }

    @Test
    @DisplayName("GET /api/reports?status=RECOVERED returns 200 OK with valid schema status enum")
    void testGetReportsValidRecoveredStatusFilterReturnsOk() throws Exception {
        ReportResponse report = new ReportResponse();
        report.setId(3L);
        report.setReportId("FND-20261016-E5F6");
        report.setItemName("Laptop Charger");
        report.setStatus(ReportStatus.RECOVERED);

        when(reportService.getReports(null, null, null, ReportStatus.RECOVERED))
                .thenReturn(List.of(report));

        mockMvc.perform(get("/api/reports").param("status", "RECOVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("RECOVERED"));
    }

    @Test
    @DisplayName("POST /api/reports with invalid enum in request body returns 400 Bad Request instead of 500")
    void testCreateReportInvalidEnumInBodyReturnsBadRequest() throws Exception {
        String invalidEnumJson = """
                {
                    "type": "UNKNOWN_TYPE",
                    "itemName": "Notebook",
                    "category": "stationery",
                    "description": "Lost spiral notebook",
                    "location": "canteen",
                    "incidentDate": "2026-10-15"
                }
                """;

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEnumJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid value 'UNKNOWN_TYPE' for enum field 'type'. Accepted values are: [LOST, FOUND]"));
    }

    @Test
    @DisplayName("POST /api/reports with malformed JSON syntax returns 400 Bad Request")
    void testCreateReportMalformedJsonReturnsBadRequest() throws Exception {
        String malformedJson = "{ \"type\": \"LOST\", \"itemName\": ";

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body or invalid field value"));
    }

    @Test
    @DisplayName("POST /api/reports with valid imageUrl succeeds and returns 201 Created")
    void testCreateReportWithValidImageUrl() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest(
                ReportType.LOST,
                "Scientific Calculator",
                "electronics",
                "Black Casio fx-991EX left on desk",
                "library",
                LocalDate.now()
        );
        request.setImageUrl("/api/images/img_test123.jpg");

        ReportResponse response = new ReportResponse();
        response.setId(10L);
        response.setReportId("LST-20261021-X9Y8");
        response.setType(ReportType.LOST);
        response.setItemName("Scientific Calculator");
        response.setCategory("electronics");
        response.setDescription("Black Casio fx-991EX left on desk");
        response.setLocation("library");
        response.setIncidentDate(LocalDate.now());
        response.setImageUrl("/api/images/img_test123.jpg");
        response.setStatus(ReportStatus.OPEN);

        when(reportService.createReport(any(ReportCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value("LST-20261021-X9Y8"))
                .andExpect(jsonPath("$.imageUrl").value("/api/images/img_test123.jpg"));
    }

    @Test
    @DisplayName("POST /api/reports with Base64 or dangerous local file URL returns 400 Bad Request")
    void testCreateReportWithProhibitedImageUrl() throws Exception {
        ReportCreateRequest base64Request = new ReportCreateRequest(
                ReportType.LOST,
                "Keys",
                "accessories",
                "Silver dorm keys",
                "hostel",
                LocalDate.now()
        );
        base64Request.setImageUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(base64Request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());

        ReportCreateRequest fileUriRequest = new ReportCreateRequest(
                ReportType.LOST,
                "Keys",
                "accessories",
                "Silver dorm keys",
                "hostel",
                LocalDate.now()
        );
        fileUriRequest.setImageUrl("file:///C:/Users/Admin/secret.jpg");

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fileUriRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());

        ReportCreateRequest traversalRequest = new ReportCreateRequest(
                ReportType.LOST,
                "Keys",
                "accessories",
                "Silver dorm keys",
                "hostel",
                LocalDate.now()
        );
        traversalRequest.setImageUrl("../../etc/passwd.jpg");

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(traversalRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.imageUrl").exists());
    }
}

