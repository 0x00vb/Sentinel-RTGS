package com.example.backend.service;

import com.example.backend.entity.Transfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.example.backend.dto.TransferEvent;

@Service
public class TransferEventPublisher {
    @Autowired 
    private SimpMessagingTemplate messagingTemplate; // for WebSocket notifications
    
    /**
     * Publish transfer events for real-time WebSocket notifications
     * Supports FR-12: Real-time UI updates via WebSockets
     */

    public void publishTransferEvent(Transfer transfer) {
        try{
            // Publish the transfer event to the WebSocket topic
            messagingTemplate.convertAndSend("/topic/transfers", createTransferEvent(transfer));


            // Publish to compliance worklist topic if blocked
            if (transfer.getStatus() == Transfer.TransferStatus.BLOCKED_AML) {
                messagingTemplate.convertAndSend("/topic/compliance/worklist", createTransferEvent(transfer));
            }
        } catch (Exception e) {
            // Log and push to dead-letter monitoring — do not throw
            // (add structured logging/metrics here)
            System.err.println("Failed to publish transfer event: " + e.getMessage());
        }
    }

    private TransferEvent createTransferEvent(Transfer transfer) {
        return new TransferEvent(transfer.getId(), transfer.getMsgId(), transfer.getStatus(), transfer.getAmount(), transfer.getSource().getIban(), transfer.getDestination().getIban(), transfer.getCreatedAt());
    }
    
}
