package org.sda.notificationservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * One record per message this service tried to send.
 *
 * <p>Kept for two reasons: it is the customer's in-app notification list, and it is the audit trail
 * that answers "was the receipt ever sent?" without having to read provider logs.
 *
 * <p>{@code eventKey} is the routing key plus the correlation id. A unique index on it makes the
 * whole pipeline idempotent for free: if RabbitMQ redelivers a message, the second insert is
 * rejected and the customer is not notified twice.
 */
@Document(collection = "notifications")
@Data
@NoArgsConstructor
public class Notification {

    @Id
    private String id;

    @Field
    @Indexed
    private String userId;

    /** Correlation key carried by every order event; null for promotional messages. */
    @Field
    @Indexed
    private String orderId;

    @Field
    private NotificationType type;

    @Field
    private Channel channel;

    @Field
    private String title;

    @Field
    private String body;

    @Field
    private NotificationStatus status;

    @Field
    private String failureReason;

    /** Deduplication key: {@code <routingKey>:<correlationId>:<channel>}. */
    @Field
    @Indexed(unique = true)
    private String eventKey;

    @Field
    private boolean read = false;

    @Field
    private Instant createdAt = Instant.now();
}
