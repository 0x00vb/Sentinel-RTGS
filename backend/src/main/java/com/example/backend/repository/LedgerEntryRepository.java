package com.example.backend.repository;

import com.example.backend.entity.LedgerEntry;
import com.example.backend.entity.Account;
import com.example.backend.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Find all ledger entries for a transfer (double-entry verification)
     * Should return exactly 2 entries (debit + credit) for completed transfers
     */
    List<LedgerEntry> findByTransfer(Transfer transfer);

    /**
     * Find all ledger entries for an account
     * Used for account statement generation and balance verification
     */
    List<LedgerEntry> findByAccountOrderByCreatedAtDesc(Account account);

    /**
     * Find ledger entries for an account within date range
     */
    List<LedgerEntry> findByAccountAndCreatedAtBetweenOrderByCreatedAtDesc(
            Account account, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Calculate account balance from ledger entries
     * Sum of all credits minus sum of all debits
     * DEBIT entries reduce balance, CREDIT entries increase balance
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'DEBIT' THEN -le.amount ELSE le.amount END), 0) " +
           "FROM LedgerEntry le WHERE le.account = :account")
    BigDecimal calculateBalanceFromLedger(@Param("account") Account account);

    /**
     * Verify double-entry integrity for a transfer
     * Should sum to zero (debits = credits)
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'DEBIT' THEN -le.amount ELSE le.amount END), 0) " +
           "FROM LedgerEntry le WHERE le.transfer = :transfer")
    BigDecimal verifyTransferBalance(@Param("transfer") Transfer transfer);

    /**
     * Find entries by transfer and account for reconciliation
     */
    List<LedgerEntry> findByTransferAndAccount(Transfer transfer, Account account);

    /**
     * Count entries for a transfer (should be 2 for double-entry)
     */
    long countByTransfer(Transfer transfer);

    /**
     * Get all entries created after a specific time (for real-time dashboard)
     */
    List<LedgerEntry> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /**
     * Get entries by entry type for reporting
     */
    List<LedgerEntry> findByEntryType(LedgerEntry.EntryType entryType);

    /**
     * Get total debit/credit amounts by account for period
     */
    @Query("SELECT SUM(le.amount) FROM LedgerEntry le " +
           "WHERE le.account = :account AND le.entryType = :entryType " +
           "AND le.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalByAccountAndTypeAndPeriod(@Param("account") Account account,
                                                 @Param("entryType") LedgerEntry.EntryType entryType,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Find transfers affecting an account (through ledger entries)
     */
    @Query("SELECT DISTINCT le.transfer FROM LedgerEntry le WHERE le.account = :account")
    List<Transfer> findTransfersByAccount(@Param("account") Account account);
}
