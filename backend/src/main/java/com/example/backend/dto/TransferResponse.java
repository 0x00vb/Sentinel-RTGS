package com.example.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.backend.entity.Transfer.TransferStatus;

public class TransferResponse {
    private UUID msgId;
    private TransferStatus status;
    private String message;
    private LocalDateTime processedAt;


    public TransferResponse(UUID msgId, TransferStatus status, String message, LocalDateTime processedAt) {
        this.msgId = msgId;
        this.status = status;
        this.message = message;
        this.processedAt = processedAt;
    }
    
    public UUID getMsgId() {
        return msgId;
    }
    public TransferStatus getStatus() {
        return status;
    }
    public String getMessage() {
        return message;
    }
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
