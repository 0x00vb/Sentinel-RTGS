package com.example.backend.repository;

import com.example.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for LedgerEntryRepository using @SpringBootTest
 * Tests repository operations against an H2 in-memory database
 */
@SpringBootTest
@DirtiesContext
class LedgerEntryRepositoryTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void shouldSaveAndRetrieveLedgerEntryWithAllFields() {
        // Given - Create test accounts and transfer
        Account debitAccount = createTestAccount("DEBIT_ACC", "Debit Account", "EUR", "1000.00");
        Account creditAccount = createTestAccount("CREDIT_ACC", "Credit Account", "EUR", "500.00");

        Transfer transfer = createTestTransfer(UUID.randomUUID(), debitAccount, creditAccount, "250.00", Transfer.TransferStatus.CLEARED);
        Transfer savedTransfer = transferRepository.save(transfer);

        // Given - Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(debitAccount, savedTransfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("250.00"));
        LedgerEntry creditEntry = new LedgerEntry(creditAccount, savedTransfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("250.00"));

        // When
        LedgerEntry savedDebit = ledgerEntryRepository.save(debitEntry);
        LedgerEntry savedCredit = ledgerEntryRepository.save(creditEntry);

        // Then - Verify debit entry
        assertThat(savedDebit.getId()).isNotNull();
        assertThat(savedDebit.getAccount().getId()).isEqualTo(debitAccount.getId());
        assertThat(savedDebit.getTransfer().getId()).isEqualTo(savedTransfer.getId());
        assertThat(savedDebit.getEntryType()).isEqualTo(LedgerEntry.EntryType.DEBIT);
        assertThat(savedDebit.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(savedDebit.getCreatedAt()).isNotNull();

        // Then - Verify credit entry
        assertThat(savedCredit.getId()).isNotNull();
        assertThat(savedCredit.getAccount().getId()).isEqualTo(creditAccount.getId());
        assertThat(savedCredit.getTransfer().getId()).isEqualTo(savedTransfer.getId());
        assertThat(savedCredit.getEntryType()).isEqualTo(LedgerEntry.EntryType.CREDIT);
        assertThat(savedCredit.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(savedCredit.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindLedgerEntriesByTransfer() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("SOURCE_LEDGER", "Source", "USD", "2000.00");
        Account destAccount = createTestAccount("DEST_LEDGER", "Dest", "USD", "1000.00");

        final Transfer transfer = transferRepository.save(
            createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "500.00", Transfer.TransferStatus.CLEARED)
        );

        // Given - Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, transfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("500.00"));
        LedgerEntry creditEntry = new LedgerEntry(destAccount, transfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("500.00"));
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // When
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransfer(transfer);

        // Then
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)).isTrue();
        assertThat(entries.stream().anyMatch(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)).isTrue();
        assertThat(entries.stream().allMatch(e -> e.getTransfer().getId().equals(transfer.getId()))).isTrue();
    }

    @Test
    void shouldFindLedgerEntriesByAccount() {
        // Given - Create test accounts
        Account account1 = createTestAccount("ACC1", "Account 1", "EUR", "3000.00");
        Account account2 = createTestAccount("ACC2", "Account 2", "EUR", "2000.00");

        // Given - Create transfers and ledger entries
        Transfer transfer1 = createTestTransfer(UUID.randomUUID(), account1, account2, "100.00", Transfer.TransferStatus.CLEARED);
        Transfer transfer2 = createTestTransfer(UUID.randomUUID(), account2, account1, "200.00", Transfer.TransferStatus.CLEARED);
        transfer1 = transferRepository.save(transfer1);
        transfer2 = transferRepository.save(transfer2);

        // Create entries for account1
        LedgerEntry debitEntry1 = new LedgerEntry(account1, transfer1, LedgerEntry.EntryType.DEBIT, new BigDecimal("100.00"));
        LedgerEntry creditEntry2 = new LedgerEntry(account1, transfer2, LedgerEntry.EntryType.CREDIT, new BigDecimal("200.00"));
        ledgerEntryRepository.save(debitEntry1);
        ledgerEntryRepository.save(creditEntry2);

        // When
        List<LedgerEntry> account1Entries = ledgerEntryRepository.findByAccountOrderByCreatedAtDesc(account1);

        // Then
        assertThat(account1Entries).hasSize(2);
        assertThat(account1Entries.stream().allMatch(e -> e.getAccount().getId().equals(account1.getId()))).isTrue();
        // Should be ordered by createdAt descending
        assertThat(account1Entries.get(0).getCreatedAt()).isAfterOrEqualTo(account1Entries.get(1).getCreatedAt());
    }

    @Test
    void shouldVerifyTransferBalanceForBalancedTransfer() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("BALANCE_SRC", "Source", "GBP", "10000.00");
        Account destAccount = createTestAccount("BALANCE_DST", "Dest", "GBP", "5000.00");

        Transfer transfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "750.00", Transfer.TransferStatus.CLEARED);
        transfer = transferRepository.save(transfer);

        // Given - Create balanced ledger entries (debit + credit should sum to zero)
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, transfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("750.00"));
        LedgerEntry creditEntry = new LedgerEntry(destAccount, transfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("750.00"));
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // When
        BigDecimal balance = ledgerEntryRepository.verifyTransferBalance(transfer.getId());

        // Then - Should be zero for balanced transfer
        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldVerifyTransferBalanceForUnbalancedTransfer() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("UNBALANCE_SRC", "Source", "JPY", "100000.00");
        Account destAccount = createTestAccount("UNBALANCE_DST", "Dest", "JPY", "50000.00");

        Transfer transfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "10000.00", Transfer.TransferStatus.CLEARED);
        transfer = transferRepository.save(transfer);

        // Given - Create unbalanced ledger entries (debit != credit)
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, transfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("10000.00"));
        LedgerEntry creditEntry = new LedgerEntry(destAccount, transfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("9000.00")); // Wrong amount
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // When
        BigDecimal balance = ledgerEntryRepository.verifyTransferBalance(transfer.getId());

        // Then - Should not be zero (unbalanced)
        assertThat(balance).isNotEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance).isEqualByComparingTo(new BigDecimal("-1000.00")); // Debit: -10000, Credit: +9000 = -1000
    }

    @Test
    void shouldCalculateBalanceFromLedgerEntries() {
        // Given - Create test account
        Account account = createTestAccount("CALC_BALANCE", "Balance Test", "EUR", "0.00");

        // Given - Create multiple transfers and ledger entries
        Account otherAccount1 = createTestAccount("OTHER1", "Other 1", "EUR", "0.00");
        Account otherAccount2 = createTestAccount("OTHER2", "Other 2", "EUR", "0.00");

        Transfer transfer1 = createTestTransfer(UUID.randomUUID(), account, otherAccount1, "500.00", Transfer.TransferStatus.CLEARED);
        Transfer transfer2 = createTestTransfer(UUID.randomUUID(), otherAccount2, account, "300.00", Transfer.TransferStatus.CLEARED);
        transfer1 = transferRepository.save(transfer1);
        transfer2 = transferRepository.save(transfer2);

        // Create ledger entries for account
        // Transfer 1: account debits 500 (money out)
        LedgerEntry debitEntry1 = new LedgerEntry(account, transfer1, LedgerEntry.EntryType.DEBIT, new BigDecimal("500.00"));
        // Transfer 2: account credits 300 (money in)
        LedgerEntry creditEntry2 = new LedgerEntry(account, transfer2, LedgerEntry.EntryType.CREDIT, new BigDecimal("300.00"));

        ledgerEntryRepository.save(debitEntry1);
        ledgerEntryRepository.save(creditEntry2);

        // When
        BigDecimal calculatedBalance = ledgerEntryRepository.calculateBalanceFromLedger(account);

        // Then - Balance should be credits - debits = 300 - 500 = -200
        assertThat(calculatedBalance).isEqualByComparingTo(new BigDecimal("-200.00"));
    }

    @Test
    void shouldFindLedgerEntriesByTransferAndAccount() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("SRC_COMBO", "Source", "USD", "5000.00");
        Account destAccount = createTestAccount("DST_COMBO", "Dest", "USD", "3000.00");

        Transfer transfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "1000.00", Transfer.TransferStatus.CLEARED);
        transfer = transferRepository.save(transfer);

        // Given - Create ledger entries
        LedgerEntry sourceDebit = new LedgerEntry(sourceAccount, transfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("1000.00"));
        LedgerEntry destCredit = new LedgerEntry(destAccount, transfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("1000.00"));
        ledgerEntryRepository.save(sourceDebit);
        ledgerEntryRepository.save(destCredit);

        // When
        List<LedgerEntry> sourceEntries = ledgerEntryRepository.findByTransferAndAccount(transfer, sourceAccount);
        List<LedgerEntry> destEntries = ledgerEntryRepository.findByTransferAndAccount(transfer, destAccount);

        // Then
        assertThat(sourceEntries).hasSize(1);
        assertThat(sourceEntries.get(0).getEntryType()).isEqualTo(LedgerEntry.EntryType.DEBIT);
        assertThat(sourceEntries.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));

        assertThat(destEntries).hasSize(1);
        assertThat(destEntries.get(0).getEntryType()).isEqualTo(LedgerEntry.EntryType.CREDIT);
        assertThat(destEntries.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void shouldCountEntriesByTransfer() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("COUNT_SRC", "Source", "EUR", "8000.00");
        Account destAccount = createTestAccount("COUNT_DST", "Dest", "EUR", "4000.00");

        Transfer transfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "600.00", Transfer.TransferStatus.CLEARED);
        transfer = transferRepository.save(transfer);

        // Given - Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, transfer, LedgerEntry.EntryType.DEBIT, new BigDecimal("600.00"));
        LedgerEntry creditEntry = new LedgerEntry(destAccount, transfer, LedgerEntry.EntryType.CREDIT, new BigDecimal("600.00"));
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // When
        long entryCount = ledgerEntryRepository.countByTransfer(transfer);

        // Then
        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void shouldFindTransfersByAccount() {
        // Given - Create test accounts
        Account account = createTestAccount("TRANSFERS_ACC", "Main Account", "GBP", "10000.00");
        Account otherAccount = createTestAccount("OTHER_ACC", "Other Account", "GBP", "5000.00");

        // Given - Create multiple transfers involving the account
        final Transfer transfer1 = transferRepository.save(
            createTestTransfer(UUID.randomUUID(), account, otherAccount, "1000.00", Transfer.TransferStatus.CLEARED)
        );
        final Transfer transfer2 = transferRepository.save(
            createTestTransfer(UUID.randomUUID(), otherAccount, account, "500.00", Transfer.TransferStatus.CLEARED)
        );

        // Create ledger entries
        ledgerEntryRepository.save(new LedgerEntry(account, transfer1, LedgerEntry.EntryType.DEBIT, new BigDecimal("1000.00")));
        ledgerEntryRepository.save(new LedgerEntry(account, transfer2, LedgerEntry.EntryType.CREDIT, new BigDecimal("500.00")));

        // When
        List<Transfer> transfers = ledgerEntryRepository.findTransfersByAccount(account);

        // Then
        assertThat(transfers).hasSize(2);
        assertThat(transfers.stream().anyMatch(t -> t.getId().equals(transfer1.getId()))).isTrue();
        assertThat(transfers.stream().anyMatch(t -> t.getId().equals(transfer2.getId()))).isTrue();
    }

    // Helper methods
    private Account createTestAccount(String iban, String ownerName, String currency, String balance) {
        Account account = new Account();
        account.setIban(iban);
        account.setOwnerName(ownerName);
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        return accountRepository.save(account);
    }

    private Transfer createTestTransfer(java.util.UUID msgId, Account source, Account dest, String amount, Transfer.TransferStatus status) {
        Transfer transfer = new Transfer();
        transfer.setMsgId(msgId);
        transfer.setExternalReference("EXT-" + msgId.toString().substring(0, 8));
        transfer.setSource(source);
        transfer.setDestination(dest);
        transfer.setAmount(new BigDecimal(amount));
        transfer.setStatus(status);
        transfer.setCreatedAt(LocalDateTime.now());
        return transfer;
    }
}
