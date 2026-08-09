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

/**
 * Declares the shared exchange plus the nine queues Order Service owns.
 *
 * <p>Declaring the exchange here is safe even though other services declare it too: an AMQP
 * exchange declaration with identical name and type is a no-op, which is what lets each service
 * start up independently in any order.
 */
@Configuration
public class MessagingConfig {

    @Bean
    public TopicExchange foodExchange() {
        return new TopicExchange(Constants.EXCHANGE, true, false);
    }

    // ---- Queues owned by Order Service ----

    @Bean
    public Queue orderPricedQueue() {
        return new Queue(Constants.QUEUE_ORDER_PRICED, true);
    }

    @Bean
    public Queue orderUnavailableQueue() {
        return new Queue(Constants.QUEUE_ORDER_UNAVAILABLE, true);
    }

    @Bean
    public Queue orderAcceptedQueue() {
        return new Queue(Constants.QUEUE_ORDER_ACCEPTED, true);
    }

    @Bean
    public Queue orderReadyQueue() {
        return new Queue(Constants.QUEUE_ORDER_READY, true);
    }

    @Bean
    public Queue orderRejectedQueue() {
        return new Queue(Constants.QUEUE_ORDER_REJECTED, true);
    }

    @Bean
    public Queue paymentSucceededQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_SUCCEEDED, true);
    }

    @Bean
    public Queue paymentFailedQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_FAILED, true);
    }

    @Bean
    public Queue deliveryStartedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_STARTED, true);
    }

    @Bean
    public Queue deliveryCompletedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_COMPLETED, true);
    }

    // ---- Bindings ----

    @Bean
    public Binding orderPricedBinding() {
        return BindingBuilder.bind(orderPricedQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_PRICED);
    }

    @Bean
    public Binding orderUnavailableBinding() {
        return BindingBuilder.bind(orderUnavailableQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_UNAVAILABLE);
    }

    @Bean
    public Binding orderAcceptedBinding() {
        return BindingBuilder.bind(orderAcceptedQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_ACCEPTED);
    }

    @Bean
    public Binding orderReadyBinding() {
        return BindingBuilder.bind(orderReadyQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_READY);
    }

    @Bean
    public Binding orderRejectedBinding() {
        return BindingBuilder.bind(orderRejectedQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_REJECTED);
    }

    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentSucceededQueue())
                .to(foodExchange())
                .with(Constants.RK_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue())
                .to(foodExchange())
                .with(Constants.RK_PAYMENT_FAILED);
    }

    @Bean
    public Binding deliveryStartedBinding() {
        return BindingBuilder.bind(deliveryStartedQueue())
                .to(foodExchange())
                .with(Constants.RK_DELIVERY_STARTED);
    }

    @Bean
    public Binding deliveryCompletedBinding() {
        return BindingBuilder.bind(deliveryCompletedQueue())
                .to(foodExchange())
                .with(Constants.RK_DELIVERY_COMPLETED);
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
