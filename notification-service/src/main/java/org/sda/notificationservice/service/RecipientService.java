package org.sda.notificationservice.service;

import org.sda.notificationservice.entity.Recipient;
import org.sda.notificationservice.repository.RecipientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

/**
 * Owns the Recipient aggregate: device tokens and the push switch.
 *
 * <p>The row is created on first use, keyed by the user id the gateway puts in {@code X-User-Id}, so
 * registering a recipient never involves calling User Service.
 */
@Service
public class RecipientService {

    private final RecipientRepository recipientRepository;

    public RecipientService(RecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    /** Adds a device token, ignoring a token the app has already registered. */
    public Recipient registerDevice(String userId, String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceToken is required");
        }
        Recipient recipient = getOrCreate(userId);
        if (!recipient.getDeviceTokens().contains(deviceToken)) {
            recipient.getDeviceTokens().add(deviceToken);
        }
        return save(recipient);
    }

    /** Removes a device token, e.g. on sign-out. */
    public Recipient unregisterDevice(String userId, String deviceToken) {
        Recipient recipient = require(userId);
        recipient.getDeviceTokens().remove(deviceToken);
        return save(recipient);
    }

    public Recipient updatePreferences(String userId, Boolean push) {
        Recipient recipient = getOrCreate(userId);
        if (push != null) {
            recipient.setPushEnabled(push);
        }
        return save(recipient);
    }

    /**
     * Looked up by the dispatcher. Returns empty rather than throwing, because a missing recipient
     * is a normal situation - a customer who never opened the app has nowhere to be notified - and
     * an inbound event must not be bounced over it.
     */
    public Optional<Recipient> find(String userId) {
        return userId == null ? Optional.empty() : recipientRepository.findById(userId);
    }

    public Recipient require(String userId) {
        return recipientRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No notification profile yet; register a device or contact details first"));
    }

    private Recipient getOrCreate(String userId) {
        return recipientRepository.findById(userId).orElseGet(() -> {
            Recipient fresh = new Recipient();
            fresh.setId(userId);
            return fresh;
        });
    }

    private Recipient save(Recipient recipient) {
        recipient.setUpdatedAt(Instant.now());
        return recipientRepository.save(recipient);
    }
}
