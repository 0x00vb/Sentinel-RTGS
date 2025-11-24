package com.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;

@Configuration
public class RabbitMQConfig {
    public static final String INBOUND_EX = "bank.inbound.exchange";
    public static final String INBOUND_QUEUE = "bank.inbound";
    public static final String INBOUND_DLQ = "bank.inbound.dlq";
    public static final String INBOUND_DLX = "bank.inbound.dlx";
    public static final String OUTBOUND_EX = "bank.outbound.exchange";
    public static final String OUTBOUND_QUEUE = "bank.outbound";

    @Bean
    public TopicExchange inboundExchange() {
        return new TopicExchange(INBOUND_EX);
    }
    @Bean
    public Queue inboundQueue() {
        return QueueBuilder.durable(INBOUND_QUEUE)
            .withArgument("x-dead-letter-exchange", INBOUND_DLX)
            .build();
    }
    @Bean
    public Queue inboundDLQ() {
        return QueueBuilder.durable(INBOUND_DLQ).build();
    }
    @Bean
    public FanoutExchange inboundDlx() {
        return new FanoutExchange(INBOUND_DLX);
    }
    @Bean
    public Queue outboundQueue() {
        return QueueBuilder.durable(OUTBOUND_QUEUE).build();
    }
    @Bean
    public DirectExchange outboundExchange() {
        return new DirectExchange(OUTBOUND_EX);
    }
    @Bean
    public Binding bindingInbound(Queue inboundQueue, TopicExchange inboundExchange) {
        return BindingBuilder.bind(inboundQueue).to(inboundExchange).with("#");
    }
    @Bean
    public Binding bindingDLQ(Queue inboundDLQ, FanoutExchange inboundDlx) {
        return BindingBuilder.bind(inboundDLQ).to(inboundDlx);
    }
    @Bean
    public Binding bindingOutbound(Queue outboundQueue, DirectExchange outboundExchange) {
        return BindingBuilder.bind(outboundQueue).to(outboundExchange).with("pacs.002");
    }

    // Retry template / rabbit listener container factory with manual ack
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // manual ack for safety
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }
}
