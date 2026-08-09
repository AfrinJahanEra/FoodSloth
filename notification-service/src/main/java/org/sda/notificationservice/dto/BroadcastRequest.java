package org.sda.notificationservice.dto;

import org.sda.notificationservice.entity.Channel;

import java.util.List;

/**
 * Body of {@code POST /notifications/broadcast}, used by marketing.
 *
 * <p>The endpoint does not send anything itself - it publishes {@code marketing.broadcast} and
 * returns. The campaign then survives a restart of this service and takes exactly the same path as a
 * broadcast triggered by another service.
 */
public record BroadcastRequest(
        String title,
        String body,
        /** Omit for push only. */
        List<Channel> channels
) {
}
