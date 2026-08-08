package org.sda.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(Constants.ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(Constants.QUEUE_ORDER_CONFIRMED, true);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(Constants.QUEUE_ORDER_CANCELLED, true);
    }

    @Bean
    public Queue orderDeliveredQueue() {
        return new Queue(Constants.QUEUE_ORDER_DELIVERED, true);
    }

    @Bean
    public Binding orderConfirmedBinding() {
        return BindingBuilder.bind(orderConfirmedQueue())
                .to(orderExchange())
                .with(Constants.ROUTING_KEY_ORDER_CONFIRMED);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(orderCancelledQueue())
                .to(orderExchange())
                .with(Constants.ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public Binding orderDeliveredBinding() {
        return BindingBuilder.bind(orderDeliveredQueue())
                .to(orderExchange())
                .with(Constants.ROUTING_KEY_ORDER_DELIVERED);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
