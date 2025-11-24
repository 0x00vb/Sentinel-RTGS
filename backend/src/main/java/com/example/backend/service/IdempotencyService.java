package com.example.backend.service;

import com.example.backend.dto.Pacs008Message;
import com.example.backend.dto.ProcessingResult;
import com.example.backend.entity.Transfer;
import com.example.backend.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling message idempotency checks (FR-03).
 * Prevents duplicate processing of ISO 20022 messages by checking msgId uniqueness.
 */
@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    private final TransferRepository transferRepository;

    @Autowired
    public IdempotencyService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    /**
     * Checks if a message with the given msgId has already been processed.
     * Implements FR-03: Idempotency - Duplicate Message IDs must be detected and discarded.
     *
     * @param msgId The message ID to check
     * @return ProcessingResult indicating if message is duplicate or can proceed
     */
    @Transactional(readOnly = true)
    public ProcessingResult checkDuplicate(UUID msgId) {
        if (msgId == null) {
            logger.warn("Received null msgId for idempotency check");
            return ProcessingResult.processingError("Null message ID", "IDEMP001");
        }

        Optional<Transfer> existingTransfer = transferRepository.findByMsgId(msgId);

        if (existingTransfer.isPresent()) {
            Transfer transfer = existingTransfer.get();
            logger.info("Duplicate message detected: msgId={}, existing transfer id={}, status={}",
                       msgId, transfer.getId(), transfer.getStatus());

            // Log the duplicate attempt for audit purposes
            // In a full implementation, this would trigger an audit log entry

            return ProcessingResult.duplicate(transfer);
        }

        logger.debug("Message is unique: msgId={}", msgId);
        return null; // null indicates message can proceed with processing
    }

    /**
     * Verifies that a message can be processed (not a duplicate).
     * This is a convenience method that throws an exception for duplicates.
     *
     * @param pacs008Message The parsed message to check
     * @throws DuplicateMessageException if message is a duplicate
     */
    public void verifyNotDuplicate(Pacs008Message pacs008Message) throws DuplicateMessageException {
        ProcessingResult result = checkDuplicate(pacs008Message.getMessageId());

        if (result != null && result.getStatus() == ProcessingResult.Status.DUPLICATE) {
            throw new DuplicateMessageException(
                "Duplicate message detected: " + pacs008Message.getMessageId(),
                result.getTransfer()
            );
        }
    }

    /**
     * Exception thrown when a duplicate message is detected.
     */
    public static class DuplicateMessageException extends Exception {
        private final Transfer existingTransfer;

        public DuplicateMessageException(String message, Transfer existingTransfer) {
            super(message);
            this.existingTransfer = existingTransfer;
        }

        public Transfer getExistingTransfer() {
            return existingTransfer;
        }
    }
}
