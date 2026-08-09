package org.sda.notificationservice.service;

import org.sda.notificationservice.dto.event.DeliveryEvent;
import org.sda.notificationservice.dto.event.OrderCancelledEvent;
import org.sda.notificationservice.dto.event.OrderConfirmedEvent;
import org.sda.notificationservice.dto.event.OrderRejectedEvent;
import org.sda.notificationservice.dto.event.OrderStatusEvent;
import org.sda.notificationservice.dto.event.PaymentFailedEvent;
import org.sda.notificationservice.dto.event.PaymentSucceededEvent;
import org.sda.notificationservice.dto.event.UserRegisteredEvent;
import org.sda.notificationservice.entity.AdminRegistration;
import org.sda.notificationservice.entity.Channel;
import org.sda.notificationservice.entity.NotificationType;
import org.sda.notificationservice.messaging.Constants;
import org.sda.notificationservice.repository.AdminRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Decides what each event says and which channels carry it. The mechanics of sending live in
 * {@link NotificationDispatcher}.
 *
 * <p>Channel policy: push is the only channel. Every message - order lifecycle, payment,
 * delivery and promotional broadcasts alike - goes out as a push notification to the device
 * tokens the customer registered. Email and SMS were removed on purpose.
 *
 * <p>The dedupe scope passed to the dispatcher is always {@code <routingKey>:<orderId>}, so a
 * redelivered message cannot notify the customer twice. When the same event also goes to the
 * rider or the admins, each extra recipient gets its own suffix on the scope
 * ({@code :rider}, {@code :admin:<adminId>}) so one audience never dedupes another away.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final List<Channel> PUSH_ONLY = List.of(Channel.PUSH);
    private static final String ROLE_ADMIN = "ADMIN";

    private final NotificationDispatcher dispatcher;
    private final AdminRegistrationRepository adminRegistrationRepository;

    public NotificationService(NotificationDispatcher dispatcher,
                               AdminRegistrationRepository adminRegistrationRepository) {
        this.dispatcher = dispatcher;
        this.adminRegistrationRepository = adminRegistrationRepository;
    }

    // ------------------------------------------------------------------
    // User accounts
    // ------------------------------------------------------------------

    /**
     * Remembers the platform's admins so order-placed and delivery-done events can be copied to
     * them. Saving is an upsert on the admin's user id, and User Service replays every admin at
     * startup, so this handler is safe to run any number of times.
     */
    public void onUserRegistered(UserRegisteredEvent event) {
        if (!ROLE_ADMIN.equals(event.role())) {
            return;
        }
        AdminRegistration admin = new AdminRegistration();
        admin.setId(event.userId());
        admin.setName(event.name());
        adminRegistrationRepository.save(admin);
        log.info("Remembered admin {} ({})", event.userId(), event.name());
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
                "Order confirmed", body + reference(event.orderNo(), event.orderId()), PUSH_ONLY);

        // The admins watch every order: tell them a new one just came in.
        String total = event.grandTotal() == null ? "" : " Total: " + money(event.grandTotal()) + ".";
        notifyAdmins(Constants.RK_ORDER_CONFIRMED, event.orderId(), event.userId(),
                NotificationType.ORDER_CONFIRMED, "New order placed",
                "A customer just placed an order." + total + reference(event.orderNo(), event.orderId()));
    }

    public void onOrderCancelled(OrderCancelledEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " Reason: " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_ORDER_CANCELLED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order has been cancelled." + because
                        + " Any payment already taken will be refunded." + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    public void onOrderDelivered(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_DELIVERED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_DELIVERED,
                "Order delivered",
                "Enjoy your meal. Tap to rate your order." + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);

        // Close the loop for the admins: the delivery is done.
        notifyAdmins(Constants.RK_ORDER_DELIVERED, event.orderId(), event.userId(),
                NotificationType.ORDER_DELIVERED, "Delivery completed",
                "The order has been delivered to the customer." + reference(event.orderNo(), event.orderId()));
    }

    // ------------------------------------------------------------------
    // Kitchen
    // ------------------------------------------------------------------

    public void onOrderAccepted(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_ACCEPTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_ACCEPTED,
                "The restaurant is preparing your order",
                "Your order has been accepted and is being prepared." + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    public void onOrderRejected(OrderRejectedEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " Reason: " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_ORDER_REJECTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_REJECTED,
                "The restaurant could not take your order",
                "Your order was not accepted." + because + " You will be refunded in full."
                        + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    public void onOrderReady(OrderStatusEvent event) {
        dispatcher.dispatch(scope(Constants.RK_ORDER_READY, event.orderId()),
                event.userId(), event.orderId(), NotificationType.ORDER_READY,
                "Your food is ready",
                "The restaurant has finished your order and a rider is being assigned."
                        + reference(event.orderNo(), event.orderId()),
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
                "Payment receipt", body + reference(event.orderNo(), event.orderId()), PUSH_ONLY);
    }

    public void onPaymentFailed(PaymentFailedEvent event) {
        String because = event.reason() == null || event.reason().isBlank() ? "" : " " + event.reason() + ".";
        dispatcher.dispatch(scope(Constants.RK_PAYMENT_FAILED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.PAYMENT_FAILED,
                "Payment failed",
                "We could not take payment for your order." + because
                        + " Please try again to keep your order." + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    // ------------------------------------------------------------------
    // Delivery
    // ------------------------------------------------------------------

    public void onRiderAssigned(DeliveryEvent event) {
        String rider = event.riderDisplayName() == null ? "A rider" : event.riderDisplayName();
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_ASSIGNED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.RIDER_ASSIGNED,
                "A rider is on the way to the restaurant",
                rider + " will bring your order." + eta(event.etaMinutes()) + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);

        // The deliveryman himself must see the assignment so he can accept it from his inbox.
        if (event.riderId() != null && !event.riderId().isBlank()) {
            dispatcher.dispatch(scope(Constants.RK_DELIVERY_ASSIGNED, event.orderId() + ":rider"),
                    event.riderId(), event.orderId(), NotificationType.RIDER_ASSIGNED,
                    "New delivery assigned to you",
                    "You have been assigned a new delivery. Open your rider dashboard to accept it."
                            + reference(event.orderNo(), event.orderId()),
                    PUSH_ONLY);
        }
    }

    public void onOutForDelivery(DeliveryEvent event) {
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_STARTED, event.orderId()),
                event.userId(), event.orderId(), NotificationType.OUT_FOR_DELIVERY,
                "Your order is out for delivery",
                "Your rider has collected your order and is on the way."
                        + eta(event.etaMinutes()) + " Track them live in the app."
                        + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    /** Rider arrival - the customer needs to come to the door now. */
    public void onRiderArriving(DeliveryEvent event) {
        String rider = event.riderDisplayName() == null ? "Your rider" : event.riderDisplayName();
        dispatcher.dispatch(scope(Constants.RK_DELIVERY_ARRIVING, event.orderId()),
                event.userId(), event.orderId(), NotificationType.RIDER_ARRIVING,
                "Your rider is arriving",
                rider + " is almost at your door. Please be ready to collect your order."
                        + reference(event.orderNo(), event.orderId()),
                PUSH_ONLY);
    }

    // ------------------------------------------------------------------
    // Admin fan-out
    // ------------------------------------------------------------------

    /**
     * Copies one event to every registered admin, each with a per-admin dedupe suffix. The admin
     * who placed the order himself is skipped - he already got the customer copy.
     */
    private void notifyAdmins(String routingKey, String orderId, String excludeUserId,
                              NotificationType type, String title, String body) {
        for (AdminRegistration admin : adminRegistrationRepository.findAll()) {
            if (admin.getId().equals(excludeUserId)) {
                continue;
            }
            dispatcher.dispatch(scope(routingKey, orderId + ":admin:" + admin.getId()),
                    admin.getId(), orderId, type, title, body, PUSH_ONLY);
        }
    }

    // ------------------------------------------------------------------
    // Wording helpers
    // ------------------------------------------------------------------

    private String scope(String routingKey, String correlationId) {
        return routingKey + ":" + correlationId;
    }

    /** Friendly order reference: the sequential #number when known, the UUID otherwise. */
    private String reference(Long orderNo, String orderId) {
        return orderNo == null ? " Order " + orderId + "." : " Order #" + orderNo + ".";
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
