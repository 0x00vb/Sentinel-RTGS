package com.example.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class HashChainService {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private final ObjectMapper canonicalObjectMapper;

    // CHANGE 1: Inject the global ObjectMapper provided by Spring
    public HashChainService(ObjectMapper defaultMapper) {
        // CHANGE 2: Create a copy of the global mapper. 
        // This keeps the "smart" Spring configuration (Java 8 dates, etc.) 
        // but allows us to add our own strict sorting rules without breaking the rest of the app.
        this.canonicalObjectMapper = defaultMapper.copy();

        // Configure for Canonical (Deterministic) JSON
        this.canonicalObjectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.canonicalObjectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        
        // RECOMMENDATION: Set this to FALSE. 
        // 'true' turns a date into a number or array [2025,11,27...]. 
        // 'false' turns it into a standard ISO String "2025-11-27T03:46:30" which is better for hashing.
        this.canonicalObjectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Ignore Hibernate-specific properties that can't be serialized
        // This is a defensive measure - the main fix is in AuditEntityListener
        // to extract IDs from proxies before serialization
        this.canonicalObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // Safety check: Explicitly register module again just in case, 
        // though copying the defaultMapper usually handles this.
        this.canonicalObjectMapper.registerModule(new JavaTimeModule());
    }

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

    public String calculateSha256Hash(String input) throws NoSuchAlgorithmException {
        if (input == null) {
            input = "";
        }

        MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    public String calculateNextHash(String canonicalJson, String previousHash) throws NoSuchAlgorithmException {
        String input = canonicalJson + (previousHash != null ? previousHash : ZERO_HASH);
        return calculateSha256Hash(input);
    }

    public boolean validateHashLink(String canonicalJson, String previousHash, String expectedHash) {
        try {
            String calculatedHash = calculateNextHash(canonicalJson, previousHash);
            return calculatedHash.equals(expectedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String getZeroHash() {
        return ZERO_HASH;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public boolean isValidHashFormat(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }
        return hash.matches("^[a-f0-9]{64}$");
    }
}