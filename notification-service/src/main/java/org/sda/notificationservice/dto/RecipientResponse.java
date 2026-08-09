package org.sda.notificationservice.dto;

import org.sda.notificationservice.entity.Recipient;

import java.util.List;

/**
 * A customer's push reachability and preferences as the settings screen edits them.
 * Timestamps are internal bookkeeping and stay off the wire.
 */
public record RecipientResponse(
        String id,
        List<String> deviceTokens,
        boolean pushEnabled) {

    public static RecipientResponse from(Recipient recipient) {
        return new RecipientResponse(recipient.getId(), recipient.getDeviceTokens(),
                recipient.isPushEnabled());
    }
}
