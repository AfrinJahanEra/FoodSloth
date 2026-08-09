package org.sda.deliveryservice.entity;

public enum DeliveryStatus {
    /** The food is ready but no rider has been found yet; the assignment sweep keeps retrying. */
    PENDING_ASSIGNMENT,
    /** A rider was picked automatically and has been offered the job. */
    ASSIGNED,
    /** The rider accepted and is riding to the restaurant. */
    ACCEPTED,
    /** The rider collected the food and is riding to the customer. */
    PICKED_UP,
    /** Handed over to the customer. */
    DELIVERED,
    /** The order was cancelled before hand-over. */
    CANCELLED
}
