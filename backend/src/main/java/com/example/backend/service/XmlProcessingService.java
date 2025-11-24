package com.example.backend.service;

import com.example.backend.dto.Pacs008Message;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.exception.XmlValidationException;

import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.example.backend.iso20022.pacs008.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for processing ISO 20022 pacs.008 XML messages.
 * Handles XSD validation and JAXB unmarshalling to internal DTOs.
 */
@Service
public class XmlProcessingService {

    private final JAXBContext jaxbContext;
    private final Schema pacs008Schema;

    public XmlProcessingService() throws JAXBException {
        // Initialize JAXB context for pacs.008
        this.jaxbContext = JAXBContext.newInstance(Document.class);

        // Load XSD schema for validation
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.pacs008Schema = schemaFactory.newSchema(
                getClass().getClassLoader().getResource("xsd/pacs.008.001.10.xsd")
            );
        } catch (Exception e) {
            throw new JAXBException("Failed to load pacs.008 XSD schema", e);
        }
    }

    /**
     * Validates and parses a pacs.008 XML message string.
     *
     * @param xmlContent The XML message content as string
     * @return ProcessingResult with parsed message or validation error
     */
    public ProcessingResult validateAndParse(String xmlContent) {
        if (!StringUtils.hasText(xmlContent)) {
            return ProcessingResult.invalidXml("Empty or null XML content");
        }

        try {
            // First, validate against XSD schema
            validateXmlSchema(xmlContent);

            // If validation passes, unmarshal to JAXB objects
            Document document = unmarshalXml(xmlContent);

            // Convert to internal DTO
            Pacs008Message message = convertToInternalMessage(document);

            return ProcessingResult.success(null); // Will be enhanced with transfer creation later

        } catch (XmlValidationException e) {
            return ProcessingResult.invalidXml(e.getMessage());
        } catch (Exception e) {
            String errorMessage = "XML processing failed: " + e.getMessage();
            return ProcessingResult.invalidXml(errorMessage);
        }
    }

    /**
     * Validates XML content against the pacs.008 XSD schema.
     */
    private void validateXmlSchema(String xmlContent) throws XmlValidationException {
        try {
            Validator validator = pacs008Schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            throw new XmlValidationException(
                "XML validation failed: " + e.getMessage(),
                "unknown", // messageId not available at this stage
                e
            );
        }
    }

    /**
     * Unmarshals XML content to JAXB Document object.
     */
    private Document unmarshalXml(String xmlContent) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (Document) unmarshaller.unmarshal(new StringReader(xmlContent));
    }

    /**
     * Converts JAXB Document to internal Pacs008Message DTO.
     * For simplicity, we process the first transaction in the message.
     */
    private Pacs008Message convertToInternalMessage(Document document) {
        FIToFICustomerCreditTransferV10 creditTransfer = document.getFIToFICstmrCdtTrf();
        GroupHeader93 groupHeader = creditTransfer.getGrpHdr();

        // For this implementation, we process the first transaction only
        // In a full implementation, you'd handle multiple transactions
        CreditTransferTransactionInformation26 transaction = creditTransfer.getCdtTrfTxInf().get(0);

        // Generate UUID from message ID or create new one
        UUID messageId = UUID.randomUUID(); // In practice, this would be derived from the message

        // Parse creation date time
        LocalDateTime creationDateTime = parseCreationDateTime(groupHeader.getCreDtTm());

        // Extract amount and currency
        ActiveCurrencyAndAmount amount = transaction.getIntrBkSttlmAmt();
        BigDecimal amountValue = amount.getValue();
        String currency = amount.getCcy();

        // Extract party information
        String senderName = extractPartyName(transaction.getDbtr());
        String senderIban = extractIban(transaction.getDbtrAcct());

        String receiverName = extractPartyName(transaction.getCdtr());
        String receiverIban = extractIban(transaction.getCdtrAcct());

        // Extract other fields
        String chargeBearer = transaction.getChrgBr().value();
        String endToEndId = transaction.getPmtId().getEndToEndId();

        return new Pacs008Message(
            messageId,
            groupHeader.getMsgId(),
            creationDateTime,
            amountValue,
            currency,
            senderName,
            senderIban,
            receiverName,
            receiverIban,
            chargeBearer,
            endToEndId
        );
    }

    private LocalDateTime parseCreationDateTime(XMLGregorianCalendar calendar) {
        try {
            if (calendar != null) {
                return calendar.toGregorianCalendar().toZonedDateTime().withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            // Log the error but don't throw - we'll use current time as fallback
        }
        // Fallback to current time if parsing fails
        return LocalDateTime.now();
    }

    private String extractPartyName(PartyIdentification135 party) {
        return party != null && party.getNm() != null ? party.getNm() : "";
    }

    private String extractIban(CashAccount38 account) {
        if (account != null && account.getId() != null) {
            AccountIdentification4Choice id = account.getId();
            if (id.getIBAN() != null) {
                return id.getIBAN();
            }
        }
        return "";
    }

    /**
     * Returns the parsed message if validation was successful.
     * This method should be called after validateAndParse returns a successful result.
     */
    public Pacs008Message parseValidatedXml(String xmlContent) throws Exception {
        Document document = unmarshalXml(xmlContent);
        return convertToInternalMessage(document);
    }
}
