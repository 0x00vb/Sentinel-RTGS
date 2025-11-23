package com.example.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

@Entity
@Table(name = "ledger_entries") 
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryType entryType;
    public enum EntryType {
        DEBIT,
        CREDIT
    }

    @Column(nullable = false)
    private BigDecimal amount; 

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Default constructor for JPA
    protected LedgerEntry() {}

    public LedgerEntry(Account account, Transfer transfer, EntryType entryType, BigDecimal amount) {
        this.account = Objects.requireNonNull(account, "Account cannot be null");
        this.transfer = Objects.requireNonNull(transfer, "Transfer cannot be null");
        this.entryType = Objects.requireNonNull(entryType, "Entry type cannot be null");
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }
    public Account getAccount() {
        return account;
    }
    public Transfer getTransfer() {
        return transfer;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
