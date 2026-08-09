package org.sda.restaurantservice.service;

import org.sda.restaurantservice.dto.UploadSignatureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Issues short-lived signed upload parameters so the browser can push food photos straight to
 * Cloudinary. The API secret stays server-side; the client only receives the SHA-1 signature
 * Cloudinary's upload API verifies.
 *
 * <p>Signing rule (per Cloudinary): join every signed parameter as {@code key=value} with '&'
 * in alphabetical key order, append the API secret, SHA-1 hash it. Here the signed parameters
 * are {@code folder} and {@code timestamp}, so the browser must send exactly those two plus
 * {@code api_key} and the file.
 */
@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Value("${cloudinary.folder}")
    private String folder;

    /**
     * Spring does not read {@code .env} files, so when secrets.yml still holds placeholders the
     * credentials are picked up from the project's {@code .env} instead (the file the credentials
     * were pasted into). Works no matter which directory the service was started from.
     */
    @PostConstruct
    void loadEnvFallback() {
        if (isConfigured()) {
            log.info("Cloudinary configured from Spring properties");
            return;
        }
        for (Path candidate : List.of(Paths.get(".env"), Paths.get("..", ".env"), Paths.get("..", "..", ".env"))) {
            Path file = candidate.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Map<String, String> env = parseEnv(file);
            if (!filled(cloudName)) {
                cloudName = env.getOrDefault("CLOUDINARY_CLOUD_NAME", cloudName);
            }
            if (!filled(apiKey)) {
                apiKey = env.getOrDefault("CLOUDINARY_API_KEY", apiKey);
            }
            if (!filled(apiSecret)) {
                apiSecret = env.getOrDefault("CLOUDINARY_API_SECRET", apiSecret);
            }
            if (isConfigured()) {
                log.info("Cloudinary credentials loaded from {}", file);
                return;
            }
        }
        log.warn("Cloudinary is NOT configured - no real credentials in secrets.yml or .env");
    }

    /** Parses KEY=VALUE lines, skipping comments; tolerant of quotes and an export prefix. */
    private static Map<String, String> parseEnv(Path file) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                        && value.charAt(value.length() - 1) == value.charAt(0)) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            // An unreadable .env is not fatal - Spring properties may still configure us.
            log.warn("Could not read {}: {}", file, e.getMessage());
        }
        return values;
    }

    /** Admin-only, like every other write in this service. */
    public UploadSignatureResponse uploadSignature(String role) {
        requireAdmin(role);
        requireConfigured();

        long timestamp = Instant.now().getEpochSecond();

        // Alphabetical order matters - this must match what the browser submits.
        TreeMap<String, String> params = new TreeMap<>();
        params.put("folder", folder);
        params.put("timestamp", String.valueOf(timestamp));

        StringBuilder toSign = new StringBuilder();
        params.forEach((key, value) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(key).append('=').append(value);
        });
        toSign.append(apiSecret);

        return new UploadSignatureResponse(cloudName, apiKey, timestamp, sha1Hex(toSign.toString()),
                folder, "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload");
    }

    /** True when every credential is present and not a placeholder from the example files. */
    public boolean isConfigured() {
        return filled(cloudName) && filled(apiKey) && filled(apiSecret);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured - set CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET "
                            + "in .env or restaurant-service/secrets.yml");
        }
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank() && !value.contains("replace_with");
    }

    private void requireAdmin(String role) {
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token");
        }
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant admin role required");
        }
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is part of the JDK contract; this cannot happen on a real JVM.
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
