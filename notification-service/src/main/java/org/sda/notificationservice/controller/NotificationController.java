package org.sda.notificationservice.controller;

import org.sda.notificationservice.dto.NotificationResponse;
import org.sda.notificationservice.dto.PreferencesRequest;
import org.sda.notificationservice.dto.RecipientResponse;
import org.sda.notificationservice.dto.RegisterDeviceRequest;
import org.sda.notificationservice.service.InboxService;
import org.sda.notificationservice.service.RecipientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST surface of Notification Service. Reached by clients through api-gateway at
 * {@code /notifications/**}.
 *
 * <p>Auth note: the JWT is verified by api-gateway, not here. The gateway strips any client-supplied
 * {@code X-User-*} headers and re-adds them from the verified claims, so this service trusts
 * {@code X-User-Id} / {@code X-User-Role} instead of parsing tokens itself.
 *
 * <p>No endpoint here is called by another service. Services trigger notifications by publishing to
 * {@code food.exchange} - including a promotional broadcast, which any service can raise with the
 * {@code marketing.broadcast} routing key.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final RecipientService recipientService;
    private final InboxService inboxService;

    public NotificationController(RecipientService recipientService,
                                  InboxService inboxService) {
        this.recipientService = recipientService;
        this.inboxService = inboxService;
    }

    // ------------------------------------------------------------------
    // How to reach me (push is the only channel)
    // ------------------------------------------------------------------

    /** The mobile app calls this after every sign-in with the token it got from the OS. */
    @PostMapping("/devices")
    public RecipientResponse registerDevice(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                    @RequestBody RegisterDeviceRequest request) {
        return RecipientResponse.from(recipientService.registerDevice(requireAuthenticated(userId), request.deviceToken()));
    }

    @DeleteMapping("/devices/{deviceToken}")
    public RecipientResponse unregisterDevice(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                      @PathVariable String deviceToken) {
        return RecipientResponse.from(recipientService.unregisterDevice(requireAuthenticated(userId), deviceToken));
    }

    @PutMapping("/preferences")
    public RecipientResponse updatePreferences(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @RequestBody PreferencesRequest request) {
        return RecipientResponse.from(recipientService.updatePreferences(requireAuthenticated(userId),
                request.pushEnabled()));
    }

    @GetMapping("/preferences")
    public RecipientResponse getPreferences(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return RecipientResponse.from(recipientService.require(requireAuthenticated(userId)));
    }

    // ------------------------------------------------------------------
    // In-app notification list
    // ------------------------------------------------------------------

    @GetMapping("/me")
    public List<NotificationResponse> getMyNotifications(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return inboxService.listForUser(requireAuthenticated(userId)).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/me/unread-count")
    public Map<String, Long> getUnreadCount(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Map.of("unread", inboxService.unreadCount(requireAuthenticated(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                 @PathVariable String notificationId) {
        return NotificationResponse.from(inboxService.markRead(requireAuthenticated(userId), notificationId));
    }

    @PatchMapping("/me/read-all")
    public Map<String, Long> markAllRead(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Map.of("markedRead", inboxService.markAllRead(requireAuthenticated(userId)));
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    /** Support view: every message sent about one order, and what came of each. */
    @GetMapping("/order/{orderId}")
    public List<NotificationResponse> getNotificationsForOrder(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String orderId) {
        requireAdmin(role);
        return inboxService.listForOrder(orderId).stream().map(NotificationResponse::from).toList();
    }

    // ------------------------------------------------------------------
    // Gateway-header guards
    // ------------------------------------------------------------------

    private String requireAuthenticated(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        return userId;
    }

    private void requireAdmin(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!ROLE_ADMIN.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }
}
