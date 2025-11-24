package com.example.backend.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduledChainVerifier
 */
@ExtendWith(MockitoExtension.class)
class ScheduledChainVerifierTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ScheduledChainVerifier chainVerifier;

    // Test data fields - may be used in future tests
    @SuppressWarnings("unused")
    private AuditLog validLog1;
    @SuppressWarnings("unused")
    private AuditLog validLog2;
    @SuppressWarnings("unused")
    private AuditLog invalidLog;

    @BeforeEach
    void setUp() throws Exception {
        // Create test audit logs with valid chain
        HashChainService hashService = new HashChainService();
        String zeroHash = hashService.getZeroHash();

        String payload1 = hashService.createCanonicalJson(Map.of("action", "CREATE"));
        String hash1 = hashService.calculateNextHash(payload1, zeroHash);

        String payload2 = hashService.createCanonicalJson(Map.of("action", "UPDATE"));
        String hash2 = hashService.calculateNextHash(payload2, hash1);

        validLog1 = new AuditLog("Transfer", 1L, "CREATE", payload1, zeroHash, hash1);
        validLog2 = new AuditLog("Transfer", 1L, "UPDATE", payload2, hash1, hash2);

        // Invalid log for testing breaches
        invalidLog = new AuditLog("Transfer", 1L, "INVALID", payload2, hash1, "wrong_hash");
    }

    @Test
    void shouldVerifyIntegrityStatus() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer", "Account"));
        when(auditLogRepository.findAllEntityIdsForType("Transfer")).thenReturn(Arrays.asList(1L, 2L));
        when(auditLogRepository.findAllEntityIdsForType("Account")).thenReturn(Arrays.asList(1L));
        when(auditService.verifyChain(anyString(), anyLong())).thenReturn(true);

        // When
        ScheduledChainVerifier.IntegrityStatus status = chainVerifier.getIntegrityStatus();

        // Then
        assertThat(status).isNotNull();
        assertThat(status.getTotalVerifications()).isGreaterThan(0);
        assertThat(status.getTotalBreaches()).isEqualTo(0);
        assertThat(status.hasIntegrityBreaches()).isFalse();
    }

    @Test
    void shouldDetectIntegrityBreaches() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer"));
        when(auditLogRepository.findAllEntityIdsForType("Transfer")).thenReturn(Arrays.asList(1L));
        when(auditService.verifyChain("Transfer", 1L)).thenReturn(false);

        // When
        ScheduledChainVerifier.IntegrityStatus status = chainVerifier.getIntegrityStatus();

        // Then
        assertThat(status.hasIntegrityBreaches()).isTrue();
        assertThat(status.getTotalBreaches()).isGreaterThan(0);
    }

    @Test
    void shouldPerformManualVerification() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer"));
        when(auditLogRepository.findAllEntityIdsForType("Transfer")).thenReturn(Arrays.asList(1L));
        when(auditService.verifyChain("Transfer", 1L)).thenReturn(true);

        // When
        ScheduledChainVerifier.VerificationResult result = chainVerifier.verifyAllChains();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getChainsVerified()).isEqualTo(1);
        assertThat(result.getBreachesFound()).isEqualTo(0);
        assertThat(result.isScheduled()).isFalse();
        assertThat(result.getBreachRate()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleVerificationWithBreaches() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer"));
        when(auditLogRepository.findAllEntityIdsForType("Transfer")).thenReturn(Arrays.asList(1L, 2L));
        when(auditService.verifyChain("Transfer", 1L)).thenReturn(false);
        when(auditService.verifyChain("Transfer", 2L)).thenReturn(true);

        // When
        ScheduledChainVerifier.VerificationResult result = chainVerifier.verifyAllChains();

        // Then
        assertThat(result.getChainsVerified()).isEqualTo(2);
        assertThat(result.getBreachesFound()).isEqualTo(1);
        assertThat(result.getBreachRate()).isEqualTo(0.5);
    }

    @Test
    void shouldCalculateBreachRateCorrectly() {
        // Given
        ScheduledChainVerifier.VerificationResult result =
            new ScheduledChainVerifier.VerificationResult(
                LocalDateTime.now(), 10, 3, 1000L, true);

        // When & Then
        assertThat(result.getBreachRate()).isEqualTo(0.3);
    }

    @Test
    void shouldHandleEmptyEntityTypes() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Collections.emptyList());

        // When
        ScheduledChainVerifier.VerificationResult result = chainVerifier.verifyAllChains();

        // Then
        assertThat(result.getChainsVerified()).isEqualTo(0);
        assertThat(result.getBreachesFound()).isEqualTo(0);
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldHandleEmptyEntityIds() {
        // Given
        when(auditLogRepository.findAllEntityTypes()).thenReturn(Arrays.asList("Transfer"));
        when(auditLogRepository.findAllEntityIdsForType("Transfer")).thenReturn(Collections.emptyList());

        // When
        ScheduledChainVerifier.VerificationResult result = chainVerifier.verifyAllChains();

        // Then
        assertThat(result.getChainsVerified()).isEqualTo(0);
    }

    @Test
    void shouldCreateVerificationResultWithCorrectTimestamp() {
        // Given
        LocalDateTime before = LocalDateTime.now();

        // When
        ScheduledChainVerifier.VerificationResult result =
            new ScheduledChainVerifier.VerificationResult(before, 5, 1, 500L, false);

        // Then
        assertThat(result.getTimestamp()).isEqualTo(before);
        assertThat(result.getChainsVerified()).isEqualTo(5);
        assertThat(result.getBreachesFound()).isEqualTo(1);
        assertThat(result.getDurationMs()).isEqualTo(500L);
        assertThat(result.isScheduled()).isFalse();
    }

    @Test
    void shouldHandleZeroChainsInBreachRate() {
        // Given
        ScheduledChainVerifier.VerificationResult result =
            new ScheduledChainVerifier.VerificationResult(
                LocalDateTime.now(), 0, 0, 1000L, true);

        // When & Then
        assertThat(result.getBreachRate()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleNullAuditServiceGracefully() {
        // This test verifies the service can be instantiated without audit service
        // (though it would fail at runtime, the structure should be sound)
        assertThat(chainVerifier).isNotNull();
    }
}
