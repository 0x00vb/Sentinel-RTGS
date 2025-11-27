package com.example.backend.service;

import com.example.backend.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Traffic Simulator Service for generating continuous realistic pacs.008 messages.
 * Provides controlled simulation of banking traffic for testing and demonstration.
 */
@Service
public class TrafficSimulatorService {

    private static final Logger logger = LoggerFactory.getLogger(TrafficSimulatorService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AccountSeedingService accountSeedingService;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    // Simulation control
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> simulationTask;

    // Realistic IBAN pool - mix of generated and real-looking IBANs
    private static final List<String> IBAN_POOL = generateIbanPool();

    // Currency-indexed IBAN map for O(1) lookup (initialized once)
    private static final Map<String, List<String>> IBAN_BY_CURRENCY = initializeCurrencyIndex();

    // Bank names for realistic simulation
    private static final List<String> BANK_NAMES = Arrays.asList(
        "Deutsche Bank AG", "Commerzbank AG", "HSBC Bank PLC", "Barclays Bank PLC",
        "BNP Paribas", "Société Générale", "Santander Bank", "Banco Bilbao Vizcaya Argentaria",
        "UBS AG", "Credit Suisse AG", "ING Group", "ABN AMRO Bank", "Nordea Bank",
        "Danske Bank", "SEB", "Swedbank", "DNB", "Handelsbanken", "Erste Group Bank",
        "Raiffeisen Bank", "UniCredit Group", "Intesa Sanpaolo", "Banca Monte dei Paschi",
        "Banco Santander", "CaixaBank", "Bankia", "Sabadell", "Kutxabank",
        "JPMorgan Chase Bank NA", "Bank of America", "Wells Fargo Bank", "Citibank NA",
        "Royal Bank of Canada", "Toronto-Dominion Bank", "Bank of Nova Scotia",
        "Commonwealth Bank of Australia", "Westpac Banking Corporation", "Australia and New Zealand Banking Group",
        "Mizuho Corporate Bank Ltd", "Sumitomo Mitsui Banking Corporation", "Mitsubishi UFJ Financial Group",
        "Bank of China", "Industrial and Commercial Bank of China", "China Construction Bank",
        "State Bank of India", "ICICI Bank", "HDFC Bank",
        "Banco do Brasil", "Itaú Unibanco", "Banco Bradesco",
        "Banco Nacional de México", "Banco Santander México", "BBVA Bancomer",
        "Korea Development Bank", "Shinhan Bank", "Woori Bank",
        "DBS Bank Ltd", "United Overseas Bank", "OCBC Bank",
        "HSBC Hong Kong", "Bank of China Hong Kong", "Hang Seng Bank",
        "Emirates NBD", "First Abu Dhabi Bank", "Abu Dhabi Commercial Bank",
        "Al Rajhi Bank", "Saudi National Bank", "Riyad Bank",
        "Standard Bank of South Africa", "FirstRand Bank", "Nedbank",
        "PKO Bank Polski", "Bank Pekao", "mBank",
        "Česká spořitelna", "Komerční banka", "ČSOB",
        "Türkiye İş Bankası", "Garanti BBVA", "Akbank",
        "Sberbank", "VTB Bank", "Gazprombank",
        "Banco de la Nación Argentina", "Banco Santander Río", "Banco Galicia",
        "Banco de Chile", "Banco Santander Chile", "Banco de Crédito e Inversiones"
    );

    // Sanctions-triggering names (reduced frequency for realistic simulation)
    private static final List<String> POTENTIAL_SANCTIONS_NAMES = Arrays.asList(
        "Osama Bin Laden", "Kim Jong Un", "Vladimir Putin", "Ali Mahmoud Hassan",
        "Hassan Ali Mahmoud", "Mahmoud Hassan Ali", "Corporation XYZ Ltd"
    );

    /**
     * Start continuous traffic simulation.
     */
    public synchronized String startSimulation(int messagesPerSecond) {
        if (isRunning.get()) {
            return "Simulation already running";
        }

        if (!devMode) {
            return "Traffic simulation only available in development mode";
        }

        logger.info("Starting traffic simulation at {} messages per second", messagesPerSecond);

        // Pre-populate simulation accounts to prevent race conditions
        int accountsCreated = accountSeedingService.seedSimulationAccounts(IBAN_POOL);
        logger.info("Simulation accounts ready: {} created, {} total", accountsCreated, IBAN_POOL.size());

        isRunning.set(true);
        messagesSent.set(0);

        scheduler = Executors.newScheduledThreadPool(2);
        long intervalMs = Math.max(50, 1000 / messagesPerSecond); // Minimum 50ms between messages

        simulationTask = scheduler.scheduleAtFixedRate(
            this::sendRandomMessage,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        );

        return String.format("Traffic simulation started at %d messages per second", messagesPerSecond);
    }

    /**
     * Stop traffic simulation.
     */
    public synchronized String stopSimulation() {
        if (!isRunning.get()) {
            return "Simulation not running";
        }

        logger.info("Stopping traffic simulation");

        isRunning.set(false);

        if (simulationTask != null) {
            simulationTask.cancel(false);
            simulationTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        long totalSent = messagesSent.get();
        logger.info("Traffic simulation stopped. Total messages sent: {}", totalSent);

        return String.format("Traffic simulation stopped. Total messages sent: %d", totalSent);
    }

    /**
     * Get simulation status.
     */
    public Map<String, Object> getSimulationStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", isRunning.get());
        status.put("messagesSent", messagesSent.get());
        status.put("devMode", devMode);

        if (isRunning.get()) {
            status.put("uptime", System.currentTimeMillis() - getStartTime());
        }

        return status;
    }

    /**
     * Send a single random message (for testing).
     */
    public void sendSingleMessage() {
        if (!devMode) {
            throw new IllegalStateException("Traffic simulation only available in development mode");
        }
        sendRandomMessage();
    }

    private void sendRandomMessage() {
        if (!isRunning.get()) {
            return;
        }

        try {
            String messageXml = generateRealisticPacs008Message();
            rabbitTemplate.convertAndSend(RabbitMQConfig.INBOUND_EX, "#", messageXml);

            long count = messagesSent.incrementAndGet();
            if (count % 100 == 0) {
                logger.info("Traffic simulation: {} messages sent", count);
            }

        } catch (Exception e) {
            logger.error("Error sending simulated message", e);
        }
    }

    private String generateRealisticPacs008Message() {
        UUID messageId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Generate realistic amount (weighted distribution)
        BigDecimal amount = generateRealisticAmount();

        // Select sender/receiver from bank pool
        String senderName = BANK_NAMES.get(ThreadLocalRandom.current().nextInt(BANK_NAMES.size()));
        String receiverName = BANK_NAMES.get(ThreadLocalRandom.current().nextInt(BANK_NAMES.size()));

        // Small chance of sanctions-triggering name (1% for realistic simulation)
        if (ThreadLocalRandom.current().nextDouble() < 0.01) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                senderName = POTENTIAL_SANCTIONS_NAMES.get(ThreadLocalRandom.current().nextInt(POTENTIAL_SANCTIONS_NAMES.size()));
            } else {
                receiverName = POTENTIAL_SANCTIONS_NAMES.get(ThreadLocalRandom.current().nextInt(POTENTIAL_SANCTIONS_NAMES.size()));
            }
        }

        // Select sender IBAN from pool
        String senderIban = IBAN_POOL.get(ThreadLocalRandom.current().nextInt(IBAN_POOL.size()));
        
        // Derive currency from sender IBAN (matching AccountSeedingService logic)
        // This ensures currency matches the account currency in the database
        String currency = mapIbanToCurrency(senderIban);
        
        // Get IBANs with matching currency (O(1) lookup from pre-computed index)
        List<String> matchingCurrencyIbans = IBAN_BY_CURRENCY.get(currency);
        
        if (matchingCurrencyIbans == null || matchingCurrencyIbans.isEmpty()) {
            logger.error("No IBANs found for currency: {}. This should not happen. Sender IBAN: {}", currency, senderIban);
            // Fallback: retry with a new sender (edge case handling)
            return generateRealisticPacs008Message();
        }
        
        // Filter out sender IBAN to ensure different accounts
        // Use ArrayList for efficient random access
        List<String> availableReceivers = new ArrayList<>();
        for (String iban : matchingCurrencyIbans) {
            if (!iban.equals(senderIban)) {
                availableReceivers.add(iban);
            }
        }
        
        // Handle edge case: only one IBAN for this currency
        if (availableReceivers.isEmpty()) {
            // Select a different sender from a currency with multiple IBANs
            logger.debug("Only one IBAN available for currency: {}. Selecting different sender.", currency);
            // Find a currency with multiple IBANs
            for (Map.Entry<String, List<String>> entry : IBAN_BY_CURRENCY.entrySet()) {
                if (entry.getValue().size() > 1) {
                    senderIban = entry.getValue().get(ThreadLocalRandom.current().nextInt(entry.getValue().size()));
                    currency = entry.getKey();
                    // Re-filter with new sender
                    availableReceivers.clear();
                    for (String iban : entry.getValue()) {
                        if (!iban.equals(senderIban)) {
                            availableReceivers.add(iban);
                        }
                    }
                    break;
                }
            }
            // If still empty (shouldn't happen with proper IBAN pool), use fallback
            if (availableReceivers.isEmpty()) {
                logger.warn("Unable to find suitable IBAN pair. Using fallback selection.");
                // Fallback: use first two IBANs from pool (they may have different currencies, but this is edge case)
                senderIban = IBAN_POOL.get(0);
                currency = mapIbanToCurrency(senderIban);
                // Try to find a matching currency receiver
                List<String> fallbackReceivers = IBAN_BY_CURRENCY.get(currency);
                if (fallbackReceivers != null && !fallbackReceivers.isEmpty()) {
                    availableReceivers = new ArrayList<>(fallbackReceivers);
                    availableReceivers.remove(senderIban);
                }
                if (availableReceivers.isEmpty() && IBAN_POOL.size() > 1) {
                    // Last resort: use second IBAN and adjust currency
                    String fallbackReceiver = IBAN_POOL.get(1);
                    String fallbackCurrency = mapIbanToCurrency(fallbackReceiver);
                    // Use receiver's currency if it has more IBANs
                    if (IBAN_BY_CURRENCY.getOrDefault(fallbackCurrency, Collections.emptyList()).size() > 
                        IBAN_BY_CURRENCY.getOrDefault(currency, Collections.emptyList()).size()) {
                        currency = fallbackCurrency;
                        senderIban = fallbackReceiver;
                        availableReceivers = new ArrayList<>(IBAN_BY_CURRENCY.get(currency));
                        availableReceivers.remove(senderIban);
                    } else {
                        availableReceivers.add(fallbackReceiver);
                    }
                }
            }
        }
        
        // Select receiver IBAN from filtered list (guaranteed to have matching currency)
        // This will always succeed because we've ensured availableReceivers is not empty above
        String receiverIban;
        if (availableReceivers.isEmpty()) {
            // Ultimate fallback (should never happen with proper IBAN pool)
            logger.error("Critical: No receiver IBANs available. Using first IBAN as fallback.");
            receiverIban = IBAN_POOL.get(0);
        } else {
            receiverIban = availableReceivers.get(ThreadLocalRandom.current().nextInt(availableReceivers.size()));
        }

        // Ensure different accounts for same bank (final check)
        if (senderIban.equals(receiverIban) && senderName.equals(receiverName)) {
            // If same IBAN and same bank name, select different receiver name
            receiverName = BANK_NAMES.get(ThreadLocalRandom.current().nextInt(BANK_NAMES.size()));
        }

        return generatePacs008Xml(messageId, now, amount, currency,
                                senderName, senderIban, receiverName, receiverIban,
                                messagesSent.get());
    }

    private BigDecimal generateRealisticAmount() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double rand = random.nextDouble();

        // Weighted distribution: most transactions small, few large ones
        if (rand < 0.7) {
            // 70% small transactions (100-5000)
            return BigDecimal.valueOf(random.nextDouble(100, 5000)).setScale(2, java.math.RoundingMode.HALF_UP);
        } else if (rand < 0.9) {
            // 20% medium transactions (5000-50000)
            return BigDecimal.valueOf(random.nextDouble(5000, 50000)).setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            // 10% large transactions (50000-1000000)
            return BigDecimal.valueOf(random.nextDouble(50000, 1000000)).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Initialize currency-indexed IBAN map for O(1) lookup.
     * This is computed once at class initialization for optimal performance.
     * 
     * @return Immutable map of currency -> list of IBANs
     */
    private static Map<String, List<String>> initializeCurrencyIndex() {
        Map<String, List<String>> index = new HashMap<>();
        
        for (String iban : IBAN_POOL) {
            String currency = mapIbanToCurrencyStatic(iban);
            index.computeIfAbsent(currency, k -> new ArrayList<>()).add(iban);
        }
        
        // Make lists immutable for thread safety and prevent accidental modification
        Map<String, List<String>> immutableIndex = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : index.entrySet()) {
            immutableIndex.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        
        logger.info("Initialized currency index: {} currencies, {} total IBANs", 
                   immutableIndex.size(), IBAN_POOL.size());
        for (Map.Entry<String, List<String>> entry : immutableIndex.entrySet()) {
            logger.debug("Currency {}: {} IBANs", entry.getKey(), entry.getValue().size());
        }
        
        return Collections.unmodifiableMap(immutableIndex);
    }

    /**
     * Map IBAN to currency based on country code.
     * This matches the logic in AccountSeedingService to ensure currency compatibility.
     * 
     * @param iban The IBAN to map
     * @return The currency code for the IBAN's country
     */
    private String mapIbanToCurrency(String iban) {
        return mapIbanToCurrencyStatic(iban);
    }

    /**
     * Static version for use in static initialization.
     */
    private static String mapIbanToCurrencyStatic(String iban) {
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
            // EUR for most European countries (DE, FR, ES, IT, NL, AT, BE, FI, etc.)
            return "EUR";
        }
    }

    private String generatePacs008Xml(UUID messageId, LocalDateTime timestamp, BigDecimal amount,
                                    String currency, String senderName, String senderIban,
                                    String receiverName, String receiverIban, long sequenceNumber) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <pacs008:Document xmlns:pacs008="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
                <pacs008:FIToFICstmrCdtTrf>
                    <pacs008:GrpHdr>
                        <pacs008:MsgId>%s</pacs008:MsgId>
                        <pacs008:CreDtTm>%s</pacs008:CreDtTm>
                        <pacs008:NbOfTxs>1</pacs008:NbOfTxs>
                        <pacs008:TtlIntrBkSttlmAmt Ccy="%s">%.2f</pacs008:TtlIntrBkSttlmAmt>
                        <pacs008:IntrBkSttlmDt>%s</pacs008:IntrBkSttlmDt>
                        <pacs008:SttlmInf>
                            <pacs008:SttlmMtd>CLRG</pacs008:SttlmMtd>
                        </pacs008:SttlmInf>
                    </pacs008:GrpHdr>
                    <pacs008:CdtTrfTxInf>
                        <pacs008:PmtId>
                            <pacs008:EndToEndId>E2E-%010d</pacs008:EndToEndId>
                        </pacs008:PmtId>
                        <pacs008:IntrBkSttlmAmt Ccy="%s">%.2f</pacs008:IntrBkSttlmAmt>
                        <pacs008:ChrgBr>SLEV</pacs008:ChrgBr>
                        <pacs008:Dbtr>
                            <pacs008:Nm>%s</pacs008:Nm>
                        </pacs008:Dbtr>
                        <pacs008:DbtrAcct>
                            <pacs008:Id>
                                <pacs008:IBAN>%s</pacs008:IBAN>
                            </pacs008:Id>
                        </pacs008:DbtrAcct>
                        <pacs008:DbtrAgt>
                            <pacs008:FinInstnId>
                                <pacs008:BICFI>DEUTDEFFXXX</pacs008:BICFI>
                            </pacs008:FinInstnId>
                        </pacs008:DbtrAgt>
                        <pacs008:CdtrAgt>
                            <pacs008:FinInstnId>
                                <pacs008:BICFI>HSBCGB2LXXX</pacs008:BICFI>
                            </pacs008:FinInstnId>
                        </pacs008:CdtrAgt>
                        <pacs008:Cdtr>
                            <pacs008:Nm>%s</pacs008:Nm>
                        </pacs008:Cdtr>
                        <pacs008:CdtrAcct>
                            <pacs008:Id>
                                <pacs008:IBAN>%s</pacs008:IBAN>
                            </pacs008:Id>
                        </pacs008:CdtrAcct>
                    </pacs008:CdtTrfTxInf>
                </pacs008:FIToFICstmrCdtTrf>
            </pacs008:Document>
            """,
            messageId.toString(),
            timestamp.toString().replace('T', 'T').substring(0, 19),
            currency, amount,
            timestamp.toLocalDate(),
            sequenceNumber,
            currency, amount,
            senderName, senderIban,
            receiverName, receiverIban
        );
    }


    private static List<String> generateIbanPool() {
        List<String> ibans = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add some real-looking IBANs for major European banks
        ibans.addAll(Arrays.asList(
            "DE89370400440532013000", "DE89370400440532013001", "DE89370400440532013002", // Deutsche Bank
            "DE89370400440532013003", "DE89370400440532013004", "DE89370400440532013005",
            "GB29RBOS60161331926819", "GB29RBOS60161331926820", "GB29RBOS60161331926821", // Royal Bank of Scotland
            "GB29RBOS60161331926822", "GB29RBOS60161331926823", "GB29RBOS60161331926824",
            "FR7630006000011234567890189", "FR7630006000011234567890190", "FR7630006000011234567890191", // BNP Paribas
            "FR7630006000011234567890192", "FR7630006000011234567890193", "FR7630006000011234567890194",
            "ES9121000418450200051332", "ES9121000418450200051333", "ES9121000418450200051334", // Banco Santander
            "ES9121000418450200051335", "ES9121000418450200051336", "ES9121000418450200051337",
            "IT60X0542811101000000123456", "IT60X0542811101000000123457", "IT60X0542811101000000123458", // Unicredit
            "IT60X0542811101000000123459", "IT60X0542811101000000123460", "IT60X0542811101000000123461",
            "NL91ABNA0417164300", "NL91ABNA0417164301", "NL91ABNA0417164302", // ING Group
            "NL91ABNA0417164303", "NL91ABNA0417164304", "NL91ABNA0417164305",
            "CH6309000000900100000", "CH6309000000900100001", "CH6309000000900100002", // UBS
            "CH6309000000900100003", "CH6309000000900100004", "CH6309000000900100005"
        ));

        // United States (account numbers, not IBAN)
        for (int i = 0; i < 10; i++) {
            ibans.add(String.format("US%02d%04d%012d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(100000000000L, 999999999999L)
            ));
        }

        // Canada
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("CA%02d%04d%012d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(100000000000L, 999999999999L)
            ));
        }

        // Australia
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("AU%02d%04d%010d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // Japan
        for (int i = 0; i < 10; i++) {
            ibans.add(String.format("JP%02d%04d%010d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // China
        for (int i = 0; i < 10; i++) {
            ibans.add(String.format("CN%02d%04d%016d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000000000L, 9999999999999999L)
            ));
        }

        // India
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("IN%02d%04d%011d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(10000000000L, 99999999999L)
            ));
        }

        // Brazil
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("BR%02d%08d%05d%01d%010d",
                random.nextInt(10, 99),
                random.nextInt(10000000, 99999999),
                random.nextInt(10000, 99999),
                random.nextInt(0, 9),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // Mexico
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("MX%02d%04d%018d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(100000000000000000L, 999999999999999999L)
            ));
        }

        // South Korea
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("KR%02d%04d%010d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // Singapore
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("SG%02d%04d%010d%03d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000L, 9999999999L),
                random.nextInt(100, 999)
            ));
        }

        // Hong Kong
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("HK%02d%03d%010d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // UAE
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("AE%02d%03d%016d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextLong(1000000000000000L, 9999999999999999L)
            ));
        }

        // Saudi Arabia
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("SA%02d%02d%018d",
                random.nextInt(10, 99),
                random.nextInt(10, 99),
                random.nextLong(100000000000000000L, 999999999999999999L)
            ));
        }

        // South Africa
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("ZA%02d%02d%010d%03d",
                random.nextInt(10, 99),
                random.nextInt(10, 99),
                random.nextLong(1000000000L, 9999999999L),
                random.nextInt(100, 999)
            ));
        }

        // Norway
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("NO%02d%04d%06d%01d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextInt(100000, 999999),
                random.nextInt(0, 9)
            ));
        }

        // Sweden
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("SE%02d%03d%17d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextLong(10000000000000000L, 99999999999999999L)
            ));
        }

        // Denmark
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("DK%02d%04d%010d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // Poland
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("PL%02d%08d%016d",
                random.nextInt(10, 99),
                random.nextInt(10000000, 99999999),
                random.nextLong(1000000000000000L, 9999999999999999L)
            ));
        }

        // Czech Republic
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("CZ%02d%04d%06d%010d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextInt(100000, 999999),
                random.nextLong(1000000000L, 9999999999L)
            ));
        }

        // Turkey
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("TR%02d%05d%01d%016d",
                random.nextInt(10, 99),
                random.nextInt(10000, 99999),
                random.nextInt(0, 9),
                random.nextLong(1000000000000000L, 9999999999999999L)
            ));
        }

        // Russia
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("RU%02d%04d%04d%015d%01d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextInt(1000, 9999),
                random.nextLong(100000000000000L, 999999999999999L),
                random.nextInt(0, 9)
            ));
        }

        // Argentina
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("AR%02d%04d%018d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextLong(100000000000000000L, 999999999999999999L)
            ));
        }

        // Chile
        for (int i = 0; i < 8; i++) {
            ibans.add(String.format("CL%02d%03d%014d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextLong(10000000000000L, 99999999999999L)
            ));
        }

        // Additional European countries
        // Austria
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("AT%02d%05d%011d",
                random.nextInt(10, 99),
                random.nextInt(10000, 99999),
                random.nextLong(10000000000L, 99999999999L)
            ));
        }

        // Belgium
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("BE%02d%03d%07d%02d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextInt(1000000, 9999999),
                random.nextInt(10, 99)
            ));
        }

        // Finland
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("FI%02d%06d%07d",
                random.nextInt(10, 99),
                random.nextInt(100000, 999999),
                random.nextInt(1000000, 9999999)
            ));
        }

        // Greece
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("GR%02d%03d%04d%016d",
                random.nextInt(10, 99),
                random.nextInt(100, 999),
                random.nextInt(1000, 9999),
                random.nextLong(1000000000000000L, 9999999999999999L)
            ));
        }

        // Portugal
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("PT%02d%04d%04d%011d%02d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextInt(1000, 9999),
                random.nextLong(10000000000L, 99999999999L),
                random.nextInt(10, 99)
            ));
        }

        // Ireland
        for (int i = 0; i < 6; i++) {
            ibans.add(String.format("IE%02d%04d%04d%08d",
                random.nextInt(10, 99),
                random.nextInt(1000, 9999),
                random.nextInt(1000, 9999),
                random.nextInt(10000000, 99999999)
            ));
        }

        // Generate additional random but valid-looking IBANs (mostly European)
        for (int i = 0; i < 100; i++) {
            // Generate German IBAN (most common)
            String iban = String.format("DE%02d%04d%010d",
                random.nextInt(10, 99), // BLZ
                random.nextInt(1000, 9999), // Bank code
                random.nextLong(1000000000L, 9999999999L) // Account number
            );
            ibans.add(iban);
        }

        return ibans;
    }

    private long getStartTime() {
        // This would need to be tracked properly - for now return current time
        return System.currentTimeMillis();
    }

    @PreDestroy
    public void shutdown() {
        stopSimulation();
    }
}
