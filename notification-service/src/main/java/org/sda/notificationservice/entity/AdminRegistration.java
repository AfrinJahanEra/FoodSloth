package org.sda.notificationservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * A platform admin this service has learned about from {@code user.registered}.
 *
 * <p>Notification Service never calls User Service, so this list is how it knows who to tell when
 * an order is placed or a delivery finishes. User Service replays every admin at startup, keeping
 * the list correct even if accounts predate this event or a message was lost.
 *
 * <p>{@code id} is the admin's user id; saving is an upsert, so replays are harmless.
 */
@Document(collection = "platform_admins")
@Data
@NoArgsConstructor
public class AdminRegistration {

    @Id
    private String id;

    @Field
    private String name;

    @Field
    private Instant registeredAt = Instant.now();
}
