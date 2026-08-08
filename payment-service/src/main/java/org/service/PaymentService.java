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
import org.client.OrderServiceClient;
import org.entity.Payment;
import org.entity.PaymentStatus;
import org.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private static final long MINIMUM_AMOUNT_POISHA = 10000L; // ~ BDT 100.00, safely above Stripe's minimum charge

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderServiceClient orderServiceClient;

    @Value("${stripe.checkout.success-url}")
    private String successUrl;

    @Value("${stripe.checkout.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    public CheckoutSessionResult createCheckoutSession(String orderId, String userId, Long amount,
                                                         String currency, String description) {
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        if (amount == null || amount < MINIMUM_AMOUNT_POISHA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "amount is required and must be at least " + MINIMUM_AMOUNT_POISHA
                            + " (smallest currency unit, e.g. poisha for BDT - so BDT 100.00 minimum)");
        }

        String effectiveCurrency = (currency == null || currency.isBlank()) ? "bdt" : currency.toLowerCase();
        String productName = (description == null || description.isBlank()) ? "Order " + orderId : description;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(effectiveCurrency)
                                .setUnitAmount(amount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build())
                                .build())
                        .build())
                .putMetadata("orderId", orderId)
                .putMetadata("userId", userId == null ? "" : userId)
                .build();

        try {
            Session session = Session.create(params);

            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setAmount(amount);
            payment.setCurrency(effectiveCurrency);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setStripeSessionId(session.getId());
            Payment saved = paymentRepository.save(payment);

            return new CheckoutSessionResult(saved, session.getUrl());
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to create Stripe checkout session: " + e.getMessage());
        }
    }

    public Payment verifySession(String sessionId) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for session"));

        try {
            Session session = Session.retrieve(sessionId);
            applySessionResult(payment, session);
            return paymentRepository.save(payment);
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to verify session with Stripe: " + e.getMessage());
        }
    }

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
                            .ifPresent(payment -> {
                                applySessionResult(payment, session);
                                paymentRepository.save(payment);
                            });
                }
            }
            default -> {
                // Event type not relevant to this demo; ignored.
            }
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
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();
            Refund.create(params);

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

    public Payment markCancelled(String sessionId) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for session"));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Customer cancelled checkout");
            payment.setUpdatedAt(Instant.now());
            payment = paymentRepository.save(payment);
            orderServiceClient.notifyPaymentStatus(payment.getOrderId(), payment.getId(), payment.getStatus());
        }
        return payment;
    }

    private void applySessionResult(Payment payment, Session session) {
        PaymentStatus previousStatus = payment.getStatus();

        if ("paid".equals(session.getPaymentStatus())) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setStripePaymentIntentId(session.getPaymentIntent());
        } else if ("expired".equals(session.getStatus())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Checkout session expired without payment");
        }
        payment.setUpdatedAt(Instant.now());

        boolean reachedFinalState = payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.FAILED;
        if (payment.getStatus() != previousStatus && reachedFinalState) {
            orderServiceClient.notifyPaymentStatus(payment.getOrderId(), payment.getId(), payment.getStatus());
        }
    }

    public record CheckoutSessionResult(Payment payment, String checkoutUrl) {
    }
}
