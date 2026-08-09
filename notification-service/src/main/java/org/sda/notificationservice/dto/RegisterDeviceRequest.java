package org.sda.notificationservice.dto;

/**
 * Body of {@code POST /notifications/devices}. The mobile app sends the token it got from the OS
 * push service, once per sign-in.
 */
public record RegisterDeviceRequest(
        String deviceToken
) {
}
