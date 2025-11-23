package com.example.backend.repository;

import com.example.backend.entity.Account;
import com.example.backend.entity.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TransferRepository using @SpringBootTest
 * Tests repository operations against an H2 in-memory database
 */
@SpringBootTest
@DirtiesContext
class TransferRepositoryTest {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAndRetrieveTransferWithAllFields() {
        // Given - Create test accounts first
        Account sourceAccount = new Account();
        sourceAccount.setIban("GB29 NWBK 6016 1331 9268 19");
        sourceAccount.setOwnerName("Source User");
        sourceAccount.setCurrency("USD");
        sourceAccount.setBalance(new BigDecimal("1000.00"));
        sourceAccount = accountRepository.save(sourceAccount);

        Account destAccount = new Account();
        destAccount.setIban("DE89370400440532013000");
        destAccount.setOwnerName("Dest User");
        destAccount.setCurrency("USD");
        destAccount.setBalance(new BigDecimal("500.00"));
        destAccount = accountRepository.save(destAccount);

        // Given - Create transfer
        Transfer transfer = new Transfer();
        UUID msgId = UUID.randomUUID();
        transfer.setMsgId(msgId);
        transfer.setExternalReference("EXT-REF-123");
        transfer.setSource(sourceAccount);
        transfer.setDestination(destAccount);
        transfer.setAmount(new BigDecimal("250.00"));
        transfer.setStatus(Transfer.TransferStatus.PENDING);
        transfer.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        transfer.setCompletedAt(null);

        // When
        Transfer savedTransfer = transferRepository.save(transfer);

        // Then
        assertThat(savedTransfer.getId()).isNotNull();
        assertThat(savedTransfer.getMsgId()).isEqualTo(msgId);
        assertThat(savedTransfer.getExternalReference()).isEqualTo("EXT-REF-123");
        assertThat(savedTransfer.getSource().getId()).isEqualTo(sourceAccount.getId());
        assertThat(savedTransfer.getDestination().getId()).isEqualTo(destAccount.getId());
        assertThat(savedTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(savedTransfer.getStatus()).isEqualTo(Transfer.TransferStatus.PENDING);
        assertThat(savedTransfer.getCreatedAt()).isNotNull();
        assertThat(savedTransfer.getCompletedAt()).isNull();

        // Verify retrieval
        Optional<Transfer> retrieved = transferRepository.findById(savedTransfer.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).usingRecursiveComparison()
                .ignoringFields("source", "destination", "createdAt", "completedAt") // timestamps and relationships may differ slightly
                .isEqualTo(savedTransfer);
    }

    @Test
    void shouldFindTransferByMsgId() {
        // Given - Create test accounts and transfer
        Account sourceAccount = createTestAccount("SOURCE001", "Source", "EUR", "1000.00");
        Account destAccount = createTestAccount("DEST001", "Dest", "EUR", "500.00");

        UUID msgId = UUID.randomUUID();
        Transfer transfer = createTestTransfer(msgId, sourceAccount, destAccount, "100.00", Transfer.TransferStatus.PENDING);
        transferRepository.save(transfer);

        // When
        Optional<Transfer> found = transferRepository.findByMsgId(msgId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getMsgId()).isEqualTo(msgId);
        assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldReturnEmptyWhenMsgIdNotFound() {
        // When
        Optional<Transfer> found = transferRepository.findByMsgId(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceMsgIdUniquenessConstraint() {
        // Given - Create first transfer
        Account sourceAccount = createTestAccount("SOURCE002", "Source", "USD", "2000.00");
        Account destAccount = createTestAccount("DEST002", "Dest", "USD", "1000.00");

        UUID duplicateMsgId = UUID.randomUUID();
        Transfer transfer1 = createTestTransfer(duplicateMsgId, sourceAccount, destAccount, "500.00", Transfer.TransferStatus.PENDING);
        transferRepository.save(transfer1);

        // Given - Try to create second transfer with same msg_id
        Transfer transfer2 = createTestTransfer(duplicateMsgId, sourceAccount, destAccount, "300.00", Transfer.TransferStatus.PENDING);

        // When & Then - Should throw DataIntegrityViolationException due to unique constraint
        assertThatThrownBy(() -> transferRepository.saveAndFlush(transfer2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void shouldFindTransfersByStatus() {
        // Given - Create test accounts
        Account sourceAccount = createTestAccount("SOURCE003", "Source", "GBP", "3000.00");
        Account destAccount = createTestAccount("DEST003", "Dest", "GBP", "2000.00");

        // Given - Create transfers with different statuses
        Transfer pendingTransfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "100.00", Transfer.TransferStatus.PENDING);
        Transfer clearedTransfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "200.00", Transfer.TransferStatus.CLEARED);
        Transfer blockedTransfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "50.00", Transfer.TransferStatus.BLOCKED_AML);

        transferRepository.save(pendingTransfer);
        transferRepository.save(clearedTransfer);
        transferRepository.save(blockedTransfer);

        // When
        List<Transfer> pendingTransfers = transferRepository.findByStatus(Transfer.TransferStatus.PENDING);
        List<Transfer> clearedTransfers = transferRepository.findByStatus(Transfer.TransferStatus.CLEARED);
        List<Transfer> blockedTransfers = transferRepository.findByStatus(Transfer.TransferStatus.BLOCKED_AML);

        // Then
        assertThat(pendingTransfers).hasSizeGreaterThanOrEqualTo(1);
        assertThat(clearedTransfers).hasSizeGreaterThanOrEqualTo(1);
        assertThat(blockedTransfers).hasSizeGreaterThanOrEqualTo(1);

        assertThat(pendingTransfers.stream().allMatch(t -> t.getStatus() == Transfer.TransferStatus.PENDING)).isTrue();
        assertThat(clearedTransfers.stream().allMatch(t -> t.getStatus() == Transfer.TransferStatus.CLEARED)).isTrue();
        assertThat(blockedTransfers.stream().allMatch(t -> t.getStatus() == Transfer.TransferStatus.BLOCKED_AML)).isTrue();
    }

    @Test
    void shouldFindBlockedTransfersForReview() {
        // Given - Create test accounts
        Account sourceAccount = createTestAccount("SOURCE004", "Source", "EUR", "5000.00");
        Account destAccount = createTestAccount("DEST004", "Dest", "EUR", "3000.00");

        // Given - Create blocked transfers
        Transfer blockedTransfer1 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "100.00", Transfer.TransferStatus.BLOCKED_AML);
        Transfer blockedTransfer2 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "200.00", Transfer.TransferStatus.BLOCKED_AML);
        Transfer clearedTransfer = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "300.00", Transfer.TransferStatus.CLEARED);

        transferRepository.save(blockedTransfer1);
        transferRepository.save(blockedTransfer2);
        transferRepository.save(clearedTransfer);

        // When
        List<Transfer> blockedForReview = transferRepository.findBlockedForReview();

        // Then
        assertThat(blockedForReview).hasSizeGreaterThanOrEqualTo(2);
        assertThat(blockedForReview.stream().allMatch(t -> t.getStatus() == Transfer.TransferStatus.BLOCKED_AML)).isTrue();
        // Should be ordered by createdAt ascending for FIFO processing
        assertThat(blockedForReview.get(0).getCreatedAt()).isBeforeOrEqualTo(blockedForReview.get(1).getCreatedAt());
    }

    @Test
    void shouldCountTransfersByStatus() {
        // Given - Create test accounts
        Account sourceAccount = createTestAccount("SOURCE005", "Source", "USD", "10000.00");
        Account destAccount = createTestAccount("DEST005", "Dest", "USD", "5000.00");

        // Given - Create transfers with different statuses
        Transfer pending1 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "100.00", Transfer.TransferStatus.PENDING);
        Transfer pending2 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "200.00", Transfer.TransferStatus.PENDING);
        Transfer cleared = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "300.00", Transfer.TransferStatus.CLEARED);

        transferRepository.save(pending1);
        transferRepository.save(pending2);
        transferRepository.save(cleared);

        // When
        long pendingCount = transferRepository.countByStatus(Transfer.TransferStatus.PENDING);
        long clearedCount = transferRepository.countByStatus(Transfer.TransferStatus.CLEARED);
        long blockedCount = transferRepository.countByStatus(Transfer.TransferStatus.BLOCKED_AML);

        // Then
        assertThat(pendingCount).isGreaterThanOrEqualTo(2);
        assertThat(clearedCount).isGreaterThanOrEqualTo(1);
        assertThat(blockedCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldCalculateTotalAmountByStatus() {
        // Given - Create test accounts
        Account sourceAccount = createTestAccount("SOURCE006", "Source", "EUR", "20000.00");
        Account destAccount = createTestAccount("DEST006", "Dest", "EUR", "10000.00");

        // Given - Create transfers
        Transfer cleared1 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "500.00", Transfer.TransferStatus.CLEARED);
        Transfer cleared2 = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "750.00", Transfer.TransferStatus.CLEARED);
        Transfer pending = createTestTransfer(UUID.randomUUID(), sourceAccount, destAccount, "1000.00", Transfer.TransferStatus.PENDING);

        transferRepository.save(cleared1);
        transferRepository.save(cleared2);
        transferRepository.save(pending);

        // When
        java.math.BigDecimal totalCleared = transferRepository.getTotalAmountByStatus(Transfer.TransferStatus.CLEARED);
        java.math.BigDecimal totalPending = transferRepository.getTotalAmountByStatus(Transfer.TransferStatus.PENDING);

        // Then
        assertThat(totalCleared).isGreaterThanOrEqualTo(new BigDecimal("1250.00")); // 500 + 750
        assertThat(totalPending).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void shouldReturnTrueWhenMsgIdExists() {
        // Given - Create transfer
        Account sourceAccount = createTestAccount("SOURCE007", "Source", "GBP", "5000.00");
        Account destAccount = createTestAccount("DEST007", "Dest", "GBP", "3000.00");

        UUID msgId = UUID.randomUUID();
        Transfer transfer = createTestTransfer(msgId, sourceAccount, destAccount, "250.00", Transfer.TransferStatus.PENDING);
        transferRepository.save(transfer);

        // When & Then
        assertThat(transferRepository.existsByMsgId(msgId)).isTrue();
        assertThat(transferRepository.existsByMsgId(UUID.randomUUID())).isFalse();
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

    private Transfer createTestTransfer(UUID msgId, Account source, Account dest, String amount, Transfer.TransferStatus status) {
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
