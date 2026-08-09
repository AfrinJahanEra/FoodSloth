package org.sda.notificationservice.dto;

/**
 * Body of {@code POST /notifications/contact} - the email and phone the customer wants receipts and
 * alerts on. Either field may be omitted to leave the stored value alone.
 */
public record ContactRequest(
        String email,
        String phone
) {
}
