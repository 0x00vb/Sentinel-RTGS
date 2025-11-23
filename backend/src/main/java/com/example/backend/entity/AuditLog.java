package com.example.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action; 
    // Examples:
    // ACCOUNT_CREATED, TRANSFER_EXECUTED, LOGIN, SANCTION_CHECK

    @Column(nullable = false)
    private String entityType; 
    // e.g. "Account", "Transfer", "User", etc.

    @Column(nullable = false)
    private Long entityId;

    // JSON-string payload describing the before/after or details
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private String prevHash;

    @Column(nullable = false, updatable = false)
    private String currHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructor for creating immutable audit logs
    public AuditLog(String entityType, Long entityId, String action, String payload, String prevHash, String currHash) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.payload = payload;
        this.prevHash = prevHash;
        this.currHash = currHash;
        this.createdAt = LocalDateTime.now();
    }
    
    // Default constructor for JPA
    protected AuditLog() {}
    
    // Getters
    public Long getId() {
        return id;
    }
    public String getAction() {
        return action;
    }
    public String getEntityType() {
        return entityType;
    }
    public Long getEntityId() {
        return entityId;
    }
    public String getPayload() {
        return payload;
    }
    public String getPrevHash() {
        return prevHash;
    }
    public String getCurrHash() {
        return currHash;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
