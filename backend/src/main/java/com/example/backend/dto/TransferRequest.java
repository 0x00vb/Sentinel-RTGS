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

    public void setMsgId(UUID msgId) {
        this.msgId = msgId;
    }

    public String getSenderIban() {
        return senderIban;
    }

    public void setSenderIban(String senderIban) {
        this.senderIban = senderIban;
    }

    public String getReceiverIban() {
        return receiverIban;
    }

    public void setReceiverIban(String receiverIban) {
        this.receiverIban = receiverIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
