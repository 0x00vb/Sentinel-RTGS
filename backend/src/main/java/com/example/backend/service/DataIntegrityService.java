package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Data Integrity Testing Service for SOX Compliance Demonstration.
 * Provides controlled data integrity testing capabilities for compliance verification.
 *
 * This service allows authorized personnel to simulate data integrity scenarios
 * in a controlled development environment to demonstrate tamper detection capabilities.
 */
@Service
public class DataIntegrityService {

    private static final Logger logger = LoggerFactory.getLogger(DataIntegrityService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    /**
     * Perform controlled data integrity test.
     * This simulates a data integrity incident for compliance demonstration purposes.
     *
     * Only available in development mode for security reasons.
     */
    @Transactional
    public String performIntegrityTest() {
        if (!devMode) {
            throw new IllegalStateException("Data integrity testing only available in development mode");
        }

        // Find the most recent audit log for testing by getting all and finding the latest
        List<AuditLog> recentLogs = auditLogRepository.findRecentAuditLogs(
            java.time.LocalDateTime.now().minusDays(1)
        );

        if (recentLogs.isEmpty()) {
            return "No recent audit logs available for integrity testing. Send some transactions first.";
        }

        AuditLog latestLog = recentLogs.get(0); // Already ordered by createdAt DESC

        // Store original payload for reference
        String originalPayload = latestLog.getPayload();

        // Simulate data modification (controlled corruption for testing)
        String modifiedPayload = originalPayload.replaceFirst(
            "\"status\"\\s*:\\s*\"[^\"]*\"",
            "\"status\":\"INTEGRITY_TEST_TRIGGERED\""
        );

        // Update the database directly using a custom query to simulate tampering
        auditLogRepository.updatePayloadById(latestLog.getId(), modifiedPayload);

        // Log the integrity test action itself
        auditService.logAudit("system", 0L, "DATA_INTEGRITY_TEST", Map.of(
            "action", "INTEGRITY_TEST_EXECUTED",
            "entityType", latestLog.getEntityType(),
            "entityId", latestLog.getEntityId(),
            "originalPayloadHash", latestLog.getCurrHash(),
            "testMode", "development_only",
            "purpose", "SOX_compliance_demonstration"
        ));

        logger.warn("DATA INTEGRITY TEST: Modified audit log {} for entity {}-{} to demonstrate tamper detection",
                   latestLog.getId(), latestLog.getEntityType(), latestLog.getEntityId());

        return String.format("Data integrity test executed on audit log %d. Hash chain verification should now detect the anomaly.",
                           latestLog.getId());
    }

    /**
     * Verify that integrity testing capabilities are functioning.
     */
    public String verifyIntegrityTestingCapabilities() {
        if (!devMode) {
            return "Data integrity testing not available in production mode";
        }

        long logCount = auditLogRepository.count();
        if (logCount == 0) {
            return "No audit logs available for integrity testing. Send some transactions first.";
        }

        return String.format("Data integrity testing available. %d audit logs ready for testing.", logCount);
    }
}
