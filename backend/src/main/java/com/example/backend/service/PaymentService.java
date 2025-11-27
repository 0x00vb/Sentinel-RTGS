package com.example.backend.service;

import com.example.backend.dto.ComplianceDecision;
import com.example.backend.dto.TransferRequest;
import com.example.backend.dto.TransferResponse;
import com.example.backend.entity.*;
import com.example.backend.exception.*;
import com.example.backend.repository.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired private AccountRepository accountRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private AuditService auditService;
    @Autowired private TransferEventPublisher eventPublisher; // publishes after commit

    /**
     * Process a cleared transfer with proper transaction boundaries and robust idempotency.
     *
     * Key points:
     * - Method-level transaction (short timeout)
     * - Retryable for DB deadlocks / lock timeouts
     * - Canonical locking order to reduce deadlocks
     * - Publish events only after commit
     */
    @Transactional(timeout = 30)
    @Retryable(
        value = {org.springframework.dao.DeadlockLoserDataAccessException.class,
                 PessimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public TransferResponse processTransfer(TransferRequest request, String actor) {
        final String normalizedActor = actor == null ? "system" : actor;

        // --- IDENTITY / IDEMPOTENCY: single read attempt to avoid race ---
        Optional<Transfer> maybeExisting = transferRepository.findByMsgId(request.getMsgId());
        if (maybeExisting.isPresent()) {
            Transfer existing = maybeExisting.get();
            auditService.logAudit("transfer", existing.getId(), "DUPLICATE_ATTEMPT",
                    createAuditPayload(existing, normalizedActor, "Duplicate msg_id detected"));
            return new TransferResponse(existing.getMsgId(), existing.getStatus(),
                    "Duplicate transfer - already processed", existing.getCompletedAt());
        }

        // --- Validate existence of accounts (no locks yet) ---
        Account sourceAccount = accountRepository.findByIban(request.getSenderIban())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + request.getSenderIban()));

        Account destAccount = accountRepository.findByIban(request.getReceiverIban())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + request.getReceiverIban()));

        // Currency
        validateCurrencyCompatibility(sourceAccount, destAccount, request.getCurrency());

        // Create transfer row in PENDING state. This may race with other writers; we handle uniqueness below.
        Transfer transfer = createPendingTransfer(request, sourceAccount, destAccount);

        Transfer savedTransfer;
        try {
            savedTransfer = transferRepository.saveAndFlush(transfer); // may throw DataIntegrityViolationException if msg_id uniqueness violated
        } catch (DataIntegrityViolationException dive) {
            // concurrent insert happened — fetch existing record and return idempotent response
            Optional<Transfer> concurrent = transferRepository.findByMsgId(request.getMsgId());
            if (concurrent.isPresent()) {
                Transfer existing = concurrent.get();
                auditService.logAudit("transfer", existing.getId(), "DUPLICATE_RACE",
                        createAuditPayload(existing, normalizedActor, "Duplicate detected via constraint"));
                return new TransferResponse(existing.getMsgId(), existing.getStatus(),
                        "Duplicate transfer (concurrent) - already processed", existing.getCompletedAt());
            }
            // else rethrow if unexpected
            throw dive;
        }

        // Acquire locks in canonical order to avoid deadlocks:
        // lock the account with smaller id first.
        Account firstToLock = sourceAccount.getId() <= destAccount.getId() ? sourceAccount : destAccount;
        Account secondToLock = sourceAccount.getId() <= destAccount.getId() ? destAccount : sourceAccount;

        // Fetch locked entities (repositories must use @Lock(PESSIMISTIC_WRITE))
        Account lockedFirst = accountRepository.findByIdForUpdate(firstToLock.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account lock failed: " + firstToLock.getId()));
        Account lockedSecond = accountRepository.findByIdForUpdate(secondToLock.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account lock failed: " + secondToLock.getId()));

        // Map lockedFirst/Second back to source/dest
        Account lockedSource = lockedFirst.getId().equals(sourceAccount.getId()) ? lockedFirst : lockedSecond;
        Account lockedDest   = lockedFirst.getId().equals(destAccount.getId()) ? lockedFirst : lockedSecond;

        // Balance check
        if (lockedSource.getBalance().compareTo(request.getAmount()) < 0) {
            auditService.logAudit("transfer", savedTransfer.getId(), "INSUFFICIENT_FUNDS",
                    createAuditPayload(savedTransfer, normalizedActor, "Insufficient funds"));
            throw new InsufficientFundsException("Insufficient funds in source account");
        }

        try {
            // 1) Create ledger entries (must be in same tx so rollback reverts them)
            createLedgerEntries(savedTransfer, lockedSource, lockedDest);

            // 2) Update balances
            updateBalances(lockedSource, lockedDest, request.getAmount());

            // 3) Finalize transfer (status set last)
            savedTransfer.setStatus(Transfer.TransferStatus.CLEARED);
            savedTransfer.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            transferRepository.save(savedTransfer);

            // Audit (separate transaction via AuditService REQUIRES_NEW)
            auditService.logAudit("transfer", savedTransfer.getId(), "CLEARED",
                    createAuditPayload(savedTransfer, normalizedActor, "Transfer processed successfully"));

            // Publish event AFTER COMMIT to avoid lost notifications
            registerAfterCommit(() -> eventPublisher.publishTransferEvent(savedTransfer));

            return new TransferResponse(savedTransfer.getMsgId(), savedTransfer.getStatus(),
                    "Transfer processed successfully", savedTransfer.getCompletedAt());

        } catch (RuntimeException rte) {
            // audit failure in a separate transaction to guarantee record of failure
            auditService.logAudit("transfer", savedTransfer.getId(), "PROCESSING_FAILED",
                    createAuditPayload(savedTransfer, normalizedActor, "Processing failed: " + rte.getMessage()));
            throw rte;
        }
    }

    // --- Compliance decision handling with replay protection ---
    @Transactional(timeout = 30)
    @Retryable(
        value = {org.springframework.dao.DeadlockLoserDataAccessException.class,
                 PessimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public TransferResponse processComplianceDecision(ComplianceDecision decision) {
        Transfer transfer = transferRepository.findById(decision.getTransferId())
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));

        // Replay protection
        if (transfer.getStatus() != Transfer.TransferStatus.BLOCKED_AML) {
            auditService.logAudit("transfer", transfer.getId(), "REPLAY_ATTEMPT",
                    createAuditPayload(transfer, decision.getReviewer(), "Attempted to review transfer in status: " + transfer.getStatus()));
            throw new InvalidTransferException("Transfer not in blocked state - possible replay attack");
        }

        if (decision.getDecision() == ComplianceDecision.DecisionType.APPROVE) {
            return processApprovedTransfer(transfer, decision);
        } else {
            return processRejectedTransfer(transfer, decision);
        }
    }

    // --- process approved ---
    private TransferResponse processApprovedTransfer(Transfer transfer, ComplianceDecision decision) {
        // canonical lock ordering
        Account source = accountRepository.findByIdForUpdate(transfer.getSource().getId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
        Account dest = accountRepository.findByIdForUpdate(transfer.getDestination().getId())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

        // double-check funds
        if (source.getBalance().compareTo(transfer.getAmount()) < 0) {
            auditService.logAudit("transfer", transfer.getId(), "APPROVAL_FAILED",
                    createAuditPayload(transfer, decision.getReviewer(), "Insufficient funds after approval"));
            throw new InsufficientFundsException("Insufficient funds after review");
        }

        // create ledger entries + update balances + finalize
        createLedgerEntries(transfer, source, dest);
        updateBalances(source, dest, transfer.getAmount());

        transfer.setStatus(Transfer.TransferStatus.CLEARED);
        transfer.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        transferRepository.save(transfer);

        auditService.logAudit("transfer", transfer.getId(), "REVIEW_APPROVED",
                createAuditPayload(transfer, decision.getReviewer(), "Approved by compliance. Notes: " + decision.getNotes()));

        registerAfterCommit(() -> eventPublisher.publishTransferEvent(transfer));

        return new TransferResponse(transfer.getMsgId(), transfer.getStatus(),
                "Transfer approved and processed", transfer.getCompletedAt());
    }

    // --- process rejected ---
    private TransferResponse processRejectedTransfer(Transfer transfer, ComplianceDecision decision) {
        transfer.setStatus(Transfer.TransferStatus.REJECTED);
        transfer.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        transferRepository.save(transfer);

        auditService.logAudit("transfer", transfer.getId(), "REVIEW_REJECTED",
                createAuditPayload(transfer, decision.getReviewer(), "Rejected by compliance. Notes: " + decision.getNotes()));

        registerAfterCommit(() -> eventPublisher.publishTransferEvent(transfer));

        return new TransferResponse(transfer.getMsgId(), transfer.getStatus(),
                "Transfer rejected by compliance", transfer.getCompletedAt());
    }

    // --- Helpers ---

    private Transfer createPendingTransfer(TransferRequest request, Account source, Account dest) {
        Transfer transfer = new Transfer();
        transfer.setMsgId(request.getMsgId());
        transfer.setExternalReference(request.getMsgId().toString());
        transfer.setSource(source);
        transfer.setDestination(dest);
        transfer.setAmount(request.getAmount());
        transfer.setStatus(Transfer.TransferStatus.PENDING);
        transfer.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return transfer;
    }

    private void validateCurrencyCompatibility(Account source, Account dest, String currency) {
        if (!source.getCurrency().equals(currency) || !dest.getCurrency().equals(currency)) {
            throw new InvalidTransferException("Currency mismatch in transfer request");
        }
    }

    private void createLedgerEntries(Transfer transfer, Account source, Account dest) {
        // debit
        LedgerEntry debitEntry = new LedgerEntry(source, transfer, LedgerEntry.EntryType.DEBIT, transfer.getAmount());
        ledgerEntryRepository.save(debitEntry);

        // credit
        LedgerEntry creditEntry = new LedgerEntry( dest, transfer, LedgerEntry.EntryType.CREDIT, transfer.getAmount());

        ledgerEntryRepository.save(creditEntry);

        BigDecimal imbalance = ledgerEntryRepository.verifyTransferBalance(transfer.getId());
        if (imbalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new PaymentException("Double-entry violation detected - atomicity breach");
        }
    }

    private void updateBalances(Account source, Account dest, BigDecimal amount) {
        // arithmetic with explicit scale policies if needed
        source.setBalance(source.getBalance().subtract(amount));
        source.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        accountRepository.save(source);

        dest.setBalance(dest.getBalance().add(amount));
        dest.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        accountRepository.save(dest);
    }

    private Map<String, Object> createAuditPayload(Transfer transfer, String actor, String reason) {
        return Map.of(
                "msg_id", transfer.getMsgId(),
                "transfer_id", transfer.getId(),
                "status", transfer.getStatus().name(),
                "amount", transfer.getAmount(),
                "source_iban", transfer.getSource().getIban(),
                "dest_iban", transfer.getDestination().getIban(),
                "actor", actor == null ? "system" : actor,
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "reason", reason
        );
    }

    /**
     * Register a Runnable to execute AFTER transaction commits successfully.
     * This avoids sending events when the transaction later rolls back.
     */
    private void registerAfterCommit(Runnable afterCommit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        afterCommit.run();
                    } catch (Exception e) {
                        // Log and push to dead-letter monitoring — do not throw
                        // (add structured logging/metrics here)
                        System.err.println("Failed to publish transfer event after commit: " + e.getMessage());
                    }
                }
            });
        } else {
            // No transaction active — run immediately (unit tests, etc.)
            afterCommit.run();
        }
    }
}
