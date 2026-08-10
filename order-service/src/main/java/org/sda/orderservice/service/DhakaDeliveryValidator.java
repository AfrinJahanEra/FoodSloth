package org.sda.orderservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the Dhaka-only delivery restriction on reorder (checkout is guarded by Cart Service).
 *
 * <p>A real GPS pin is checked against the Dhaka district bounding box - the customer's actual
 * coordinates decide, not the address text. Addresses saved without a pin are resolved to one
 * through OpenStreetMap Nominatim (free, no API key) and the resolved pin is box-checked the same
 * way; only when the geocoder cannot help at all (no match or no network) does the raw text have
 * to mention Dhaka.
 */
@Component
public class DhakaDeliveryValidator {

    public static final String REJECTION_MESSAGE = "Sorry, we currently deliver only within Dhaka, Bangladesh.";

    /** Dhaka district (Bangladesh) bounding box with a small safety margin. */
    private static final double LAT_MIN = 23.66;
    private static final double LAT_MAX = 23.90;
    private static final double LNG_MIN = 90.25;
    private static final double LNG_MAX = 90.55;

    private static final Logger log = LoggerFactory.getLogger(DhakaDeliveryValidator.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    /** One lookup per distinct address text: true = inside Dhaka, false = not. */
    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    /** Returns null when the location is deliverable, otherwise {@link #REJECTION_MESSAGE}. */
    public String check(Double latitude, Double longitude, String addressText) {
        if (isReal(latitude, longitude)) {
            return insideDhaka(latitude, longitude) ? null : REJECTION_MESSAGE;
        }
        String text = addressText == null ? "" : addressText.trim();
        if (text.isEmpty()) {
            return REJECTION_MESSAGE;
        }
        boolean ok = cache.computeIfAbsent(text.toLowerCase(), this::resolveInsideDhaka);
        return ok ? null : REJECTION_MESSAGE;
    }

    /** The (0,0) sentinel means "no pin saved"; anything else is a real coordinate. */
    static boolean isReal(Double lat, Double lng) {
        return lat != null && lng != null && !(Math.abs(lat) < 0.01 && Math.abs(lng) < 0.01);
    }

    static boolean insideDhaka(double lat, double lng) {
        return lat >= LAT_MIN && lat <= LAT_MAX && lng >= LNG_MIN && lng <= LNG_MAX;
    }

    private boolean resolveInsideDhaka(String text) {
        for (String candidate : candidates(text)) {
            Optional<double[]> pin = geocode(candidate);
            if (pin.isPresent()) {
                return insideDhaka(pin.get()[0], pin.get()[1]);
            }
        }
        // Geocoder could not resolve it (offline or unknown place): fall back to the text.
        return text.contains("dhaka");
    }

    /** Full address first, then progressively simpler fallbacks. */
    private List<String> candidates(String text) {
        List<String> out = new ArrayList<>();
        out.add(text);
        if (!text.toLowerCase().contains("bangladesh")) {
            out.add(text + ", Bangladesh");
        }
        String first = text.split(",")[0].trim();
        if (!first.isEmpty() && !first.equalsIgnoreCase(text)) {
            out.add(first + ", Bangladesh");
        }
        return out;
    }

    private Optional<double[]> geocode(String query) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "FoodSlothSQA/1.0 (student project)")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode arr = objectMapper.readTree(res.body());
            if (!arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            JsonNode hit = arr.get(0);
            return Optional.of(new double[]{hit.get("lat").asDouble(), hit.get("lon").asDouble()});
        } catch (Exception e) {
            log.warn("Dhaka geocode lookup failed for '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }
}
