package com.example.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.example.backend.listener.AuditEntityListener;



@Entity
@Table(name = "transfers")
@EntityListeners(AuditEntityListener.class)
public class Transfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, updatable = false)
    private UUID msgId;

    @Column(nullable = false)
    private String externalReference;  // useful for idempotency
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account source;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private Account destination;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 34)
    private String senderIban;

    @Column(length = 34)
    private String receiverIban;

    public enum TransferStatus {
        PENDING,
        CLEARED,
        BLOCKED_AML,
        REJECTED
    }
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status = TransferStatus.PENDING;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime completedAt;

    // Getters and setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getExternalReference() {
        return externalReference;
    }
    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }
    public Account getSource() {
        return source;
    }
    public UUID getMsgId() {
        return msgId;
    }
    public void setMsgId(UUID msgId) {
        this.msgId = msgId;
    }
    public void setSource(Account source) {
        this.source = source;
    }
    public Account getDestination() {
        return destination;
    }
    public void setDestination(Account destination) {
        this.destination = destination;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    public String getSenderIban() {
        return senderIban;
    }
    public void setSenderIban(String senderIban) {
        this.senderIban = senderIban;
    }
    public String getReceiverIban() {
        return receiverIban;
    }
    public void setReceiverIban(String receiverIban) {
        this.receiverIban = receiverIban;
    }
    public TransferStatus getStatus() {
        return status;
    }
    public void setStatus(TransferStatus status) {
        this.status = status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}