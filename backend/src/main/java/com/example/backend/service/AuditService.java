package com.example.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.repository.AuditLogRepository;

import java.util.Map;
import java.util.List;
import com.example.backend.entity.AuditLog;

@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HashChainService hashChainService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAudit(String entityType, Long entityId, String action, Map<String, Object> payload) {
        try {
            String prevHash = getLastHashForEntity(entityType, entityId);
            String canonicalPayload = hashChainService.createCanonicalJson(payload);
            String currHash = hashChainService.calculateNextHash(canonicalPayload, prevHash);

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
                .orElse(hashChainService.getZeroHash());
    }

    public boolean verifyChain(String entityType, Long entityId) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);
        String prev = hashChainService.getZeroHash();

        for (AuditLog log : logs) {
            if (!hashChainService.validateHashLink(log.getPayload(), prev, log.getCurrHash())) {
                return false;
            }
            prev = log.getCurrHash();
        }
        return true;
    }
}