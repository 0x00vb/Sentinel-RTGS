package com.example.backend.exception;

public class TransferNotFoundException extends PaymentException {
    public TransferNotFoundException(String message) {
        super(message, "TRANSFER_NOT_FOUND");
    }
}
