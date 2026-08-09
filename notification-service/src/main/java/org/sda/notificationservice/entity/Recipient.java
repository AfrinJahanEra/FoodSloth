package org.sda.notificationservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a customer can be reached: push is the only channel, so this is the device tokens the
 * app registered plus the push switch.
 *
 * <p>{@code id} is the user id the gateway puts in {@code X-User-Id}, so the two stay in step
 * without this service ever calling User Service.
 *
 * <p>This is not a copy of the User Service account. A device token only exists once the mobile app
 * has asked the OS for one.
 */
@Document(collection = "recipients")
@Data
@NoArgsConstructor
public class Recipient {

    /** Same value as the user id issued by User Service. */
    @Id
    private String id;

    /** One per device the customer signed in on. */
    @Field
    private List<String> deviceTokens = new ArrayList<>();

    @Field
    private boolean pushEnabled = true;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
