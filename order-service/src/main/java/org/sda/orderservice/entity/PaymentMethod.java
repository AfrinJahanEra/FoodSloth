package org.sda.orderservice.entity;

/** How the order is paid for. Anything that is not cash on delivery is charged on a card. */
public enum PaymentMethod {
    CARD,
    CASH_ON_DELIVERY;

    /** Events carry the method as a string; unknown or missing values fall back to card. */
    public static PaymentMethod fromWire(String value) {
        if (value == null || value.isBlank()) {
            return CARD;
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CARD;
        }
    }
}
