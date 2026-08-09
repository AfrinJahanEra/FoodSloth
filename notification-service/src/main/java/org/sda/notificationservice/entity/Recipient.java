package org.sda.notificationservice.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a customer can be reached, and on which channels they want to be.
 *
 * <p>{@code id} is the user id the gateway puts in {@code X-User-Id}, so the two stay in step
 * without this service ever calling User Service.
 *
 * <p>This is not a copy of the User Service account. A device token only exists once the mobile app
 * has asked the OS for one, and marketing consent is a notification concern - so both are data this
 * service genuinely owns. The email and phone recorded here are the ones the customer chose to
 * receive notifications on, registered through {@code POST /notifications/contact}; if they never
 * register, transactional messages on that channel are recorded as SKIPPED rather than guessed at.
 */
@Document(collection = "recipients")
@Data
@NoArgsConstructor
public class Recipient {

    /** Same value as the user id issued by User Service. */
    @Id
    private String id;

    @Field
    private String email;

    @Field
    private String phone;

    /** One per device the customer signed in on. */
    @Field
    private List<String> deviceTokens = new ArrayList<>();

    @Field
    private boolean pushEnabled = true;

    @Field
    private boolean emailEnabled = true;

    @Field
    private boolean smsEnabled = true;

    /**
     * Marketing consent. Off until the customer opts in, and the promotional broadcast is the only
     * thing that looks at it - order and payment messages are transactional and always sent.
     */
    @Field
    @Indexed
    private boolean marketingOptIn = false;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
