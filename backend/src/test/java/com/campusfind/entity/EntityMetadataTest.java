package com.campusfind.entity;

import com.campusfind.entity.enums.AccountStatus;
import com.campusfind.entity.enums.ClaimStatus;
import com.campusfind.entity.enums.ReportStatus;
import com.campusfind.entity.enums.ReportType;
import com.campusfind.entity.enums.UserRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates entity structural integrity, JPA metadata annotations, and
 * non-destructive cascading policies against approved schema specifications.
 */
class EntityMetadataTest {

    @Test
    @DisplayName("Verify User entity mapping and constraints")
    void testUserEntityMetadata() throws NoSuchFieldException {
        assertTrue(User.class.isAnnotationPresent(Entity.class));
        Table table = User.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("users", table.name());

        Field idField = User.class.getDeclaredField("id");
        assertTrue(idField.isAnnotationPresent(Id.class));
        assertEquals(Long.class, idField.getType());

        Field emailField = User.class.getDeclaredField("email");
        Column emailCol = emailField.getAnnotation(Column.class);
        assertNotNull(emailCol);
        assertTrue(emailCol.unique());
        assertFalse(emailCol.nullable());
        assertEquals(150, emailCol.length());
    }

    @Test
    @DisplayName("Verify Report entity mapping, column types, and non-destructive cascades")
    void testReportEntityMetadata() throws NoSuchFieldException {
        assertTrue(Report.class.isAnnotationPresent(Entity.class));
        Table table = Report.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("reports", table.name());

        Field idField = Report.class.getDeclaredField("id");
        assertTrue(idField.isAnnotationPresent(Id.class));
        assertEquals(Long.class, idField.getType());

        Field reportIdField = Report.class.getDeclaredField("reportId");
        Column reportIdCol = reportIdField.getAnnotation(Column.class);
        assertNotNull(reportIdCol);
        assertEquals("report_id", reportIdCol.name());
        assertTrue(reportIdCol.unique());
        assertEquals(36, reportIdCol.length());

        // CRITICAL CHECK: CascadeType.REMOVE must NOT be present on claims
        Field claimsField = Report.class.getDeclaredField("claims");
        OneToMany oneToMany = claimsField.getAnnotation(OneToMany.class);
        assertNotNull(oneToMany);
        assertEquals("report", oneToMany.mappedBy());
        assertFalse(Arrays.asList(oneToMany.cascade()).contains(CascadeType.ALL),
                "CascadeType.ALL is prohibited on Report.claims to protect audit history");
        assertFalse(Arrays.asList(oneToMany.cascade()).contains(CascadeType.REMOVE),
                "CascadeType.REMOVE is prohibited on Report.claims to protect audit history");

        // CRITICAL CHECK: CascadeType.REMOVE must NOT be present on recoveryRecord
        Field recoveryField = Report.class.getDeclaredField("recoveryRecord");
        OneToOne oneToOne = recoveryField.getAnnotation(OneToOne.class);
        assertNotNull(oneToOne);
        assertEquals("report", oneToOne.mappedBy());
        assertFalse(Arrays.asList(oneToOne.cascade()).contains(CascadeType.ALL),
                "CascadeType.ALL is prohibited on Report.recoveryRecord");
        assertFalse(Arrays.asList(oneToOne.cascade()).contains(CascadeType.REMOVE),
                "CascadeType.REMOVE is prohibited on Report.recoveryRecord");
    }

    @Test
    @DisplayName("Verify Claim entity mapping and private verificationDetails field")
    void testClaimEntityMetadata() throws NoSuchFieldException {
        assertTrue(Claim.class.isAnnotationPresent(Entity.class));
        Table table = Claim.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("claims", table.name());

        Field idField = Claim.class.getDeclaredField("id");
        assertEquals(Long.class, idField.getType());

        Field verificationField = Claim.class.getDeclaredField("verificationDetails");
        Column verificationCol = verificationField.getAnnotation(Column.class);
        assertNotNull(verificationCol);
        assertEquals("verification_details", verificationCol.name());
        assertEquals("TEXT", verificationCol.columnDefinition());
        assertFalse(verificationCol.nullable());
    }

    @Test
    @DisplayName("Verify RecoveryRecord entity mapping and uniqueness constraints")
    void testRecoveryRecordEntityMetadata() throws NoSuchFieldException {
        assertTrue(RecoveryRecord.class.isAnnotationPresent(Entity.class));
        Table table = RecoveryRecord.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("recovery_records", table.name());

        Field idField = RecoveryRecord.class.getDeclaredField("id");
        assertEquals(Long.class, idField.getType());

        Field reportField = RecoveryRecord.class.getDeclaredField("report");
        OneToOne reportOneToOne = reportField.getAnnotation(OneToOne.class);
        assertNotNull(reportOneToOne);
        assertFalse(reportOneToOne.optional());
    }

    @Test
    @DisplayName("Verify Enum values conform to approved constraints")
    void testEnumConstants() {
        assertArrayEquals(new String[]{"LOST", "FOUND"},
                Arrays.stream(ReportType.values()).map(Enum::name).toArray());

        assertArrayEquals(new String[]{"OPEN", "CLAIM_PENDING", "RECOVERED", "CLOSED", "DISPOSED"},
                Arrays.stream(ReportStatus.values()).map(Enum::name).toArray());

        assertArrayEquals(new String[]{"PENDING", "APPROVED", "REJECTED", "WITHDRAWN"},
                Arrays.stream(ClaimStatus.values()).map(Enum::name).toArray());

        assertArrayEquals(new String[]{"STUDENT", "ADMIN", "STAFF"},
                Arrays.stream(UserRole.values()).map(Enum::name).toArray());

        assertArrayEquals(new String[]{"ACTIVE", "SUSPENDED", "INACTIVE"},
                Arrays.stream(AccountStatus.values()).map(Enum::name).toArray());
    }
}
