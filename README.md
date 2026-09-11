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
               ├────────────────────────────────►  (consumes it)
               │                                │
               │   Kafka topics: inventory.reserved / inventory.failed
               │ ◄──────────────────────────────┤
               │   (consumes it, updates order status)
```

## Services

| Service | Folder | Port | Role |
|---|---|---|---|
| **API Gateway** | `api-gateway/` | 8080 | Single public entry point; routes to the two services below by path, keeps internal-only endpoints (like restock) off the public route table |
| **Order Service** | `/` (repo root) | 8081 | Users, products, orders; writes an outbox event per order; consumes inventory outcomes to update order status |
| **Inventory Service** | `inventory-service/` | 8082 | Owns live stock; consumes order events, reserves stock idempotently, publishes the outcome |

Each service has its own database (own Oracle user locally, own Postgres database on the same RDS instance in AWS) — no service reads another's tables directly.

## What this project actually demonstrates

- **Transactional outbox pattern** — an order write and its "notify Kafka" note commit in one database transaction, avoiding the dual-write problem of calling Kafka directly from request-handling code. A scheduled poller relays outbox rows to Kafka, only marking them published once the broker actually confirms receipt.
- **Choreographed saga** — Order Service creates orders optimistically (`PENDING`) and reacts asynchronously to Inventory Service's verdict (`CONFIRMED`/`CANCELLED`), rather than either service calling the other synchronously.
- **Idempotent consumers** — Inventory Service tracks processed event IDs so a redelivered Kafka message (a real possibility under at-least-once delivery) doesn't double-deduct stock.
- **Retry + dead-letter topic** — a failing consumer retries a bounded number of times, then the record is routed to a DLT instead of blocking the consumer indefinitely.
- **API Gateway as the only public door** — internal/admin-only operations (like restocking inventory) are deliberately excluded from the gateway's routes, reachable only from inside the network.
- **Real AWS deployment** — RDS PostgreSQL (translated from the Oracle schema used locally), Dockerized services on EC2, least-privilege IAM (a role scoped to exactly the CloudWatch permissions needed, nothing more), and security groups that reference each other rather than open IP ranges.

## Local development

```bash
docker compose up -d oracle          # Oracle XE for local dev
mvn spring-boot:run                  # Order Service (repo root), :8081
cd inventory-service && mvn spring-boot:run   # :8082
cd api-gateway && mvn spring-boot:run         # :8080
```

Swagger UI (exploring/testing by hand — not proxied through the gateway):
- Order Service: http://localhost:8081/swagger-ui.html
- Inventory Service: http://localhost:8082/swagger-ui.html

## API (through the Gateway — `http://localhost:8080`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/users`, `/users/{id}` | Look up users |
| `GET` | `/products`, `/products/{id}` | Look up products and prices |
| `POST` | `/orders` | Create an order (always returns `201`, status starts `PENDING`) |
| `GET` | `/orders/{id}` | Get one order — poll this to watch status settle |
| `GET` | `/users/{id}/orders` | Order history for a user |
| `GET` | `/stock`, `/stock/{id}` | Live stock levels |

`POST /stock/{id}/restock` exists but is intentionally **not** reachable through the Gateway — see Inventory Service directly (internal-only by design).

## AWS deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for the full provisioning walkthrough (RDS, EC2, IAM, security groups, CloudWatch) and cost breakdown. `scripts/start-all.ps1` / `scripts/stop-all.ps1` start and stop the deployed environment (copy `scripts/config.example.ps1` to `scripts/config.ps1` with your own instance/DB identifiers first).

## Known limitations

- No tests.
- No authentication/authorization on any endpoint.
- Stock reservation has no locking — a narrow race is possible under real concurrent load.
- No Payment or Notification service yet (deliberately out of scope so far).
- No CI/CD — deploys are manual (`git clone` / file transfer + `docker compose up` on the EC2 instance).
