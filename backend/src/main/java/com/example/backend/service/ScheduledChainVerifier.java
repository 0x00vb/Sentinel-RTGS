package com.example.backend.service;

import com.example.backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduled service for continuous verification of audit hash chains.
 * Implements SOX compliance requirement FR-11 for tamper detection.
 *
 * Features:
 * - Hourly integrity checks for active entities
 * - Daily comprehensive verification of all chains
 * - Alert system for integrity breaches
 * - Performance metrics and monitoring
 * - Configurable verification scope
 */
@Service
@EnableScheduling
public class ScheduledChainVerifier {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledChainVerifier.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    // Metrics for monitoring
    private final AtomicLong totalVerifications = new AtomicLong(0);
    private final AtomicLong integrityBreaches = new AtomicLong(0);
    private final AtomicLong lastVerificationTime = new AtomicLong(System.currentTimeMillis());

    // Track verification results for reporting
    private final Map<String, VerificationResult> lastVerificationResults = new ConcurrentHashMap<>();

    /**
     * Hourly verification of recently active entities.
     * Runs every hour to catch integrity issues quickly.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3,600,000 milliseconds
    public void hourlyIntegrityCheck() {
        logger.info("Starting hourly audit chain integrity check");

        try {
            long startTime = System.currentTimeMillis();

            // Get entities that have been active in the last 24 hours
            LocalDateTime since = LocalDateTime.now().minusDays(1);
            List<String> activeEntityTypes = auditLogRepository.findRecentEntityTypes(since);

            int totalEntities = 0;
            int breachedChains = 0;

            for (String entityType : activeEntityTypes) {
                List<Long> entityIds = auditLogRepository.findRecentEntityIds(entityType, since);

                for (Long entityId : entityIds) {
                    totalEntities++;
                    boolean isValid = auditService.verifyChain(entityType, entityId);

                    if (!isValid) {
                        breachedChains++;
                        logIntegrityBreach(entityType, entityId);
                        alertIntegrityBreach(entityType, entityId);
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            updateMetrics(totalEntities, breachedChains);

            logger.info("Hourly integrity check completed: {} entities verified, {} breaches found, {}ms",
                       totalEntities, breachedChains, duration);

            // Audit the verification itself
            Map<String, Object> verificationPayload = Map.of(
                "verificationType", "HOURLY",
                "entitiesVerified", totalEntities,
                "breachesFound", breachedChains,
                "durationMs", duration,
                "timestamp", LocalDateTime.now()
            );

            auditService.logAudit("AuditSystem", 0L, "CHAIN_VERIFICATION_HOURLY", verificationPayload);

        } catch (Exception e) {
            logger.error("Failed to complete hourly integrity check", e);
            alertSystemError("HOURLY_VERIFICATION_FAILED", e.getMessage());
        }
    }

    /**
     * Daily comprehensive verification of all audit chains.
     * Runs at 2 AM daily for thorough integrity checking.
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2:00 AM
    public void dailyComprehensiveVerification() {
        logger.info("Starting daily comprehensive audit chain verification");

        try {
            long startTime = System.currentTimeMillis();

            // Get all unique entity types and IDs that have audit logs
            List<String> allEntityTypes = auditLogRepository.findAllEntityTypes();

            int totalEntities = 0;
            int breachedChains = 0;
            int totalChains = 0;

            for (String entityType : allEntityTypes) {
                List<Long> allEntityIds = auditLogRepository.findAllEntityIdsForType(entityType);

                for (Long entityId : allEntityIds) {
                    totalChains++;
                    boolean isValid = auditService.verifyChain(entityType, entityId);

                    if (!isValid) {
                        breachedChains++;
                        logIntegrityBreach(entityType, entityId);
                        alertIntegrityBreach(entityType, entityId);
                    }
                }

                totalEntities += allEntityIds.size();
            }

            long duration = System.currentTimeMillis() - startTime;
            updateMetrics(totalEntities, breachedChains);

            logger.info("Daily comprehensive verification completed: {} chains verified across {} entities, {} breaches found, {}ms",
                       totalChains, totalEntities, breachedChains, duration);

            // Store verification results for reporting
            VerificationResult result = new VerificationResult(
                LocalDateTime.now(), totalChains, breachedChains, duration, true
            );
            lastVerificationResults.put("DAILY", result);

            // Audit the verification itself
            Map<String, Object> verificationPayload = Map.of(
                "verificationType", "DAILY_COMPREHENSIVE",
                "chainsVerified", totalChains,
                "entitiesVerified", totalEntities,
                "breachesFound", breachedChains,
                "durationMs", duration,
                "timestamp", LocalDateTime.now()
            );

            auditService.logAudit("AuditSystem", 0L, "CHAIN_VERIFICATION_DAILY", verificationPayload);

        } catch (Exception e) {
            logger.error("Failed to complete daily comprehensive verification", e);
            alertSystemError("DAILY_VERIFICATION_FAILED", e.getMessage());
        }
    }

    /**
     * Manual trigger for chain verification (for admin operations).
     */
    public VerificationResult verifyAllChains() {
        logger.info("Starting manual comprehensive audit chain verification");

        long startTime = System.currentTimeMillis();
        List<String> allEntityTypes = auditLogRepository.findAllEntityTypes();

        int totalChains = 0;
        int breachedChains = 0;

        for (String entityType : allEntityTypes) {
            List<Long> allEntityIds = auditLogRepository.findAllEntityIdsForType(entityType);

            for (Long entityId : allEntityIds) {
                totalChains++;
                boolean isValid = auditService.verifyChain(entityType, entityId);

                if (!isValid) {
                    breachedChains++;
                    logIntegrityBreach(entityType, entityId);
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        VerificationResult result = new VerificationResult(
            LocalDateTime.now(), totalChains, breachedChains, duration, false
        );

        logger.info("Manual verification completed: {} chains verified, {} breaches found, {}ms",
                   totalChains, breachedChains, duration);

        return result;
    }

    /**
     * Get current integrity status and metrics.
     */
    public IntegrityStatus getIntegrityStatus() {
        return new IntegrityStatus(
            totalVerifications.get(),
            integrityBreaches.get(),
            lastVerificationTime.get(),
            new ConcurrentHashMap<>(lastVerificationResults)
        );
    }

    private void logIntegrityBreach(String entityType, Long entityId) {
        logger.error("INTEGRITY BREACH DETECTED: Entity {}/{} has broken hash chain", entityType, entityId);
    }

    private void alertIntegrityBreach(String entityType, Long entityId) {
        // TODO: Integrate with alerting system (email, Slack, monitoring dashboard)
        // For now, just log with high severity
        logger.error("CRITICAL: Data integrity breach in {}/{} - potential tampering detected", entityType, entityId);

        // Could send to monitoring system, trigger alerts, etc.
        // alertService.sendAlert("INTEGRITY_BREACH", Map.of("entityType", entityType, "entityId", entityId));
    }

    private void alertSystemError(String errorType, String message) {
        logger.error("SYSTEM ERROR in chain verification: {} - {}", errorType, message);
        // Could integrate with error monitoring systems
    }

    private void updateMetrics(int entitiesVerified, int breachesFound) {
        totalVerifications.addAndGet(entitiesVerified);
        integrityBreaches.addAndGet(breachesFound);
        lastVerificationTime.set(System.currentTimeMillis());
    }

    /**
     * Data class for verification results.
     */
    public static class VerificationResult {
        private final LocalDateTime timestamp;
        private final int chainsVerified;
        private final int breachesFound;
        private final long durationMs;
        private final boolean isScheduled;

        public VerificationResult(LocalDateTime timestamp, int chainsVerified, int breachesFound,
                                long durationMs, boolean isScheduled) {
            this.timestamp = timestamp;
            this.chainsVerified = chainsVerified;
            this.breachesFound = breachesFound;
            this.durationMs = durationMs;
            this.isScheduled = isScheduled;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getChainsVerified() { return chainsVerified; }
        public int getBreachesFound() { return breachesFound; }
        public long getDurationMs() { return durationMs; }
        public boolean isScheduled() { return isScheduled; }
        public double getBreachRate() {
            return chainsVerified > 0 ? (double) breachesFound / chainsVerified : 0.0;
        }
    }

    /**
     * Data class for overall integrity status.
     */
    public static class IntegrityStatus {
        private final long totalVerifications;
        private final long totalBreaches;
        private final long lastVerificationTime;
        private final Map<String, VerificationResult> lastResults;

        public IntegrityStatus(long totalVerifications, long totalBreaches, long lastVerificationTime,
                             Map<String, VerificationResult> lastResults) {
            this.totalVerifications = totalVerifications;
            this.totalBreaches = totalBreaches;
            this.lastVerificationTime = lastVerificationTime;
            this.lastResults = lastResults;
        }

        // Getters
        public long getTotalVerifications() { return totalVerifications; }
        public long getTotalBreaches() { return totalBreaches; }
        public long getLastVerificationTime() { return lastVerificationTime; }
        public Map<String, VerificationResult> getLastResults() { return lastResults; }
        public boolean hasIntegrityBreaches() { return totalBreaches > 0; }
    }
}
