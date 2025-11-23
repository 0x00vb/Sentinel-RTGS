package com.example.backend.repository;

import com.example.backend.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /**
     * Find transfer by msg_id for idempotency checks (FR-03)
     * Critical for preventing duplicate processing of ISO20022 messages
     */
    Optional<Transfer> findByMsgId(UUID msgId);

    /**
     * Check if msg_id exists for idempotency validation
     */
    boolean existsByMsgId(UUID msgId);

    /**
     * Find transfers by status for compliance workflow
     * Used by dashboard to show blocked transactions for manual review
     */
    List<Transfer> findByStatus(Transfer.TransferStatus status);

    /**
     * Find transfers by status with pagination support
     */
    List<Transfer> findByStatusOrderByCreatedAtDesc(Transfer.TransferStatus status);

    /**
     * Find blocked AML transfers for compliance officer worklist
     * Ordered by creation time for FIFO processing
     */
    @Query("SELECT t FROM Transfer t WHERE t.status = 'BLOCKED_AML' ORDER BY t.createdAt ASC")
    List<Transfer> findBlockedForReview();


    /**
     * Find transfers within date range for reporting
     */
    List<Transfer> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find transfers by status within date range
     */
    @Query("SELECT t FROM Transfer t WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate")
    List<Transfer> findByStatusAndDateRange(@Param("status") Transfer.TransferStatus status,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Count transfers by status for dashboard metrics
     */
    long countByStatus(Transfer.TransferStatus status);

    /**
     * Get total transfer amount by status for reporting
     */
    @Query("SELECT SUM(t.amount) FROM Transfer t WHERE t.status = :status")
    java.math.BigDecimal getTotalAmountByStatus(@Param("status") Transfer.TransferStatus status);
}
