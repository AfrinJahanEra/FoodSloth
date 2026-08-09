package org.sda.notificationservice.dto;

import org.sda.notificationservice.entity.Recipient;

import java.util.List;

/**
 * A customer's reachability and channel preferences as the settings screen edits them.
 * Timestamps are internal bookkeeping and stay off the wire.
 */
public record RecipientResponse(
        String id,
        String email,
        String phone,
        List<String> deviceTokens,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean marketingOptIn) {

    public static RecipientResponse from(Recipient recipient) {
        return new RecipientResponse(recipient.getId(), recipient.getEmail(), recipient.getPhone(),
                recipient.getDeviceTokens(), recipient.isPushEnabled(), recipient.isEmailEnabled(),
                recipient.isSmsEnabled(), recipient.isMarketingOptIn());
    }
}
