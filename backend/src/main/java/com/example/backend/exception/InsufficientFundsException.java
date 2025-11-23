package com.example.backend.exception;

public class InsufficientFundsException extends PaymentException {
    public InsufficientFundsException(String message) {
        super(message, "INSUFFICIENT_FUNDS");
    }
}
