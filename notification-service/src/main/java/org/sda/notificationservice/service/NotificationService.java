package org.sda.notificationservice.service;

import org.sda.notificationservice.dto.event.DeliveryEvent;
import org.sda.notificationservice.dto.event.MarketingBroadcastEvent;
import org.sda.notificationservice.dto.event.OrderCancelledEvent;
import org.sda.notificationservice.dto.event.OrderConfirmedEvent;
import org.sda.notificationservice.dto.event.OrderRejectedEvent;
import org.sda.notificationservice.dto.event.OrderStatusEvent;
import org.sda.notificationservice.dto.event.PaymentFailedEvent;
import org.sda.notificationservice.dto.event.PaymentSucceededEvent;
import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.NotificationType;
import org.sda.notificationservice.entity.Recipient;
import org.sda.notificationservice.messaging.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Decides what each event says and which channels carry it. The mechanics of sending live in
 * {@link NotificationDispatcher}.
 *
 * <p>Channel policy, in one place so it is easy to argue with:
 * <ul>
 *   <li>PUSH for every order-status change - free, instant, and what the app badge reads.
 *   <li>EMAIL only for the two things a customer keeps: the order confirmation and the payment
 *       receipt.
 *   <li>SMS only where a missed message costs money or time: a failed payment, and the rider
 *       standing at the door.
 * </ul>
 *
 * <p>The dedupe scope passed to the dispatcher is always {@code <routingKey>:<orderId>}, so a
 * redelivered message cannot notify the customer twice.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final List<Channel> PUSH_ONLY = List.of(Channel.PUSH);
    private static final List<Channel> PUSH_AND_EMAIL = List.of(Channel.PUSH, Channel.EMAIL);
    private static final List<Channel> PUSH_AND_SMS = List.of(Channel.PUSH, Channel.SMS);

    private final NotificationDispatcher dispatcher;
    private final RecipientService recipientService;

    public NotificationService(NotificationDispatcher dispatcher, RecipientService recipientService) {
        this.dispatcher = dispatcher;
        this.recipientService = recipientService;
    }

    // ------------------------------------------------------------------
    // Order lifecycle
    // ------------------------------------------------------------------

    public void onOrderConfirmed(OrderConfirmedEvent event) {
        String body = event.grandTotal() == null
                ? "Your order has been confirmed and sent to the restaurant."
                : "Your order has been confirmed and sent to the restaurant. Total: "
                        + money(event.grandTotal()) + ".";
        dispatcher.dispatch(scope(Constants.RK_ORDER_CONFIRMED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_CONFIRMED,
                "Order confirmed", body + reference(event.orderId()), PUSH_AND_EMAIL);
    }

    public void onOrderCancelled(OrderCancelledEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " Reason: " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_ORDER_CANCELLED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order has been cancelled." + because
                        + " Any payment already taken will be refunded." + reference(event.orderId()),
                PUSH_AND_EMAIL);
    }

    public void onOrderDelivered(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_DELIVERED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_DELIVERED,
                "Order delivered",
                "Enjoy your meal. Tap to rate your order." + reference(event.orderId()),
                PUSH_ONLY);
    }

    // ------------------------------------------------------------------
    // Kitchen
    // ------------------------------------------------------------------

    public void onOrderAccepted(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_ACCEPTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_ACCEPTED,
                "The restaurant is preparing your order",
                "Your order has been accepted and is being prepared." + reference(event.orderId()),
                PUSH_ONLY);
    }

    public void onOrderRejected(OrderRejectedEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " Reason: " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_ORDER_REJECTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_REJECTED,
                "The restaurant could not take your order",
                "Your order was not accepted." + because + " You will be refunded in full."
                        + reference(event.orderId()),
                PUSH_AND_EMAIL);
    }

    public void onOrderReady(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_READY, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_READY,
                "Your food is ready",
                "The restaurant has finished your order and a rider is being assigned."
                        + reference(event.orderId()),
                PUSH_ONLY);
    }

    // ------------------------------------------------------------------
    // Payment
    // ------------------------------------------------------------------

    /** The transactional payment receipt. */
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        StringBuilder body = new StringBuilder("Payment received");
        if (event.amountMinor() != null) {
            body.append(" of ").append(money(event.amountMinor() / 100.0, event.currency()));
        }
        if (event.paymentMethod() != null) {
            body.append(" by ").append(event.paymentMethod().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        body.append(".");
        if (event.paymentId() != null) {
            body.append(" Payment reference: ").append(event.paymentId()).append(".");
        }
        dispatcher.dispatch(scope(Constants.RK_PAYMENT_SUCCEEDED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.PAYMENT_RECEIPT,
                "Payment receipt", body + reference(event.orderId()), PUSH_AND_EMAIL);
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_PAYMENT_FAILED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.PAYMENT_FAILED,
                "Payment failed",
                "We could not take payment for your order." + because
                        + " Please try again to keep your order." + reference(event.orderId()),
                PUSH_AND_SMS);
    }

    // ------------------------------------------------------------------
    // Delivery
    // ------------------------------------------------------------------

    public void onRiderAssigned(DeliveryEvent event) {
        String rider = event.riderDisplayName() == null ? "A rider" : event.riderDisplayName();
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_ASSIGNED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.RIDER_ASSIGNED,
                "A rider is on the way to the restaurant",
                rider + " will bring your order." + eta(event.etaMinutes()) + reference(event.orderId()),
                PUSH_ONLY);
    }

    public void onOutForDelivery(DeliveryEvent event) {
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_STARTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.OUT_FOR_DELIVERY,
                "Your order is out for delivery",
                "Your rider has collected your order and is on the way."
                        + eta(event.etaMinutes()) + " Track them live in the app."
                        + reference(event.orderId()),
                PUSH_ONLY);
    }

    /** Rider arrival - worth an SMS, because the customer needs to come to the door now. */
    public void onRiderArriving(DeliveryEvent event) {
        String rider = event.riderDisplayName() == null ? "Your rider" : event.riderDisplayName();
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_ARRIVING, event.orderId()),
                event.userId(), event.orderId(), NotificationType.RIDER_ARRIVING,
                "Your rider is arriving",
                rider + " is almost at your door. Please be ready to collect your order."
                        + reference(event.orderId()),
                PUSH_AND_SMS);
    }

    // ------------------------------------------------------------------
    // Promotional broadcast
    // ------------------------------------------------------------------

    /**
     * Fans a campaign out to every customer who opted in.
     *
     * <p>Consent is checked here and nowhere else: transactional messages above are always sent,
     * promotional ones only to {@code marketingOptIn} recipients.
     */
    public void onMarketingBroadcast(MarketingBroadcastEvent event) {
        List<Channel> channels = event.channels() == null || event.channels().isEmpty()
                ? PUSH_ONLY
                : event.channels();

        List<Recipient> audience = recipientService.findMarketingAudience();
        log.info("Broadcasting campaign {} to {} opted-in recipient(s) on {}",
                event.campaignId(), audience.size(), channels);

        for (Recipient recipient : audience) {
            // Campaign id plus recipient id keeps the dedupe key unique per person.
            dispatcher.dispatchTo(recipient,
                    scope(Constants.RK_MARKETING_BROADCAST, event.campaignId() + ":" + recipient.getId()),
                    NotificationType.PROMOTION, event.title(), event.body(), channels);
        }
    }

    // ------------------------------------------------------------------
    // Wording helpers
    // ------------------------------------------------------------------

    private String scope(String routingKey, String correlationId) {
        return routingKey + ":" + correlationId;
    }

    private String reference(String orderId) {
        return " Order " + orderId + ".";
    }

    private String eta(Integer etaMinutes) {
        return etaMinutes == null ? "" : " Estimated arrival in about " + etaMinutes + " minutes.";
    }

    private String money(double amount) {
        return money(amount, "BDT");
    }

    private String money(double amount, String currency) {
        String code = currency == null ? "BDT" : currency.toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%s %.2f", code, amount);
    }
}
