package com.example.backend.service;

import com.example.backend.config.RabbitMQConfig;
import com.example.backend.dto.StatusReportMessage;
import com.example.backend.iso20022.pacs002.Document;
import com.example.backend.iso20022.pacs002.FIToFIPaymentStatusReportV12;
import com.example.backend.iso20022.pacs002.ObjectFactory;
import com.example.backend.iso20022.pacs002.GroupHeader96;
import com.example.backend.iso20022.pacs002.OriginalGroupInformation29;
import com.example.backend.iso20022.pacs002.TransactionGroupStatus3Code;
import com.example.backend.iso20022.pacs002.StatusReasonInformation12;
import com.example.backend.iso20022.pacs002.StatusReason6Choice;
import com.example.backend.iso20022.pacs002.ExternalStatusReason1Code;
import com.example.backend.iso20022.pacs002.PaymentTransaction110;
import com.example.backend.iso20022.pacs002.TransactionIndividualStatus5Code;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for generating and sending ISO 20022 pacs.002 status reports to bank.outbound queue.
 * Handles rejection notifications for invalid or blocked messages.
 */
@Service
public class MessageOutboundService {

    private static final Logger logger = LoggerFactory.getLogger(MessageOutboundService.class);

    private final RabbitTemplate rabbitTemplate;
    private final JAXBContext jaxbContext;

    @Autowired
    public MessageOutboundService(RabbitTemplate rabbitTemplate) throws JAXBException {
        this.rabbitTemplate = rabbitTemplate;
        // Initialize JAXB context for pacs.002
        this.jaxbContext = JAXBContext.newInstance(Document.class);
    }

    /**
     * Sends a status report for a rejected or blocked message.
     *
     * @param statusReport The status report details
     */
    public void sendStatusReport(StatusReportMessage statusReport) {
        try {
            String xmlContent = generateStatusReportXml(statusReport);
            sendToOutboundQueue(xmlContent);
            logger.info("Sent status report for message: {}, status: {}",
                       statusReport.getOriginalMessageId(), statusReport.getStatus());
        } catch (Exception e) {
            logger.error("Failed to send status report for message: {}",
                        statusReport.getOriginalMessageId(), e);
            // In a production system, you might want to implement retry logic or dead-letter handling
        }
    }

    /**
     * Generates pacs.002 XML content from status report message.
     */
    private String generateStatusReportXml(StatusReportMessage statusReport) throws JAXBException {
        Document document = createStatusReportDocument(statusReport);

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        StringWriter writer = new StringWriter();
        ObjectFactory objectFactory = new ObjectFactory();
        marshaller.marshal(objectFactory.createDocument(document), writer);

        return writer.toString();
    }

    /**
     * Creates a JAXB Document object for the status report.
     */
    private Document createStatusReportDocument(StatusReportMessage statusReport) {
        Document document = new Document();
        FIToFIPaymentStatusReportV12 statusReportObj = new FIToFIPaymentStatusReportV12();

        // Set group header
        GroupHeader96 groupHeader = new GroupHeader96();
        groupHeader.setMsgId(generateMessageId());
        groupHeader.setCreDtTm(convertToXMLGregorianCalendar(LocalDateTime.now()));
        statusReportObj.setGrpHdr(groupHeader);

        // Set original group information and status
        OriginalGroupInformation29 originalGroupInfo = new OriginalGroupInformation29();
        originalGroupInfo.setOrgnlMsgId(statusReport.getOriginalMessageId());
        originalGroupInfo.setOrgnlMsgNmId("pacs.008.001.10");

        // Set group status based on the status report
        TransactionGroupStatus3Code groupStatus = mapStatusToGroupStatus(statusReport.getStatus());
        originalGroupInfo.setGrpSts(groupStatus);

        // Add status reason information if applicable
        if (statusReport.getReasonCode() != null && statusReport.getReasonDescription() != null) {
            StatusReasonInformation12 statusReasonInfo = new StatusReasonInformation12();
            StatusReason6Choice reason = new StatusReason6Choice();
            try {
                ExternalStatusReason1Code reasonCode = ExternalStatusReason1Code.fromValue(statusReport.getReasonCode());
                reason.setCd(reasonCode);
                statusReasonInfo.setRsn(reason);
                statusReasonInfo.getAddtlInf().add(statusReport.getReasonDescription());
                originalGroupInfo.getStsRsnInf().add(statusReasonInfo);
            } catch (IllegalArgumentException e) {
                // If the reason code is not valid, skip adding status reason
            }
        }

        statusReportObj.getOrgnlGrpInfAndSts().add(originalGroupInfo);

        // Add transaction information if we have a transaction ID
        if (statusReport.getTransactionId() != null) {
            PaymentTransaction110 transactionInfo = new PaymentTransaction110();
            transactionInfo.setOrgnlEndToEndId(statusReport.getTransactionId().toString());

            TransactionIndividualStatus5Code txStatus = mapStatusToTransactionStatus(statusReport.getStatus());
            transactionInfo.setTxSts(txStatus);

            // Add status reason for transaction if applicable
            if (statusReport.getReasonCode() != null && statusReport.getReasonDescription() != null) {
                StatusReasonInformation12 txStatusReasonInfo = new StatusReasonInformation12();
                StatusReason6Choice txReason = new StatusReason6Choice();
                try {
                    ExternalStatusReason1Code txReasonCode = ExternalStatusReason1Code.fromValue(statusReport.getReasonCode());
                    txReason.setCd(txReasonCode);
                    txStatusReasonInfo.setRsn(txReason);
                    txStatusReasonInfo.getAddtlInf().add(statusReport.getReasonDescription());
                    transactionInfo.getStsRsnInf().add(txStatusReasonInfo);
                } catch (IllegalArgumentException e) {
                    // If the reason code is not valid, skip adding status reason
                }
            }

            statusReportObj.getTxInfAndSts().add(transactionInfo);
        }

        document.setFIToFIPmtStsRpt(statusReportObj);
        return document;
    }

    /**
     * Maps internal status string to ISO 20022 group status code.
     */
    private TransactionGroupStatus3Code mapStatusToGroupStatus(String status) {
        switch (status.toUpperCase()) {
            case "REJECTED":
            case "INVALID_XML":
                return TransactionGroupStatus3Code.RJCT;
            case "BLOCKED_AML":
                return TransactionGroupStatus3Code.PDNG; // Pending manual review
            default:
                return TransactionGroupStatus3Code.RJCT;
        }
    }

    /**
     * Maps internal status string to ISO 20022 transaction status code.
     */
    private TransactionIndividualStatus5Code mapStatusToTransactionStatus(String status) {
        switch (status.toUpperCase()) {
            case "REJECTED":
            case "INVALID_XML":
                return TransactionIndividualStatus5Code.RJCT;
            case "BLOCKED_AML":
                return TransactionIndividualStatus5Code.PDNG; // Pending manual review
            default:
                return TransactionIndividualStatus5Code.RJCT;
        }
    }

    /**
     * Sends XML content to the outbound queue.
     */
    private void sendToOutboundQueue(String xmlContent) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.OUTBOUND_EX,
            "pacs.002",
            xmlContent
        );
    }

    /**
     * Generates a unique message ID for the status report.
     */
    private String generateMessageId() {
        return "STS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Converts LocalDateTime to XMLGregorianCalendar for JAXB.
     */
    private XMLGregorianCalendar convertToXMLGregorianCalendar(LocalDateTime dateTime) {
        try {
            GregorianCalendar gregorianCalendar = GregorianCalendar.from(dateTime.atZone(ZoneId.systemDefault()));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);
        } catch (Exception e) {
            // Fallback: create a simple XMLGregorianCalendar
            try {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
