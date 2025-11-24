package com.example.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service responsible for cryptographic hash chain operations.
 * Implements deterministic JSON canonicalization and SHA-256 hash calculations
 * for SOX compliance and tamper detection.
 *
 * Key features:
 * - Deterministic JSON serialization (canonical form)
 * - SHA-256 hash calculation with proper error handling
 * - Hash chain validation for integrity checking
 */
@Service
public class HashChainService {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private final ObjectMapper canonicalObjectMapper;

    public HashChainService() {
        // Configure ObjectMapper for deterministic (canonical) JSON output
        this.canonicalObjectMapper = new ObjectMapper();
        this.canonicalObjectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.canonicalObjectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        // Ensure consistent formatting
        this.canonicalObjectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    /**
     * Creates a canonical (deterministic) JSON representation of the payload.
     * This ensures that identical data always produces identical JSON strings,
     * which is crucial for hash chain integrity.
     *
     * @param payload The data to serialize
     * @return Canonical JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public String createCanonicalJson(Object payload) throws JsonProcessingException {
        if (payload == null) {
            return "{}";
        }

        // Convert to sorted map for consistent key ordering
        if (payload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) payload;
            Map<String, Object> sortedMap = new TreeMap<>(map);
            return canonicalObjectMapper.writeValueAsString(sortedMap);
        }

        return canonicalObjectMapper.writeValueAsString(payload);
    }

    /**
     * Calculates SHA-256 hash of the input string.
     *
     * @param input The string to hash
     * @return Hexadecimal representation of the SHA-256 hash
     * @throws NoSuchAlgorithmException if SHA-256 is not available (should never happen)
     */
    public String calculateSha256Hash(String input) throws NoSuchAlgorithmException {
        if (input == null) {
            input = "";
        }

        MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * Calculates the next hash in a hash chain.
     * Formula: SHA256(canonicalJson + previousHash)
     *
     * @param canonicalJson The canonical JSON representation of the payload
     * @param previousHash The previous hash in the chain (or ZERO_HASH for chain start)
     * @return The next hash in the chain
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public String calculateNextHash(String canonicalJson, String previousHash) throws NoSuchAlgorithmException {
        String input = canonicalJson + (previousHash != null ? previousHash : ZERO_HASH);
        return calculateSha256Hash(input);
    }

    /**
     * Validates a single hash chain link.
     *
     * @param canonicalJson The canonical JSON payload
     * @param previousHash The previous hash (or ZERO_HASH)
     * @param expectedHash The hash that should result from the calculation
     * @return true if the hash chain link is valid
     */
    public boolean validateHashLink(String canonicalJson, String previousHash, String expectedHash) {
        try {
            String calculatedHash = calculateNextHash(canonicalJson, previousHash);
            return calculatedHash.equals(expectedHash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Gets the zero hash constant used to start hash chains.
     */
    public String getZeroHash() {
        return ZERO_HASH;
    }

    /**
     * Converts byte array to hexadecimal string representation.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Validates that a hash string has the correct format (64 hex characters).
     */
    public boolean isValidHashFormat(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }
        return hash.matches("^[a-f0-9]{64}$");
    }
}
