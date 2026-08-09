package org.controller;

import org.dto.PaymentResponse;
import org.entity.Payment;
import org.entity.PaymentStatus;
import org.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST surface of Payment Service. Reached by clients through api-gateway at {@code /payments/**}.
 *
 * <p>There is no endpoint to start a payment: payments are started by {@code order.payment-requested},
 * so nobody can be charged for an order that does not exist. The customer's browser gets the Stripe
 * link from {@code GET /payments/order/{orderId}} and can ask for a fresh one after a failure.
 *
 * <p>Auth note: the JWT is verified by api-gateway, which strips client-supplied {@code X-User-*}
 * headers and re-adds them from the verified claims. The two Stripe-facing endpoints (the webhook and
 * the return pages) are deliberately unauthenticated - Stripe and a redirected browser carry no
 * token; the webhook is instead verified by its signature.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    private PaymentService paymentService;

    // ------------------------------------------------------------------
    // Customer
    // ------------------------------------------------------------------

    /** The payment for one of my orders, including the Stripe link while it is still payable. */
    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrder(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @RequestHeader(value = "X-User-Role", required = false) String role,
                                     @PathVariable String orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        return ROLE_ADMIN.equals(role)
                ? PaymentResponse.from(payment)
                : PaymentResponse.from(paymentService.requireOwnedBy(requireAuthenticated(userId), payment));
    }

    @GetMapping("/me")
    public List<PaymentResponse> getMyPayments(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return paymentService.getByUserId(requireAuthenticated(userId)).stream().map(PaymentResponse::from).toList();
    }

    /** A fresh checkout link after a declined or expired attempt. */
    @PostMapping("/order/{orderId}/retry")
    public PaymentResponse retry(@RequestHeader(value = "X-User-Id", required = false) String userId,
                         @PathVariable String orderId) {
        return PaymentResponse.from(paymentService.retry(requireAuthenticated(userId), orderId));
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    /** The admin's ledger: every payment on the platform, newest first. */
    @GetMapping
    public List<PaymentResponse> getAllPayments(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return paymentService.getAll().stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@RequestHeader(value = "X-User-Id", required = false) String userId,
                              @RequestHeader(value = "X-User-Role", required = false) String role,
                              @PathVariable String id) {
        Payment payment = paymentService.getById(id);
        return ROLE_ADMIN.equals(role)
                ? PaymentResponse.from(payment)
                : PaymentResponse.from(paymentService.requireOwnedBy(requireAuthenticated(userId), payment));
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refundPayment(@RequestHeader(value = "X-User-Role", required = false) String role,
                                 @PathVariable String id) {
        requireAdmin(role);
        return PaymentResponse.from(paymentService.refund(id));
    }

    // ------------------------------------------------------------------
    // Stripe-facing (no token - see the class note)
    // ------------------------------------------------------------------

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload,
                                              @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebhookEvent(payload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/checkout-success", produces = MediaType.TEXT_HTML_VALUE)
    public String checkoutSuccess(@RequestParam("session_id") String sessionId) {
        Payment payment = paymentService.verifySession(sessionId);
        boolean paid = payment.getStatus() == PaymentStatus.SUCCEEDED;
        return paid
                ? resultPage(true, "Payment successful", "Your payment has been received. You can close this tab.")
                : resultPage(false, "Payment not confirmed yet",
                        "We couldn't confirm your payment yet. If you completed checkout, refresh this page in a few seconds.");
    }

    @GetMapping(value = "/checkout-cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String checkoutCancel(@RequestParam(value = "session_id", required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            paymentService.markCancelled(sessionId);
        }
        return resultPage(false, "Payment cancelled", "You cancelled the checkout. No charge was made. You can close this tab.");
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

    private String resultPage(boolean success, String title, String message) {
        String color = success ? "#16a34a" : "#dc2626";
        String icon = success ? "&#10003;" : "&#10007;";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title>"
                + "<style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;"
                + "height:100vh;margin:0;background:#f8fafc;}"
                + ".card{text-align:center;padding:2.5rem;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,0.08);"
                + "background:#fff;max-width:360px;}"
                + ".icon{font-size:3rem;color:" + color + ";}"
                + "h1{color:" + color + ";font-size:1.4rem;margin:0.5rem 0;}"
                + "p{color:#475569;font-size:0.95rem;}</style></head>"
                + "<body><div class=\"card\"><div class=\"icon\">" + icon + "</div><h1>" + title + "</h1><p>" + message
                + "</p></div></body></html>";
    }
}
