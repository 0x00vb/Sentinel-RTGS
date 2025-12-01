package com.example.backend.service;

import com.example.backend.config.RabbitMQConfig;
import com.example.backend.dto.Pacs008Message;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.dto.StatusReportMessage;
import com.example.backend.dto.TransferRequest;
import com.example.backend.dto.TransferResponse;
import com.example.backend.entity.Transfer;
import com.example.backend.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Main Gateway Service for ISO 20022 message ingestion (FR-01).
 * Orchestrates the complete message processing pipeline:
 * 1. XML validation and parsing
 * 2. Idempotency checking
 * 3. Transfer processing (future: sanctions checking, ledger operations)
 * 4. Status report generation for failures
 */
@Service
public class GatewayService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);

    private final XmlProcessingService xmlProcessingService;
    private final IdempotencyService idempotencyService;
    private final MessageOutboundService messageOutboundService;
    private final PaymentService paymentService; // Will be integrated later
    private final ComplianceService complianceService; // Added for sanction checking
    private final AuditService auditService;
    private final TransferRepository transferRepository;

    @Autowired
    public GatewayService(XmlProcessingService xmlProcessingService,
                         IdempotencyService idempotencyService,
                         MessageOutboundService messageOutboundService,
                         PaymentService paymentService,
                         ComplianceService complianceService,
                         AuditService auditService,
                         TransferRepository transferRepository) {
        this.xmlProcessingService = xmlProcessingService;
        this.idempotencyService = idempotencyService;
        this.messageOutboundService = messageOutboundService;
        this.paymentService = paymentService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.transferRepository = transferRepository;
    }

    /**
     * Main message listener for bank.inbound queue (FR-01).
     * Processes ISO 20022 pacs.008 messages through the complete ingestion pipeline.
     */
    @RabbitListener(queues = RabbitMQConfig.INBOUND_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void processInboundMessage(String messageXml, org.springframework.amqp.core.Message amqpMessage) {
        String messageId = amqpMessage.getMessageProperties().getMessageId();
        if (messageId == null) {
            messageId = "unknown-" + System.currentTimeMillis();
        }

        logger.info("Received message from bank.inbound queue: {}", messageId);

        try {
            // Step 1: Validate XML and parse to internal format
            ProcessingResult validationResult = xmlProcessingService.validateAndParse(messageXml);

            if (!validationResult.isSuccessful()) {
                handleValidationFailure(validationResult, messageId);
                return;
            }

            // Parse the validated message
            Pacs008Message pacs008Message = xmlProcessingService.parseValidatedXml(messageXml);

            // Step 2: Check for duplicates (idempotency)
            ProcessingResult duplicateCheck = idempotencyService.checkDuplicate(pacs008Message.getMessageId());

            if (duplicateCheck != null && duplicateCheck.getStatus() == ProcessingResult.Status.DUPLICATE) {
                handleDuplicateMessage(duplicateCheck, pacs008Message);
                return;
            }

            // Step 3: Process the transfer (placeholder - will integrate with sanctions and ledger later)
            ProcessingResult transferResult = processTransfer(pacs008Message);

            if (!transferResult.isSuccessful()) {
                handleTransferFailure(transferResult, pacs008Message);
                return;
            }

            // Step 4: Success - acknowledge the message
            logger.info("Successfully processed message: {}", pacs008Message.getMessageId());

        } catch (Exception e) {
            logger.error("Unexpected error processing message {}: {}", messageId, e.getMessage(), e);
            // Send generic error status report
            sendErrorStatusReport(messageId, "PROCESSING_ERROR", "Internal processing error: " + e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Processes the transfer through sanctions checking and ledger operations.
     * Integrates with ComplianceService for sanction screening before payment processing.
     * 
     * Flow:
     * 1. Create transfer in PENDING state
     * 2. Run compliance evaluation
     * 3. If cleared, process payment
     * 4. If blocked, keep as BLOCKED_AML
     */
    private ProcessingResult processTransfer(Pacs008Message pacs008Message) {
        try {
            // Step 1: Create transfer request from message
            TransferRequest transferRequest = createTransferRequestFromMessage(pacs008Message);

            // Step 2: Create transfer in PENDING state (without processing payment)
            Transfer transfer = paymentService.createPendingTransferForCompliance(transferRequest, "gateway");
            logger.debug("Created PENDING transfer {} for compliance evaluation", transfer.getId());

            // Step 3: Run compliance evaluation on PENDING transfer
            logger.debug("Running compliance evaluation for transfer {}", transfer.getId());
            ProcessingResult complianceResult = complianceService.evaluateTransfer(transfer);

            // Step 4: Handle compliance decision
            if (complianceResult.getStatus() == ProcessingResult.Status.BLOCKED_SANCTIONS) {
                // Transfer is blocked - keep as BLOCKED_AML, don't process payment
                logger.warn("Transfer {} blocked by compliance engine: {}",
                           transfer.getId(), complianceResult.getErrorMessage());
                return complianceResult;
            }

            // Step 5: If cleared by compliance, process the payment
            logger.info("Transfer {} cleared by compliance, processing payment", transfer.getId());
            try {
                TransferResponse paymentResponse = paymentService.processPaymentForTransfer(transfer, "gateway");
                logger.info("Transfer {} payment processed successfully", transfer.getId());
                return ProcessingResult.success(transfer);
            } catch (Exception paymentException) {
                logger.error("Payment processing failed for transfer {}: {}",
                           transfer.getId(), paymentException.getMessage(), paymentException);
                // Update transfer status to indicate payment failure
                transfer.setStatus(Transfer.TransferStatus.REJECTED);
                transferRepository.save(transfer);
                return ProcessingResult.processingError(
                    "Payment processing failed: " + paymentException.getMessage(), "PAY001");
            }

        } catch (Exception e) {
            logger.error("Transfer processing failed for message {}: {}",
                        pacs008Message.getMessageId(), e.getMessage(), e);
            return ProcessingResult.processingError("Transfer processing failed: " + e.getMessage(), "PROC001");
        }
    }

    /**
     * Creates a TransferRequest from the parsed pacs.008 message.
     */
    private TransferRequest createTransferRequestFromMessage(Pacs008Message message) {
        TransferRequest request = new TransferRequest();
        request.setMsgId(message.getMessageId());
        request.setAmount(message.getAmount());
        request.setCurrency(message.getCurrency());
        request.setSenderIban(message.getSenderIban());
        request.setReceiverIban(message.getReceiverIban());

        request.setSenderIban(message.getSenderIban());
        request.setReceiverIban(message.getReceiverIban());

        request.setSenderName(message.getSenderName());
        request.setReceiverName(message.getReceiverName());

        return request;
    }

    /**
     * Handles XML validation failures.
     */
    private void handleValidationFailure(ProcessingResult result, String messageId) {
        logger.warn("XML validation failed for message {}: {}", messageId, result.getErrorMessage());

        sendErrorStatusReport(
            messageId,
            "INVALID_XML",
            result.getErrorCode(),
            result.getErrorMessage()
        );

        // Reject the message (don't requeue invalid XML)
        throw new AmqpRejectAndDontRequeueException("XML validation failed: " + result.getErrorMessage());
    }

    /**
     * Handles duplicate message detection.
     */
    private void handleDuplicateMessage(ProcessingResult result, Pacs008Message message) {
        logger.info("Duplicate message detected: {}", message.getMessageId());

        // For duplicates, we acknowledge the message without sending a status report
        // as per idempotency requirements (FR-03)
        // The original processing already generated appropriate responses
    }

    /**
     * Handles transfer processing failures.
     */
    private void handleTransferFailure(ProcessingResult result, Pacs008Message message) {
        logger.warn("Transfer processing failed for message {}: {}", message.getMessageId(), result.getErrorMessage());

        String status = mapProcessingStatusToOutboundStatus(result.getStatus());
        sendErrorStatusReport(
            message.getGroupHeaderMsgId(),
            status,
            result.getErrorCode(),
            result.getErrorMessage()
        );
    }

    /**
     * Maps internal processing status to outbound status report status.
     */
    private String mapProcessingStatusToOutboundStatus(ProcessingResult.Status status) {
        switch (status) {
            case BLOCKED_SANCTIONS:
                return "BLOCKED_AML";
            case PROCESSING_ERROR:
                return "REJECTED";
            default:
                return "REJECTED";
        }
    }

    /**
     * Sends an error status report to the outbound queue.
     */
    private void sendErrorStatusReport(String originalMessageId, String status, String reasonCode, String reasonDescription) {
        StatusReportMessage statusReport = new StatusReportMessage(
            originalMessageId,
            status,
            reasonCode,
            reasonDescription,
            null, // No specific transaction ID for general errors
            LocalDateTime.now()
        );

        messageOutboundService.sendStatusReport(statusReport);
    }

    /**
     * Convenience method for sending error status reports without reason codes.
     */
    private void sendErrorStatusReport(String originalMessageId, String status, String reasonDescription) {
        sendErrorStatusReport(originalMessageId, status, null, reasonDescription);
    }
}
