package org.sda.deliveryservice.entity;

public enum RiderStatus {
    /** Not working right now; never auto-assigned. */
    OFFLINE,
    /** Working and free; eligible for auto-assignment. */
    AVAILABLE,
    /** Holds an assignment that is not finished yet. */
    ON_DELIVERY
}
