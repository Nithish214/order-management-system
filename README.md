# Order Management System

An event-driven order management system built as a set of independent Spring Boot
microservices, communicating over Kafka, with a real deployment to AWS. Built as a
learning project to work through the actual mechanics of event-driven microservices —
the outbox pattern, idempotent consumers, dead-letter handling, an API gateway, and
cloud deployment — rather than just reading about them.

## Architecture

```
                    ┌────────────────┐
  Frontend/Postman ─►  API Gateway    │  :8080  (the one public address)
                    └───────┬────────┘
                            │ routes by URL path
              ┌─────────────┴─────────────┐
              ▼                           ▼
     ┌──────────────────┐        ┌──────────────────────┐
     │  Order Service    │        │  Inventory Service    │
     │      :8081        │        │      :8082            │
     │  users, products,  │        │  live stock,           │
     │  orders, outbox     │        │  idempotency ledger     │
     └─────────┬──────────┘        └───────────┬────────────┘
               │                                │
               │   Kafka topic: order.created    │
               ├────────────────────────────────►  (consumes it, reserves stock)
               │                                │
               │                                │ Kafka topic: inventory.reserved
               │                                ├─────────────────┐
               │                                │                 ▼
               │   Kafka topic: inventory.failed │      ┌──────────────────────┐
               │ ◄──────────────────────────────┤      │  Payment Service       │
               │   (stock unavailable -- reject   │      │      :8083             │
               │    immediately, no payment ever   │      │  simulated payment,     │
               │    attempted)                     │      │  its own outbox         │
               │                                │      └───────────┬────────────┘
               │   Kafka topics: payment.completed / payment.failed
               │ ◄──────────────────────────────────────────────────┤
               │   (consumes it, CONFIRMED/REJECTED)                 │
                                                                      │
                                                Kafka topic: payment.failed
                                          (Inventory Service also consumes this
                                           one directly, to release the stock
                                           it reserved)
```

## Services

| Service | Folder | Port | Role |
|---|---|---|---|
| **API Gateway** | `api-gateway/` | 8080 | Single public entry point; routes to the services below by path; validates every request's JWT (AWS Cognito), enforces group-based authorization for admin routes, and hosts the BFF (`/auth/login`, `/auth/refresh`, `/auth/logout`) that holds the refresh token in an HttpOnly cookie |
| **Order Service** | `order-service/` | 8081 | Users, products, orders; writes an outbox event per order; consumes inventory/payment outcomes to update order status |
| **Inventory Service** | `inventory-service/` | 8082 | Owns live stock; consumes order events, reserves stock idempotently, publishes the outcome; releases reserved stock on cancellation or a failed payment |
| **Payment Service** | `payment-service/` | 8083 | Consumes `inventory.reserved`, simulates a payment (~90% success), publishes `payment.completed`/`payment.failed` via its own outbox. No REST API of its own — pure Kafka consumer/producer |

Each service has its own database (own Postgres database locally, and its own Postgres database on the same RDS instance in AWS) — no service reads another's tables directly. Local dev used to run against Oracle XE; that's gone now, so every service is Postgres end to end.

## What this project actually demonstrates

- **Transactional outbox pattern** — an order write and its "notify Kafka" note commit in one database transaction, avoiding the dual-write problem of calling Kafka directly from request-handling code. A scheduled poller relays outbox rows to Kafka, only marking them published once the broker actually confirms receipt.
- **Choreographed saga, now three services deep** — Order Service creates orders optimistically (`PENDING`); Inventory Service reserves stock and hands off to Payment Service rather than confirming the order itself; only a completed payment actually confirms it. A declined payment (`payment.failed`) is consumed by *both* Order Service (`REJECTED`) and Inventory Service (release the stock it reserved) — one event, two independent reactions, no service calling another synchronously anywhere in the chain. Customers can also cancel a `PENDING`/`CONFIRMED` order themselves (`CANCELLED`), the mirror-image event.
- **Idempotent consumers** — every consumer in this system (Inventory Service, Payment Service) tracks processed event IDs so a redelivered Kafka message (a real possibility under at-least-once delivery) doesn't double-deduct stock, double-charge a simulated payment, or double-release a reservation. Worth knowing: this only works when each producer's event ids are unique *within the table checking them* — Inventory Service's own `processed_event` table is shared across three independently-numbered id sequences (Order Service's outbox ids for two event types, Payment Service's own separate outbox ids for a third), so its `payment.failed` consumer namespaces its key (`"payment-failed:" + id`) specifically to avoid colliding with the other two.
- **Retry + dead-letter topic** — a failing consumer retries a bounded number of times, then the record is routed to a DLT instead of blocking the consumer indefinitely.
- **API Gateway as the only public door** — the Gateway is the sole trust boundary: it validates every request's JWT and is the only service exposed to the internet at all.
- **Authentication vs. authorization, cleanly separated** — AWS Cognito (a User Pool) handles authentication (issuing signed JWTs on login); the Gateway handles authorization (any valid token for most routes, a specific `admin` group claim for the restock route). Order Service and Inventory Service have zero auth code — they trust the Gateway completely, which only works because their ports stay off the public internet at the network level.
- **Real AWS deployment** — RDS PostgreSQL, Dockerized services on EC2, least-privilege IAM (a role scoped to exactly the CloudWatch permissions needed, nothing more), and security groups that reference each other rather than open IP ranges.

## Local development

```bash
docker compose up -d postgres        # Postgres for local dev (creates orderdb + inventorydb)
cd order-service && mvn spring-boot:run       # :8081
cd inventory-service && mvn spring-boot:run   # :8082
cd api-gateway && mvn spring-boot:run         # :8080
```

Swagger UI (exploring/testing by hand — not proxied through the gateway):
- Order Service: http://localhost:8081/swagger-ui.html
- Inventory Service: http://localhost:8082/swagger-ui.html

## API (through the Gateway — `http://localhost:8080`)

Every route below requires `Authorization: Bearer <token>` — see [DEPLOYMENT.md](DEPLOYMENT.md#phase-4--authentication-at-the-gateway-aws-cognito) for how to get one from Cognito.

| Method | Path | Auth required | Purpose |
|---|---|---|---|
| `GET` | `/users`, `/users/{id}` | Any valid token | Look up users |
| `GET` | `/products`, `/products/{id}` | Any valid token | Look up products and prices |
| `POST` | `/orders` | Any valid token | Create an order for the caller (identity from the token, not the request body) |
| `GET` | `/orders/{id}` | Only the order's owner | Get one order — poll this to watch status settle; `403` if it isn't yours |
| `POST` | `/orders/{id}/cancel` | Only the order's owner | Cancel a `PENDING`/`CONFIRMED` order — releases any reserved stock; `409` if it's already terminal |
| `GET` | `/orders/mine` | Any valid token | The caller's own order history |
| `GET` | `/stock`, `/stock/{id}` | Any valid token | Live stock levels |
| `POST` | `/stock/{id}/restock` | Token must carry the `admin` group | Record a stock delivery |

## AWS deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for the full provisioning walkthrough (RDS, EC2, IAM, security groups, CloudWatch) and cost breakdown. `scripts/start-all.ps1` / `scripts/stop-all.ps1` start and stop the deployed environment (copy `scripts/config.example.ps1` to `scripts/config.ps1` with your own instance/DB identifiers first).

## Known limitations

- No tests.
- No self-service signup flow — Cognito users are still created via the CLI (see DEPLOYMENT.md), not a real registration form. A `frontend/` (React + Vite) does exist, talking to the Gateway directly, no BFF.
- A Cognito identity's first order auto-creates its `app_user` row with placeholder email/name (the access token carries no profile info) — a real signup flow would populate these properly instead.
- Stock reservation has no locking — a narrow race is possible under real concurrent load.
- Payment Service's payment is entirely simulated (a coin flip, not a real gateway) — no Notification service yet either.
- No CI/CD — deploys are manual (`git clone` / file transfer + `docker compose up` on the EC2 instance).
