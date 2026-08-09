package org.sda.notificationservice.config;

import org.sda.notificationservice.messaging.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by Notification Service.
 *
 * <p>Declares the shared exchange plus this service's own queues and bindings. Re-declaring an
 * existing exchange is a no-op in AMQP, so every service can declare {@code food.exchange} and the
 * services can start in any order.
 */
@Configuration
public class MessagingConfig {

    @Bean
    public TopicExchange foodExchange() {
        return new TopicExchange(Constants.EXCHANGE, true, false);
    }

    // ---- User accounts ----

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(Constants.QUEUE_USER_REGISTERED, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(foodExchange).with(Constants.RK_USER_REGISTERED);
    }

    // ---- Order lifecycle ----

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
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderConfirmedQueue).to(foodExchange).with(Constants.RK_ORDER_CONFIRMED);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(foodExchange).with(Constants.RK_ORDER_CANCELLED);
    }

    @Bean
    public Binding orderDeliveredBinding(Queue orderDeliveredQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderDeliveredQueue).to(foodExchange).with(Constants.RK_ORDER_DELIVERED);
    }

    // ---- Payment ----

    @Bean
    public Queue paymentSucceededQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_SUCCEEDED, true);
    }

    @Bean
    public Queue paymentFailedQueue() {
        return new Queue(Constants.QUEUE_PAYMENT_FAILED, true);
    }

    @Bean
    public Binding paymentSucceededBinding(Queue paymentSucceededQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(paymentSucceededQueue).to(foodExchange).with(Constants.RK_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(foodExchange).with(Constants.RK_PAYMENT_FAILED);
    }

    // ---- Kitchen ----

    @Bean
    public Queue orderAcceptedQueue() {
        return new Queue(Constants.QUEUE_ORDER_ACCEPTED, true);
    }

    @Bean
    public Queue orderRejectedQueue() {
        return new Queue(Constants.QUEUE_ORDER_REJECTED, true);
    }

    @Bean
    public Queue orderReadyQueue() {
        return new Queue(Constants.QUEUE_ORDER_READY, true);
    }

    @Bean
    public Binding orderAcceptedBinding(Queue orderAcceptedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderAcceptedQueue).to(foodExchange).with(Constants.RK_ORDER_ACCEPTED);
    }

    @Bean
    public Binding orderRejectedBinding(Queue orderRejectedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderRejectedQueue).to(foodExchange).with(Constants.RK_ORDER_REJECTED);
    }

    @Bean
    public Binding orderReadyBinding(Queue orderReadyQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(orderReadyQueue).to(foodExchange).with(Constants.RK_ORDER_READY);
    }

    // ---- Delivery ----

    @Bean
    public Queue deliveryAssignedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_ASSIGNED, true);
    }

    @Bean
    public Queue deliveryStartedQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_STARTED, true);
    }

    @Bean
    public Queue deliveryArrivingQueue() {
        return new Queue(Constants.QUEUE_DELIVERY_ARRIVING, true);
    }

    @Bean
    public Binding deliveryAssignedBinding(Queue deliveryAssignedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(deliveryAssignedQueue).to(foodExchange).with(Constants.RK_DELIVERY_ASSIGNED);
    }

    @Bean
    public Binding deliveryStartedBinding(Queue deliveryStartedQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(deliveryStartedQueue).to(foodExchange).with(Constants.RK_DELIVERY_STARTED);
    }

    @Bean
    public Binding deliveryArrivingBinding(Queue deliveryArrivingQueue, TopicExchange foodExchange) {
        return BindingBuilder.bind(deliveryArrivingQueue).to(foodExchange).with(Constants.RK_DELIVERY_ARRIVING);
    }

    // ---- Serialisation ----

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
