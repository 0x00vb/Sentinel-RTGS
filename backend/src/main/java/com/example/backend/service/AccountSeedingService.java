package com.example.backend.service;

import com.example.backend.entity.Account;
import com.example.backend.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for seeding simulation accounts.
 * Handles account creation for traffic simulation without interfering with RTGS operations.
 * Only active in development mode.
 */
@Service
public class AccountSeedingService {

    private static final Logger logger = LoggerFactory.getLogger(AccountSeedingService.class);

    @Autowired
    private AccountRepository accountRepository;

    /**
     * Pre-populate all accounts from the IBAN pool with realistic data.
     * This ensures accounts exist before simulation starts, preventing race conditions.
     *
     * @param ibanPool List of IBANs to create accounts for
     * @return number of accounts created
     */
    @Transactional
    public int seedSimulationAccounts(List<String> ibanPool) {
        int created = 0;

        for (String iban : ibanPool) {
            if (!accountRepository.existsByIban(iban)) {
                Account account = createSimulationAccount(iban);
                accountRepository.save(account);
                created++;
            }
        }

        if (created > 0) {
            logger.info("Seeded {} simulation accounts", created);
        } else {
            logger.debug("All {} simulation accounts already exist", ibanPool.size());
        }

        return created;
    }

    /**
     * Create a simulation account with realistic institutional banking data.
     */
    private Account createSimulationAccount(String iban) {
        Account account = new Account();

        // Extract bank name from IBAN pattern (simplified mapping)
        String ownerName = mapIbanToBankName(iban);
        String currency = mapIbanToCurrency(iban);

        account.setIban(iban);
        account.setOwnerName(ownerName);
        account.setCurrency(currency);
        account.setBalance(generateStartingBalance());

        return account;
    }

    /**
     * Map IBAN to a realistic bank name based on country code.
     */
    private String mapIbanToBankName(String iban) {
        if (iban.startsWith("DE")) {
            return "Deutsche Bank AG";
        } else if (iban.startsWith("GB")) {
            return "Royal Bank of Scotland";
        } else if (iban.startsWith("FR")) {
            return "BNP Paribas";
        } else if (iban.startsWith("ES")) {
            return "Banco Santander";
        } else if (iban.startsWith("IT")) {
            return "UniCredit Group";
        } else if (iban.startsWith("NL")) {
            return "ING Group";
        } else if (iban.startsWith("CH")) {
            return "UBS AG";
        } else {
            return "European Commercial Bank";
        }
    }

    /**
     * Map IBAN to currency based on country.
     */
    private String mapIbanToCurrency(String iban) {
        if (iban.startsWith("GB")) {
            return "GBP";
        } else if (iban.startsWith("CH")) {
            return "CHF";
        } else {
            // EUR for most European countries
            return "EUR";
        }
    }

    /**
     * Generate a realistic starting balance for institutional accounts.
     */
    private BigDecimal generateStartingBalance() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Institutional accounts: €1M to €10M starting balance
        double balance = random.nextDouble(1000000, 10000000);
        return BigDecimal.valueOf(balance).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
