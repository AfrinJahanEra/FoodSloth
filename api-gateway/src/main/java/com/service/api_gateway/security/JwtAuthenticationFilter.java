package com.service.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Single place in the whole system that verifies JWTs (issued by user-service).
 *
 * On every request: strips any client-supplied X-User-* headers (so nobody can forge
 * identity by sending them directly), then if a valid Bearer token is present, re-adds
 * them from the verified claims. Downstream services trust X-User-Id / X-User-Role /
 * X-User-Email instead of parsing the token themselves.
 *
 * A missing token is allowed through anonymously - each service still decides for itself
 * which of its own endpoints require an identity. An invalid/expired token is rejected here
 * with 401, since presenting a bad token is different from presenting none at all.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_EMAIL = "X-User-Email";

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        ServerHttpRequest.Builder mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_USER_EMAIL);
                });

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange.mutate().request(mutatedRequest.build()).build());
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);

        mutatedRequest.headers(headers -> {
            if (userId != null) {
                headers.add(HEADER_USER_ID, userId);
            }
            if (role != null) {
                headers.add(HEADER_USER_ROLE, role);
            }
            if (email != null) {
                headers.add(HEADER_USER_EMAIL, email);
            }
        });

        return chain.filter(exchange.mutate().request(mutatedRequest.build()).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] bytes = "{\"message\":\"Invalid or expired token\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
