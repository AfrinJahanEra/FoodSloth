package org.sda.notificationservice.entity;

public enum NotificationStatus {
    /** Handed to the channel provider successfully. */
    SENT,
    /** The provider rejected it; {@code failureReason} says why. */
    FAILED,
    /**
     * Not attempted - the customer has no address for this channel, or opted out. Recorded rather
     * than dropped so support can explain why a message never arrived.
     */
    SKIPPED
}
