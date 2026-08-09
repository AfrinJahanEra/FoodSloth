package org.sda.userservice.dto.event;

/**
 * Published on {@code user.registered}: once per new account, and again for every admin each time
 * this service starts so listeners never lose track of who runs the platform.
 */
public record UserRegisteredEvent(
        String userId,
        String name,
        String email,
        String role
) {
}
