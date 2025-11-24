package com.example.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal representation for generating pacs.002 status reports.
 */
public class StatusReportMessage {

    private final String originalMessageId;
    private final String status;
    private final String reasonCode;
    private final String reasonDescription;
    private final UUID transactionId;
    private final LocalDateTime timestamp;

    public StatusReportMessage(String originalMessageId, String status, String reasonCode,
                             String reasonDescription, UUID transactionId, LocalDateTime timestamp) {
        this.originalMessageId = originalMessageId;
        this.status = status;
        this.reasonCode = reasonCode;
        this.reasonDescription = reasonDescription;
        this.transactionId = transactionId;
        this.timestamp = timestamp;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public String getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonDescription() {
        return reasonDescription;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "StatusReportMessage{" +
                "originalMessageId='" + originalMessageId + '\'' +
                ", status='" + status + '\'' +
                ", reasonCode='" + reasonCode + '\'' +
                ", reasonDescription='" + reasonDescription + '\'' +
                ", transactionId=" + transactionId +
                ", timestamp=" + timestamp +
                '}';
    }
}
