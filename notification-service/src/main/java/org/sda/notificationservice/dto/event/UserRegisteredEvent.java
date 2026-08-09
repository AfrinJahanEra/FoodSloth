package org.sda.notificationservice.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code user.registered}. Notification Service only remembers the admins - they
 * are the ones told about new orders and finished deliveries - but every account passes through
 * so the event stays useful if another audience ever appears.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegisteredEvent(
        String userId,
        String name,
        String email,
        String role
) {
}
