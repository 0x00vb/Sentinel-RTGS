package com.example.backend.repository;

import com.example.backend.entity.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AccountRepository using @SpringBootTest
 * Tests repository operations against an H2 in-memory database
 */
@SpringBootTest
@DirtiesContext
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAndRetrieveAccountWithAllFields() {
        // Given
        Account account = new Account();
        account.setIban("GB29 NWBK 6016 1331 9268 19");
        account.setOwnerName("John Doe");
        account.setCurrency("USD");
        account.setBalance(new BigDecimal("1000.50"));
        account.setCreatedAt(LocalDateTime.now().minusDays(1));
        account.setUpdatedAt(LocalDateTime.now());

        // When
        Account savedAccount = accountRepository.save(account);

        // Then
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getIban()).isEqualTo("GB29 NWBK 6016 1331 9268 19");
        assertThat(savedAccount.getOwnerName()).isEqualTo("John Doe");
        assertThat(savedAccount.getCurrency()).isEqualTo("USD");
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1000.50"));
        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();

        // Verify retrieval
        Optional<Account> retrieved = accountRepository.findById(savedAccount.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt") // timestamps may differ slightly
                .isEqualTo(savedAccount);
    }

    @Test
    void shouldAllowSavingAccountWithNegativeBalance() {
        // Given - Account with negative balance (allowed by current schema)
        Account account = new Account();
        account.setIban("GB29 NWBK 6016 1331 9268 20");
        account.setOwnerName("Jane Doe");
        account.setCurrency("USD");
        account.setBalance(new BigDecimal("-100.00")); // Negative balance allowed

        // When
        Account saved = accountRepository.save(account);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBalance()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void shouldFindAccountByIban() {
        // Given
        Account account = new Account();
        account.setIban("DE89370400440532013000");
        account.setOwnerName("Test User");
        account.setCurrency("EUR");
        account.setBalance(new BigDecimal("500.00"));
        accountRepository.save(account);

        // When
        Optional<Account> found = accountRepository.findByIban("DE89370400440532013000");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getIban()).isEqualTo("DE89370400440532013000");
        assertThat(found.get().getOwnerName()).isEqualTo("Test User");
        assertThat(found.get().getCurrency()).isEqualTo("EUR");
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldReturnEmptyWhenIbanNotFound() {
        // When
        Optional<Account> found = accountRepository.findByIban("NONEXISTENT_IBAN");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldEnforceUniqueIbanConstraint() {
        // Given - First account
        Account account1 = new Account();
        account1.setIban("FR1420041010050500013M02606");
        account1.setOwnerName("User 1");
        account1.setCurrency("EUR");
        account1.setBalance(new BigDecimal("100.00"));
        accountRepository.save(account1);

        // Given - Second account with same IBAN
        Account account2 = new Account();
        account2.setIban("FR1420041010050500013M02606"); // Same IBAN
        account2.setOwnerName("User 2");
        account2.setCurrency("EUR");
        account2.setBalance(new BigDecimal("200.00"));

        // When & Then
        assertThatThrownBy(() -> accountRepository.saveAndFlush(account2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("iban"); // Unique constraint violation
    }

    @Test
    void shouldFindAccountsByCurrency() {
        // Given - Use a unique currency for this test
        Account jpyAccount = new Account();
        jpyAccount.setIban("JP12345678901234567890");
        jpyAccount.setOwnerName("JPY User");
        jpyAccount.setCurrency("JPY");
        jpyAccount.setBalance(new BigDecimal("100000.00"));
        accountRepository.save(jpyAccount);

        // When
        Iterable<Account> jpyAccounts = accountRepository.findByCurrency("JPY");

        // Then
        assertThat(jpyAccounts).hasSize(1);
        assertThat(jpyAccounts.iterator().next().getCurrency()).isEqualTo("JPY");
        assertThat(jpyAccounts.iterator().next().getIban()).isEqualTo("JP12345678901234567890");
    }

    @Test
    void shouldReturnTrueWhenIbanExists() {
        // Given
        Account account = new Account();
        account.setIban("IT60X0542811101000000123456");
        account.setOwnerName("Existing User");
        account.setCurrency("EUR");
        account.setBalance(new BigDecimal("750.00"));
        accountRepository.save(account);

        // When & Then
        assertThat(accountRepository.existsByIban("IT60X0542811101000000123456")).isTrue();
        assertThat(accountRepository.existsByIban("NONEXISTENT_IBAN")).isFalse();
    }

    @Test
    @Transactional
    void shouldSupportPessimisticWriteLocking() {
        // Given
        Account account = new Account();
        account.setIban("ES9121000418450200051332");
        account.setOwnerName("Lock Test User");
        account.setCurrency("EUR");
        account.setBalance(new BigDecimal("1000.00"));
        Account saved = accountRepository.save(account);

        // When - This should work without throwing an exception
        Optional<Account> lockedAccount = accountRepository.findByIdForUpdate(saved.getId());

        // Then
        assertThat(lockedAccount).isPresent();
        assertThat(lockedAccount.get().getId()).isEqualTo(saved.getId());
        assertThat(lockedAccount.get().getIban()).isEqualTo("ES9121000418450200051332");
    }

    @Test
    @Transactional
    void shouldSupportPessimisticWriteLockingByIban() {
        // Given
        Account account = new Account();
        account.setIban("NL91ABNA0417164300");
        account.setOwnerName("IBAN Lock Test");
        account.setCurrency("EUR");
        account.setBalance(new BigDecimal("2000.00"));
        accountRepository.save(account);

        // When - This should work without throwing an exception
        Optional<Account> lockedAccount = accountRepository.findByIbanForUpdate("NL91ABNA0417164300");

        // Then
        assertThat(lockedAccount).isPresent();
        assertThat(lockedAccount.get().getIban()).isEqualTo("NL91ABNA0417164300");
        assertThat(lockedAccount.get().getOwnerName()).isEqualTo("IBAN Lock Test");
    }
}
