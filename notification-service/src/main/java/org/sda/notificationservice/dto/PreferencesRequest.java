package org.sda.notificationservice.dto;

/**
 * Body of {@code PUT /notifications/preferences}. Every field is a boxed Boolean so that omitting
 * one leaves the stored preference untouched.
 *
 * <p>Switching a channel off silences promotional messages on it; transactional messages are still
 * recorded, so the customer can always see the receipt in their in-app list.
 */
public record PreferencesRequest(
        Boolean pushEnabled,
        Boolean emailEnabled,
        Boolean smsEnabled,
        Boolean marketingOptIn
) {
}
