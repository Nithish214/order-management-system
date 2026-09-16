// Escalated follow-up to write-heavy.js: the original 5->10->25->50->100 ramp held at 0%
// errors with EC2 CPU peaking at 81.5% (not yet pinned) and RDS connections flat at 30 --
// meaning the actual breaking point is somewhere past 100 VUs, not found yet. This script
// skips past the already-verified-safe low end and pushes straight into new territory:
// 50->100->150->200->300 VUs, to actually locate where something gives.
//
// Same script otherwise -- same unique-Idempotency-Key-per-iteration reasoning, same
// custom server_error_rate metric, same "restock first" requirement -- see write-heavy.js
// for the full explanation of both; not repeated here.
//
// Run with:
//   k6 run -e GATEWAY_URL=http://<ec2-ip>:8080 -e ACCESS_TOKEN=<token> load-tests/write-heavy-escalate.js

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const serverErrorRate = new Rate("server_error_rate");
const idempotencyCollisionRate = new Rate("idempotency_collision_rate");

export const options = {
  stages: [
    { duration: "30s", target: 50 }, // start where the previous run finished comfortably
    { duration: "1m", target: 100 }, // re-confirm the previous ceiling before going past it
    { duration: "1m", target: 150 },
    { duration: "1m", target: 200 },
    { duration: "1m", target: 300 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    // Same numbers as write-heavy.js -- deliberately NOT loosened for this run. The point
    // here is to see these fail and find out exactly which stage it happens at, not to
    // grade a passing result.
    http_req_duration: ["p(95)<1000", "p(99)<3000"],
    server_error_rate: ["rate<0.01"],
  },
};

const GATEWAY = __ENV.GATEWAY_URL;
const TOKEN = __ENV.ACCESS_TOKEN;
const PRODUCT_IDS = [1, 2, 3, 4, 5, 6, 7];

function uniqueIdempotencyKey() {
  return `loadtest-esc-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function () {
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];

  const res = http.post(
    `${GATEWAY}/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }),
    {
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
        "Idempotency-Key": uniqueIdempotencyKey(),
      },
      // Explicit timeout, shorter than k6's own default (60s): at the load levels this
      // script is aiming for, a request that's still waiting after 10s is itself a sign of
      // real trouble (connection-pool exhaustion, EC2 CPU saturation queuing requests) --
      // this makes that show up promptly as a clear, countable timeout in the results
      // instead of the whole test just quietly running long.
      timeout: "10s",
    }
  );

  serverErrorRate.add(res.status === 0 || res.status >= 500);
  idempotencyCollisionRate.add(res.status === 409 || res.status === 400);
  check(res, { "order created (201)": (r) => r.status === 201 });

  sleep(1);
}
