package com.example.backend.service;

import com.example.backend.entity.Transfer;
import com.example.backend.service.FuzzyMatchService.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service implementing configurable business rules for compliance decisions.
 * Evaluates transfers against fuzzy matching results and applies risk-based decision logic.
 *
 * FR-05: Similarity score threshold (default 85%)
 */
@Service
public class RuleEngineService {

    private static final Logger logger = LoggerFactory.getLogger(RuleEngineService.class);

    @Value("${compliance.rules.high-risk-threshold:90}")
    private int highRiskThreshold;

    @Value("${compliance.rules.medium-risk-threshold:75}")
    private int mediumRiskThreshold;

    @Value("${compliance.rules.amount-threshold:10000}")
    private BigDecimal amountThreshold;

    @Value("${compliance.rules.velocity-check-enabled:false}")
    private boolean velocityCheckEnabled;

    @Value("${compliance.rules.geographic-check-enabled:false}")
    private boolean geographicCheckEnabled;

    /**
     * Evaluate a transfer against compliance rules and return decision.
     */
    public ComplianceDecision evaluateTransfer(Transfer transfer, List<MatchResult> matches) {
        logger.debug("Evaluating transfer {} against compliance rules", transfer.getId());

        // Check for high-confidence matches first
        MatchResult bestMatch = matches.stream()
            .max((a, b) -> Double.compare(a.getSimilarityScore(), b.getSimilarityScore()))
            .orElse(null);

        if (bestMatch != null && bestMatch.isAboveThreshold(highRiskThreshold)) {
            // High-confidence match - block immediately
            String reason = String.format("High-confidence fuzzy match (%.1f%%) to sanctioned entity '%s' from %s",
                    bestMatch.getSimilarityScore(),
                    bestMatch.getSanction().getName(),
                    bestMatch.getSanction().getSource());

            logger.warn("Transfer {} blocked: {}", transfer.getId(), reason);
            return ComplianceDecision.blocked(reason, bestMatch);
        }

        // Check for medium-confidence matches
        if (bestMatch != null && bestMatch.isAboveThreshold(mediumRiskThreshold)) {
            // Apply additional risk factors
            RiskAssessment risk = assessAdditionalRisk(transfer, bestMatch);

            // Block if very high risk factors are present
            if (risk.getTotalRiskScore() >= 5) { // Very high risk threshold
                String reason = String.format("Medium-confidence fuzzy match (%.1f%%) to sanctioned entity '%s' from %s with high-risk factors: %s",
                        bestMatch.getSimilarityScore(),
                        bestMatch.getSanction().getName(),
                        bestMatch.getSanction().getSource(),
                        risk.getReason());

                logger.warn("Transfer {} blocked due to high-risk factors: {}", transfer.getId(), reason);
                return ComplianceDecision.blocked(reason, bestMatch);
            }

            // Otherwise, flag for manual review
            String reason = String.format("Medium-confidence fuzzy match (%.1f%%) to sanctioned entity '%s'",
                    bestMatch.getSimilarityScore(), bestMatch.getSanction().getName());

            logger.info("Transfer {} flagged for manual review: {}", transfer.getId(), reason);
            return ComplianceDecision.manualReview(reason, bestMatch);
        }

        // Check for low-confidence matches that might still warrant attention
        List<MatchResult> lowConfidenceMatches = matches.stream()
            .filter(match -> match.isAboveThreshold(50)) // Configurable low threshold
            .toList();

        if (!lowConfidenceMatches.isEmpty() && shouldEscalateForReview(transfer)) {
            String reason = String.format("Multiple low-confidence matches (%d) for high-value transfer",
                    lowConfidenceMatches.size());

            logger.info("Transfer {} escalated for review: {}", transfer.getId(), reason);
            return ComplianceDecision.manualReview(reason, lowConfidenceMatches.get(0));
        }

        // No significant matches found
        logger.debug("Transfer {} cleared: no significant sanction matches", transfer.getId());
        return ComplianceDecision.cleared();
    }

    /**
     * Assess additional risk factors beyond fuzzy matching.
     */
    private RiskAssessment assessAdditionalRisk(Transfer transfer, MatchResult match) {
        RiskAssessment risk = new RiskAssessment();

        // Amount-based risk
        if (transfer.getAmount().compareTo(amountThreshold) > 0) {
            risk.addRiskFactor("High-value transfer", 2);
        }

        // Source risk based on sanction source
        int sourceRisk = switch (match.getSanction().getSource()) {
            case "OFAC" -> 3;
            case "UN" -> 3;
            case "EU" -> 2;
            default -> 1;
        };
        risk.addRiskFactor("High-risk sanction source: " + match.getSanction().getSource(), sourceRisk);

        // Risk score from sanction entry
        if (match.getSanction().getRiskScore() >= 90) {
            risk.addRiskFactor("Very high-risk sanctioned entity", 3);
        } else if (match.getSanction().getRiskScore() >= 75) {
            risk.addRiskFactor("High-risk sanctioned entity", 2);
        }

        // Geographic risk (placeholder for future implementation)
        if (geographicCheckEnabled) {
            // Could check sender/receiver countries against high-risk jurisdictions
            // risk.addRiskFactor("High-risk jurisdiction", 2);
        }

        // Velocity risk (placeholder for future implementation)
        if (velocityCheckEnabled) {
            // Could check for unusual transaction patterns
            // risk.addRiskFactor("Suspicious velocity pattern", 1);
        }

        return risk;
    }

    /**
     * Determine if a transfer should be escalated for manual review based on business rules.
     */
    private boolean shouldEscalateForReview(Transfer transfer) {
        // Escalate high-value transfers for additional scrutiny
        if (transfer.getAmount().compareTo(amountThreshold) > 0) {
            return true;
        }

        // Could add more rules here:
        // - New customer transfers
        // - Transfers to/from high-risk countries
        // - Unusual transaction patterns

        return false;
    }

    /**
     * Update rule configuration at runtime.
     */
    public void updateConfiguration(ComplianceConfig config) {
        this.highRiskThreshold = config.getHighRiskThreshold();
        this.mediumRiskThreshold = config.getMediumRiskThreshold();
        this.amountThreshold = config.getAmountThreshold();
        this.velocityCheckEnabled = config.isVelocityCheckEnabled();
        this.geographicCheckEnabled = config.isGeographicCheckEnabled();

        logger.info("Updated compliance rule configuration: {}", config);
    }

    /**
     * Get current rule configuration.
     */
    public ComplianceConfig getCurrentConfiguration() {
        return new ComplianceConfig(
            highRiskThreshold,
            mediumRiskThreshold,
            amountThreshold,
            velocityCheckEnabled,
            geographicCheckEnabled
        );
    }

    /**
     * Decision result from rule engine evaluation.
     */
    public static class ComplianceDecision {
        public enum Type {
            CLEARED,      // Proceed to settlement
            BLOCKED,      // Block transaction
            MANUAL_REVIEW // Queue for compliance officer review
        }

        private final Type type;
        private final String reason;
        private final MatchResult bestMatch;

        private ComplianceDecision(Type type, String reason, MatchResult bestMatch) {
            this.type = type;
            this.reason = reason;
            this.bestMatch = bestMatch;
        }

        public static ComplianceDecision cleared() {
            return new ComplianceDecision(Type.CLEARED, "No sanction matches found", null);
        }

        public static ComplianceDecision blocked(String reason, MatchResult match) {
            return new ComplianceDecision(Type.BLOCKED, reason, match);
        }

        public static ComplianceDecision manualReview(String reason, MatchResult match) {
            return new ComplianceDecision(Type.MANUAL_REVIEW, reason, match);
        }

        public Type getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public MatchResult getBestMatch() {
            return bestMatch;
        }

        public boolean isBlocked() {
            return type == Type.BLOCKED;
        }

        public boolean needsReview() {
            return type == Type.MANUAL_REVIEW;
        }

        public boolean isCleared() {
            return type == Type.CLEARED;
        }

        @Override
        public String toString() {
            return String.format("ComplianceDecision{type=%s, reason='%s'}", type, reason);
        }
    }

    /**
     * Configuration class for rule engine settings.
     */
    public static class ComplianceConfig {
        private int highRiskThreshold;
        private int mediumRiskThreshold;
        private BigDecimal amountThreshold;
        private boolean velocityCheckEnabled;
        private boolean geographicCheckEnabled;

        public ComplianceConfig() {}

        public ComplianceConfig(int highRiskThreshold, int mediumRiskThreshold,
                              BigDecimal amountThreshold, boolean velocityCheckEnabled,
                              boolean geographicCheckEnabled) {
            this.highRiskThreshold = highRiskThreshold;
            this.mediumRiskThreshold = mediumRiskThreshold;
            this.amountThreshold = amountThreshold;
            this.velocityCheckEnabled = velocityCheckEnabled;
            this.geographicCheckEnabled = geographicCheckEnabled;
        }

        // Getters and setters
        public int getHighRiskThreshold() {
            return highRiskThreshold;
        }

        public void setHighRiskThreshold(int highRiskThreshold) {
            this.highRiskThreshold = highRiskThreshold;
        }

        public int getMediumRiskThreshold() {
            return mediumRiskThreshold;
        }

        public void setMediumRiskThreshold(int mediumRiskThreshold) {
            this.mediumRiskThreshold = mediumRiskThreshold;
        }

        public BigDecimal getAmountThreshold() {
            return amountThreshold;
        }

        public void setAmountThreshold(BigDecimal amountThreshold) {
            this.amountThreshold = amountThreshold;
        }

        public boolean isVelocityCheckEnabled() {
            return velocityCheckEnabled;
        }

        public void setVelocityCheckEnabled(boolean velocityCheckEnabled) {
            this.velocityCheckEnabled = velocityCheckEnabled;
        }

        public boolean isGeographicCheckEnabled() {
            return geographicCheckEnabled;
        }

        public void setGeographicCheckEnabled(boolean geographicCheckEnabled) {
            this.geographicCheckEnabled = geographicCheckEnabled;
        }

        @Override
        public String toString() {
            return String.format("ComplianceConfig{highRisk=%d, mediumRisk=%d, amountThreshold=%s}",
                    highRiskThreshold, mediumRiskThreshold, amountThreshold);
        }
    }

    /**
     * Risk assessment helper class.
     */
    private static class RiskAssessment {
        private int totalRiskScore = 0;
        private final StringBuilder reasons = new StringBuilder();

        public void addRiskFactor(String reason, int riskLevel) {
            totalRiskScore += riskLevel;
            if (reasons.length() > 0) reasons.append("; ");
            reasons.append(reason);
        }

        public boolean isHighRisk() {
            return totalRiskScore >= 4; // Configurable threshold
        }

        public int getTotalRiskScore() {
            return totalRiskScore;
        }

        public String getReason() {
            return reasons.toString();
        }
    }
}
