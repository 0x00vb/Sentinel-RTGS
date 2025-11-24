package com.example.backend.service;

import com.example.backend.dto.ProcessingResult;
import com.example.backend.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Gateway & Ingestion pipeline components.
 * Tests XML validation, idempotency checking, and basic processing.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
public class GatewayServiceIntegrationTest {

    @Autowired
    private XmlProcessingService xmlProcessingService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void shouldRejectInvalidXmlMessage() {
        // Given: Invalid XML message
        String invalidXml = "<invalid>xml</invalid>";

        // When: Processing the message
        ProcessingResult result = xmlProcessingService.validateAndParse(invalidXml);

        // Then: Message should be rejected
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ProcessingResult.Status.INVALID_XML);
    }

    @Test
    void shouldValidateXmlProcessingComponents() {
        // Test that XML processing service is properly configured
        assertThat(xmlProcessingService).isNotNull();

        // Test that idempotency service is properly configured
        assertThat(idempotencyService).isNotNull();

        // Test basic XML validation with invalid input
        String invalidXml = "<not-xml>";
        ProcessingResult result = xmlProcessingService.validateAndParse(invalidXml);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void shouldHandleIdempotencyForNewMessages() {
        // Given: A new message ID
        UUID messageId = UUID.randomUUID();

        // When: Checking for duplicates
        ProcessingResult result = idempotencyService.checkDuplicate(messageId);

        // Then: Should return null (no duplicate found)
        assertThat(result).isNull();
    }

    /**
     * Creates a minimal valid pacs.008 XML message for testing.
     */
    private String createValidPacs008Xml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
                <FIToFICstmrCdtTrf>
                    <GrpHdr>
                        <MsgId>MSG-001</MsgId>
                        <CreDtTm>2025-11-23T12:00:00</CreDtTm>
                        <NbOfTxs>1</NbOfTxs>
                        <TtlIntrBkSttlmAmt Ccy="EUR">500.00</TtlIntrBkSttlmAmt>
                        <IntrBkSttlmDt>2025-11-23</IntrBkSttlmDt>
                        <SttlmInf>
                            <SttlmMtd>CLRG</SttlmMtd>
                        </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                        <PmtId>
                            <EndToEndId>E2E-001</EndToEndId>
                        </PmtId>
                        <IntrBkSttlmAmt Ccy="EUR">500.00</IntrBkSttlmAmt>
                        <ChrgBr>SLEV</ChrgBr>
                        <Dbtr>
                            <Nm>Test Sender</Nm>
                        </Dbtr>
                        <DbtrAcct>
                            <Id>
                                <IBAN>DE89370400440532013000</IBAN>
                            </Id>
                        </DbtrAcct>
                        <Cdtr>
                            <Nm>Test Receiver</Nm>
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
    }
}
