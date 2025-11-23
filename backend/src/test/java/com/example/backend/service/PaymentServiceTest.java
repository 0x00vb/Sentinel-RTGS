package com.example.backend.service;

import com.example.backend.dto.ComplianceDecision;
import com.example.backend.dto.TransferRequest;
import com.example.backend.dto.TransferResponse;
import com.example.backend.entity.*;
import com.example.backend.exception.*;
import com.example.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService using Mockito
 * Tests transfer processing, compliance decisions, and error scenarios
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private TransferEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private TransferRequest validRequest;
    private Account sourceAccount;
    private Account destAccount;
    private Transfer pendingTransfer;

    @BeforeEach
    void setUp() throws Exception {
        validRequest = createTransferRequest(
            UUID.randomUUID(),
            "DE89370400440532013000",
            "FR1420041010050500013M02606",
            new BigDecimal("100.00"),
            "EUR"
        );

        sourceAccount = createTestAccount("DE89370400440532013000", "EUR", "1000.00");
        destAccount = createTestAccount("FR1420041010050500013M02606", "EUR", "500.00");

        pendingTransfer = new Transfer();
        pendingTransfer.setId(1L);
        pendingTransfer.setMsgId(validRequest.getMsgId());
        pendingTransfer.setSource(sourceAccount);
        pendingTransfer.setDestination(destAccount);
        pendingTransfer.setAmount(validRequest.getAmount());
        pendingTransfer.setStatus(Transfer.TransferStatus.PENDING);
    }

    @Test
    void shouldProcessTransferSuccessfully() {
        // Given
        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.of(destAccount));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(pendingTransfer);
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(ledgerEntryRepository.verifyTransferBalance(pendingTransfer.getId())).thenReturn(BigDecimal.ZERO);
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When
        TransferResponse response = paymentService.processTransfer(validRequest, "test-user");

        // Then
        assertThat(response.getMsgId()).isEqualTo(validRequest.getMsgId());
        assertThat(response.getStatus()).isEqualTo(Transfer.TransferStatus.CLEARED);
        assertThat(response.getMessage()).isEqualTo("Transfer processed successfully");

        // Verify interactions
        verify(transferRepository).findByMsgId(validRequest.getMsgId());
        verify(accountRepository, times(2)).findByIban(anyString());
        verify(transferRepository).saveAndFlush(any(Transfer.class));
        verify(accountRepository).findByIdForUpdate(sourceAccount.getId());
        verify(accountRepository).findByIdForUpdate(destAccount.getId());
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(ledgerEntryRepository).verifyTransferBalance(pendingTransfer.getId());
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transferRepository).save(any(Transfer.class));
        verify(auditService).logAudit(eq("transfer"), eq(pendingTransfer.getId()), eq("CLEARED"), anyMap());
    }

    @Test
    void shouldHandleDuplicateTransferIdempotently() {
        // Given
        Transfer existingTransfer = new Transfer();
        existingTransfer.setId(2L);
        existingTransfer.setMsgId(validRequest.getMsgId());
        existingTransfer.setSource(sourceAccount);
        existingTransfer.setDestination(destAccount);
        existingTransfer.setAmount(validRequest.getAmount());
        existingTransfer.setStatus(Transfer.TransferStatus.CLEARED);
        existingTransfer.setCompletedAt(LocalDateTime.now());

        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.of(existingTransfer));
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When
        TransferResponse response = paymentService.processTransfer(validRequest, "test-user");

        // Then
        assertThat(response.getMsgId()).isEqualTo(validRequest.getMsgId());
        assertThat(response.getStatus()).isEqualTo(Transfer.TransferStatus.CLEARED);
        assertThat(response.getMessage()).isEqualTo("Duplicate transfer - already processed");

        // Verify audit was logged and no further processing occurred
        verify(auditService).logAudit(eq("transfer"), eq(existingTransfer.getId()), eq("DUPLICATE_ATTEMPT"), anyMap());
        verify(accountRepository, never()).findByIban(anyString());
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void shouldThrowExceptionWhenSourceAccountNotFound() {
        // Given
        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessage("Source account not found: " + validRequest.getSenderIban());

        verify(accountRepository).findByIban(validRequest.getSenderIban());
        verify(accountRepository, never()).findByIban(validRequest.getReceiverIban());
    }

    @Test
    void shouldThrowExceptionWhenDestinationAccountNotFound() {
        // Given
        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(AccountNotFoundException.class)
            .hasMessage("Destination account not found: " + validRequest.getReceiverIban());

        verify(accountRepository).findByIban(validRequest.getReceiverIban());
    }

    @Test
    void shouldThrowExceptionWhenCurrencyMismatch() {
        // Given
        Account wrongCurrencyAccount = createTestAccount("DE89370400440532013000", "USD", "1000.00");

        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(wrongCurrencyAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.of(destAccount));

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(InvalidTransferException.class)
            .hasMessage("Currency mismatch in transfer request");

        verify(accountRepository, times(2)).findByIban(anyString());
    }

    @Test
    void shouldThrowExceptionWhenInsufficientFunds() {
        // Given
        Account lowBalanceAccount = createTestAccount("DE89370400440532013000", "EUR", "50.00"); // Less than 100.00

        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(lowBalanceAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.of(destAccount));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(pendingTransfer);
        when(accountRepository.findByIdForUpdate(lowBalanceAccount.getId())).thenReturn(Optional.of(lowBalanceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessage("Insufficient funds in source account");

        verify(auditService).logAudit(eq("transfer"), eq(pendingTransfer.getId()), eq("INSUFFICIENT_FUNDS"), anyMap());
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldHandleConcurrentDuplicateTransfer() {
        // Given
        Transfer concurrentTransfer = new Transfer();
        concurrentTransfer.setId(3L);
        concurrentTransfer.setMsgId(validRequest.getMsgId());
        concurrentTransfer.setSource(sourceAccount);
        concurrentTransfer.setDestination(destAccount);
        concurrentTransfer.setAmount(validRequest.getAmount());
        concurrentTransfer.setStatus(Transfer.TransferStatus.CLEARED);

        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.of(concurrentTransfer));
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When
        TransferResponse response = paymentService.processTransfer(validRequest, "test-user");

        // Then
        assertThat(response.getMsgId()).isEqualTo(validRequest.getMsgId());
        assertThat(response.getStatus()).isEqualTo(Transfer.TransferStatus.CLEARED);
        assertThat(response.getMessage()).isEqualTo("Duplicate transfer - already processed");

        verify(auditService).logAudit(eq("transfer"), eq(concurrentTransfer.getId()), eq("DUPLICATE_ATTEMPT"), anyMap());
    }

    @Test
    void shouldProcessApprovedComplianceDecision() throws Exception {
        // Given
        Transfer blockedTransfer = new Transfer();
        blockedTransfer.setId(4L);
        blockedTransfer.setMsgId(UUID.randomUUID());
        blockedTransfer.setSource(sourceAccount);
        blockedTransfer.setDestination(destAccount);
        blockedTransfer.setAmount(new BigDecimal("200.00"));
        blockedTransfer.setStatus(Transfer.TransferStatus.BLOCKED_AML);

        ComplianceDecision decision = createComplianceDecision(
            blockedTransfer.getId(),
            ComplianceDecision.DecisionType.APPROVE,
            "compliance-user",
            "Approved after review"
        );

        when(transferRepository.findById(blockedTransfer.getId())).thenReturn(Optional.of(blockedTransfer));
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(ledgerEntryRepository.verifyTransferBalance(blockedTransfer.getId())).thenReturn(BigDecimal.ZERO);
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When
        TransferResponse response = paymentService.processComplianceDecision(decision);

        // Then
        assertThat(response.getMsgId()).isEqualTo(blockedTransfer.getMsgId());
        assertThat(response.getStatus()).isEqualTo(Transfer.TransferStatus.CLEARED);
        assertThat(response.getMessage()).isEqualTo("Transfer approved and processed");

        verify(transferRepository).save(blockedTransfer);
        verify(auditService).logAudit(eq("transfer"), eq(blockedTransfer.getId()), eq("REVIEW_APPROVED"), anyMap());
    }

    @Test
    void shouldProcessRejectedComplianceDecision() throws Exception {
        // Given
        Transfer blockedTransfer = new Transfer();
        blockedTransfer.setId(5L);
        blockedTransfer.setMsgId(UUID.randomUUID());
        blockedTransfer.setSource(sourceAccount);
        blockedTransfer.setDestination(destAccount);
        blockedTransfer.setAmount(validRequest.getAmount());
        blockedTransfer.setStatus(Transfer.TransferStatus.BLOCKED_AML);

        ComplianceDecision decision = createComplianceDecision(
            blockedTransfer.getId(),
            ComplianceDecision.DecisionType.REJECT,
            "compliance-user",
            "Rejected due to suspicious activity"
        );

        when(transferRepository.findById(blockedTransfer.getId())).thenReturn(Optional.of(blockedTransfer));
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When
        TransferResponse response = paymentService.processComplianceDecision(decision);

        // Then
        assertThat(response.getMsgId()).isEqualTo(blockedTransfer.getMsgId());
        assertThat(response.getStatus()).isEqualTo(Transfer.TransferStatus.REJECTED);
        assertThat(response.getMessage()).isEqualTo("Transfer rejected by compliance");

        verify(transferRepository).save(blockedTransfer);
        verify(auditService).logAudit(eq("transfer"), eq(blockedTransfer.getId()), eq("REVIEW_REJECTED"), anyMap());
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void shouldThrowExceptionForComplianceDecisionOnNonBlockedTransfer() throws Exception {
        // Given
        Transfer clearedTransfer = new Transfer();
        clearedTransfer.setId(6L);
        clearedTransfer.setMsgId(UUID.randomUUID());
        clearedTransfer.setSource(sourceAccount);
        clearedTransfer.setDestination(destAccount);
        clearedTransfer.setAmount(validRequest.getAmount());
        clearedTransfer.setStatus(Transfer.TransferStatus.CLEARED);

        ComplianceDecision decision = createComplianceDecision(
            clearedTransfer.getId(),
            ComplianceDecision.DecisionType.APPROVE,
            "compliance-user",
            "Attempted replay"
        );

        when(transferRepository.findById(clearedTransfer.getId())).thenReturn(Optional.of(clearedTransfer));
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When & Then
        assertThatThrownBy(() -> paymentService.processComplianceDecision(decision))
            .isInstanceOf(InvalidTransferException.class)
            .hasMessage("Transfer not in blocked state - possible replay attack");

        verify(auditService).logAudit(eq("transfer"), eq(clearedTransfer.getId()), eq("REPLAY_ATTEMPT"), anyMap());
    }

    @Test
    void shouldThrowExceptionWhenTransferNotFoundForComplianceDecision() throws Exception {
        // Given
        ComplianceDecision decision = createComplianceDecision(
            999L,
            ComplianceDecision.DecisionType.APPROVE,
            "compliance-user",
            "Transfer not found"
        );

        when(transferRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentService.processComplianceDecision(decision))
            .isInstanceOf(TransferNotFoundException.class)
            .hasMessage("Transfer not found");
    }

    @Test
    void shouldThrowExceptionWhenDoubleEntryViolationDetected() {
        // Given
        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.of(destAccount));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(pendingTransfer);
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(ledgerEntryRepository.verifyTransferBalance(pendingTransfer.getId())).thenReturn(new BigDecimal("100.00")); // Non-zero balance
        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(PaymentException.class)
            .hasMessage("Double-entry violation detected - atomicity breach");

        verify(ledgerEntryRepository).verifyTransferBalance(pendingTransfer.getId());
        verify(auditService).logAudit(eq("transfer"), eq(pendingTransfer.getId()), eq("PROCESSING_FAILED"), anyMap());
    }

    @Test
    void shouldHandleProcessingFailureWithAuditLogging() {
        // Given
        when(transferRepository.findByMsgId(validRequest.getMsgId())).thenReturn(Optional.empty());
        when(accountRepository.findByIban(validRequest.getSenderIban())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIban(validRequest.getReceiverIban())).thenReturn(Optional.of(destAccount));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenReturn(pendingTransfer);
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(ledgerEntryRepository.verifyTransferBalance(pendingTransfer.getId())).thenReturn(BigDecimal.ZERO);

        // Simulate a failure during balance update
        doThrow(new RuntimeException("Database connection lost")).when(accountRepository).save(sourceAccount);

        doNothing().when(auditService).logAudit(anyString(), anyLong(), anyString(), anyMap());

        // When & Then
        assertThatThrownBy(() -> paymentService.processTransfer(validRequest, "test-user"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection lost");

        // Verify failure audit was logged
        verify(auditService).logAudit(eq("transfer"), eq(pendingTransfer.getId()), eq("PROCESSING_FAILED"), anyMap());
    }

    // Helper methods
    private TransferRequest createTransferRequest(UUID msgId, String senderIban, String receiverIban,
                                                BigDecimal amount, String currency) throws Exception {
        TransferRequest request = new TransferRequest();
        setField(request, "msgId", msgId);
        setField(request, "senderIban", senderIban);
        setField(request, "receiverIban", receiverIban);
        setField(request, "amount", amount);
        setField(request, "currency", currency);
        return request;
    }

    private ComplianceDecision createComplianceDecision(long transferId, ComplianceDecision.DecisionType decisionType,
                                                      String reviewer, String notes) throws Exception {
        ComplianceDecision decision = new ComplianceDecision();
        setField(decision, "transferId", transferId);
        setField(decision, "decision", decisionType);
        setField(decision, "reviewer", reviewer);
        setField(decision, "notes", notes);
        return decision;
    }

    private void setField(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    private Account createTestAccount(String iban, String currency, String balance) {
        Account account = new Account();
        account.setId((long) (iban.hashCode() + currency.hashCode())); // Simple ID generation
        account.setIban(iban);
        account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        account.setOwnerName("Test Owner");
        return account;
    }
}
