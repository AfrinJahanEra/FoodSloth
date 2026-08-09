package org.sda.deliveryservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sda.deliveryservice.entity.GeoPoint;
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
 * Resolves a free-text address into a GPS pin through OpenStreetMap Nominatim
 * (free, no API key). Used only when an order arrives without drop coordinates,
 * so the remaining-distance and ETA math still has a destination to measure to.
 *
 * <p>The full address string is tried first, then progressively simpler
 * fallbacks - Nominatim resolves "area, Bangladesh" reliably but chokes on
 * uncommon street tokens. Results, including "no pin found" misses, are cached
 * in memory, so each distinct address text touches the network at most once
 * per service run.
 */
@Component
public class AddressGeocoder {

    private static final Logger log = LoggerFactory.getLogger(AddressGeocoder.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentHashMap<String, Optional<GeoPoint>> cache = new ConcurrentHashMap<>();

    public Optional<GeoPoint> geocode(String addressText) {
        if (addressText == null || addressText.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(addressText.trim(), this::lookup);
    }

    private Optional<GeoPoint> lookup(String addressText) {
        List<String> candidates = candidates(addressText);
        for (int i = 0; i < candidates.size(); i++) {
            Optional<GeoPoint> hit = query(candidates.get(i));
            if (hit.isPresent()) {
                log.info("Geocoded '{}' via query '{}' to {}", addressText, candidates.get(i), hit.get());
                return hit;
            }
            if (i < candidates.size() - 1) {
                // Nominatim's free tier politely allows about one request per second.
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        log.info("Geocoder found no pin for '{}'", addressText);
        return Optional.empty();
    }

    /** Full text first, then simpler fallbacks built from the address parts. */
    private List<String> candidates(String addressText) {
        String text = addressText.trim();
        List<String> out = new ArrayList<>();
        out.add(text + ", Bangladesh");
        String[] parts = text.split(",");
        String first = parts[0].trim();
        if (!first.isEmpty() && parts.length > 1) {
            String city = "";
            for (String p : parts) {
                String low = p.toLowerCase();
                if (low.contains("dhaka") || low.contains("chattogram") || low.contains("gazipur")
                        || low.contains("narayanganj") || low.contains("khulna") || low.contains("sylhet")) {
                    city = p.trim();
                    break;
                }
            }
            if (!city.isEmpty() && !city.equalsIgnoreCase(first)) {
                out.add(first + " " + city);
            }
            out.add(first + ", Bangladesh");
        }
        return out;
    }

    private Optional<GeoPoint> query(String queryText) {
        String encoded = URLEncoder.encode(queryText, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + encoded))
                .header("User-Agent", "FoodSlothSQA/1.0 (student project)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Geocoder answered {} for '{}'", response.statusCode(), queryText);
                return Optional.empty();
            }
            JsonNode hits = json.readTree(response.body());
            if (hits.isArray() && !hits.isEmpty()) {
                JsonNode first = hits.get(0);
                double lat = Double.parseDouble(first.get("lat").asText());
                double lon = Double.parseDouble(first.get("lon").asText());
                return Optional.of(new GeoPoint(lat, lon));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Geocoding '{}' failed: {}", queryText, e.toString());
            return Optional.empty();
        }
    }
}
