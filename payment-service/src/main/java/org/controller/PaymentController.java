package org.controller;

import org.entity.Payment;
import org.entity.PaymentStatus;
import org.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/checkout-session")
    public CheckoutSessionResponse createCheckoutSession(@RequestBody CreateCheckoutRequest request) {
        PaymentService.CheckoutSessionResult result = paymentService.createCheckoutSession(
                request.orderId(), request.userId(), request.amount(), request.currency(), request.description());
        return new CheckoutSessionResponse(
                result.payment().getId(),
                result.payment().getStripeSessionId(),
                result.checkoutUrl(),
                result.payment().getStatus().name());
    }

    @GetMapping("/session/{sessionId}/verify")
    public Payment verifySession(@PathVariable String sessionId) {
        return paymentService.verifySession(sessionId);
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

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable String id) {
        return paymentService.getById(id);
    }

    @GetMapping("/order/{orderId}")
    public Payment getPaymentByOrder(@PathVariable String orderId) {
        return paymentService.getByOrderId(orderId);
    }

    @GetMapping("/user/{userId}")
    public List<Payment> getPaymentsByUser(@PathVariable String userId) {
        return paymentService.getByUserId(userId);
    }

    @PostMapping("/{id}/refund")
    public Payment refundPayment(@PathVariable String id) {
        return paymentService.refund(id);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload,
                                               @RequestHeader("Stripe-Signature") String signature) {
        paymentService.handleWebhookEvent(payload, signature);
        return ResponseEntity.ok().build();
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

    public record CreateCheckoutRequest(String orderId, String userId, Long amount, String currency, String description) {
    }

    public record CheckoutSessionResponse(String paymentId, String sessionId, String checkoutUrl, String status) {
    }
}
