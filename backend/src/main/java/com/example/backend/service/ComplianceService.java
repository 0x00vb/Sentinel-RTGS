package com.example.backend.service;

import com.example.backend.dto.ComplianceDecision;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.entity.Transfer;
import com.example.backend.service.FuzzyMatchService.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Compliance Service orchestrating sanction screening and compliance evaluation.
 * Coordinates fuzzy matching, rule evaluation, and audit logging for transfer compliance checks.
 *
 * FR-04: Sanctions lookup using fuzzy matching
 * FR-05: Similarity score threshold evaluation
 * FR-06: Manual review workflow support
 */
@Service
public class ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);

    private final FuzzyMatchService fuzzyMatchService;
    private final RuleEngineService ruleEngineService;
    private final AuditService auditService;

    @Autowired
    public ComplianceService(FuzzyMatchService fuzzyMatchService,
                           RuleEngineService ruleEngineService,
                           AuditService auditService) {
        this.fuzzyMatchService = fuzzyMatchService;
        this.ruleEngineService = ruleEngineService;
        this.auditService = auditService;
    }

    /**
     * Evaluate a transfer for compliance violations.
     * This is the main entry point for sanction screening in the processing pipeline.
     * Uses REQUIRES_NEW propagation to ensure compliance evaluation runs in its own transaction
     * and doesn't interfere with payment processing transactions.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ProcessingResult evaluateTransfer(Transfer transfer) {
        logger.info("Starting compliance evaluation for transfer {}", transfer.getId());

        try {
            // Step 1: Extract names for screening
            List<String> namesToScreen = extractNamesForScreening(transfer);

            // Step 2: Perform fuzzy matching against sanctions database
            List<MatchResult> allMatches = performFuzzyMatching(namesToScreen);

            // Step 3: Apply business rules to determine compliance decision
            RuleEngineService.ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, allMatches);

            // Step 4: Update transfer status based on decision
            updateTransferStatus(transfer, decision);

            // Step 5: Audit the compliance evaluation
            auditComplianceEvaluation(transfer, decision, allMatches);

            // Step 6: Return appropriate processing result
            return createProcessingResult(decision, allMatches);

        } catch (Exception e) {
            logger.error("Error during compliance evaluation for transfer {}: {}", transfer.getId(), e.getMessage(), e);

            // Audit the error
            Map<String, Object> errorPayload = new HashMap<>();
            errorPayload.put("error", e.getMessage());
            errorPayload.put("action", "COMPLIANCE_ERROR");
            auditService.logAudit("transfer", transfer.getId(), "COMPLIANCE_ERROR", errorPayload);

            return ProcessingResult.processingError(
                "Compliance evaluation failed: " + e.getMessage(),
                "COMP001"
            );
        }
    }

    /**
     * Process a manual compliance decision (approve/reject) from a compliance officer.
     */
    @Transactional
    public ProcessingResult processManualDecision(Long transferId, ComplianceDecision decision) {
        logger.info("Processing manual compliance decision for transfer {}: {}", transferId, decision.getDecision());

        try {
            // Find and update the transfer
            Transfer transfer = findTransferById(transferId);
            if (transfer == null) {
                return ProcessingResult.processingError("Transfer not found: " + transferId, "COMP003");
            }

            // Validate transfer is in correct state for manual decision
            if (transfer.getStatus() != Transfer.TransferStatus.BLOCKED_AML) {
                return ProcessingResult.processingError(
                    "Transfer is not in BLOCKED_AML status: " + transfer.getStatus(),
                    "COMP002"
                );
            }

            // Update transfer status based on decision
            if (decision.getDecision() == ComplianceDecision.DecisionType.APPROVE) {
                transfer.setStatus(Transfer.TransferStatus.CLEARED);
                logger.info("Transfer {} approved for processing", transferId);
            } else {
                transfer.setStatus(Transfer.TransferStatus.REJECTED);
                logger.info("Transfer {} rejected", transferId);
            }

            // Audit the manual decision
            Map<String, Object> manualDecisionPayload = new HashMap<>();
            manualDecisionPayload.put("decision", decision.getDecision().name());
            manualDecisionPayload.put("reviewer", decision.getReviewer());
            manualDecisionPayload.put("notes", decision.getNotes() != null ? decision.getNotes() : "None");
            manualDecisionPayload.put("action", "MANUAL_DECISION");
            auditService.logAudit("transfer", transferId, "MANUAL_DECISION", manualDecisionPayload);

            return ProcessingResult.success(transfer);

        } catch (Exception e) {
            logger.error("Error processing manual decision for transfer {}: {}", transferId, e.getMessage(), e);
            return ProcessingResult.processingError(
                "Manual decision processing failed: " + e.getMessage(),
                "COMP003"
            );
        }
    }

    /**
     * Extract relevant names from transfer for sanction screening.
     * This includes sender names, receiver names, and any other parties mentioned.
     */
    private List<String> extractNamesForScreening(Transfer transfer) {
        // For now, we focus on sender and receiver names from IBANs
        // In a full implementation, this would parse the original pacs.008 message
        // to extract all party names (sender, receiver, intermediaries, etc.)

        List<String> names = new ArrayList<>();

        // Add sender IBAN (placeholder - in real implementation would extract from pacs.008)
        if (transfer.getSenderIban() != null) {
            names.add(extractNameFromIban(transfer.getSenderIban()));
        }

        // Add receiver IBAN (placeholder)
        if (transfer.getReceiverIban() != null) {
            names.add(extractNameFromIban(transfer.getReceiverIban()));
        }

        // Filter out null/empty names
        return names.stream()
                   .filter(name -> name != null && !name.trim().isEmpty())
                   .distinct()
                   .toList();
    }

    /**
     * Placeholder method to extract names from IBANs.
     * In production, this would be replaced by proper parsing of pacs.008 party information.
     */
    private String extractNameFromIban(String iban) {
        // This is a placeholder - in real implementation, names would come from pacs.008
        // For demo purposes, we'll use some mock names
        if (iban != null && iban.length() > 4) {
            // Return last 4 characters as a mock name identifier
            return "Party" + iban.substring(iban.length() - 4);
        }
        return null;
    }

    /**
     * Perform fuzzy matching for all names to screen.
     */
    private List<MatchResult> performFuzzyMatching(List<String> namesToScreen) {
        List<MatchResult> allMatches = new ArrayList<>();

        for (String name : namesToScreen) {
            List<MatchResult> matches = fuzzyMatchService.findMatches(name);
            allMatches.addAll(matches);
        }

        logger.debug("Found {} total fuzzy matches for {} names", allMatches.size(), namesToScreen.size());
        return allMatches;
    }

    /**
     * Update transfer status based on compliance decision.
     * 
     * IMPORTANT: When compliance decision is CLEARED, we keep the transfer in PENDING state.
     * The PaymentService will change it to CLEARED only after successful payment processing.
     * This ensures proper separation of concerns:
     * - Compliance: approves or blocks (BLOCKED_AML)
     * - Payment: processes payment and sets CLEARED after successful settlement
     * 
     * This maintains compliance with devplan.md FR-08: "Each cleared transfer must create
     * two ledger entries (debit & credit) summing to zero in the transaction boundary."
     * The CLEARED status should only be set after the ledger entries are created.
     */
    private void updateTransferStatus(Transfer transfer, RuleEngineService.ComplianceDecision decision) {
        Transfer.TransferStatus newStatus;

        switch (decision.getType()) {
            case CLEARED:
                // Keep as PENDING - PaymentService will set CLEARED after successful payment processing
                // This ensures the transfer can be processed by PaymentService.processPaymentForTransfer()
                // which requires PENDING state (FR-08 compliance)
                newStatus = Transfer.TransferStatus.PENDING;
                logger.debug("Transfer {} cleared by compliance, keeping PENDING for payment processing", transfer.getId());
                break;
            case BLOCKED:
                newStatus = Transfer.TransferStatus.BLOCKED_AML;
                break;
            case MANUAL_REVIEW:
                newStatus = Transfer.TransferStatus.BLOCKED_AML; // Will be reviewed manually
                break;
            default:
                newStatus = Transfer.TransferStatus.PENDING;
                break;
        }

        transfer.setStatus(newStatus);
        logger.info("Updated transfer {} status to {} (compliance decision: {})", 
                   transfer.getId(), newStatus, decision.getType());
    }

    /**
     * Create audit log entry for compliance evaluation.
     */
    private void auditComplianceEvaluation(Transfer transfer, RuleEngineService.ComplianceDecision decision,
                                         List<MatchResult> matches) {
        String action = switch (decision.getType()) {
            case CLEARED -> "COMPLIANCE_CLEARED";
            case BLOCKED -> "COMPLIANCE_BLOCKED";
            case MANUAL_REVIEW -> "COMPLIANCE_REVIEW";
        };

        Map<String, Object> payload = createComplianceAuditPayload(transfer, decision, matches);

        auditService.logAudit("transfer", transfer.getId(), action, payload);
    }

    /**
     * Create detailed audit payload for compliance evaluation.
     */
    private Map<String, Object> createComplianceAuditPayload(Transfer transfer, RuleEngineService.ComplianceDecision decision,
                                              List<MatchResult> matches) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("decision", decision.getType().name());
        payload.put("reason", decision.getReason());
        payload.put("matchesCount", matches.size());

        if (!matches.isEmpty()) {
            MatchResult bestMatch = matches.get(0);
            Map<String, Object> bestMatchInfo = new HashMap<>();
            bestMatchInfo.put("sanctionName", bestMatch.getSanction().getName());
            bestMatchInfo.put("similarityScore", bestMatch.getSimilarityScore());
            bestMatchInfo.put("source", bestMatch.getSanction().getSource());
            bestMatchInfo.put("algorithm", bestMatch.getAlgorithm());
            payload.put("bestMatch", bestMatchInfo);
        }

        payload.put("transferAmount", transfer.getAmount());
        payload.put("senderIban", transfer.getSenderIban());
        payload.put("receiverIban", transfer.getReceiverIban());

        return payload;
    }

    /**
     * Create processing result based on compliance decision.
     */
    private ProcessingResult createProcessingResult(RuleEngineService.ComplianceDecision decision,
                                                  List<MatchResult> matches) {
        return switch (decision.getType()) {
            case CLEARED -> ProcessingResult.success(null);
            case BLOCKED -> ProcessingResult.blockedSanctions(null, decision.getReason());
            case MANUAL_REVIEW -> ProcessingResult.blockedSanctions(null, decision.getReason());
        };
    }

    /**
     * Find transfer by ID (placeholder - would use repository in real implementation).
     */
    private Transfer findTransferById(Long transferId) {
        // This is a placeholder - in real implementation would inject TransferRepository
        // and use transferRepository.findById(transferId).orElse(null);
        throw new UnsupportedOperationException("Transfer lookup not implemented - integrate with TransferRepository");
    }

    /**
     * Get compliance statistics for monitoring.
     */
    public ComplianceStats getComplianceStats() {
        // This would aggregate statistics from the database
        // For now, return placeholder stats
        return new ComplianceStats(0, 0, 0, 0);
    }

    /**
     * Statistics class for compliance monitoring.
     */
    public static class ComplianceStats {
        private final long totalEvaluations;
        private final long blockedTransactions;
        private final long clearedTransactions;
        private final long manualReviews;

        public ComplianceStats(long totalEvaluations, long blockedTransactions,
                             long clearedTransactions, long manualReviews) {
            this.totalEvaluations = totalEvaluations;
            this.blockedTransactions = blockedTransactions;
            this.clearedTransactions = clearedTransactions;
            this.manualReviews = manualReviews;
        }

        public long getTotalEvaluations() { return totalEvaluations; }
        public long getBlockedTransactions() { return blockedTransactions; }
        public long getClearedTransactions() { return clearedTransactions; }
        public long getManualReviews() { return manualReviews; }

        public double getBlockRate() {
            return totalEvaluations > 0 ? (double) blockedTransactions / totalEvaluations * 100 : 0;
        }
    }
}
