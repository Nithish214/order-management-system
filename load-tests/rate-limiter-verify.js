// One-off verification script (not meant to be a permanent addition like the other
// load-tests/*.js files) -- confirms the gateway's order-creation rate limiter
// (OrderCreationRateLimiterFilter, 50/sec) actually trips under genuine same-second
// concurrency, which a plain shell loop of background curl processes can't reliably
// produce (process-spawning/TLS-handshake overhead spreads them out over several
// seconds instead). k6 runs real concurrent virtual users in one process, so 150 VUs
// with no sleep genuinely overlap within the same second.
//
// Run with:
//   k6 run -e GATEWAY_URL=https://nithish-ordermgmt.duckdns.org -e ACCESS_TOKEN=<token> load-tests/rate-limiter-verify.js
import http from "k6/http";
import { Counter } from "k6/metrics";

const tooManyRequests = new Counter("too_many_requests_count");
const created = new Counter("created_count");

export const options = {
  vus: 150,
  iterations: 150,
};

const GATEWAY = __ENV.GATEWAY_URL;
const TOKEN = __ENV.ACCESS_TOKEN;

export default function () {
  const res = http.post(
    `${GATEWAY}/orders`,
    JSON.stringify({ items: [{ productId: 1, quantity: 1 }] }),
    {
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        "Content-Type": "application/json",
        "Idempotency-Key": `rl-verify-${__VU}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      },
    }
  );
  if (res.status === 429) tooManyRequests.add(1);
  if (res.status === 201) created.add(1);
}
