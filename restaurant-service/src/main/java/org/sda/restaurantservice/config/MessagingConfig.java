package org.sda.restaurantservice.config;

import org.sda.restaurantservice.messaging.Constants;
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
 * Declares the shared exchange plus the three queues Restaurant Service owns.
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

    // ---- Queues owned by Restaurant Service ----

    @Bean
    public Queue orderIntakeQueue() {
        return new Queue(Constants.QUEUE_ORDER_INTAKE, true);
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(Constants.QUEUE_ORDER_CONFIRMED, true);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(Constants.QUEUE_ORDER_CANCELLED, true);
    }

    // ---- Bindings ----

    @Bean
    public Binding cartCheckedOutBinding() {
        return BindingBuilder.bind(orderIntakeQueue())
                .to(foodExchange())
                .with(Constants.RK_CART_CHECKED_OUT);
    }

    /** Second key bound to the same intake queue - see Constants.QUEUE_ORDER_INTAKE. */
    @Bean
    public Binding reorderRequestedBinding() {
        return BindingBuilder.bind(orderIntakeQueue())
                .to(foodExchange())
                .with(Constants.RK_REORDER_REQUESTED);
    }

    @Bean
    public Binding orderConfirmedBinding() {
        return BindingBuilder.bind(orderConfirmedQueue())
                .to(foodExchange())
                .with(Constants.RK_ORDER_CONFIRMED);
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
