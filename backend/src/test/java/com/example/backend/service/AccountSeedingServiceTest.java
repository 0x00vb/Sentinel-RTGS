package com.example.backend.service;

import com.example.backend.entity.Account;
import com.example.backend.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test suite for AccountSeedingService.
 * Verifies account creation logic for simulation purposes.
 */
@ExtendWith(MockitoExtension.class)
class AccountSeedingServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountSeedingService accountSeedingService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @Test
    void seedSimulationAccounts_ShouldCreateMissingAccounts() {
        // Given
        List<String> ibanPool = Arrays.asList(
            "DE89370400440532013000", // Will be created
            "GB29RBOS60161331926819", // Will be created
            "DE89370400440532013001"  // Already exists
        );

        when(accountRepository.existsByIban("DE89370400440532013000")).thenReturn(false);
        when(accountRepository.existsByIban("GB29RBOS60161331926819")).thenReturn(false);
        when(accountRepository.existsByIban("DE89370400440532013001")).thenReturn(true);

        // When
        int created = accountSeedingService.seedSimulationAccounts(ibanPool);

        // Then
        assertThat(created).isEqualTo(2);
        verify(accountRepository, times(3)).existsByIban(anyString());
        verify(accountRepository, times(2)).save(accountCaptor.capture());

        List<Account> createdAccounts = accountCaptor.getAllValues();
        assertThat(createdAccounts).hasSize(2);

        // Verify first account (German IBAN)
        Account germanAccount = createdAccounts.get(0);
        assertThat(germanAccount.getIban()).isEqualTo("DE89370400440532013000");
        assertThat(germanAccount.getOwnerName()).isEqualTo("Deutsche Bank AG");
        assertThat(germanAccount.getCurrency()).isEqualTo("EUR");
        assertThat(germanAccount.getBalance()).isBetween(BigDecimal.valueOf(1000000), BigDecimal.valueOf(10000000));

        // Verify second account (UK IBAN)
        Account ukAccount = createdAccounts.get(1);
        assertThat(ukAccount.getIban()).isEqualTo("GB29RBOS60161331926819");
        assertThat(ukAccount.getOwnerName()).isEqualTo("Royal Bank of Scotland");
        assertThat(ukAccount.getCurrency()).isEqualTo("GBP");
        assertThat(ukAccount.getBalance()).isBetween(BigDecimal.valueOf(1000000), BigDecimal.valueOf(10000000));
    }

    @Test
    void seedSimulationAccounts_ShouldReturnZero_WhenAllAccountsExist() {
        // Given
        List<String> ibanPool = Arrays.asList("DE89370400440532013000", "GB29RBOS60161331926819");
        when(accountRepository.existsByIban(anyString())).thenReturn(true);

        // When
        int created = accountSeedingService.seedSimulationAccounts(ibanPool);

        // Then
        assertThat(created).isEqualTo(0);
        verify(accountRepository, times(2)).existsByIban(anyString());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void seedSimulationAccounts_ShouldHandleEmptyPool() {
        // Given
        List<String> ibanPool = Arrays.asList();

        // When
        int created = accountSeedingService.seedSimulationAccounts(ibanPool);

        // Then
        assertThat(created).isEqualTo(0);
        verify(accountRepository, never()).existsByIban(anyString());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void seedSimulationAccounts_ShouldMapCurrenciesCorrectly() {
        // Given
        List<String> ibanPool = Arrays.asList(
            "DE89370400440532013000", // EUR
            "GB29RBOS60161331926819", // GBP
            "CH6309000000900100000"   // CHF
        );

        when(accountRepository.existsByIban(anyString())).thenReturn(false);

        // When
        accountSeedingService.seedSimulationAccounts(ibanPool);

        // Then
        verify(accountRepository, times(3)).save(accountCaptor.capture());

        List<Account> accounts = accountCaptor.getAllValues();
        assertThat(accounts).hasSize(3);

        // Check currencies are mapped correctly
        assertThat(accounts.stream().anyMatch(a -> a.getCurrency().equals("EUR"))).isTrue();
        assertThat(accounts.stream().anyMatch(a -> a.getCurrency().equals("GBP"))).isTrue();
        assertThat(accounts.stream().anyMatch(a -> a.getCurrency().equals("CHF"))).isTrue();
    }

    @Test
    void seedSimulationAccounts_ShouldGenerateRealisticBalances() {
        // Given
        List<String> ibanPool = Arrays.asList("DE89370400440532013000");
        when(accountRepository.existsByIban(anyString())).thenReturn(false);

        // When
        accountSeedingService.seedSimulationAccounts(ibanPool);

        // Then
        verify(accountRepository).save(accountCaptor.capture());

        Account account = accountCaptor.getValue();
        BigDecimal balance = account.getBalance();

        // Verify balance is in institutional range
        assertThat(balance).isNotNull();
        assertThat(balance).isGreaterThanOrEqualTo(BigDecimal.valueOf(1000000)); // €1M minimum
        assertThat(balance).isLessThan(BigDecimal.valueOf(10000000)); // €10M maximum
        assertThat(balance.scale()).isEqualTo(2); // 2 decimal places
    }
}
