package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditReportService
 */
@ExtendWith(MockitoExtension.class)
class AuditReportServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ScheduledChainVerifier chainVerifier;

    @InjectMocks
    private AuditReportService auditReportService;

    private AuditLog testLog1;
    private AuditLog testLog2;
    private ScheduledChainVerifier.IntegrityStatus mockIntegrityStatus;

    @BeforeEach
    void setUp() {
        testLog1 = new AuditLog("Transfer", 1L, "CREATED", "{\"amount\":\"100.00\"}",
                               "0000000000000000000000000000000000000000000000000000000000000000",
                               "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890");

        testLog2 = new AuditLog("Transfer", 1L, "UPDATED", "{\"status\":\"COMPLETED\"}",
                               "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890",
                               "b2c3d4e5f6789012345678901234567890123456789012345678901234567890");

        mockIntegrityStatus = new ScheduledChainVerifier.IntegrityStatus(100, 2, System.currentTimeMillis(),
            Map.of("DAILY", new ScheduledChainVerifier.VerificationResult(
                LocalDateTime.now(), 50, 1, 5000L, true)));
    }

    @Test
    void shouldGenerateIntegrityStatusReport() {
        // Given
        when(chainVerifier.getIntegrityStatus()).thenReturn(mockIntegrityStatus);

        // When
        AuditReportService.IntegrityReport report = auditReportService.getIntegrityStatusReport();

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getTotalVerifications()).isEqualTo(100);
        assertThat(report.getTotalBreaches()).isEqualTo(2);
        assertThat(report.isSystemIntegrityIntact()).isFalse();
        assertThat(report.getLastDailyResult()).isNotNull();
    }

    @Test
    void shouldGenerateActivityReport() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 7);
        List<AuditLog> logs = Arrays.asList(testLog1, testLog2);

        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(logs);

        // When
        AuditReportService.ActivityReport report = auditReportService.getActivityReport(startDate, endDate);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getStartDate()).isEqualTo(startDate);
        assertThat(report.getEndDate()).isEqualTo(endDate);
        assertThat(report.getTotalActivities()).isEqualTo(2);
        assertThat(report.getActivityByEntityType()).containsKey("Transfer");
        assertThat(report.getActivityByAction()).containsKey("CREATED");
    }

    @Test
    void shouldGenerateEntityAuditTrail() {
        // Given
        List<AuditLog> logs = Arrays.asList(testLog1, testLog2);
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("Transfer", 1L)).thenReturn(logs);

        // When
        AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail("Transfer", 1L);

        // Then
        assertThat(trail).isNotNull();
        assertThat(trail.getEntityType()).isEqualTo("Transfer");
        assertThat(trail.getEntityId()).isEqualTo(1L);
        assertThat(trail.getTotalEntries()).isEqualTo(2);
        assertThat(trail.getEntries()).hasSize(2);
    }

    @Test
    void shouldGenerateComplianceReport() {
        // Given
        LocalDate reportDate = LocalDate.now();
        when(auditLogRepository.countByCreatedAtBetween(any(), any())).thenReturn(25L);
        when(auditLogRepository.countTotalAuditEntries()).thenReturn(1000L);
        when(chainVerifier.getIntegrityStatus()).thenReturn(mockIntegrityStatus);
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer", "Account"));

        // When
        AuditReportService.ComplianceReport report = auditReportService.getComplianceReport(reportDate);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getReportDate()).isEqualTo(reportDate);
        assertThat(report.getDailyAuditEntries()).isEqualTo(25L);
        assertThat(report.getTotalAuditEntries()).isEqualTo(1000L);
        assertThat(report.isIntegrityIntact()).isFalse();
    }

    @Test
    void shouldGenerateSystemHealthReport() {
        // Given
        when(auditLogRepository.countTotalAuditEntries()).thenReturn(500L);
        when(auditLogRepository.countByCreatedAtAfter(any())).thenReturn(50L);
        when(chainVerifier.getIntegrityStatus()).thenReturn(mockIntegrityStatus);

        // When
        AuditReportService.SystemHealthReport report = auditReportService.getSystemHealthReport();

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getTotalAuditEntries()).isEqualTo(500L);
        assertThat(report.getDailyGrowthRate()).isEqualTo(50.0 / 30.0); // 50 entries over 30 days
        assertThat(report.getHealthStatus()).isEqualTo("ISSUES_DETECTED");
        assertThat(report.getIssues()).contains("INTEGRITY_BREACHES_DETECTED");
    }

    @Test
    void shouldReportHealthySystem() {
        // Given
        ScheduledChainVerifier.IntegrityStatus healthyStatus =
            new ScheduledChainVerifier.IntegrityStatus(100, 0, System.currentTimeMillis(),
                Collections.emptyMap());

        when(auditLogRepository.countTotalAuditEntries()).thenReturn(1000L);
        when(auditLogRepository.countByCreatedAtAfter(any())).thenReturn(100L);
        when(chainVerifier.getIntegrityStatus()).thenReturn(healthyStatus);

        // When
        AuditReportService.SystemHealthReport report = auditReportService.getSystemHealthReport();

        // Then
        assertThat(report.getHealthStatus()).isEqualTo("HEALTHY");
        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void shouldHandleEmptyActivityReport() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 2);

        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
            .thenReturn(Collections.emptyList());

        // When
        AuditReportService.ActivityReport report = auditReportService.getActivityReport(startDate, endDate);

        // Then
        assertThat(report.getTotalActivities()).isEqualTo(0);
        assertThat(report.getActivityByEntityType()).isEmpty();
        assertThat(report.getActivityByAction()).isEmpty();
    }

    @Test
    void shouldHandleEmptyEntityAuditTrail() {
        // Given
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("Transfer", 1L))
            .thenReturn(Collections.emptyList());

        // When
        AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail("Transfer", 1L);

        // Then
        assertThat(trail.getTotalEntries()).isEqualTo(0);
        assertThat(trail.getEntries()).isEmpty();
    }

    @Test
    void shouldCreateProperAuditTrailEntry() {
        // Given
        AuditLog log = testLog1;

        // When
        AuditReportService.EntityAuditTrail trail = auditReportService.getEntityAuditTrail("Transfer", 1L);
        AuditReportService.AuditTrailEntry entry = trail.getEntries().get(0);

        // Then
        assertThat(entry.getId()).isEqualTo(log.getId());
        assertThat(entry.getAction()).isEqualTo(log.getAction());
        assertThat(entry.getPayload()).isEqualTo(log.getPayload());
        assertThat(entry.getPreviousHash()).isEqualTo(log.getPrevHash());
        assertThat(entry.getCurrentHash()).isEqualTo(log.getCurrHash());
    }

    @Test
    void shouldHandleZeroTotalAuditEntries() {
        // Given
        when(auditLogRepository.countTotalAuditEntries()).thenReturn(0L);
        when(chainVerifier.getIntegrityStatus()).thenReturn(mockIntegrityStatus);

        // When
        AuditReportService.SystemHealthReport report = auditReportService.getSystemHealthReport();

        // Then
        assertThat(report.getIssues()).contains("NO_AUDIT_ENTRIES_FOUND");
        assertThat(report.getHealthStatus()).isEqualTo("ISSUES_DETECTED");
    }
}
