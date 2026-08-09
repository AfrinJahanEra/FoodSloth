package org.sda.notificationservice.dto;

/**
 * Body of {@code PUT /notifications/preferences}. The field is a boxed Boolean so that omitting
 * it leaves the stored preference untouched.
 *
 * <p>Switching push off silences device pushes; transactional messages are still recorded,
 * so the customer can always see them in their in-app list.
 */
public record PreferencesRequest(
        Boolean pushEnabled
) {
}
