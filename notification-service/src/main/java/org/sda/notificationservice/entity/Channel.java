package org.sda.notificationservice.entity;

/**
 * How a notification reaches the customer. Push is the only channel the platform sends on.
 * EMAIL and SMS are kept as legacy values purely so notification records stored before the
 * push-only switch still deserialize; no sender is registered for them anymore.
 */
public enum Channel {
    /** Mobile push to the device tokens the customer registered - the only active channel. */
    PUSH,
    /** Legacy: email was retired with the push-only switch; nothing is sent on it. */
    @Deprecated
    EMAIL,
    /** Legacy: SMS was retired with the push-only switch; nothing is sent on it. */
    @Deprecated
    SMS
}
