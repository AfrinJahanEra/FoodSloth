# FoodSloth

FoodSloth is a food-delivery platform built with Spring Boot microservices. A customer browses the
menu, checks out, pays (card via Stripe or cash on delivery), watches the kitchen cook, tracks the
rider and receives push notifications - while an admin runs the kitchen screen, the menu, rider
assignments and the payment ledger from the same web app.

The platform follows two hard rules:

- **Client to service:** REST only, always through `api-gateway` (port 8080).
- **Service to service:** RabbitMQ events only. No service ever calls another service over HTTP.

---

## Architecture at a glance

```
                         +-------------------+
   Browser (SPA)  ---->  |    api-gateway    |  :8080   JWT verified once here
   frontend-service :9008 +--------+---------+
                                   |  REST + X-User-Id / X-User-Role headers
        +----------+----------+----+----+-----------+-----------+
        |          |          |         |           |           |
   user-service restaurant cart-    order-      payment-    delivery-   notification-
        :9001      :9002    service  service     service     service      service
                          :9003      :9004       :9005       :9006        :9007
        |          |          |         |           |           |           |
        +-----+----+----+-----+----+----+-----+-----+-----+-----+-----+-----+
              |                              RabbitMQ
              +-------------------->  food.exchange  <--------------------+
                       (durable topic exchange, one for the whole platform)

   service-registry (Eureka) :8761     every service has its own MongoDB database
```

Every backend service registers with Eureka, owns exactly one MongoDB database, and speaks to the
rest of the platform exclusively through the shared RabbitMQ exchange. A service that is down
simply leaves messages waiting in its durable queue - nothing is lost and nothing else blocks.

---

## Service catalogue

### service-registry (port 8761)

Eureka server. No business logic, no database. Every other service registers with it and
discovers its peers through it.

### api-gateway (port 8080)

The only door into the platform for clients. Spring Cloud Gateway with:

- **Route forwarding** by path prefix (`/users/**`, `/restaurant/**`, `/carts/**`, `/orders/**`,
  `/payments/**`, `/deliveries/**`, `/notifications/**`) to the owning service via Eureka.
- **JWT verification once**: the gateway validates the token issued by user-service, then strips
  any client-supplied `X-User-Id` / `X-User-Role` headers and re-adds them from the verified
  claims. Downstream services trust these headers and never parse tokens themselves - this makes
  spoofing the headers impossible from outside.
- **CORS** for the frontend origin (`http://localhost:9008`).
- A small set of routes is public without a token: login/signup, the menu, the Stripe webhook and
  Stripe return pages.

### user-service (port 9001, MongoDB `food_user_db`)

Owns accounts and identity. What it does:

- Signup and login for four roles: `CUSTOMER`, `ADMIN`, `DELIVERYMAN` and kitchen staff; issues
  the signed JWT that every other request carries.
- Profile management (`GET/PUT /users/me`), saved delivery addresses (add/update/delete/set
  default), and preferences (food preferences, dietary tags, default payment method).
- Admin user management: list all users, delete a user.
- **Publishes `user.registered`** for every new account, and **re-publishes it for every admin at
  startup** - a self-healing replay so notification-service never loses track of who runs the
  platform (details in the sync section below).
- It consumes nothing and calls nothing.

Key endpoints: `POST /users/signup`, `POST /users/login`, `GET|PUT /users/me`,
`GET|POST /users/me/addresses`, `PUT|DELETE /users/me/addresses/{id}`,
`PUT /users/me/addresses/{id}/default`, `PUT /users/me/preferences`, `GET /users`,
`DELETE /users/{id}`.

### restaurant-service (port 9002, MongoDB `food_restaurant_db`)

Single-tenant: the whole service represents one restaurant, so routes live under `/restaurant`
with no restaurant id. It plays two roles - menu owner and pricing/kitchen engine.

REST side (client-facing):

- Restaurant profile, open/closed switch (`PATCH /restaurant/status`) and operating hours.
- Menu CRUD (`GET|POST /restaurant/menu`, `PUT|DELETE /restaurant/menu/{itemId}`) with an
  availability flag per item.
- Signed Cloudinary upload parameters (`GET /restaurant/images/upload-signature`, admin only); the
  browser uploads the photo directly to Cloudinary and stores the returned URL on the menu item.
- **Kitchen screen** under `/restaurant/kitchen/**`: active queue ordered oldest-first, queue by
  status, single ticket with full line items and totals, and the staff actions
  `accept`, `reject` (with reason) and `ready`.

Event side (inter-service):

- Consumes `cart.checked-out` and `order.reorder-requested` - both are bound to a single
  `restaurant.order-intake.queue` because they carry the same shape and are handled identically:
  "price these items against today's menu".
- Publishes `restaurant.order-priced` when pricing succeeds, or
  `restaurant.order-unavailable` when an item is unknown or sold out (the checkout dies before any
  order exists).
- When `order.confirmed` arrives (money secured) the order enters the kitchen queue; staff
  decisions become `restaurant.order-accepted`, `restaurant.order-rejected` and
  `restaurant.order-ready` (packed and waiting for a rider).
- Consumes `order.cancelled` to stop cooking a cancelled order.

### cart-service (port 9003, MongoDB `food_cart_db`)

One cart per user. Add item, update quantity, remove item, clear cart, and checkout. The path
carries the cart owner and must match the authenticated user, so nobody can shop with somebody
else's id.

Checkout is the entry point of the whole order pipeline and is deliberately fire-and-forget:

1. It mints a UUID `orderId` - the platform's correlation key.
2. It publishes `cart.checked-out` (items, drop address, payment method) and returns **202 with
   the orderId immediately**.
3. Pricing and order creation happen downstream over the broker, so checkout stays instant even if
   restaurant-service or order-service is down.

Key endpoints: `GET /carts/{userId}`, `POST /carts/{userId}/items`,
`PUT|DELETE /carts/{userId}/items/{itemId}`, `DELETE /carts/{userId}`,
`POST /carts/{userId}/checkout`.

### order-service (port 9004, MongoDB `food_order_db`)

The state machine of the platform. It owns the order lifecycle
(`PENDING_PAYMENT -> CONFIRMED -> PREPARING -> READY -> OUT_FOR_DELIVERY -> DELIVERED`, with
`CANCELLED` reachable from most states) and is the hub that connects payment, kitchen and
delivery.

There is **no endpoint to create an order**: orders come into existence when
`restaurant.order-priced` arrives. The REST surface is read/action only:

- `GET /orders/{id}` - the customer polls this after checkout; a 404 simply means pricing has not
  finished yet.
- `GET /orders/me` - order history (admin sees all).
- `PATCH /orders/{id}/cancel` - customer cancel (allowed until the kitchen starts cooking);
  publishes `order.cancelled`, which triggers a refund downstream.
- `POST /orders/{id}/reorder` - publishes `order.reorder-requested` (a fresh orderId, 202) so the
  restaurant re-prices the old basket at today's prices.

Consumed events: `restaurant.order-priced`, `restaurant.order-unavailable`,
`restaurant.order-accepted`, `restaurant.order-rejected`, `restaurant.order-ready`,
`payment.succeeded`, `payment.failed`, `delivery.started`, `delivery.completed`.
Published events: `order.payment-requested`, `order.confirmed`, `order.cancelled`,
`order.delivered`, `order.reorder-requested`.

### payment-service (port 9005, MongoDB `food_payment_db`)

Owns all money movement through Stripe, and is the only service that touches the outside payment
world. There is **no endpoint to start a payment**: payments are started exclusively by
`order.payment-requested`, so nobody can be charged for an order that does not exist.

Behaviour:

- Card orders get a Stripe Checkout session; COD orders auto-succeed immediately.
- Amounts are stored in minor units (cents); the UI divides by 100 for display.
- `payment.succeeded` / `payment.failed` are published as a result of the Stripe webhook
  (signature-verified), the checkout return pages, or the COD shortcut.
- Consuming `order.cancelled` automatically refunds any card charge that already succeeded - the
  platform's compensation mechanism instead of a distributed transaction.
- A declined or expired session can be re-issued with `POST /payments/order/{orderId}/retry`.

Key endpoints: `GET /payments/order/{orderId}` (includes the live Stripe link),
`GET /payments/me`, `POST /payments/order/{orderId}/retry`, admin ledger `GET /payments`,
`GET /payments/{id}`, `POST /payments/{id}/refund`, plus the unauthenticated Stripe-facing
`POST /payments/webhook`, `GET /payments/checkout-success`, `GET /payments/checkout-cancel`.

### delivery-service (port 9006, MongoDB `food_delivery_db`)

The rider app on one side, the customer's tracking feed on the other, and admin dispatch on top.

- **Rider shift**: `POST /deliveries/riders/online` (takes the rider's GPS position once, at go
  online) and `POST /deliveries/riders/offline`; `GET /deliveries/riders/me` returns the rider's
  status and daily slot usage.
- **Capacity**: every rider gets `delivery.slots-per-day` slots (default 5) per calendar day; the
  counter resets with the date. A rider is assignable only while online and with free slots left.
- **Assignment**: a delivery record is created when `restaurant.order-ready` arrives. The admin
  assigns it (`POST /deliveries/{deliveryId}/assign`) to any online rider with remaining slots;
  the job is mandatory for the rider. Consuming `order.cancelled` releases the rider.
- **Rider job flow**: `PATCH .../accept` (this is the moment the customer's order turns
  OUT_FOR_DELIVERY, via `delivery.started`), `PATCH .../picked-up`, `PATCH .../delivered`
  (publishes `delivery.completed`).
- **Live tracking**: `GET /deliveries/order/{orderId}/track` returns rider position, remaining
  distance and ETA; the customer's map polls it. Access is restricted to the parcel's owner, the
  rider on the job, or an admin.
- **Admin**: list all deliveries, list all riders, single delivery lookup.

Consumed events: `restaurant.order-ready`, `order.cancelled`.
Published events: `delivery.assigned`, `delivery.started`, `delivery.completed`.

### notification-service (port 9007, MongoDB `food_notification_db`)

The platform's biggest listener and a pure consumer - it publishes nothing. Push is the only
delivery channel.

- Learns who exists from `user.registered` (including the admin replay from user-service) so it
  knows which users to notify about new orders and finished deliveries.
- Listens to the full order lifecycle: `order.confirmed`, `order.cancelled`, `order.delivered`,
  `restaurant.order-accepted`, `restaurant.order-rejected`, `restaurant.order-ready`,
  `delivery.assigned`, `delivery.started`.
- Listens to money events: `payment.succeeded` (receipt) and `payment.failed` (retry hint).
- Stores every message in an inbox per user; device tokens are registered by the app
  (`POST /notifications/devices`), preferences control the push switch, and the inbox REST API
  (`/notifications/me`, unread count, mark read/read-all) backs the frontend's notification page.
- Because all queues are durable, notification-service can be restarted or lag without dropping a
  single message.

### frontend-service (port 9008)

Serves the single-page web app from `src/main/resources/static` - plain HTML/CSS/JS, no build
step. It has no database and joins no event bus; the browser talks straight to api-gateway with
the JWT. One codebase, three role-based experiences:

- **Customer**: home page with hero and menu, food search, cart, checkout, payment, order list
  with live tracking map, notification inbox, profile.
- **Admin / kitchen**: kitchen screen with live stats and order details, menu management, rider
  dispatch, user management, payment ledger with details and refunds.
- **Rider**: shift toggle, current job with accept/picked-up/delivered actions, history.

The UI uses a small inline SVG icon library (`js/icon.js`) throughout - no emoji anywhere.

---

## Inter-service communication

### The rules

1. **No service ever calls another service over HTTP.** Every cross-service interaction is an
   event on RabbitMQ. REST exists only for clients (browser) talking to services through the
   gateway, and for Stripe talking to payment-service.
2. **Each service owns its own MongoDB database.** No shared collections, no cross-database joins.
   If a service needs another service's data, it listens to that service's events and keeps its
   own copy (e.g. restaurant-service keeps kitchen copies of orders, delivery-service keeps
   delivery copies, notification-service keeps an inbox copy of everything).
3. **No shared event JAR.** Each service owns a minimal record for the events it consumes. The
   contract is the routing key plus the JSON field names.

### RabbitMQ topology

One shared durable topic exchange: **`food.exchange`**.

- Routing keys: `<owning-service>.<event>` - the publisher is always obvious from the key.
- Queues: `<consuming-service>.<event>.queue`, declared **only by the consumer** of that queue.
  Publishers declare only the exchange. An identical exchange re-declaration is a no-op in AMQP,
  so services can start in any order.
- Reliability per service: `default-requeue-rejected: false` (a poison message is dropped after
  retries instead of looping forever) and `retry.enabled: true, max-attempts: 3`.
- All queues are durable: if a consumer is down, messages wait for it.

### Event catalogue on `food.exchange`

| Routing key | Publisher | Consumed by | Meaning |
| --- | --- | --- | --- |
| `user.registered` | user | notification | New account; replayed for every admin at user-service startup |
| `cart.checked-out` | cart | restaurant | Cart checked out; price these items |
| `order.reorder-requested` | order | restaurant | Past order reordered (same shape as checkout) |
| `restaurant.order-priced` | restaurant | order | Checkout priced; creates the order |
| `restaurant.order-unavailable` | restaurant | order | Checkout could not be priced |
| `restaurant.order-accepted` | restaurant | order, notification | Kitchen accepted the order |
| `restaurant.order-rejected` | restaurant | order, notification | Kitchen rejected the order |
| `restaurant.order-ready` | restaurant | order, delivery, notification | Food packed; needs a rider |
| `order.payment-requested` | order | payment | Charge this order (amount in minor units) |
| `payment.succeeded` | payment | order, notification | Charge succeeded (COD auto-succeeds) |
| `payment.failed` | payment | order, notification | Charge failed |
| `order.confirmed` | order | restaurant, notification | Money secured; kitchen may cook |
| `order.cancelled` | order | restaurant, payment, delivery, notification | Order off; stop cooking, refund, release rider |
| `delivery.assigned` | delivery | notification | Rider assigned to the delivery |
| `delivery.started` | delivery | order, notification | Rider accepted; order goes OUT_FOR_DELIVERY |
| `delivery.completed` | delivery | order | Food handed over |
| `order.delivered` | order | notification | Final stop of the pipeline |

### The order pipeline

```
checkout (202 + orderId)
  -> cart.checked-out            restaurant prices it against the live menu
  -> restaurant.order-priced     order-service creates the order, asks for payment
  -> order.payment-requested     payment charges the card / auto-succeeds COD
  -> payment.succeeded           order.confirmed -> kitchen queue -> accept -> cook
  -> restaurant.order-ready      admin assigns a free rider (5 slots/rider/day)
  -> delivery.started            rider accepts - customer sees OUT_FOR_DELIVERY
  -> delivery.completed          order.delivered
```

Failures have their own paths:

- `restaurant.order-unavailable` closes the checkout before any order exists.
- `payment.failed` lets the customer retry with a fresh Stripe link.
- `restaurant.order-rejected` and customer cancels raise `order.cancelled`, which stops cooking,
  releases any rider, and refunds any card charge.

### Correlation

`orderId` is a UUID minted by cart-service at checkout and carried through every event of that
order - it is the platform's correlation key. The client gets it back immediately in the 202
checkout response and then follows the order by id everywhere (`GET /orders/{orderId}`,
`GET /payments/order/{orderId}`, `GET /deliveries/order/{orderId}/track`,
`GET /notifications/order/{orderId}`).

---

## How synchronization works

The system is **eventually consistent by design**: every service writes to its own database, and
state propagates through durable events. Synchronization happens on three levels.

### 1. Service-to-service sync: durable events

When an order changes state, the owning service publishes an event; every interested service has a
durable queue bound to that key and applies the change to its own copy whenever it is able to.
A consumer that is down simply accumulates messages and catches up when it restarts - the producer
never waits, never retries synchronously, and never fails because a peer is offline.

Example - a cancellation ripples through four services from one event:

```
order-service publishes order.cancelled
  -> restaurant-service: kitchen ticket closed, cooking stopped
  -> payment-service:    card charge refunded if it existed
  -> delivery-service:   assigned rider released, slot logic untouched
  -> notification-service: customer and admins informed
```

### 2. The self-healing admin replay

Notification-service must know who the admins are, but user-service is the only source of user
data - and it never exposes an internal sync API. The solution: user-service re-publishes
`user.registered` for **every admin at startup**. An admin registered before the event existed,
or a message lost while the broker was down, is repaired by the next restart. Signup itself never
fails because of the broker: the publish is best-effort, and the replay makes it correct again.

### 3. Client-to-service sync: REST polling behind 202

Because checkout returns 202 before the order even exists, the client synchronizes with REST
polling - deliberately, instead of websockets:

- **Checkout**: `POST /carts/{userId}/checkout` returns the orderId instantly; the frontend polls
  `GET /orders/{orderId}` until it stops returning 404, then follows the status field.
- **Kitchen screen**: polls `GET /restaurant/kitchen/orders` every few seconds; new paid orders
  appear as soon as `order.confirmed` has been applied locally.
- **Rider dashboard**: polls `GET /deliveries/riders/me/current`; an assignment shows up as soon
  as the admin's REST call wrote it.
- **Tracking map**: polls `GET /deliveries/order/{orderId}/track` every few seconds and draws the
  marker itself.
- **Notification inbox**: polls `GET /notifications/me/unread-count` for the badge and the inbox
  list for content.

Polling is safe here because every list endpoint reads the service's own database, which is kept
fresh by the event listeners above - the client only ever asks "what do you know now?", never
triggers cross-service work.

### Consistency choices

- **No distributed transactions.** Compensation instead: `order.cancelled` triggers the refund,
  rider release and kitchen stop as independent, idempotent reactions.
- **Event listeners are idempotent** on the order id, so at-least-once delivery (redelivery after
  a crash) cannot double-create records.
- **Optimistic client UX**: the UI moves forward on local state and reconciles on the next poll.

---

## Ports, databases and gateway paths

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

**Start order**: RabbitMQ and MongoDB first, then service-registry, then any services in any
order, then api-gateway, then frontend-service (open http://localhost:9008).

## Tech stack

- Java 21, Spring Boot 3, Spring Cloud Gateway, Eureka (service discovery)
- Spring AMQP (RabbitMQ) for all inter-service messaging
- Spring Data MongoDB (one Atlas database per service)
- Stripe Checkout + webhooks for card payments
- Cloudinary signed uploads for menu photos
- Frontend: vanilla HTML/CSS/JS single-page app with hash routing and an inline SVG icon set

## Secrets

No secret is hard-coded; every one is a `${ENV_VAR:default}` placeholder:

| Secret | Env var | Used by |
| --- | --- | --- |
| Stripe API key | `STRIPE_SECRET_KEY` | payment-service |
| Stripe webhook secret | `STRIPE_WEBHOOK_SECRET` | payment-service |
| JWT signing key | `JWT_SECRET` | api-gateway + user-service (must match) |
| Admin signup key | `ADMIN_SIGNUP_KEY` | user-service (required to sign up with the ADMIN role; default `ad123`) |

Three ways to supply them, pick one:

1. Copy `.env.example` to `.env` and load it from IntelliJ run configurations (or export in your
   shell). `.env` is gitignored.
2. payment-service only: copy `payment-service/secrets.example.yml` to
   `payment-service/secrets.yml` (also gitignored) - imported via
   `spring.config.import: optional:file:./secrets.yml`.
3. Set the variables directly: IntelliJ Run -> Edit Configurations -> Environment variables.

Never commit real keys. The checked-in defaults are placeholders for local testing only.
