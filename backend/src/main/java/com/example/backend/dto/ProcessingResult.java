package com.example.backend.dto;

import com.example.backend.entity.Transfer;

/**
 * Represents the result of processing a pacs.008 message through the gateway pipeline.
 */
public class ProcessingResult {

    public enum Status {
        SUCCESS,
        DUPLICATE,
        INVALID_XML,
        BLOCKED_SANCTIONS,
        PROCESSING_ERROR
    }

    private final Status status;
    private final Transfer transfer;
    private final String errorMessage;
    private final String errorCode;

    // Success result
    public static ProcessingResult success(Transfer transfer) {
        return new ProcessingResult(Status.SUCCESS, transfer, null, null);
    }

    // Duplicate result
    public static ProcessingResult duplicate(Transfer existingTransfer) {
        return new ProcessingResult(Status.DUPLICATE, existingTransfer, null, null);
    }

    // Invalid XML result
    public static ProcessingResult invalidXml(String errorMessage) {
        return new ProcessingResult(Status.INVALID_XML, null, errorMessage, "XML001");
    }

    // Blocked sanctions result
    public static ProcessingResult blockedSanctions(Transfer transfer, String reason) {
        return new ProcessingResult(Status.BLOCKED_SANCTIONS, transfer, reason, "SANC001");
    }

    // Processing error result
    public static ProcessingResult processingError(String errorMessage, String errorCode) {
        return new ProcessingResult(Status.PROCESSING_ERROR, null, errorMessage, errorCode);
    }

    private ProcessingResult(Status status, Transfer transfer, String errorMessage, String errorCode) {
        this.status = status;
        this.transfer = transfer;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public Status getStatus() {
        return status;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isSuccessful() {
        return status == Status.SUCCESS;
    }

    public boolean shouldSendStatusReport() {
        return status != Status.SUCCESS && status != Status.DUPLICATE;
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "status=" + status +
                ", transfer=" + (transfer != null ? transfer.getId() : "null") +
                ", errorMessage='" + errorMessage + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
