package com.example.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.List;
import com.example.backend.entity.AuditLog;

@Service
public class AuditService {
        
    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAudit(String entityType, Long entityId, String action, Map<String, Object> payload) {
        try {
            String prevHash = getLastHashForEntity(entityType, entityId);
            String canonicalPayload = createCanonicalJson(payload);
            String currHash = calculateHash(canonicalPayload + prevHash);
            
            AuditLog auditLog = new AuditLog(entityType, entityId, action, canonicalPayload, prevHash, currHash);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log the error but don't fail the main transaction
            // Consider using a proper logging framework
            System.err.println("Failed to create audit log: " + e.getMessage());
            throw new RuntimeException("Audit logging failed", e);
        }
    }

    private String getLastHashForEntity(String entityType, Long entityId) {
        return auditLogRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .map(AuditLog::getCurrHash)
                .orElse(ZERO_HASH);
    }

    private String createCanonicalJson(Map<String, Object> payload) throws JsonProcessingException {
        // Create a canonical JSON representation
        ObjectMapper mapper = new ObjectMapper();
        // Sort keys for canonical representation
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private String calculateHash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public boolean verifyChain(String entityType, Long entityId) {
        try {
            List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);
            String prev = ZERO_HASH;
            
            for (AuditLog log : logs) {
                String expected = calculateHash(log.getPayload() + prev);
                if (!expected.equals(log.getCurrHash())) {
                    return false;
                }
                prev = log.getCurrHash();
            }
            return true;
        } catch (NoSuchAlgorithmException e) {
            // This should never happen with SHA-256
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}