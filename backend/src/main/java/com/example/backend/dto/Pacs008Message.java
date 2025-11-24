package com.example.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal representation of a parsed and validated pacs.008 message.
 * Provides convenient access to key fields needed for transfer processing.
 */
public class Pacs008Message {

    private final UUID messageId;
    private final String groupHeaderMsgId;
    private final LocalDateTime creationDateTime;
    private final BigDecimal amount;
    private final String currency;
    private final String senderName;
    private final String senderIban;
    private final String receiverName;
    private final String receiverIban;
    private final String chargeBearer;
    private final String endToEndId;

    public Pacs008Message(UUID messageId, String groupHeaderMsgId, LocalDateTime creationDateTime,
                         BigDecimal amount, String currency, String senderName, String senderIban,
                         String receiverName, String receiverIban, String chargeBearer, String endToEndId) {
        this.messageId = messageId;
        this.groupHeaderMsgId = groupHeaderMsgId;
        this.creationDateTime = creationDateTime;
        this.amount = amount;
        this.currency = currency;
        this.senderName = senderName;
        this.senderIban = senderIban;
        this.receiverName = receiverName;
        this.receiverIban = receiverIban;
        this.chargeBearer = chargeBearer;
        this.endToEndId = endToEndId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getGroupHeaderMsgId() {
        return groupHeaderMsgId;
    }

    public LocalDateTime getCreationDateTime() {
        return creationDateTime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderIban() {
        return senderIban;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverIban() {
        return receiverIban;
    }

    public String getChargeBearer() {
        return chargeBearer;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    @Override
    public String toString() {
        return "Pacs008Message{" +
                "messageId=" + messageId +
                ", groupHeaderMsgId='" + groupHeaderMsgId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", senderName='" + senderName + '\'' +
                ", senderIban='" + senderIban + '\'' +
                ", receiverName='" + receiverName + '\'' +
                ", receiverIban='" + receiverIban + '\'' +
                '}';
    }
}
