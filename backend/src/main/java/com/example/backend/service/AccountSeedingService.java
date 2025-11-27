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
        } else if (iban.startsWith("US")) {
            return "JPMorgan Chase Bank NA";
        } else if (iban.startsWith("CA")) {
            return "Royal Bank of Canada";
        } else if (iban.startsWith("AU")) {
            return "Commonwealth Bank of Australia";
        } else if (iban.startsWith("JP")) {
            return "Mizuho Corporate Bank Ltd";
        } else if (iban.startsWith("CN")) {
            return "Bank of China";
        } else if (iban.startsWith("IN")) {
            return "State Bank of India";
        } else if (iban.startsWith("BR")) {
            return "Banco do Brasil";
        } else if (iban.startsWith("MX")) {
            return "Banco Nacional de México";
        } else if (iban.startsWith("KR")) {
            return "Korea Development Bank";
        } else if (iban.startsWith("SG")) {
            return "DBS Bank Ltd";
        } else if (iban.startsWith("HK")) {
            return "HSBC Hong Kong";
        } else if (iban.startsWith("AE")) {
            return "Emirates NBD";
        } else if (iban.startsWith("SA")) {
            return "Al Rajhi Bank";
        } else if (iban.startsWith("ZA")) {
            return "Standard Bank of South Africa";
        } else if (iban.startsWith("NO")) {
            return "DNB Bank";
        } else if (iban.startsWith("SE")) {
            return "Swedbank";
        } else if (iban.startsWith("DK")) {
            return "Danske Bank";
        } else if (iban.startsWith("PL")) {
            return "PKO Bank Polski";
        } else if (iban.startsWith("CZ")) {
            return "Česká spořitelna";
        } else if (iban.startsWith("TR")) {
            return "Türkiye İş Bankası";
        } else if (iban.startsWith("RU")) {
            return "Sberbank";
        } else if (iban.startsWith("AR")) {
            return "Banco de la Nación Argentina";
        } else if (iban.startsWith("CL")) {
            return "Banco de Chile";
        } else if (iban.startsWith("AT")) {
            return "Erste Group Bank";
        } else if (iban.startsWith("BE")) {
            return "KBC Bank";
        } else if (iban.startsWith("FI")) {
            return "Nordea Bank";
        } else if (iban.startsWith("GR")) {
            return "National Bank of Greece";
        } else if (iban.startsWith("PT")) {
            return "Caixa Geral de Depósitos";
        } else if (iban.startsWith("IE")) {
            return "Bank of Ireland";
        } else {
            return "International Commercial Bank";
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
        } else if (iban.startsWith("US")) {
            return "USD";
        } else if (iban.startsWith("CA")) {
            return "CAD";
        } else if (iban.startsWith("AU")) {
            return "AUD";
        } else if (iban.startsWith("JP")) {
            return "JPY";
        } else if (iban.startsWith("CN")) {
            return "CNY";
        } else if (iban.startsWith("IN")) {
            return "INR";
        } else if (iban.startsWith("BR")) {
            return "BRL";
        } else if (iban.startsWith("MX")) {
            return "MXN";
        } else if (iban.startsWith("KR")) {
            return "KRW";
        } else if (iban.startsWith("SG")) {
            return "SGD";
        } else if (iban.startsWith("HK")) {
            return "HKD";
        } else if (iban.startsWith("AE")) {
            return "AED";
        } else if (iban.startsWith("SA")) {
            return "SAR";
        } else if (iban.startsWith("ZA")) {
            return "ZAR";
        } else if (iban.startsWith("NO")) {
            return "NOK";
        } else if (iban.startsWith("SE")) {
            return "SEK";
        } else if (iban.startsWith("DK")) {
            return "DKK";
        } else if (iban.startsWith("PL")) {
            return "PLN";
        } else if (iban.startsWith("CZ")) {
            return "CZK";
        } else if (iban.startsWith("TR")) {
            return "TRY";
        } else if (iban.startsWith("RU")) {
            return "RUB";
        } else if (iban.startsWith("AR")) {
            return "ARS";
        } else if (iban.startsWith("CL")) {
            return "CLP";
        } else {
            // EUR for most European countries (DE, FR, ES, IT, NL, AT, BE, FI, GR, PT, IE, etc.)
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
