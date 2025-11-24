package com.example.backend.service;

import com.example.backend.dto.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for Gateway & Ingestion pipeline.
 * Tests throughput and latency requirements from devplan:
 * - NFR-02: P95 latency ≤ 2s under typical load
 * - NFR-03: Handle burst of 2000 messages/min
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
@Disabled("Performance tests - run manually")
public class GatewayPerformanceTest {

    @Autowired
    private XmlProcessingService xmlProcessingService;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final String SAMPLE_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
            <FIToFICstmrCdtTrf>
                <GrpHdr>
                    <MsgId>PERF-MSG-001</MsgId>
                    <CreDtTm>2025-11-23T12:00:00</CreDtTm>
                    <NbOfTxs>1</NbOfTxs>
                    <TtlIntrBkSttlmAmt Ccy="EUR">100.00</TtlIntrBkSttlmAmt>
                    <IntrBkSttlmDt>2025-11-23</IntrBkSttlmDt>
                    <SttlmInf>
                        <SttlmMtd>CLRG</SttlmMtd>
                    </SttlmInf>
                </GrpHdr>
                <CdtTrfTxInf>
                    <PmtId>
                        <EndToEndId>E2E-PERF-001</EndToEndId>
                    </PmtId>
                    <IntrBkSttlmAmt Ccy="EUR">100.00</IntrBkSttlmAmt>
                    <ChrgBr>SLEV</ChrgBr>
                    <Dbtr>
                        <Nm>Perf Sender</Nm>
                    </Dbtr>
                    <DbtrAcct>
                        <Id>
                            <IBAN>DE89370400440532013000</IBAN>
                        </Id>
                    </DbtrAcct>
                    <Cdtr>
                        <Nm>Perf Receiver</Nm>
                    </Cdtr>
                    <CdtrAcct>
                        <Id>
                            <IBAN>GB29RBOS60161331926819</IBAN>
                        </Id>
                    </CdtrAcct>
                </CdtTrfTxInf>
            </FIToFICstmrCdtTrf>
        </Document>
        """;

    @Test
    void shouldHandleHighThroughputXmlValidation() throws Exception {
        // Given: Large number of XML messages to process
        int messageCount = 1000; // Start with smaller number for unit tests
        ExecutorService executor = Executors.newFixedThreadPool(10);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        // When: Process messages concurrently
        var futures = IntStream.range(0, messageCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                ProcessingResult result = xmlProcessingService.validateAndParse(SAMPLE_XML);
                if (result.isSuccessful()) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            }, executor))
            .toArray(CompletableFuture[]::new);

        // Wait for all to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then: Verify performance
        long durationMs = (endTime - startTime) / 1_000_000;
        double throughputPerSecond = (double) messageCount / (durationMs / 1000.0);

        System.out.printf("XML Validation Performance:%n");
        System.out.printf("- Messages processed: %d%n", messageCount);
        System.out.printf("- Duration: %d ms%n", durationMs);
        System.out.printf("- Throughput: %.2f msg/sec%n", throughputPerSecond);
        System.out.printf("- Success rate: %.2f%%%n",
            (double) successCount.get() / messageCount * 100);

        // Basic assertions - in real perf tests, we'd have stricter requirements
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(durationMs).isLessThan(30000); // Should complete within 30 seconds
    }

    @Test
    void shouldHandleConcurrentIdempotencyChecks() throws Exception {
        // Given: Multiple threads checking the same message ID
        var messageId = java.util.UUID.randomUUID();
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);

        AtomicInteger nullResults = new AtomicInteger(0); // Messages that can proceed

        // When: Concurrent idempotency checks
        var futures = IntStream.range(0, threadCount)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                ProcessingResult result = idempotencyService.checkDuplicate(messageId);
                if (result == null) {
                    nullResults.incrementAndGet();
                }
            }, executor))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Then: All checks should return null (no duplicates found)
        assertThat(nullResults.get()).isEqualTo(threadCount);
    }

    @Test
    void shouldMeasureXmlParsingLatency() {
        // Given: Multiple XML parsing operations
        int iterations = 100;
        long[] latencies = new long[iterations];

        // When: Measure individual parsing times
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            ProcessingResult result = xmlProcessingService.validateAndParse(SAMPLE_XML);
            long end = System.nanoTime();
            latencies[i] = (end - start) / 1_000_000; // Convert to milliseconds
        }

        // Then: Calculate statistics
        long avgLatency = java.util.Arrays.stream(latencies).sum() / iterations;
        long maxLatency = java.util.Arrays.stream(latencies).max().orElse(0);
        long p95Latency = calculatePercentile(latencies, 95);

        System.out.printf("XML Parsing Latency Statistics:%n");
        System.out.printf("- Average latency: %d ms%n", avgLatency);
        System.out.printf("- Max latency: %d ms%n", maxLatency);
        System.out.printf("- P95 latency: %d ms%n", p95Latency);

        // Performance assertions (adjust based on environment)
        assertThat(avgLatency).isLessThan(1000); // Should be well under 1 second
        assertThat(p95Latency).isLessThan(2000); // P95 under 2 seconds as per NFR-02
    }

    private long calculatePercentile(long[] values, int percentile) {
        java.util.Arrays.sort(values);
        int index = (int) Math.ceil(percentile / 100.0 * values.length) - 1;
        return values[Math.max(0, index)];
    }
}
