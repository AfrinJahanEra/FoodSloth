# FoodSloth

Food-delivery platform built with Spring Boot microservices.

## Communication rules

- **Client -> service:** REST only, always through `api-gateway` (port 8080). The gateway verifies the JWT once and passes identity on as `X-User-Id` / `X-User-Role` headers; services never verify tokens themselves.
- **Service -> service:** RabbitMQ only, never REST. No service ever calls another service over HTTP.
- **Infrastructure:** every service needs only RabbitMQ (localhost:5672), its own MongoDB database and Eureka (8761) to be up. A consumer that is down simply leaves messages waiting in its queue - nothing is lost, and nothing else blocks.

## RabbitMQ topology

One shared durable topic exchange: **`food.exchange`**.

- Routing keys: `<owning-service>.<event>` - the publisher is always obvious from the key.
- Queues: `<consuming-service>.<event>.queue`, declared **only by the consumer** of that queue.
- Publishers declare only the exchange; consumers declare the exchange plus their own queues and bindings. An identical exchange re-declaration is a no-op in AMQP, so services start in any order.
- Reliability per service: `default-requeue-rejected: false` (a poison message is dropped after retries instead of looping forever) and `retry.enabled: true, max-attempts: 3`.
- There is no shared event JAR. Each service owns a minimal record for what it consumes; the contract is the routing key + JSON field names.

`orderId` is a UUID minted by cart-service at checkout and carried through every event of that order - it is the platform's correlation key. The client gets it back immediately in the 202 checkout response and then polls `GET /orders/{orderId}`.

## Event catalogue on `food.exchange`

| Routing key | Publisher | Meaning |
| --- | --- | --- |
| `cart.checked-out` | cart | Cart checked out; price these items |
| `order.reorder-requested` | order | Past order reordered (same shape as checkout) |
| `restaurant.order-priced` | restaurant | Checkout priced; creates the order |
| `restaurant.order-unavailable` | restaurant | Checkout could not be priced |
| `restaurant.order-accepted` | restaurant | Kitchen accepted the order |
| `restaurant.order-rejected` | restaurant | Kitchen rejected the order |
| `restaurant.order-ready` | restaurant | Food ready; triggers rider assignment |
| `order.payment-requested` | order | Charge this order (amount in major units) |
| `payment.succeeded` | payment | Charge succeeded (COD auto-succeeds) |
| `payment.failed` | payment | Charge failed |
| `order.confirmed` | order | Money secured; kitchen screen + email |
| `order.cancelled` | order | Customer cancel, kitchen reject or unpriceable checkout |
| `delivery.assigned` | delivery | Rider assigned to the order |
| `delivery.started` | delivery | Rider picked the food up |
| `delivery.arriving` | delivery | Rider nearly at the customer |
| `delivery.completed` | delivery | Food handed over |
| `order.delivered` | order | Final stop of the pipeline |
| `marketing.broadcast` | any | Promotional fan-out (notification service sends it) |

## The order pipeline

```
checkout (202 + orderId)
  -> cart.checked-out            restaurant prices it
  -> restaurant.order-priced     order-service creates the order, asks for payment
  -> order.payment-requested     payment charges card / auto-succeeds COD
  -> payment.succeeded           order.confirmed -> kitchen accepts -> food ready
  -> restaurant.order-ready      delivery assigns a rider by proximity
  -> delivery.started / arriving customer watches the rider on the map
  -> delivery.completed          order.delivered
```

Failures have their own paths: `restaurant.order-unavailable` closes the checkout before any order exists; `payment.failed` lets the customer retry; `restaurant.order-rejected` and customer cancels raise `order.cancelled`, which refunds any card charge.

## Services

| Service | Port | MongoDB database | Gateway path |
| --- | --- | --- | --- |
| service-registry | 8761 | - | - |
| api-gateway | 8080 | - | (entry point) |
| user-service | 9001 | food_user_db | `/users/**` |
| restaurant-service | 9002 | food_restaurant_db | `/restaurant/**` |
| cart-service | 9003 | food_cart_db | `/carts/**` |
| order-service | 9004 | food_order_db | `/orders/**` |
| payment-service | 9005 | food_payment_db | `/payments/**` |
| delivery-service | 9006 | food_delivery_db | `/deliveries/**` |
| notification-service | 9007 | food_notification_db | `/notifications/**` |
| frontend-service | 9008 | - | (serves the web UI) |

Start order: service-registry, then any services in any order, then api-gateway. RabbitMQ and MongoDB must already be running.

## Web frontend

`frontend-service` (port 9008) serves a single-page app (plain HTML/CSS/JS from `src/main/resources/static` - no build step). It has no database and joins no event bus: the browser talks straight to api-gateway with the JWT, which the gateway allows from `http://localhost:9008` via its CORS config.

Three role-based experiences in one app: customer (menu, cart, checkout, pay, live tracking, inbox), admin/kitchen (order queue, menu CRUD, open/close, broadcasts, riders) and rider (shift, GPS sharing, job actions). Run `FrontendServiceApplication` in IntelliJ and open http://localhost:9008.

## Secrets

No secret is hard-coded; every one is a `${ENV_VAR:default}` placeholder:

| Secret | Env var | Used by |
| --- | --- | --- |
| Stripe API key | `STRIPE_SECRET_KEY` | payment-service |
| Stripe webhook secret | `STRIPE_WEBHOOK_SECRET` | payment-service |
| JWT signing key | `JWT_SECRET` | api-gateway + user-service (must match) |

Three ways to supply them, pick one:
1. Copy `.env.example` to `.env` and load it from IntelliJ run configurations (or export in your shell). `.env` is gitignored.
2. payment-service only: copy `payment-service/secrets.example.yml` to `payment-service/secrets.yml` (also gitignored) - it is imported via `spring.config.import: optional:file:./secrets.yml`.
3. Set the variables directly: IntelliJ Run → Edit Configurations → Environment variables.

Never commit real keys. The checked-in defaults are placeholders for local testing only.
