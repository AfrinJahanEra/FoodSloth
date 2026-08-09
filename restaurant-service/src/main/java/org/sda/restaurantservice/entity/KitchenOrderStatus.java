package org.sda.restaurantservice.entity;

/** Where a kitchen ticket is in its life. */
public enum KitchenOrderStatus {

    /** Priced and waiting for payment to clear. Not on the kitchen screen yet. */
    AWAITING_PAYMENT,

    /** Paid for and waiting for the kitchen to take it on. */
    QUEUED,

    /** The kitchen took it on and is cooking. */
    ACCEPTED,

    /** Packed and waiting for a rider. */
    READY,

    /** The kitchen turned it down. */
    REJECTED,

    /** Called off before it was cooked. */
    CANCELLED
}
