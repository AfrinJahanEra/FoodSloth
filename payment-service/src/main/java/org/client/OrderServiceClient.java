package org.client;

import org.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outbound REST call to order-service, notifying it when a payment reaches a final state.
 * <p>
 * Contract (order-service is expected to implement this endpoint once it exists):
 * <pre>
 *   PUT http://ORDER-SERVICE/orders/{orderId}/payment-status
 *   Content-Type: application/json
 *
 *   { "paymentId": "...", "status": "SUCCEEDED" | "FAILED" }
 * </pre>
 * order-service should map SUCCEEDED -&gt; CONFIRMED and FAILED -&gt; PAYMENT_FAILED per the
 * documented order lifecycle.
 * <p>
 * The order-service instance is resolved dynamically through Eureka (service id
 * {@code ORDER-SERVICE}), so no hostname/port needs to be hardcoded or reconfigured once
 * order-service registers itself. Until order-service exists, calls here simply fail fast
 * (no instances found) and are logged as a warning - they never break the payment flow.
 */
@Component
public class OrderServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

    private final RestClient restClient;

    public OrderServiceClient(RestClient.Builder loadBalancedRestClientBuilder) {
        this.restClient = loadBalancedRestClientBuilder.baseUrl("http://ORDER-SERVICE").build();
    }

    public void notifyPaymentStatus(String orderId, String paymentId, PaymentStatus status) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        try {
            restClient.put()
                    .uri("/orders/{orderId}/payment-status", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentStatusUpdate(paymentId, status.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Could not notify order-service about payment {} (order {}, status {}): {}",
                    paymentId, orderId, status, e.getMessage());
        }
    }

    public record PaymentStatusUpdate(String paymentId, String status) {
    }
}
