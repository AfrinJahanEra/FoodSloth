package org.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    @Value("${stripe.api.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        // Masked self-check: makes it obvious at startup whether secrets.yml was loaded.
        boolean placeholder = secretKey == null || secretKey.contains("replace_with");
        String masked = secretKey == null || secretKey.length() <= 12
                ? "(missing)"
                : secretKey.substring(0, 8) + "…" + secretKey.substring(secretKey.length() - 4);
        if (placeholder) {
            log.error("Stripe key is still the placeholder - secrets.yml was NOT loaded. "
                    + "Card payments will fail with 'Invalid API Key'.");
        } else {
            log.info("Stripe initialised with key {}", masked);
        }
    }
}
