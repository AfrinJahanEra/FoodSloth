package com.service.cart_service.config;

import com.service.cart_service.messaging.Constants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares only the shared exchange and the template Cart Service uses to publish on it.
 *
 * <p>No queue and no binding is declared here: Cart Service publishes {@code cart.checked-out} and
 * whoever wants it (Restaurant Service) declares its own queue. That keeps this service a pure
 * publisher with nothing to consume.
 *
 * <p>Declaring the exchange is safe even though other services declare it too: an AMQP exchange
 * declaration with identical name and type is a no-op, which is what lets each service start up
 * independently in any order.
 */
@Configuration
public class MessagingConfig {

    @Bean
    public TopicExchange foodExchange() {
        return new TopicExchange(Constants.EXCHANGE, true, false);
    }

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
