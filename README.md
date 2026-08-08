# FoodSloth

## Payment Service (Stripe integration)

`payment-service` (port 9005) handles checkout via Stripe Checkout Sessions (test mode). Full request/response examples: [payment-service.http](payment-service/requests/payment-service.http) and [postman_collection.json](payment-service/requests/postman_collection.json).

**Order Service → Payment Service** (order-service calls this to start payment):
```
POST /payments/checkout-session
{ "orderId": "...", "userId": "...", "amount": 25000, "currency": "bdt", "description": "..." }
```
Amount is in poisha (1/100 taka), minimum 10000 (BDT 100.00). Returns `{ paymentId, sessionId, checkoutUrl, status }` — redirect the customer to `checkoutUrl` to pay.

**Payment Service → Order Service** (best-effort notification once payment finishes; not yet consumed since order-service doesn't exist yet):
```
PUT http://ORDER-SERVICE/orders/{orderId}/payment-status
{ "paymentId": "...", "status": "SUCCEEDED" | "FAILED" }
```
Resolved dynamically via Eureka (service id `ORDER-SERVICE`) — no code changes needed in payment-service once order-service registers and implements this endpoint. Map `SUCCEEDED` → `CONFIRMED` and `FAILED` → `PAYMENT_FAILED` per the order status lifecycle. Until then, this call just fails fast and logs a warning (see `org.client.OrderServiceClient`) — it never breaks the payment flow.

Setup: set `STRIPE_SECRET_KEY` (and optionally `STRIPE_WEBHOOK_SECRET`) as environment variables before running — see `payment-service/src/main/resources/application.yml` for the full list of overridable properties. Never commit a real key into that file.
