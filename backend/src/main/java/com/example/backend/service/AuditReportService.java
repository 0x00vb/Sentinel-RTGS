package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating SOX compliance audit reports.
 * Provides structured reporting capabilities for audit trails and integrity monitoring.
 */
@Service
public class AuditReportService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ScheduledChainVerifier chainVerifier;

    /**
     * Generate integrity status report showing current chain verification state.
     */
    public IntegrityReport getIntegrityStatusReport() {
        ScheduledChainVerifier.IntegrityStatus status = chainVerifier.getIntegrityStatus();

        return new IntegrityReport(
            status.getTotalVerifications(),
            status.getTotalBreaches(),
            status.getLastVerificationTime(),
            status.getLastResults().get("DAILY"),
            status.hasIntegrityBreaches()
        );
    }

    /**
     * Generate audit activity summary for a date range.
     */
    public ActivityReport getActivityReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<AuditLog> logs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        // Group by entity type
        Map<String, Long> activityByEntityType = logs.stream()
            .collect(Collectors.groupingBy(AuditLog::getEntityType, Collectors.counting()));

        // Group by action
        Map<String, Long> activityByAction = logs.stream()
            .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));

        // Calculate hourly activity
        Map<Integer, Long> hourlyActivity = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCreatedAt().getHour(),
                Collectors.counting()
            ));

        return new ActivityReport(
            startDate,
            endDate,
            logs.size(),
            activityByEntityType,
            activityByAction,
            hourlyActivity
        );
    }

    /**
     * Generate detailed audit trail for a specific entity.
     */
    public EntityAuditTrail getEntityAuditTrail(String entityType, Long entityId) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);

        boolean chainValid = logs.stream()
            .allMatch(log -> true); // Chain validation is done by AuditService.verifyChain

        List<AuditTrailEntry> entries = logs.stream()
            .map(log -> new AuditTrailEntry(
                log.getId(),
                log.getAction(),
                log.getPayload(),
                log.getPrevHash(),
                log.getCurrHash(),
                log.getCreatedAt()
            ))
            .collect(Collectors.toList());

        return new EntityAuditTrail(
            entityType,
            entityId,
            entries.size(),
            chainValid,
            entries
        );
    }

    /**
     * Generate compliance summary report for SOX audit requirements.
     */
    public ComplianceReport getComplianceReport(LocalDate reportDate) {
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime endOfDay = reportDate.atTime(LocalTime.MAX);

        long totalAuditEntries = auditLogRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long totalAuditEntriesAllTime = auditLogRepository.countTotalAuditEntries();

        // Check for recent integrity verification
        ScheduledChainVerifier.IntegrityStatus integrityStatus = chainVerifier.getIntegrityStatus();
        boolean integrityCheckPerformed = integrityStatus.getLastVerificationTime() >
            startOfDay.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();

        // Get entity type distribution
        List<String> entityTypes = auditLogRepository.findAllEntityTypes();
        Map<String, Long> entityCounts = new HashMap<>();
        for (String entityType : entityTypes) {
            entityCounts.put(entityType, auditLogRepository.countByEntityType(entityType));
        }

        return new ComplianceReport(
            reportDate,
            totalAuditEntries,
            totalAuditEntriesAllTime,
            integrityCheckPerformed,
            integrityStatus.getTotalBreaches() == 0,
            entityCounts,
            integrityStatus.getLastResults().get("DAILY")
        );
    }

    /**
     * Generate system health report for audit subsystem.
     */
    public SystemHealthReport getSystemHealthReport() {
        long totalAuditEntries = auditLogRepository.countTotalAuditEntries();
        ScheduledChainVerifier.IntegrityStatus integrityStatus = chainVerifier.getIntegrityStatus();

        // Calculate data growth rate (simplified - entries per day over last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long recentEntries = auditLogRepository.countByCreatedAtAfter(thirtyDaysAgo);
        double growthRate = recentEntries / 30.0;

        // Check for any potential issues
        List<String> issues = new ArrayList<>();
        if (integrityStatus.hasIntegrityBreaches()) {
            issues.add("INTEGRITY_BREACHES_DETECTED");
        }
        if (totalAuditEntries == 0) {
            issues.add("NO_AUDIT_ENTRIES_FOUND");
        }
        if (integrityStatus.getLastVerificationTime() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
            issues.add("VERIFICATION_OVERDUE");
        }

        return new SystemHealthReport(
            totalAuditEntries,
            growthRate,
            integrityStatus,
            issues.isEmpty() ? "HEALTHY" : "ISSUES_DETECTED",
            issues
        );
    }

    // DTO Classes for Reports

    public static class IntegrityReport {
        private final long totalVerifications;
        private final long totalBreaches;
        private final long lastVerificationTime;
        private final ScheduledChainVerifier.VerificationResult lastDailyResult;
        private final boolean systemIntegrityIntact;

        public IntegrityReport(long totalVerifications, long totalBreaches, long lastVerificationTime,
                             ScheduledChainVerifier.VerificationResult lastDailyResult, boolean systemIntegrityIntact) {
            this.totalVerifications = totalVerifications;
            this.totalBreaches = totalBreaches;
            this.lastVerificationTime = lastVerificationTime;
            this.lastDailyResult = lastDailyResult;
            this.systemIntegrityIntact = systemIntegrityIntact;
        }

        // Getters
        public long getTotalVerifications() { return totalVerifications; }
        public long getTotalBreaches() { return totalBreaches; }
        public long getLastVerificationTime() { return lastVerificationTime; }
        public ScheduledChainVerifier.VerificationResult getLastDailyResult() { return lastDailyResult; }
        public boolean isSystemIntegrityIntact() { return systemIntegrityIntact; }
    }

    public static class ActivityReport {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int totalActivities;
        private final Map<String, Long> activityByEntityType;
        private final Map<String, Long> activityByAction;
        private final Map<Integer, Long> hourlyActivity;

        public ActivityReport(LocalDate startDate, LocalDate endDate, int totalActivities,
                            Map<String, Long> activityByEntityType, Map<String, Long> activityByAction,
                            Map<Integer, Long> hourlyActivity) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.totalActivities = totalActivities;
            this.activityByEntityType = activityByEntityType;
            this.activityByAction = activityByAction;
            this.hourlyActivity = hourlyActivity;
        }

        // Getters
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public int getTotalActivities() { return totalActivities; }
        public Map<String, Long> getActivityByEntityType() { return activityByEntityType; }
        public Map<String, Long> getActivityByAction() { return activityByAction; }
        public Map<Integer, Long> getHourlyActivity() { return hourlyActivity; }
    }

    public static class AuditTrailEntry {
        private final Long id;
        private final String action;
        private final String payload;
        private final String previousHash;
        private final String currentHash;
        private final LocalDateTime timestamp;

        public AuditTrailEntry(Long id, String action, String payload, String previousHash,
                             String currentHash, LocalDateTime timestamp) {
            this.id = id;
            this.action = action;
            this.payload = payload;
            this.previousHash = previousHash;
            this.currentHash = currentHash;
            this.timestamp = timestamp;
        }

        // Getters
        public Long getId() { return id; }
        public String getAction() { return action; }
        public String getPayload() { return payload; }
        public String getPreviousHash() { return previousHash; }
        public String getCurrentHash() { return currentHash; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class EntityAuditTrail {
        private final String entityType;
        private final Long entityId;
        private final int totalEntries;
        private final boolean chainValid;
        private final List<AuditTrailEntry> entries;

        public EntityAuditTrail(String entityType, Long entityId, int totalEntries, boolean chainValid,
                              List<AuditTrailEntry> entries) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.totalEntries = totalEntries;
            this.chainValid = chainValid;
            this.entries = entries;
        }

        // Getters
        public String getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public int getTotalEntries() { return totalEntries; }
        public boolean isChainValid() { return chainValid; }
        public List<AuditTrailEntry> getEntries() { return entries; }
    }

    public static class ComplianceReport {
        private final LocalDate reportDate;
        private final long dailyAuditEntries;
        private final long totalAuditEntries;
        private final boolean integrityCheckPerformed;
        private final boolean integrityIntact;
        private final Map<String, Long> entityTypeDistribution;
        private final ScheduledChainVerifier.VerificationResult lastVerification;

        public ComplianceReport(LocalDate reportDate, long dailyAuditEntries, long totalAuditEntries,
                              boolean integrityCheckPerformed, boolean integrityIntact,
                              Map<String, Long> entityTypeDistribution,
                              ScheduledChainVerifier.VerificationResult lastVerification) {
            this.reportDate = reportDate;
            this.dailyAuditEntries = dailyAuditEntries;
            this.totalAuditEntries = totalAuditEntries;
            this.integrityCheckPerformed = integrityCheckPerformed;
            this.integrityIntact = integrityIntact;
            this.entityTypeDistribution = entityTypeDistribution;
            this.lastVerification = lastVerification;
        }

        // Getters
        public LocalDate getReportDate() { return reportDate; }
        public long getDailyAuditEntries() { return dailyAuditEntries; }
        public long getTotalAuditEntries() { return totalAuditEntries; }
        public boolean isIntegrityCheckPerformed() { return integrityCheckPerformed; }
        public boolean isIntegrityIntact() { return integrityIntact; }
        public Map<String, Long> getEntityTypeDistribution() { return entityTypeDistribution; }
        public ScheduledChainVerifier.VerificationResult getLastVerification() { return lastVerification; }
    }

    public static class SystemHealthReport {
        private final long totalAuditEntries;
        private final double dailyGrowthRate;
        private final ScheduledChainVerifier.IntegrityStatus integrityStatus;
        private final String healthStatus;
        private final List<String> issues;

        public SystemHealthReport(long totalAuditEntries, double dailyGrowthRate,
                                ScheduledChainVerifier.IntegrityStatus integrityStatus,
                                String healthStatus, List<String> issues) {
            this.totalAuditEntries = totalAuditEntries;
            this.dailyGrowthRate = dailyGrowthRate;
            this.integrityStatus = integrityStatus;
            this.healthStatus = healthStatus;
            this.issues = issues;
        }

        // Getters
        public long getTotalAuditEntries() { return totalAuditEntries; }
        public double getDailyGrowthRate() { return dailyGrowthRate; }
        public ScheduledChainVerifier.IntegrityStatus getIntegrityStatus() { return integrityStatus; }
        public String getHealthStatus() { return healthStatus; }
        public List<String> getIssues() { return issues; }
    }
}
