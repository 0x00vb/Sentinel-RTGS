package com.example.backend.controller;

import com.example.backend.dto.ComplianceDecision;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Transfer;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.TransferRepository;
import com.example.backend.service.AuditService;
import com.example.backend.service.ComplianceService;
import com.example.backend.service.DataIntegrityService;
import com.example.backend.service.FuzzyMatchService;
import com.example.backend.service.RuleEngineService;
import com.example.backend.service.SanctionsIngestionService;
import com.example.backend.service.TrafficSimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final DataIntegrityService dataIntegrityService;
    private final TrafficSimulatorService trafficSimulatorService;

    @Autowired
    public ComplianceController(ComplianceService complianceService,
                               SanctionsIngestionService sanctionsIngestionService,
                               FuzzyMatchService fuzzyMatchService,
                               RuleEngineService ruleEngineService,
                               TransferRepository transferRepository,
                               AuditLogRepository auditLogRepository,
                               AuditService auditService,
                               DataIntegrityService dataIntegrityService,
                               TrafficSimulatorService trafficSimulatorService) {
        this.complianceService = complianceService;
        this.sanctionsIngestionService = sanctionsIngestionService;
        this.fuzzyMatchService = fuzzyMatchService;
        this.ruleEngineService = ruleEngineService;
        this.transferRepository = transferRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.dataIntegrityService = dataIntegrityService;
        this.trafficSimulatorService = trafficSimulatorService;
    }

    // ===== WORKLIST MANAGEMENT =====

    /**
     * Get blocked transactions worklist for manual review.
     */
    @GetMapping("/worklist")
    public ResponseEntity<Page<WorklistItemDTO>> getWorklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        logger.info("Fetching compliance worklist: page={}, size={}, sortBy={}, sortDir={}",
                   page, size, sortBy, sortDir);

        try {
            // Create pageable with sorting
            Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            // Get blocked transfers with pagination
            List<Transfer> blockedTransfers = transferRepository.findByStatus(Transfer.TransferStatus.BLOCKED_AML);
            
            // Apply pagination manually since we don't have Pageable support in the repository method
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), blockedTransfers.size());
            List<Transfer> pagedTransfers = start < blockedTransfers.size() 
                ? blockedTransfers.subList(start, end) 
                : new ArrayList<>();

            // Convert to DTOs with compliance data from audit logs
            List<WorklistItemDTO> worklistItems = new ArrayList<>();
            ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

            for (Transfer transfer : pagedTransfers) {
                WorklistItemDTO dto = createWorklistItemDTO(transfer, objectMapper);
                worklistItems.add(dto);
            }

            // Create Page response
            Page<WorklistItemDTO> result = new org.springframework.data.domain.PageImpl<>(
                worklistItems, 
                pageable, 
                blockedTransfers.size()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error fetching compliance worklist: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create WorklistItemDTO from Transfer and extract compliance data from audit logs.
     */
    private WorklistItemDTO createWorklistItemDTO(Transfer transfer, ObjectMapper objectMapper) {
        WorklistItemDTO dto = new WorklistItemDTO();
        
        // Basic transfer data
        dto.setId("PAY-" + transfer.getId());
        dto.setTransferId(transfer.getId());
        dto.setTimestamp(transfer.getCreatedAt().toString());
        dto.setAmount(transfer.getAmount().doubleValue());
        dto.setCurrency(transfer.getSource() != null ? transfer.getSource().getCurrency() : "USD");
        dto.setStatus("blocked_aml");
        dto.setPipelineStage("risk_check");
        
        // Get sender/receiver info
        if (transfer.getSource() != null) {
            dto.setSenderName(transfer.getSource().getOwnerName());
            dto.setSenderBIC(extractBICFromIBAN(transfer.getSenderIban()));
        }
        if (transfer.getDestination() != null) {
            dto.setReceiverBIC(extractBICFromIBAN(transfer.getReceiverIban()));
        }

        // Extract compliance data from audit logs
        List<AuditLog> complianceLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
            "transfer", transfer.getId());
        
        // Find the most recent compliance evaluation log
        AuditLog complianceLog = complianceLogs.stream()
            .filter(log -> log.getAction().startsWith("COMPLIANCE_"))
            .reduce((first, second) -> second)
            .orElse(null);

        if (complianceLog != null) {
            try {
                // Parse JSON payload
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) objectMapper.readValue(
                    complianceLog.getPayload(), 
                    Map.class
                );

                // Extract risk score from best match similarity
                @SuppressWarnings("unchecked")
                Map<String, Object> bestMatch = (Map<String, Object>) payload.get("bestMatch");
                if (bestMatch != null && bestMatch.get("similarityScore") != null) {
                    double similarity = ((Number) bestMatch.get("similarityScore")).doubleValue();
                    dto.setRiskScore((int) Math.round(similarity));
                } else {
                    // Default risk score based on decision
                    String decision = (String) payload.get("decision");
                    dto.setRiskScore(decision != null && decision.contains("BLOCKED") ? 85 : 50);
                }

                // Extract watchlist match info
                if (bestMatch != null) {
                    String sanctionName = (String) bestMatch.get("sanctionName");
                    String source = (String) bestMatch.get("source");
                    if (sanctionName != null) {
                        dto.setWatchlistMatch(sanctionName + (source != null ? " (" + source + ")" : ""));
                    }
                } else {
                    String reason = (String) payload.get("reason");
                    dto.setWatchlistMatch(reason != null ? reason : "Compliance Review Required");
                }

                // Build evidence array
                List<Map<String, String>> evidence = new ArrayList<>();
                
                if (bestMatch != null) {
                    double similarity = ((Number) bestMatch.getOrDefault("similarityScore", 0)).doubleValue();
                    Map<String, String> nameEvidence = new HashMap<>();
                    nameEvidence.put("type", "name_similarity");
                    nameEvidence.put("value", String.format("%.0f%%", similarity));
                    nameEvidence.put("description", String.format("Name similarity match: %.1f%%", similarity));
                    evidence.add(nameEvidence);

                    String source = (String) bestMatch.get("source");
                    if (source != null) {
                        Map<String, String> sourceEvidence = new HashMap<>();
                        sourceEvidence.put("type", "sanctions_source");
                        sourceEvidence.put("value", source);
                        sourceEvidence.put("description", "Match found in " + source + " sanctions list");
                        evidence.add(sourceEvidence);
                    }
                }

                // Add amount threshold check
                if (transfer.getAmount().doubleValue() > 2500000) {
                    Map<String, String> amountEvidence = new HashMap<>();
                    amountEvidence.put("type", "amount_threshold");
                    amountEvidence.put("value", "Exceeded");
                    amountEvidence.put("description", "Amount exceeds AML threshold of $2.5M");
                    evidence.add(amountEvidence);
                }

                String decision = (String) payload.get("decision");
                if (decision != null && decision.contains("BLOCKED")) {
                    Map<String, String> decisionEvidence = new HashMap<>();
                    decisionEvidence.put("type", "compliance_decision");
                    decisionEvidence.put("value", "BLOCKED");
                    decisionEvidence.put("description", payload.get("reason") != null ? 
                        (String) payload.get("reason") : "Transaction blocked by compliance engine");
                    evidence.add(decisionEvidence);
                }

                dto.setEvidence(evidence);

                // Determine urgency based on risk score
                if (dto.getRiskScore() >= 85) {
                    dto.setUrgency("high");
                } else if (dto.getRiskScore() >= 70) {
                    dto.setUrgency("medium");
                } else {
                    dto.setUrgency("low");
                }

            } catch (Exception e) {
                logger.warn("Error parsing compliance audit log for transfer {}: {}", 
                    transfer.getId(), e.getMessage());
                // Set defaults
                dto.setRiskScore(75);
                dto.setWatchlistMatch("Compliance Review Required");
                dto.setUrgency("medium");
                dto.setEvidence(new ArrayList<>());
            }
        } else {
            // No compliance log found, set defaults
            dto.setRiskScore(75);
            dto.setWatchlistMatch("Compliance Review Required");
            dto.setUrgency("medium");
            dto.setEvidence(new ArrayList<>());
        }

        return dto;
    }

    /**
     * Extract BIC from IBAN (simplified - in real implementation would use proper BIC lookup).
     */
    private String extractBICFromIBAN(String iban) {
        if (iban == null || iban.length() < 4) {
            return "UNKNOWN";
        }
        // Simplified: use first 4 chars as country code, then generate a mock BIC
        String countryCode = iban.substring(0, 2);
        String bankCode = iban.length() > 4 ? iban.substring(4, 8) : "XXXX";
        return countryCode + bankCode + "XX";
    }

    /**
     * Get details of a specific blocked transfer.
     */
    @GetMapping("/worklist/{transferId}")
    public ResponseEntity<WorklistItemDTO> getWorklistItem(@PathVariable Long transferId) {
        logger.info("Fetching worklist item: {}", transferId);

        try {
            Transfer transfer = transferRepository.findById(transferId).orElse(null);
            
            if (transfer == null || transfer.getStatus() != Transfer.TransferStatus.BLOCKED_AML) {
                return ResponseEntity.notFound().build();
            }

            ObjectMapper objectMapper = new ObjectMapper();
            WorklistItemDTO dto = createWorklistItemDTO(transfer, objectMapper);
            
            return ResponseEntity.ok(dto);
            
        } catch (Exception e) {
            logger.error("Error fetching worklist item {}: {}", transferId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
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

    // ===== DATA INTEGRITY TESTING =====

    /**
     * Perform controlled data integrity test for SOX compliance demonstration.
     * Only available in development mode.
     */
    @PostMapping("/integrity/test")
    public ResponseEntity<Map<String, Object>> performDataIntegrityTest() {
        logger.info("Performing controlled data integrity test");

        try {
            String result = dataIntegrityService.performIntegrityTest();

            return ResponseEntity.ok(Map.of(
                "status", "test_executed",
                "message", result,
                "recommendation", "Run audit chain verification to confirm tamper detection is working"
            ));

        } catch (Exception e) {
            logger.error("Error performing data integrity test", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Data integrity test failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Check data integrity testing capabilities.
     */
    @GetMapping("/integrity/status")
    public ResponseEntity<Map<String, Object>> getDataIntegrityStatus() {
        try {
            String status = dataIntegrityService.verifyIntegrityTestingCapabilities();

            return ResponseEntity.ok(Map.of(
                "status", "available",
                "message", status
            ));

        } catch (Exception e) {
            logger.error("Error checking data integrity status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to check integrity status: " + e.getMessage()
            ));
        }
    }

    // ===== TRAFFIC SIMULATION =====

    /**
     * Start traffic simulation (alias for /api/v1/simulation/start).
     */
    @PostMapping("/simulation/start")
    public ResponseEntity<Map<String, Object>> startTrafficSimulation(
            @RequestParam(defaultValue = "5") int messagesPerSecond) {

        logger.info("Starting traffic simulation via compliance endpoint");

        try {
            String result = trafficSimulatorService.startSimulation(messagesPerSecond);

            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", result,
                "messagesPerSecond", messagesPerSecond
            ));

        } catch (Exception e) {
            logger.error("Error starting traffic simulation", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to start simulation: " + e.getMessage()
            ));
        }
    }

    /**
     * Stop traffic simulation (alias for /api/v1/simulation/stop).
     */
    @PostMapping("/simulation/stop")
    public ResponseEntity<Map<String, Object>> stopTrafficSimulation() {

        logger.info("Stopping traffic simulation via compliance endpoint");

        try {
            String result = trafficSimulatorService.stopSimulation();

            return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", result
            ));

        } catch (Exception e) {
            logger.error("Error stopping traffic simulation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to stop simulation: " + e.getMessage()
            ));
        }
    }

    /**
     * Get traffic simulation status (alias for /api/v1/simulation/status).
     */
    @GetMapping("/simulation/status")
    public ResponseEntity<Map<String, Object>> getTrafficSimulationStatus() {

        try {
            Map<String, Object> status = trafficSimulatorService.getSimulationStatus();

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error getting traffic simulation status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get simulation status: " + e.getMessage()
            ));
        }
    }

    // ===== DTO CLASSES =====

    /**
     * DTO for worklist items with compliance data.
     */
    public static class WorklistItemDTO {
        private String id;
        private Long transferId;
        private String timestamp;
        private String senderBIC;
        private String receiverBIC;
        private double amount;
        private String currency;
        private String status;
        private String pipelineStage;
        private int riskScore;
        private String senderName;
        private String watchlistMatch;
        private List<Map<String, String>> evidence;
        private String urgency;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public Long getTransferId() { return transferId; }
        public void setTransferId(Long transferId) { this.transferId = transferId; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public String getSenderBIC() { return senderBIC; }
        public void setSenderBIC(String senderBIC) { this.senderBIC = senderBIC; }
        
        public String getReceiverBIC() { return receiverBIC; }
        public void setReceiverBIC(String receiverBIC) { this.receiverBIC = receiverBIC; }
        
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getPipelineStage() { return pipelineStage; }
        public void setPipelineStage(String pipelineStage) { this.pipelineStage = pipelineStage; }
        
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
        
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        
        public String getWatchlistMatch() { return watchlistMatch; }
        public void setWatchlistMatch(String watchlistMatch) { this.watchlistMatch = watchlistMatch; }
        
        public List<Map<String, String>> getEvidence() { return evidence; }
        public void setEvidence(List<Map<String, String>> evidence) { this.evidence = evidence; }
        
        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = urgency; }
    }

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
