// Write-heavy scenario: POST /orders repeatedly. This is the expensive endpoint -- unlike
// the cached reads, every call does a real Postgres write (order + order_item + outbox_event
// + idempotency_key, all in one transaction -- see OrderCreationService) and triggers the
// full async pipeline (order.created -> Inventory Service reserves stock -> Payment Service
// decides -> status flips). This test only measures the SYNCHRONOUS part (how long the HTTP
// call itself takes to return 201), not the async pipeline's own latency -- that's a
// deliberate scope choice, not an oversight; see this project's earlier idempotency-key work
// for why the HTTP response always comes back before Inventory/Payment have even seen the
// order.
//
// BEFORE RUNNING THIS: check current stock levels (GET /stock) and restock every product to
// a large buffer first (a few thousand units) if they're anywhere near real/realistic
// numbers. Two separate reasons, not one:
//   1. Obvious one: you don't want this test to be the reason a real demo/portfolio deploy
//      shows "out of stock" on everything afterward.
//   2. Less obvious, and the one that actually matters for THIS test's data quality: POST
//      /orders always returns 201 immediately regardless of whether stock actually exists --
//      the accept/reject decision happens later, asynchronously (InventoryOutcomeListener).
//      So exhausting stock will NOT show up as a change in this script's own error rate or
//      HTTP status codes at all. It WILL show up later if you check order outcomes
//      afterward (GET /orders/{id}, or a DB query) and find a wall of legitimate REJECTED
//      orders that has nothing to do with the load test's actual pass/fail result, but is
//      very easy to mistake for "the system broke" if you don't already know why.
//
// Run with:
//   k6 run -e GATEWAY_URL=http://<ec2-ip>:8080 -e ACCESS_TOKEN=<token> -e ADDRESS_ID=<id> load-tests/write-heavy.js
//
// ADDRESS_ID is required: POST /orders now needs one of the caller's own saved addresses (see
// AddressController -- create one for the test user first, then pass its id). Every order in the
// run ships to that one address; it's a load test, not a realism test of address variety.

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const serverErrorRate = new Rate("server_error_rate");
// Tracked separately from serverErrorRate -- a 409 here means this exact Idempotency-Key
// somehow already existed (shouldn't happen with uniqueIdempotencyKey() below, but if this
// ever shows nonzero, it's a real signal the key-generation scheme collided, not that the
// server is unhealthy).
const idempotencyCollisionRate = new Rate("idempotency_collision_rate");

export const options = {
  stages: [
    { duration: "30s", target: 5 },
    { duration: "1m", target: 10 },
    { duration: "1m", target: 25 },
    { duration: "1m", target: 50 },
    { duration: "1m", target: 100 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    // Looser than read-heavy's thresholds on purpose: this endpoint does a real,
    // multi-table transactional write, not a Redis lookup -- 1s/3s here are a starting
    // guess for this hardware (t3.small app box, t3.micro Postgres), not a claim about what
    // "should" be achievable. The actual point of this run is finding out what's normal
    // here, not grading against an assumed number.
    http_req_duration: ["p(95)<1000", "p(99)<3000"],
    server_error_rate: ["rate<0.01"],
  },
};

const GATEWAY = __ENV.GATEWAY_URL;
const TOKEN = __ENV.ACCESS_TOKEN;
const ADDRESS_ID = Number(__ENV.ADDRESS_ID);

// Small, cheap catalog spread across different rows -- like real shoppers buying different
// things, not everyone fighting over the exact same product_stock row. Concentrating every
// VU on ONE product would mostly be re-testing the optimistic-locking retry path specifically
// (already exercised directly, on purpose, earlier in this project) rather than genuine
// system-wide load.
const PRODUCT_IDS = [1, 2, 3, 4, 5, 6, 7];

// __VU (this VU's number) and __ITER (this VU's iteration count, starting at 0 each run) are
// both k6 globals -- unique WITHIN a single run, but NOT across two separate runs of this
// script (VU 1's first iteration is always __VU=1, __ITER=0 every time you start k6).
// Idempotency-Key is meant to identify "this exact new order attempt", and this scenario is
// specifically testing genuine new-order creation, not idempotency itself -- so Date.now()
// and a random suffix are added specifically to guarantee that re-running this same script
// a minute later still generates brand-new keys, rather than accidentally replaying the
// previous run's stored responses and silently not creating any new orders at all.
function uniqueIdempotencyKey() {
  return `loadtest-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function () {
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];

  const res = http.post(
    `${GATEWAY}/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }], addressId: ADDRESS_ID }),
    {
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
        "Idempotency-Key": uniqueIdempotencyKey(),
      },
    }
  );

  serverErrorRate.add(res.status === 0 || res.status >= 500);
  idempotencyCollisionRate.add(res.status === 409 || res.status === 400);
  check(res, { "order created (201)": (r) => r.status === 201 });

  sleep(1);
}
