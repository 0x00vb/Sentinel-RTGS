package com.example.backend.exception;

public class InvalidTransferException extends PaymentException {
    public InvalidTransferException(String message) {
        super(message, "INVALID_TRANSFER");
    }
}
