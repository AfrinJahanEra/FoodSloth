package org.sda.deliveryservice.config;

import org.sda.deliveryservice.messaging.Constants;
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
 * Declares the shared exchange plus the two queues Delivery Service owns.
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

    // ---- Queues owned by Delivery Service ----

    @Bean
    public Queue orderReadyQueue() {
        return new Queue(Constants.QUEUE_ORDER_READY, true);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(Constants.QUEUE_ORDER_CANCELLED, true);
    }

    // ---- Bindings ----

    @Bean
    public Binding orderReadyBinding() {
        return BindingBuilder.bind(orderReadyQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_READY);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(orderCancelledQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_CANCELLED);
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
