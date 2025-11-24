package com.example.backend.exception;

/**
 * Exception thrown when XML validation against XSD schema fails.
 * Provides detailed information about validation errors.
 */
public class XmlValidationException extends MessageProcessingException {

    public XmlValidationException(String message, String originalMessageId) {
        super(message, "XML001", originalMessageId);
    }

    public XmlValidationException(String message, String originalMessageId, Throwable cause) {
        super(message, "XML001", originalMessageId, cause);
    }
}
