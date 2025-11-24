package com.example.backend.service;

import com.example.backend.entity.SanctionsList;
import com.example.backend.entity.Transfer;
import com.example.backend.service.FuzzyMatchService.MatchResult;
import com.example.backend.service.RuleEngineService.ComplianceDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        ruleEngineService = new RuleEngineService();

        // Set default configuration values for tests
        ReflectionTestUtils.setField(ruleEngineService, "highRiskThreshold", 90);
        ReflectionTestUtils.setField(ruleEngineService, "mediumRiskThreshold", 75);
        ReflectionTestUtils.setField(ruleEngineService, "amountThreshold", BigDecimal.valueOf(10000));
        ReflectionTestUtils.setField(ruleEngineService, "velocityCheckEnabled", false);
        ReflectionTestUtils.setField(ruleEngineService, "geographicCheckEnabled", false);
    }

    @Test
    void shouldClearTransfersWithNoMatches() {
        // Given
        Transfer transfer = createTransfer(BigDecimal.valueOf(1000));
        List<MatchResult> noMatches = Collections.emptyList();

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, noMatches);

        // Then
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.CLEARED);
        assertThat(decision.getReason()).contains("No sanction matches found");
    }

    @Test
    void shouldBlockHighConfidenceMatches() {
        // Given
        Transfer transfer = createTransfer(BigDecimal.valueOf(1000));
        MatchResult highMatch = createMatchResult("HIGH RISK ENTITY", 95.0, "OFAC", 100);

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, List.of(highMatch));

        // Then
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.BLOCKED);
        assertThat(decision.getReason()).contains("High-confidence fuzzy match");
        assertThat(decision.getBestMatch()).isEqualTo(highMatch);
    }

    @Test
    void shouldFlagMediumConfidenceMatchesForReview() {
        // Given
        Transfer transfer = createTransfer(BigDecimal.valueOf(1000));
        MatchResult mediumMatch = createMatchResult("MEDIUM RISK ENTITY", 80.0, "EU", 80);

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, List.of(mediumMatch));

        // Then
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.MANUAL_REVIEW);
        assertThat(decision.getReason()).contains("Medium-confidence fuzzy match");
    }

    @Test
    void shouldBlockHighValueTransfersWithLowConfidenceMatches() {
        // Given
        Transfer transfer = createHighValueTransfer(); // > 10,000 threshold
        MatchResult lowMatch = createMatchResult("LOW RISK ENTITY", 60.0, "UN", 60);

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, List.of(lowMatch));

        // Then
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.MANUAL_REVIEW);
        assertThat(decision.getReason()).contains("Multiple low-confidence matches");
    }

    @Test
    void shouldApplyRiskScoringForMediumConfidenceMatches() {
        // Given
        Transfer transfer = createTransfer(BigDecimal.valueOf(1000));
        MatchResult mediumMatch = createMatchResult("RISKY ENTITY", 80.0, "OFAC", 100);

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, List.of(mediumMatch));

        // Then
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.BLOCKED);
        assertThat(decision.getReason()).contains("High-risk sanction source");
    }

    @Test
    void shouldHandleMultipleMatchesCorrectly() {
        // Given
        Transfer transfer = createTransfer(BigDecimal.valueOf(1000));
        List<MatchResult> matches = Arrays.asList(
            createMatchResult("HIGH MATCH", 95.0, "OFAC", 100),
            createMatchResult("MEDIUM MATCH", 80.0, "EU", 80),
            createMatchResult("LOW MATCH", 50.0, "UN", 60)
        );

        // When
        ComplianceDecision decision = ruleEngineService.evaluateTransfer(transfer, matches);

        // Then - Should use the highest scoring match
        assertThat(decision.getType()).isEqualTo(ComplianceDecision.Type.BLOCKED);
        assertThat(decision.getBestMatch().getSimilarityScore()).isEqualTo(95.0);
    }

    @Test
    void shouldAllowConfigurationUpdates() {
        // Given
        var newConfig = new RuleEngineService.ComplianceConfig(95, 80, BigDecimal.valueOf(50000), true, true);

        // When
        ruleEngineService.updateConfiguration(newConfig);

        // Then
        var currentConfig = ruleEngineService.getCurrentConfiguration();
        assertThat(currentConfig.getHighRiskThreshold()).isEqualTo(95);
        assertThat(currentConfig.getMediumRiskThreshold()).isEqualTo(80);
        assertThat(currentConfig.getAmountThreshold()).isEqualTo(BigDecimal.valueOf(50000));
        assertThat(currentConfig.isVelocityCheckEnabled()).isTrue();
        assertThat(currentConfig.isGeographicCheckEnabled()).isTrue();
    }

    @Test
    void shouldReturnDefaultConfiguration() {
        // When
        var config = ruleEngineService.getCurrentConfiguration();

        // Then
        assertThat(config.getHighRiskThreshold()).isEqualTo(90);
        assertThat(config.getMediumRiskThreshold()).isEqualTo(75);
        assertThat(config.getAmountThreshold()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(config.isVelocityCheckEnabled()).isFalse();
        assertThat(config.isGeographicCheckEnabled()).isFalse();
    }

    private Transfer createTransfer(BigDecimal amount) {
        Transfer transfer = new Transfer();
        transfer.setId(1L);
        transfer.setAmount(amount);
        transfer.setSenderIban("DE89370400440532013000");
        transfer.setReceiverIban("GB29RBOS60161331926819");
        return transfer;
    }

    private Transfer createHighValueTransfer() {
        return createTransfer(BigDecimal.valueOf(50000)); // Above 10,000 threshold
    }

    private MatchResult createMatchResult(String name, double similarity, String source, int riskScore) {
        SanctionsList sanction = new SanctionsList();
        sanction.setId(1L);
        sanction.setName(name);
        sanction.setSource(source);
        sanction.setRiskScore(riskScore);
        sanction.setNormalizedName(name.toUpperCase());

        return new MatchResult(sanction, similarity, "test");
    }
}
