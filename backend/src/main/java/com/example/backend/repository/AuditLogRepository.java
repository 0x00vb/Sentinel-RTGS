package com.example.backend.repository;

import com.example.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs for a specific entity (entity_type + entity_id)
     * Ordered by creation time for chain verification
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, Long entityId);

    /**
     * Get the most recent audit log for an entity (for hash chaining)
     */
    Optional<AuditLog> findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    /**
     * Find audit logs by action type
     */
    List<AuditLog> findByAction(String action);

    /**
     * Find audit logs by action and entity type
     */
    List<AuditLog> findByActionAndEntityType(String action, String entityType);


    /**
     * Find audit logs within date range
     */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find recent audit logs for real-time dashboard (WebSocket updates)
     */
    List<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /**
     * Count audit logs by entity type for reporting
     */
    long countByEntityType(String entityType);

    /**
     * Count audit logs by action for metrics
     */
    long countByAction(String action);

    /**
     * Get the last hash for an entity (for hash chain continuation)
     */
    @Query("SELECT a.currHash FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<String> findLastHashForEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    /**
     * Verify hash chain integrity for an entity
     * Returns all audit logs for manual chain verification
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.createdAt ASC")
    List<AuditLog> findAuditChainForEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    /**
     * Find audit logs with potential chain breaks (for monitoring)
     * This would be used by a scheduled chain verifier service
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId AND " +
           "a.prevHash != (SELECT COALESCE(MAX(b.currHash), '0000000000000000000000000000000000000000000000000000000000000000') " +
           "FROM AuditLog b WHERE b.entityType = a.entityType AND b.entityId = a.entityId AND b.createdAt < a.createdAt)")
    List<AuditLog> findPotentialChainBreaks(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    /**
     * Get audit summary for an entity (count of actions)
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId GROUP BY a.action")
    List<Object[]> getAuditSummaryForEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);


    /**
     * Search audit logs by payload content (JSONB contains)
     */
    @Query(value = "SELECT * FROM audit_logs WHERE payload::text LIKE %:searchTerm%", nativeQuery = true)
    List<AuditLog> searchByPayloadContent(@Param("searchTerm") String searchTerm);

    /**
     * Get audit logs for compliance reporting (last 90 days)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentAuditLogs(@Param("since") LocalDateTime since);

    /**
     * Count total audit entries (for monitoring data growth)
     */
    @Query("SELECT COUNT(a) FROM AuditLog a")
    long countTotalAuditEntries();
}
