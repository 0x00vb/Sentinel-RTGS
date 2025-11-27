package com.example.backend.service;

import com.example.backend.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Service for querying RabbitMQ queue information.
 * Provides real-time queue depth and status information.
 */
@Service
public class RabbitMQQueueService {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQQueueService.class);

    private final AmqpAdmin amqpAdmin;

    @Autowired
    public RabbitMQQueueService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /**
     * Get the current depth (message count) of the inbound queue.
     * 
     * @return The number of messages in the queue, or 0 if queue doesn't exist or error occurs
     */
    public int getInboundQueueDepth() {
        try {
            Properties queueProperties = amqpAdmin.getQueueProperties(RabbitMQConfig.INBOUND_QUEUE);
            
            if (queueProperties == null) {
                logger.warn("Queue '{}' not found or not accessible", RabbitMQConfig.INBOUND_QUEUE);
                return 0;
            }

            // The QUEUE_MESSAGE_COUNT property contains the current message count
            // Property key is "QUEUE_MESSAGE_COUNT" as defined in Spring AMQP
            Object messageCountObj = queueProperties.get("QUEUE_MESSAGE_COUNT");
            
            if (messageCountObj == null) {
                logger.warn("Could not retrieve message count for queue '{}'", RabbitMQConfig.INBOUND_QUEUE);
                return 0;
            }

            int messageCount = 0;
            if (messageCountObj instanceof Number) {
                messageCount = ((Number) messageCountObj).intValue();
            } else if (messageCountObj instanceof String) {
                messageCount = Integer.parseInt((String) messageCountObj);
            } else {
                logger.warn("Unexpected message count type for queue '{}': {}", 
                           RabbitMQConfig.INBOUND_QUEUE, messageCountObj.getClass());
                return 0;
            }

            logger.debug("Queue '{}' depth: {}", RabbitMQConfig.INBOUND_QUEUE, messageCount);
            return messageCount;

        } catch (Exception e) {
            logger.error("Error retrieving queue depth for '{}': {}", 
                        RabbitMQConfig.INBOUND_QUEUE, e.getMessage(), e);
            // Return 0 on error to indicate queue is unavailable or empty
            return 0;
        }
    }

    /**
     * Get the current depth (message count) of the dead letter queue.
     * 
     * @return The number of messages in the DLQ, or 0 if queue doesn't exist or error occurs
     */
    public int getDeadLetterQueueDepth() {
        try {
            Properties queueProperties = amqpAdmin.getQueueProperties(RabbitMQConfig.INBOUND_DLQ);
            
            if (queueProperties == null) {
                logger.debug("Dead letter queue '{}' not found or empty", RabbitMQConfig.INBOUND_DLQ);
                return 0;
            }

            // Property key is "QUEUE_MESSAGE_COUNT" as defined in Spring AMQP
            Object messageCountObj = queueProperties.get("QUEUE_MESSAGE_COUNT");
            
            if (messageCountObj == null) {
                return 0;
            }

            int messageCount = 0;
            if (messageCountObj instanceof Number) {
                messageCount = ((Number) messageCountObj).intValue();
            } else if (messageCountObj instanceof String) {
                messageCount = Integer.parseInt((String) messageCountObj);
            }

            return messageCount;

        } catch (Exception e) {
            logger.warn("Error retrieving DLQ depth for '{}': {}", 
                       RabbitMQConfig.INBOUND_DLQ, e.getMessage());
            return 0;
        }
    }

    /**
     * Check if RabbitMQ connection is available.
     * 
     * @return true if connection is available, false otherwise
     */
    public boolean isRabbitMQAvailable() {
        try {
            // Try to get properties from a known queue to test connectivity
            Properties queueProperties = amqpAdmin.getQueueProperties(RabbitMQConfig.INBOUND_QUEUE);
            return queueProperties != null;
        } catch (Exception e) {
            logger.debug("RabbitMQ connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}

