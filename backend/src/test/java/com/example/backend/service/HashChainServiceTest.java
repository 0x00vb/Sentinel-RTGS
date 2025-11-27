package com.example.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Unit tests for HashChainService
 */
class HashChainServiceTest {

    private HashChainService hashChainService;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        hashChainService = new HashChainService(mapper);   
    }

    @Test
    void shouldCreateCanonicalJson() throws Exception {
        // Given
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zebra", "last");
        payload.put("alpha", "first");
        payload.put("beta", 123);

        // When
        String canonicalJson = hashChainService.createCanonicalJson(payload);

        // Then
        assertThat(canonicalJson).isNotNull();
        assertThat(canonicalJson).contains("\"alpha\"");
        assertThat(canonicalJson).contains("\"beta\"");
        assertThat(canonicalJson).contains("\"zebra\"");

        // Verify keys are sorted (canonical)
        int alphaIndex = canonicalJson.indexOf("\"alpha\"");
        int betaIndex = canonicalJson.indexOf("\"beta\"");
        int zebraIndex = canonicalJson.indexOf("\"zebra\"");

        assertThat(alphaIndex).isLessThan(betaIndex);
        assertThat(betaIndex).isLessThan(zebraIndex);
    }

    @Test
    void shouldCalculateSha256Hash() throws NoSuchAlgorithmException {
        // Given
        String input = "test input";

        // When
        String hash = hashChainService.calculateSha256Hash(input);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(64);
        assertThat(hash).matches("^[a-f0-9]{64}$");

        // Verify determinism
        String hash2 = hashChainService.calculateSha256Hash(input);
        assertThat(hash).isEqualTo(hash2);
    }

    @Test
    void shouldCalculateNextHash() throws NoSuchAlgorithmException {
        // Given
        String canonicalJson = "{\"action\":\"test\"}";
        String previousHash = hashChainService.getZeroHash();

        // When
        String nextHash = hashChainService.calculateNextHash(canonicalJson, previousHash);

        // Then
        assertThat(nextHash).isNotNull();
        assertThat(nextHash.length()).isEqualTo(64);
        assertThat(nextHash).matches("^[a-f0-9]{64}$");
        assertThat(hashChainService.isValidHashFormat(nextHash)).isTrue();
    }

    @Test
    void shouldValidateValidHashLink() throws NoSuchAlgorithmException {
        // Given
        String canonicalJson = "{\"action\":\"test\"}";
        String previousHash = hashChainService.getZeroHash();
        String expectedHash = hashChainService.calculateNextHash(canonicalJson, previousHash);

        // When
        boolean isValid = hashChainService.validateHashLink(canonicalJson, previousHash, expectedHash);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectInvalidHashLink() {
        // Given
        String canonicalJson = "{\"action\":\"test\"}";
        String previousHash = hashChainService.getZeroHash();
        String wrongHash = "invalid_hash_value";

        // When
        boolean isValid = hashChainService.validateHashLink(canonicalJson, previousHash, wrongHash);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldReturnValidZeroHash() {
        // When
        String zeroHash = hashChainService.getZeroHash();

        // Then
        assertThat(zeroHash).isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(hashChainService.isValidHashFormat(zeroHash)).isTrue();
    }

    @Test
    void shouldValidateHashFormat() {
        // Valid hash
        assertThat(hashChainService.isValidHashFormat(
            "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890")).isTrue();

        // Invalid formats
        assertThat(hashChainService.isValidHashFormat("short")).isFalse();
        assertThat(hashChainService.isValidHashFormat("invalid_characters")).isFalse();
        assertThat(hashChainService.isValidHashFormat("")).isFalse();
        assertThat(hashChainService.isValidHashFormat(null)).isFalse();
    }

    @Test
    void shouldHandleNullInputForCanonicalJson() throws Exception {
        // When
        String canonicalJson = hashChainService.createCanonicalJson(null);

        // Then
        assertThat(canonicalJson).isEqualTo("{}");
    }

    @Test
    void shouldHandleEmptyMapForCanonicalJson() throws Exception {
        // When
        String canonicalJson = hashChainService.createCanonicalJson(Map.of());

        // Then
        assertThat(canonicalJson).isEqualTo("{}");
    }

    @Test
    void shouldCreateDifferentHashesForDifferentInputs() throws NoSuchAlgorithmException {
        // Given
        String input1 = "input1";
        String input2 = "input2";

        // When
        String hash1 = hashChainService.calculateSha256Hash(input1);
        String hash2 = hashChainService.calculateSha256Hash(input2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldCreateDifferentHashesForSamePayloadDifferentOrder() throws Exception {
        // Given - same data, different key order
        Map<String, Object> payload1 = Map.of("a", "1", "b", "2");
        Map<String, Object> payload2 = Map.of("b", "2", "a", "1");

        // When
        String canonical1 = hashChainService.createCanonicalJson(payload1);
        String canonical2 = hashChainService.createCanonicalJson(payload2);

        // Then - canonical JSON should be identical
        assertThat(canonical1).isEqualTo(canonical2);

        // And should produce same hash
        String hash1 = hashChainService.calculateSha256Hash(canonical1);
        String hash2 = hashChainService.calculateSha256Hash(canonical2);
        assertThat(hash1).isEqualTo(hash2);
    }
}
