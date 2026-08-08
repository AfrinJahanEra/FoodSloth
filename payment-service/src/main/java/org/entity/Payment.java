package org.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    private String id;

    @Field
    @Indexed
    private String orderId;

    @Field
    private String userId;

    @Field
    private Long amount;

    @Field
    private String currency = "bdt";

    @Field
    private PaymentStatus status = PaymentStatus.PENDING;

    @Field
    private String paymentMethod = "CARD";

    @Field
    @Indexed
    private String stripeSessionId;

    @Field
    private String stripePaymentIntentId;

    @Field
    private String failureReason;

    @Field
    private Instant createdAt = Instant.now();

    @Field
    private Instant updatedAt = Instant.now();
}
