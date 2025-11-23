package com.example.backend.exception;

public class AccountNotFoundException extends PaymentException {
    public AccountNotFoundException(String message) {
        super(message, "ACCOUNT_NOT_FOUND");
    }
}
