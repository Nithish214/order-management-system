// Read-heavy scenario: GET /stock and GET /products only. Both are cache-aside reads
// (StockController/ProductController check Redis first, fall through to Postgres on a
// miss) -- this test exists specifically to see the cache earning its keep: after the
// first request per key populates it, every VU from here on should be hitting Redis, not
// RDS, for the rest of the run. If p95 latency stays flat as VUs climb from 5 to 100, that's
// the cache working. If it climbs in lockstep with VU count, the cache isn't actually being
// hit (check TTLs, check the cache-key logic) or Redis itself is the bottleneck.
//
// Run with:
//   k6 run -e GATEWAY_URL=http://<ec2-ip>:8080 -e ACCESS_TOKEN=<token> load-tests/read-heavy.js

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

// A VU (Virtual User) is one simulated client -- k6 runs this many independent, concurrent
// copies of the default() function below, each looping continuously for as long as the
// current stage lasts. 100 VUs does NOT mean "100 requests" -- it means 100 things hitting
// the server AT THE SAME TIME, each firing its next request as soon as its previous one
// (plus the sleep() at the end) finishes. A server that's degrading under load will
// naturally produce a LOWER total request rate even with the VU count held constant, since
// each VU is stuck waiting longer per request -- watch requests-per-second in the summary,
// not just VU count, for exactly this reason.
//
// Custom metric, not k6's built-in http_req_failed: this run is explicitly trying to
// distinguish "the server is actually breaking" (5xx, or no response at all -- status 0)
// from anything else, without depending on exactly how a given k6 version decides to taint
// http_req_failed by default. serverErrorRate is 1 for a request that got a 5xx or no
// response, 0 otherwise -- an unambiguous, self-defined signal.
const serverErrorRate = new Rate("server_error_rate");

export const options = {
  // Stages ramp VU count up (and back down) over time instead of jumping straight to the
  // peak -- the whole point of this shape is to find the load level where things start to
  // bend, not just whether the system is still standing at the single highest number. Watch
  // the summary's per-stage behavior (or re-run narrower stages once you've spotted roughly
  // where it happens) to pin down which step things actually started degrading at.
  stages: [
    { duration: "30s", target: 5 }, // warm-up: populate the cache, confirm the script works
    { duration: "1m", target: 10 },
    { duration: "1m", target: 25 },
    { duration: "1m", target: 50 },
    { duration: "1m", target: 100 },
    { duration: "30s", target: 0 }, // ramp-down: let in-flight requests finish cleanly
  ],
  thresholds: {
    // A threshold marks the run PASS/FAIL at the end against a number you decide matters --
    // it doesn't stop the test by itself (unless you add abortOnFail). These are starting
    // guesses for a Redis-cached read on a t3.small/t3.micro pair, not a universal law:
    // p(95)<300 means "95% of requests should finish under 300ms". If the real run's p95
    // comes back well under this even at 100 VUs, that's a strong, concrete "the cache is
    // working" result -- exactly what this scenario is here to show.
    http_req_duration: ["p(95)<300", "p(99)<800"],
    server_error_rate: ["rate<0.01"],
  },
};

const GATEWAY = __ENV.GATEWAY_URL;
const TOKEN = __ENV.ACCESS_TOKEN;

export default function () {
  const params = { headers: { Authorization: `Bearer ${TOKEN}` } };

  const stockRes = http.get(`${GATEWAY}/stock`, params);
  serverErrorRate.add(stockRes.status === 0 || stockRes.status >= 500);
  check(stockRes, { "stock: 200": (r) => r.status === 200 });

  const productsRes = http.get(`${GATEWAY}/products`, params);
  serverErrorRate.add(productsRes.status === 0 || productsRes.status >= 500);
  check(productsRes, { "products: 200": (r) => r.status === 200 });

  // sleep() between iterations models a real user pausing to look at a page -- without it,
  // every VU fires requests back-to-back as fast as the network allows, which is a valid
  // thing to test but is a DIFFERENT test (raw throughput ceiling) than "how does this
  // behave under a realistic number of concurrently browsing users."
  sleep(1);
}
