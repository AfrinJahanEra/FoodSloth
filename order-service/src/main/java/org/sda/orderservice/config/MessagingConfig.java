package org.sda.orderservice.config;

import org.sda.orderservice.messaging.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    // ---- Exchanges ----

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(Constants.ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(Constants.PAYMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange restaurantExchange() {
        return new TopicExchange(Constants.RESTAURANT_EXCHANGE);
    }

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(Constants.DELIVERY_EXCHANGE);
    }

    // ---- Queues consumed by Order Service ----

    @Bean
    public Queue paymentSucceededQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_SUCCEEDED, true);
    }

    @Bean
    public Queue paymentFailedQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_FAILED, true);
    }

    @Bean
    public Queue orderAcceptedQueue() {
        return new Queue(Constants.QUEUE_ORDER_ACCEPTED, true);
    }

    @Bean
    public Queue deliveryStartedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_STARTED, true);
    }

    @Bean
    public Queue deliveryCompletedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_COMPLETED, true);
    }

    // TODO: placeholder queue for Payment Service, see Constants.QUEUE_PAYMENT_REQUESTED.
    @Bean
    public Queue paymentRequestedQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_REQUESTED, true);
    }

    // ---- Bindings ----

    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentSucceededQueue())
                .to(paymentExchange())
                .with(Constants.ROUTING_KEY_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue())
                .to(paymentExchange())
                .with(Constants.ROUTING_KEY_PAYMENT_FAILED);
    }

    @Bean
    public Binding orderAcceptedBinding() {
        return BindingBuilder.bind(orderAcceptedQueue())
                .to(restaurantExchange())
                .with(Constants.ROUTING_KEY_ORDER_ACCEPTED);
    }

    @Bean
    public Binding deliveryStartedBinding() {
        return BindingBuilder.bind(deliveryStartedQueue())
                .to(deliveryExchange())
                .with(Constants.ROUTING_KEY_DELIVERY_STARTED);
    }

    @Bean
    public Binding deliveryCompletedBinding() {
        return BindingBuilder.bind(deliveryCompletedQueue())
                .to(deliveryExchange())
                .with(Constants.ROUTING_KEY_DELIVERY_COMPLETED);
    }

    @Bean
    public Binding paymentRequestedBinding() {
        return BindingBuilder.bind(paymentRequestedQueue())
                .to(orderExchange())
                .with(Constants.ROUTING_KEY_PAYMENT_REQUESTED);
    }

    // ---- Message conversion / template ----

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }
}
