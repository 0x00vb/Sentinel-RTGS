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

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    // Simulation control
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> simulationTask;

    // Realistic IBAN pool - mix of generated and real-looking IBANs
    private static final List<String> IBAN_POOL = generateIbanPool();

    // Bank names for realistic simulation
    private static final List<String> BANK_NAMES = Arrays.asList(
        "Deutsche Bank AG", "Commerzbank AG", "HSBC Bank PLC", "Barclays Bank PLC",
        "BNP Paribas", "Société Générale", "Santander Bank", "Banco Bilbao Vizcaya Argentaria",
        "UBS AG", "Credit Suisse AG", "ING Group", "ABN AMRO Bank", "Nordea Bank",
        "Danske Bank", "SEB", "Swedbank", "DNB", "Handelsbanken", "Erste Group Bank",
        "Raiffeisen Bank", "UniCredit Group", "Intesa Sanpaolo", "Banca Monte dei Paschi",
        "Banco Santander", "CaixaBank", "Bankia", "Sabadell", "Kutxabank"
    );

    // Currencies with realistic distribution
    private static final List<String> CURRENCIES = Arrays.asList("EUR", "USD", "GBP", "CHF", "SEK", "NOK", "DKK", "PLN");

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

        // Select currency with realistic distribution
        String currency = selectWeightedCurrency();

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

        // Select IBANs from pool (with repetition for account reuse)
        String senderIban = IBAN_POOL.get(ThreadLocalRandom.current().nextInt(IBAN_POOL.size()));
        String receiverIban = IBAN_POOL.get(ThreadLocalRandom.current().nextInt(IBAN_POOL.size()));

        // Ensure different accounts for same bank
        while (senderIban.equals(receiverIban) && senderName.equals(receiverName)) {
            receiverIban = IBAN_POOL.get(ThreadLocalRandom.current().nextInt(IBAN_POOL.size()));
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

    private String selectWeightedCurrency() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double rand = random.nextDouble();

        // Realistic currency distribution
        if (rand < 0.5) return "EUR";      // 50% EUR (Eurozone dominance)
        if (rand < 0.7) return "USD";      // 20% USD (international trade)
        if (rand < 0.8) return "GBP";      // 10% GBP (UK financial hub)
        if (rand < 0.9) return "CHF";      // 10% CHF (Swiss banking)
        return CURRENCIES.get(random.nextInt(CURRENCIES.size())); // 10% other
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
                        <pacs008:DbtrAgt>
                            <pacs008:FinInstnId>
                                <pacs008:Nm>Deutsche Bank AG</pacs008:Nm>
                            </pacs008:FinInstnId>
                        </pacs008:DbtrAgt>
                        <pacs008:DbtrAcct>
                            <pacs008:Id>
                                <pacs008:IBAN>%s</pacs008:IBAN>
                            </pacs008:Id>
                        </pacs008:DbtrAcct>
                        <pacs008:Cdtr>
                            <pacs008:Nm>%s</pacs008:Nm>
                        </pacs008:Cdtr>
                        <pacs008:CdtrAgt>
                            <pacs008:FinInstnId>
                                <pacs008:Nm>HSBC Bank PLC</pacs008:Nm>
                            </pacs008:FinInstnId>
                        </pacs008:CdtrAgt>
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

        // Generate additional random but valid-looking IBANs
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 200; i++) {
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
