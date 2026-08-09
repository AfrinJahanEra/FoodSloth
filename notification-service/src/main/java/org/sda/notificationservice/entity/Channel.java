package org.sda.notificationservice.entity;

/** How a notification reaches the customer. */
public enum Channel {
    /** Mobile push to the device tokens the customer registered. */
    PUSH,
    /** Transactional email - confirmations and receipts. */
    EMAIL,
    /** Transactional SMS - short, time-critical messages. */
    SMS
}
