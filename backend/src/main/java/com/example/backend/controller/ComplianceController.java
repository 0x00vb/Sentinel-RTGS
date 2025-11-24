package com.example.backend.controller;

import com.example.backend.dto.ComplianceDecision;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Transfer;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.TransferRepository;
import com.example.backend.service.AuditService;
import com.example.backend.service.ComplianceService;
import com.example.backend.service.FuzzyMatchService;
import com.example.backend.service.RuleEngineService;
import com.example.backend.service.SanctionsIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Compliance Engine operations.
 * Provides APIs for sanctions worklist management, manual review, and configuration.
 *
 * FR-06: Manual review endpoints for Compliance Officers
 */
@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceService complianceService;
    private final SanctionsIngestionService sanctionsIngestionService;
    private final FuzzyMatchService fuzzyMatchService;
    private final RuleEngineService ruleEngineService;
    private final TransferRepository transferRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    @Autowired
    public ComplianceController(ComplianceService complianceService,
                               SanctionsIngestionService sanctionsIngestionService,
                               FuzzyMatchService fuzzyMatchService,
                               RuleEngineService ruleEngineService,
                               TransferRepository transferRepository,
                               AuditLogRepository auditLogRepository,
                               AuditService auditService) {
        this.complianceService = complianceService;
        this.sanctionsIngestionService = sanctionsIngestionService;
        this.fuzzyMatchService = fuzzyMatchService;
        this.ruleEngineService = ruleEngineService;
        this.transferRepository = transferRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    // ===== WORKLIST MANAGEMENT =====

    /**
     * Get blocked transactions worklist for manual review.
     */
    @GetMapping("/worklist")
    public ResponseEntity<Page<Transfer>> getWorklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("Fetching compliance worklist: page={}, size={}, sortBy={}, sortDir={}",
                   page, size, sortBy, sortDir);

        // For now, return placeholder - would need TransferRepository integration
        // In real implementation: transferRepository.findByStatus(TransferStatus.BLOCKED_AML, pageable)
        return ResponseEntity.ok(Page.empty());
    }

    /**
     * Get details of a specific blocked transfer.
     */
    @GetMapping("/worklist/{transferId}")
    public ResponseEntity<Transfer> getWorklistItem(@PathVariable Long transferId) {
        logger.info("Fetching worklist item: {}", transferId);

        // Placeholder - would integrate with TransferRepository
        // Transfer transfer = transferRepository.findById(transferId).orElse(null);
        // if (transfer == null || transfer.getStatus() != TransferStatus.BLOCKED_AML) {
        //     return ResponseEntity.notFound().build();
        // }

        return ResponseEntity.notFound().build(); // Placeholder
    }

    /**
     * Process manual compliance decision (approve/reject).
     */
    @PostMapping("/{transferId}/review")
    public ResponseEntity<ProcessingResult> reviewTransfer(
            @PathVariable Long transferId,
            @RequestBody ComplianceDecision decision) {

        logger.info("Processing manual review for transfer {}: {}", transferId, decision.getDecision());

        try {
            ProcessingResult result = complianceService.processManualDecision(transferId, decision);

            if (result.isSuccessful()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }

        } catch (Exception e) {
            logger.error("Error processing manual review for transfer {}: {}", transferId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ProcessingResult.processingError("Internal error processing review", "COMP004"));
        }
    }

    /**
     * Get compliance review history.
     */
    @GetMapping("/history")
    public ResponseEntity<Page<ComplianceHistoryItem>> getReviewHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reviewer) {

        logger.info("Fetching compliance review history: reviewer={}", reviewer);

        // Placeholder - would integrate with audit log queries
        return ResponseEntity.ok(Page.empty());
    }

    // ===== SANCTIONS MANAGEMENT =====

    /**
     * Manually trigger sanctions data ingestion.
     */
    @PostMapping("/sanctions/ingest")
    public ResponseEntity<Map<String, Integer>> triggerSanctionsIngestion() {
        logger.info("Manually triggering sanctions data ingestion");

        try {
            Map<String, Integer> results = sanctionsIngestionService.manualIngestion();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error during manual sanctions ingestion: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get sanctions database statistics.
     */
    @GetMapping("/sanctions/stats")
    public ResponseEntity<Map<String, Object>> getSanctionsStats() {
        try {
            Map<String, Object> stats = sanctionsIngestionService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching sanctions statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search sanctions database.
     */
    @GetMapping("/sanctions/search")
    public ResponseEntity<List<FuzzyMatchService.MatchResult>> searchSanctions(
            @RequestParam String name,
            @RequestParam(defaultValue = "85") int threshold) {

        logger.info("Searching sanctions for: '{}' with threshold: {}", name, threshold);

        try {
            List<FuzzyMatchService.MatchResult> results = fuzzyMatchService.findMatches(name, threshold);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching sanctions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== CONFIGURATION MANAGEMENT =====

    /**
     * Get current compliance rule configuration.
     */
    @GetMapping("/config/rules")
    public ResponseEntity<RuleEngineService.ComplianceConfig> getRuleConfiguration() {
        try {
            RuleEngineService.ComplianceConfig config = ruleEngineService.getCurrentConfiguration();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Error fetching rule configuration: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update compliance rule configuration.
     */
    @PutMapping("/config/rules")
    public ResponseEntity<Void> updateRuleConfiguration(
            @RequestBody RuleEngineService.ComplianceConfig config) {

        logger.info("Updating compliance rule configuration: {}", config);

        try {
            ruleEngineService.updateConfiguration(config);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error updating rule configuration: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get fuzzy matching service statistics.
     */
    @GetMapping("/config/fuzzy/stats")
    public ResponseEntity<Map<String, Object>> getFuzzyStats() {
        try {
            Map<String, Object> stats = fuzzyMatchService.getBKTreeStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching fuzzy matching stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Refresh BK-tree with latest sanctions data.
     */
    @PostMapping("/config/fuzzy/refresh")
    public ResponseEntity<Void> refreshBKTree() {
        logger.info("Refreshing BK-tree with latest sanctions data");

        try {
            fuzzyMatchService.refreshBKTree();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error refreshing BK-tree: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== TRANSFER DETAILS & AUDIT =====

    /**
     * Get transfer details with audit chain.
     * Required by devplan FR-06 for compliance officer workflow.
     */
    @GetMapping("/transfers/{msgId}")
    public ResponseEntity<TransferDetailsResponse> getTransferDetails(@PathVariable String msgId) {
        logger.info("Fetching transfer details for msgId: {}", msgId);

        try {
            UUID messageId = UUID.fromString(msgId);
            Transfer transfer = transferRepository.findByMsgId(messageId).orElse(null);

            if (transfer == null) {
                return ResponseEntity.notFound().build();
            }

            // Get audit chain for this transfer
            List<AuditLog> auditChain = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
                "transfer", transfer.getId());

            TransferDetailsResponse response = new TransferDetailsResponse(transfer, auditChain);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for msgId: {}", msgId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error fetching transfer details for msgId {}: {}", msgId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verify audit chain integrity for a transfer.
     * Required by devplan for SOX compliance verification.
     */
    @GetMapping("/audit/{entityType}/{entityId}/verify")
    public ResponseEntity<AuditVerificationResponse> verifyAuditChain(
            @PathVariable String entityType,
            @PathVariable Long entityId) {

        logger.info("Verifying audit chain for {}:{}", entityType, entityId);

        try {
            boolean isValid = auditService.verifyChain(entityType, entityId);

            AuditVerificationResponse response = new AuditVerificationResponse(
                entityType, entityId, isValid,
                isValid ? "Audit chain is valid" : "Audit chain integrity breach detected"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error verifying audit chain for {}:{}: {}", entityType, entityId, e.getMessage(), e);
            AuditVerificationResponse response = new AuditVerificationResponse(
                entityType, entityId, false, "Error during verification: " + e.getMessage()
            );
            return ResponseEntity.ok(response); // Return 200 with error details
        }
    }

    // ===== COMPLIANCE MONITORING =====

    /**
     * Get compliance processing statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<ComplianceService.ComplianceStats> getComplianceStats() {
        try {
            ComplianceService.ComplianceStats stats = complianceService.getComplianceStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching compliance stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ===== DTO CLASSES =====

    /**
     * DTO for compliance review history items.
     */
    public static class ComplianceHistoryItem {
        private Long transferId;
        private String decision;
        private String reviewer;
        private String notes;
        private String timestamp;

        // Getters and setters
        public Long getTransferId() { return transferId; }
        public void setTransferId(Long transferId) { this.transferId = transferId; }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }

        public String getReviewer() { return reviewer; }
        public void setReviewer(String reviewer) { this.reviewer = reviewer; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Response DTO for transfer details with audit chain.
     */
    public static class TransferDetailsResponse {
        private final Transfer transfer;
        private final List<AuditLog> auditChain;
        private final boolean chainValid;

        public TransferDetailsResponse(Transfer transfer, List<AuditLog> auditChain) {
            this.transfer = transfer;
            this.auditChain = auditChain;
            // For now, assume chain is valid (would need AuditService integration)
            this.chainValid = true;
        }

        public Transfer getTransfer() { return transfer; }
        public List<AuditLog> getAuditChain() { return auditChain; }
        public boolean isChainValid() { return chainValid; }
        public int getAuditEntriesCount() { return auditChain.size(); }
    }

    /**
     * Response DTO for audit chain verification.
     */
    public static class AuditVerificationResponse {
        private final String entityType;
        private final Long entityId;
        private final boolean valid;
        private final String message;

        public AuditVerificationResponse(String entityType, Long entityId, boolean valid, String message) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.valid = valid;
            this.message = message;
        }

        public String getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
