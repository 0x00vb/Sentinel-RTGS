package com.example.backend.dto;

import com.example.backend.entity.Transfer;

public class TransferEvent {
    private final Long id;
    private final java.util.UUID msgId;
    private final Transfer.TransferStatus status;
    private final java.math.BigDecimal amount;
    private final String sourceIban;
    private final String destIban;
    private final java.time.LocalDateTime timestamp;
    
    public TransferEvent(Long id, java.util.UUID msgId, Transfer.TransferStatus status, 
                        java.math.BigDecimal amount, String sourceIban, String destIban, 
                        java.time.LocalDateTime timestamp) {
        this.id = id;
        this.msgId = msgId;
        this.status = status;
        this.amount = amount;
        this.sourceIban = sourceIban;
        this.destIban = destIban;
        this.timestamp = timestamp;
    }
    
    // getters...
    public Long getId() { return id; }
    public java.util.UUID getMsgId() { return msgId; }
    public Transfer.TransferStatus getStatus() { return status; }
    public java.math.BigDecimal getAmount() { return amount; }
    public String getSourceIban() { return sourceIban; }
    public String getDestIban() { return destIban; }
    public java.time.LocalDateTime getTimestamp() { return timestamp; }
}
