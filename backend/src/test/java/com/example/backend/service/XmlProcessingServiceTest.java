package com.example.backend.service;

import com.example.backend.dto.Pacs008Message;
import com.example.backend.dto.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.bind.JAXBException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XmlProcessingService.
 * Tests XSD validation and JAXB unmarshalling functionality.
 */
@ExtendWith(MockitoExtension.class)
class XmlProcessingServiceTest {

    private XmlProcessingService xmlProcessingService;

    @BeforeEach
    void setUp() throws JAXBException {
        xmlProcessingService = new XmlProcessingService();
    }

    @Test
    void shouldRejectNullOrEmptyXml() {
        // Given
        String nullXml = null;
        String emptyXml = "";
        String whitespaceXml = "   ";

        // When & Then
        assertThat(xmlProcessingService.validateAndParse(nullXml).isSuccessful()).isFalse();
        assertThat(xmlProcessingService.validateAndParse(emptyXml).isSuccessful()).isFalse();
        assertThat(xmlProcessingService.validateAndParse(whitespaceXml).isSuccessful()).isFalse();
    }

    @Test
    void shouldRejectInvalidXmlFormat() {
        // Given: XML that doesn't conform to pacs.008 schema
        String invalidXml = "<invalid><element>content</element></invalid>";

        // When
        ProcessingResult result = xmlProcessingService.validateAndParse(invalidXml);

        // Then
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ProcessingResult.Status.INVALID_XML);
        assertThat(result.getErrorMessage()).isNotEmpty();
    }

    @Test
    void shouldValidateWellFormedXml() throws Exception {
        // Given: Well-formed XML that passes basic XML validation
        String wellFormedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <element>value</element>
            </root>
            """;

        // When
        ProcessingResult result = xmlProcessingService.validateAndParse(wellFormedXml);

        // Then: Should fail XSD validation but not basic XML parsing
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ProcessingResult.Status.INVALID_XML);
    }

    @Test
    void shouldHandleJaxbExceptionsGracefully() {
        // Given: XML that causes JAXB parsing errors
        String malformedXml = "<?xml version=\"1.0\"?><root><unclosed>";

        // When
        ProcessingResult result = xmlProcessingService.validateAndParse(malformedXml);

        // Then
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ProcessingResult.Status.INVALID_XML);
    }

    @Test
    void shouldInitializeJaxbContextSuccessfully() {
        // Test that the service initializes without throwing exceptions
        assertThat(xmlProcessingService).isNotNull();
    }
}
