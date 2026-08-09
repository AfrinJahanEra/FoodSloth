package org.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.dto.event.PaymentRequestedEvent;
import org.entity.Payment;
import org.entity.PaymentStatus;
import org.publisher.PaymentEventPublisher;
import org.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Payment capture, refunds and the transaction record.
 *
 * <p>Work arrives as {@code order.payment-requested} and the result goes back out as
 * {@code payment.succeeded} or {@code payment.failed}. This service makes no call to any other
 * service - if Order Service is down when a card clears, the result simply waits on its queue.
 *
 * <p>Money: the platform talks in major units (taka) on the wire; Stripe charges in the smallest
 * unit (poisha). The conversion happens once, in {@link #toMinorUnits}, and everything stored or
 * published from here on is in the smallest unit.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** ~ BDT 100.00, safely above Stripe's minimum charge. */
    private static final long MINIMUM_AMOUNT_POISHA = 10000L;

    private static final String CASH_ON_DELIVERY = "CASH_ON_DELIVERY";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventPublisher publisher;

    @Value("${stripe.checkout.success-url}")
    private String successUrl;

    @Value("${stripe.checkout.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    // ------------------------------------------------------------------
    // Driven by events
    // ------------------------------------------------------------------

    /**
     * An order needs paying for.
     *
     * <p>Cash on delivery is accepted on the spot - there is nothing to charge, so the order can go
     * to the kitchen immediately. A card payment gets a Stripe checkout session and stays PENDING
     * until Stripe says otherwise; the customer picks up the link from
     * {@code GET /payments/order/{orderId}}.
     *
     * <p>Idempotent on {@code orderId}: a redelivered request finds the existing payment and does
     * nothing rather than charging twice.
     */
    public void onPaymentRequested(PaymentRequestedEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            log.warn("Ignoring a payment request with no orderId - it cannot be correlated");
            return;
        }

        Payment existing = paymentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Order {} already has payment {} in {}; ignoring the repeat request",
                    event.orderId(), existing.getId(), existing.getStatus());
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setOrderNo(event.orderNo());
        payment.setUserId(event.userId());
        payment.setCurrency(normaliseCurrency(event.currency()));
        payment.setPaymentMethod(event.paymentMethod() == null ? "CARD" : event.paymentMethod());
        payment.setAmount(toMinorUnits(event.amount()));

        if (CASH_ON_DELIVERY.equalsIgnoreCase(payment.getPaymentMethod())) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            Payment saved = paymentRepository.save(payment);
            publisher.publishSucceeded(saved);
            return;
        }

        if (payment.getAmount() < MINIMUM_AMOUNT_POISHA) {
            failWithoutCharging(payment, "The order total is below the minimum we can charge on a card");
            return;
        }

        try {
            Session session = Session.create(sessionParams(payment, event.description()));
            payment.setStatus(PaymentStatus.PENDING);
            payment.setStripeSessionId(session.getId());
            payment.setCheckoutUrl(session.getUrl());
            paymentRepository.save(payment);
            log.info("Checkout session created for order {}", payment.getOrderId());
        } catch (StripeException e) {
            // Nothing a retry of the message can fix in a useful timeframe, and the customer is
            // waiting - so the order is told the payment failed instead of the message being requeued.
            failWithoutCharging(payment, "Could not start the card payment: " + e.getMessage());
        }
    }

    /** The order is off. Give the money back if it was taken; otherwise just close the payment off. */
    public void onOrderCancelled(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            return;
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED && payment.getStripePaymentIntentId() != null) {
            try {
                Refund.create(RefundCreateParams.builder()
                        .setPaymentIntent(payment.getStripePaymentIntentId())
                        .build());
                payment.setStatus(PaymentStatus.REFUNDED);
            } catch (StripeException e) {
                // Left as SUCCEEDED on purpose: the charge is real and still needs refunding by hand.
                log.error("Refund failed for cancelled order {} (payment {}): {}",
                        orderId, payment.getId(), e.getMessage());
                return;
            }
        } else if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason("The order was cancelled before payment");
        } else {
            return;
        }

        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
    }

    // ------------------------------------------------------------------
    // Driven by Stripe
    // ------------------------------------------------------------------

    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed",
                 "checkout.session.async_payment_succeeded",
                 "checkout.session.async_payment_failed",
                 "checkout.session.expired" -> {
                StripeObject dataObject = event.getDataObjectDeserializer().getObject().orElse(null);
                if (dataObject instanceof Session session) {
                    paymentRepository.findByStripeSessionId(session.getId())
                            .ifPresent(payment -> applySessionResult(payment, session));
                }
            }
            default -> {
                // Event type not relevant to this flow; ignored.
            }
        }
    }

    /** Asks Stripe directly instead of waiting for the webhook - used by the return-from-Stripe page. */
    public Payment verifySession(String sessionId) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for session"));
        try {
            return applySessionResult(payment, Session.retrieve(sessionId));
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to verify session with Stripe: " + e.getMessage());
        }
    }

    public Payment markCancelled(String sessionId) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for session"));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            return fail(payment, "Customer cancelled checkout");
        }
        return payment;
    }

    // ------------------------------------------------------------------
    // Client-facing
    // ------------------------------------------------------------------

    /**
     * A fresh checkout link after a failed or expired attempt. Only the customer who owns the
     * payment can ask for one, and only on a payment that never went through.
     */
    public Payment retry(String userId, String orderId) {
        Payment payment = requireOwnedBy(userId, getByOrderId(orderId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been paid for");
        }
        if (CASH_ON_DELIVERY.equalsIgnoreCase(payment.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cash-on-delivery order has nothing to pay online");
        }

        try {
            Session session = Session.create(sessionParams(payment, "Order " + payment.getOrderId()));
            payment.setStatus(PaymentStatus.PENDING);
            payment.setFailureReason(null);
            payment.setStripeSessionId(session.getId());
            payment.setCheckoutUrl(session.getUrl());
            payment.setUpdatedAt(Instant.now());
            return paymentRepository.save(payment);
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to create Stripe checkout session: " + e.getMessage());
        }
    }

    public Payment refund(String paymentId) {
        Payment payment = getById(paymentId);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a succeeded payment can be refunded");
        }
        if (payment.getStripePaymentIntentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has no associated Stripe PaymentIntent");
        }

        try {
            Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build());
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setUpdatedAt(Instant.now());
            return paymentRepository.save(payment);
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Refund failed: " + e.getMessage());
        }
    }

    public Payment getById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public Payment getByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for order"));
    }

    public List<Payment> getByUserId(String userId) {
        return paymentRepository.findByUserId(userId);
    }

    /** Every payment on the platform, newest first - the admin's ledger. */
    public List<Payment> getAll() {
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }

    public Payment requireOwnedBy(String userId, Payment payment) {
        if (!userId.equals(payment.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment belongs to someone else");
        }
        return payment;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SessionCreateParams sessionParams(Payment payment, String description) {
        String productName = (description == null || description.isBlank())
                ? "Order " + payment.getOrderId()
                : description;

        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(payment.getCurrency())
                                .setUnitAmount(payment.getAmount())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build())
                                .build())
                        .build())
                .putMetadata("orderId", payment.getOrderId())
                .putMetadata("userId", payment.getUserId() == null ? "" : payment.getUserId())
                .build();
    }

    /**
     * Records what Stripe says and, when the payment settles one way or the other for the first
     * time, announces it. The status guard is what stops the webhook and the return page - which
     * often both arrive - from publishing the same result twice.
     */
    private Payment applySessionResult(Payment payment, Session session) {
        PaymentStatus previousStatus = payment.getStatus();

        if ("paid".equals(session.getPaymentStatus())) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setStripePaymentIntentId(session.getPaymentIntent());
        } else if ("expired".equals(session.getStatus())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Checkout session expired without payment");
        }
        payment.setUpdatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        if (saved.getStatus() != previousStatus) {
            if (saved.getStatus() == PaymentStatus.SUCCEEDED) {
                publisher.publishSucceeded(saved);
            } else if (saved.getStatus() == PaymentStatus.FAILED) {
                publisher.publishFailed(saved, saved.getFailureReason());
            }
        }
        return saved;
    }

    private Payment fail(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setUpdatedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);
        publisher.publishFailed(saved, reason);
        return saved;
    }

    /** For a payment that never reached Stripe: record the failure and tell the order. */
    private void failWithoutCharging(Payment payment, String reason) {
        log.info("Cannot charge order {}: {}", payment.getOrderId(), reason);
        fail(payment, reason);
    }

    /**
     * Taka to poisha, rounded rather than truncated so a total like 12.345 is not quietly shaved.
     * Doing this in one place is what keeps a "100" from ever being charged as one taka.
     */
    private long toMinorUnits(Double majorUnits) {
        if (majorUnits == null || majorUnits <= 0) {
            return 0L;
        }
        return Math.round(majorUnits * 100.0);
    }

    private String normaliseCurrency(String currency) {
        return (currency == null || currency.isBlank()) ? "bdt" : currency.toLowerCase();
    }
}
