package com.example.backend.exception;

/**
 * Exception thrown when message processing fails in the Gateway.
 * Used for XML validation errors, parsing errors, and other message-level issues.
 */
public class MessageProcessingException extends RuntimeException {

    private final String errorCode;
    private final String originalMessageId;

    public MessageProcessingException(String message, String errorCode, String originalMessageId) {
        super(message);
        this.errorCode = errorCode;
        this.originalMessageId = originalMessageId;
    }

    public MessageProcessingException(String message, String errorCode, String originalMessageId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.originalMessageId = originalMessageId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }
}
