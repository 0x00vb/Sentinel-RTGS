package com.example.backend.repository;

import com.example.backend.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Find account by IBAN (International Bank Account Number)
     * Used for external account resolution in ISO20022 transfers
     */
    Optional<Account> findByIban(String iban);

    /**
     * Find account by IBAN with pessimistic write lock
     * Critical for preventing race conditions during balance updates
     * Uses PESSIMISTIC_WRITE lock as specified in devplan FR-07
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.iban = :iban")
    Optional<Account> findByIbanForUpdate(@Param("iban") String iban);

    /**
     * Find account by ID with pessimistic write lock
     * Used during transfer settlement to ensure serializable balance updates
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * Check if IBAN exists
     */
    boolean existsByIban(String iban);

    /**
     * Find accounts by currency for multi-currency support
     */
    Iterable<Account> findByCurrency(String currency);
}
