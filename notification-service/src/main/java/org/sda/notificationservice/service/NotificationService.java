package org.sda.notificationservice.service;

import org.sda.notificationservice.dto.event.OrderCancelledEvent;
import org.sda.notificationservice.dto.event.OrderConfirmedEvent;
import org.sda.notificationservice.dto.event.OrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// TODO: integrate real email/SMS/push delivery later; console logging is sufficient for now.
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void notifyOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Order #{} confirmed.\nNotification sent to user {}.\nNotification sent to restaurant {}.",
                event.orderId(), event.userId(), event.restaurantId());
    }

    public void notifyOrderCancelled(OrderCancelledEvent event) {
        log.info("Order #{} cancelled.\nNotification sent to user {}.\nNotification sent to restaurant {}.",
                event.orderId(), event.userId(), event.restaurantId());
    }

    public void notifyOrderDelivered(OrderDeliveredEvent event) {
        log.info("Order #{} delivered.\nNotification sent to user {}.\nNotification sent to restaurant {}.",
                event.orderId(), event.userId(), event.restaurantId());
    }
}
