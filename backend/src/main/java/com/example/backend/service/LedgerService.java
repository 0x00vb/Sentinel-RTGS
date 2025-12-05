package com.example.backend.service;

import com.example.backend.entity.Account;
import com.example.backend.entity.LedgerEntry;
import com.example.backend.entity.Transfer;
import com.example.backend.repository.AccountRepository;
import com.example.backend.repository.LedgerEntryRepository;
import com.example.backend.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for ledger operations and financial calculations.
 * Provides KPIs, ledger entry queries, and T-account visualization data.
 */
@Service
public class LedgerService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    /**
     * Calculate financial KPIs: Total Assets, Total Liabilities, Net Worth, Active Accounts.
     * In double-entry accounting:
     * - Assets = sum of all account balances where balance > 0
     * - Liabilities = sum of all account balances where balance < 0 (negative balances)
     * - Net Worth = Assets - Liabilities
     */
    @Transactional(readOnly = true)
    public FinancialKPIs calculateFinancialKPIs() {
        List<Account> allAccounts = accountRepository.findAll();
        
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        long activeAccounts = 0;
        
        for (Account account : allAccounts) {
            BigDecimal balance = account.getBalance();
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                totalAssets = totalAssets.add(balance);
                activeAccounts++;
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                totalLiabilities = totalLiabilities.add(balance.abs());
                activeAccounts++;
            }
        }
        
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);
        
        return new FinancialKPIs(totalAssets, totalLiabilities, netWorth, activeAccounts);
    }

    /**
     * Get paginated ledger entries with running balance calculation.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryDTO> getLedgerEntries(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<LedgerEntry> entries = ledgerEntryRepository.findAll(pageable);
        
        // Calculate running balances per account chronologically
        // We need to process all entries in chronological order to get accurate running balances
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll(Sort.by("createdAt").ascending());
        Map<Long, BigDecimal> accountBalances = new HashMap<>();
        Map<Long, BigDecimal> entryBalances = new HashMap<>();
        
        // Build map of entryId -> running balance after that entry
        for (LedgerEntry entry : allEntries) {
            Long accountId = entry.getAccount().getId();
            BigDecimal currentBalance = accountBalances.getOrDefault(accountId, BigDecimal.ZERO);
            
            BigDecimal newBalance;
            if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                newBalance = currentBalance.subtract(entry.getAmount());
            } else {
                newBalance = currentBalance.add(entry.getAmount());
            }
            
            accountBalances.put(accountId, newBalance);
            entryBalances.put(entry.getId(), newBalance);
        }
        
        // Convert to DTOs with running balance
        List<LedgerEntryDTO> dtos = entries.getContent().stream()
            .map(entry -> {
                BigDecimal runningBalance = entryBalances.getOrDefault(entry.getId(), BigDecimal.ZERO);
                return new LedgerEntryDTO(
                    entry.getId(),
                    entry.getTransfer().getId(),
                    entry.getTransfer().getMsgId().toString(),
                    entry.getAccount().getId(),
                    entry.getAccount().getIban(),
                    entry.getAccount().getOwnerName(),
                    entry.getTransfer().getSource().getIban(),
                    entry.getTransfer().getDestination().getIban(),
                    entry.getEntryType(),
                    entry.getAmount(),
                    entry.getAccount().getCurrency(),
                    runningBalance,
                    entry.getCreatedAt(),
                    entry.getTransfer().getStatus()
                );
            })
            .collect(Collectors.toList());
        
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, entries.getTotalElements());
    }

    /**
     * Get T-account data for a specific account (debits and credits with running balance).
     */
    @Transactional(readOnly = true)
    public TAccountData getTAccountData(Long accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountOrderByCreatedAtDesc(account);
        Collections.reverse(entries); // Reverse to get ascending order
        
        List<TAccountEntry> debitEntries = new ArrayList<>();
        List<TAccountEntry> creditEntries = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        
        for (LedgerEntry entry : entries) {
            TAccountEntry tEntry = new TAccountEntry(
                entry.getId(),
                entry.getTransfer().getId(),
                entry.getTransfer().getMsgId().toString(),
                entry.getAmount(),
                entry.getCreatedAt(),
                entry.getTransfer().getStatus()
            );
            
            if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                runningBalance = runningBalance.subtract(entry.getAmount());
                tEntry.setRunningBalance(runningBalance);
                debitEntries.add(tEntry);
            } else {
                runningBalance = runningBalance.add(entry.getAmount());
                tEntry.setRunningBalance(runningBalance);
                creditEntries.add(tEntry);
            }
        }
        
        BigDecimal finalBalance = runningBalance;
        
        return new TAccountData(
            account.getId(),
            account.getIban(),
            account.getOwnerName(),
            account.getCurrency(),
            account.getBalance(),
            finalBalance,
            debitEntries,
            creditEntries
        );
    }

    /**
     * Get all accounts for selection.
     */
    @Transactional(readOnly = true)
    public List<AccountSummaryDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
            .map(account -> new AccountSummaryDTO(
                account.getId(),
                account.getIban(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance()
            ))
            .collect(Collectors.toList());
    }

    // DTO Classes

    public static class FinancialKPIs {
        private final BigDecimal totalAssets;
        private final BigDecimal totalLiabilities;
        private final BigDecimal netWorth;
        private final long activeAccounts;

        public FinancialKPIs(BigDecimal totalAssets, BigDecimal totalLiabilities, 
                            BigDecimal netWorth, long activeAccounts) {
            this.totalAssets = totalAssets;
            this.totalLiabilities = totalLiabilities;
            this.netWorth = netWorth;
            this.activeAccounts = activeAccounts;
        }

        public BigDecimal getTotalAssets() { return totalAssets; }
        public BigDecimal getTotalLiabilities() { return totalLiabilities; }
        public BigDecimal getNetWorth() { return netWorth; }
        public long getActiveAccounts() { return activeAccounts; }
    }

    public static class LedgerEntryDTO {
        private final Long id;
        private final Long transferId;
        private final String transactionId;
        private final Long accountId;
        private final String accountIban;
        private final String accountOwner;
        private final String debitAccount;
        private final String creditAccount;
        private final LedgerEntry.EntryType entryType;
        private final BigDecimal amount;
        private final String currency;
        private final BigDecimal runningBalance;
        private final LocalDateTime timestamp;
        private final Transfer.TransferStatus complianceStatus;

        public LedgerEntryDTO(Long id, Long transferId, String transactionId, Long accountId,
                             String accountIban, String accountOwner, String debitAccount,
                             String creditAccount, LedgerEntry.EntryType entryType, BigDecimal amount,
                             String currency, BigDecimal runningBalance, LocalDateTime timestamp,
                             Transfer.TransferStatus complianceStatus) {
            this.id = id;
            this.transferId = transferId;
            this.transactionId = transactionId;
            this.accountId = accountId;
            this.accountIban = accountIban;
            this.accountOwner = accountOwner;
            this.debitAccount = debitAccount;
            this.creditAccount = creditAccount;
            this.entryType = entryType;
            this.amount = amount;
            this.currency = currency;
            this.runningBalance = runningBalance;
            this.timestamp = timestamp;
            this.complianceStatus = complianceStatus;
        }

        // Getters
        public Long getId() { return id; }
        public Long getTransferId() { return transferId; }
        public String getTransactionId() { return transactionId; }
        public Long getAccountId() { return accountId; }
        public String getAccountIban() { return accountIban; }
        public String getAccountOwner() { return accountOwner; }
        public String getDebitAccount() { return debitAccount; }
        public String getCreditAccount() { return creditAccount; }
        public LedgerEntry.EntryType getEntryType() { return entryType; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public BigDecimal getRunningBalance() { return runningBalance; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Transfer.TransferStatus getComplianceStatus() { return complianceStatus; }
    }

    public static class TAccountData {
        private final Long accountId;
        private final String iban;
        private final String ownerName;
        private final String currency;
        private final BigDecimal accountBalance;
        private final BigDecimal calculatedBalance;
        private final List<TAccountEntry> debits;
        private final List<TAccountEntry> credits;

        public TAccountData(Long accountId, String iban, String ownerName, String currency,
                           BigDecimal accountBalance, BigDecimal calculatedBalance,
                           List<TAccountEntry> debits, List<TAccountEntry> credits) {
            this.accountId = accountId;
            this.iban = iban;
            this.ownerName = ownerName;
            this.currency = currency;
            this.accountBalance = accountBalance;
            this.calculatedBalance = calculatedBalance;
            this.debits = debits;
            this.credits = credits;
        }

        // Getters
        public Long getAccountId() { return accountId; }
        public String getIban() { return iban; }
        public String getOwnerName() { return ownerName; }
        public String getCurrency() { return currency; }
        public BigDecimal getAccountBalance() { return accountBalance; }
        public BigDecimal getCalculatedBalance() { return calculatedBalance; }
        public List<TAccountEntry> getDebits() { return debits; }
        public List<TAccountEntry> getCredits() { return credits; }
    }

    public static class TAccountEntry {
        private final Long entryId;
        private final Long transferId;
        private final String transactionId;
        private final BigDecimal amount;
        private final LocalDateTime timestamp;
        private final Transfer.TransferStatus status;
        private BigDecimal runningBalance;

        public TAccountEntry(Long entryId, Long transferId, String transactionId,
                           BigDecimal amount, LocalDateTime timestamp,
                           Transfer.TransferStatus status) {
            this.entryId = entryId;
            this.transferId = transferId;
            this.transactionId = transactionId;
            this.amount = amount;
            this.timestamp = timestamp;
            this.status = status;
        }

        // Getters and setters
        public Long getEntryId() { return entryId; }
        public Long getTransferId() { return transferId; }
        public String getTransactionId() { return transactionId; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Transfer.TransferStatus getStatus() { return status; }
        public BigDecimal getRunningBalance() { return runningBalance; }
        public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }
    }

    public static class AccountSummaryDTO {
        private final Long id;
        private final String iban;
        private final String ownerName;
        private final String currency;
        private final BigDecimal balance;

        public AccountSummaryDTO(Long id, String iban, String ownerName, String currency, BigDecimal balance) {
            this.id = id;
            this.iban = iban;
            this.ownerName = ownerName;
            this.currency = currency;
            this.balance = balance;
        }

        // Getters
        public Long getId() { return id; }
        public String getIban() { return iban; }
        public String getOwnerName() { return ownerName; }
        public String getCurrency() { return currency; }
        public BigDecimal getBalance() { return balance; }
    }

}

