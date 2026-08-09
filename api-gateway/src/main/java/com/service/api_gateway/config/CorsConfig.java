package com.service.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS for the web frontend.
 *
 * <p>The SPA is served by frontend-service on http://localhost:9008 and calls this gateway
 * (http://localhost:8080) directly from the browser, which is a cross-origin request. Every other
 * client (Postman, mobile apps) is unaffected.
 *
 * <p>The JWT filter needs no change for this: preflight OPTIONS requests carry no Authorization
 * header, and a request with no token is allowed through anonymously.
 */
@Configuration
public class CorsConfig {

    /** Where the frontend runs. Add deployed origins here if the UI ever leaves localhost. */
    private static final String FRONTEND_ORIGIN = "http://localhost:9008";

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(FRONTEND_ORIGIN));
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

}
