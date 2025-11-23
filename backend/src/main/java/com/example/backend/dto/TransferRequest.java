package com.example.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class TransferRequest {
    private UUID msgId;
    private String senderIban;
    private String receiverIban;
    private BigDecimal amount;
    private String currency;

    public UUID getMsgId() {
        return msgId;
    }
    public String getSenderIban() {
        return senderIban;
    }
    public String getReceiverIban() {
        return receiverIban;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getCurrency() {
        return currency;
    }
}
