// Goal: actually find where POST /orders starts producing REAL errors (not just slow
// responses) -- every prior write-heavy run (write-heavy.js, write-heavy-escalate.js) topped
// out at 300 VUs with 0% errors, just increasingly bad latency. This pushes much further
// (up to 2000 VUs) and, critically, logs every single failure the moment it happens with a
// timestamp + the VU count active at that instant -- k6's own end-of-run summary only gives
// ONE aggregate number, which can't tell you WHEN in a multi-stage ramp things started
// breaking. Grep this run's console output for "FAILURE" afterward, sort by timestamp, and
// the earliest line is the actual moment/VU-level things first gave way.
//
// Same unique-Idempotency-Key-per-iteration and "restock first" reasoning as write-heavy.js --
// not repeated here.
//
// Run with:
//   k6 run -e GATEWAY_URL=http://<ec2-ip>:8080 -e ACCESS_TOKEN=<token> load-tests/write-heavy-breaking-point.js

import http from "k6/http";
import { check } from "k6";
import { Rate } from "k6/metrics";

const serverErrorRate = new Rate("server_error_rate");

export const options = {
  stages: [
    { duration: "30s", target: 100 },   // re-confirm the known-safe floor
    { duration: "1m", target: 300 },    // re-confirm the known "slow but 0% errors" ceiling
    { duration: "1m30s", target: 600 }, // new territory starts here
    { duration: "2m", target: 900 },
    { duration: "2m", target: 1200 },
    { duration: "2m30s", target: 1600 },
    { duration: "2m30s", target: 2000 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    // Not meant to pass -- this run is explicitly trying to find the point these get
    // violated, and by how much, not grade a pass/fail result.
    http_req_duration: ["p(95)<1000"],
    server_error_rate: ["rate<0.01"],
  },
};

const GATEWAY = __ENV.GATEWAY_URL;
const TOKEN = __ENV.ACCESS_TOKEN;
const PRODUCT_IDS = [1, 2, 3, 4, 5, 6, 7];

function uniqueIdempotencyKey() {
  return `loadtest-break-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2, 10)}`;
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
      timeout: "10s",
    }
  );

  const failed = res.status === 0 || res.status >= 500;
  serverErrorRate.add(failed);
  check(res, { "order created (201)": (r) => r.status === 201 });

  if (failed) {
    // Each VU runs in its own isolated JS context in k6, so there's no reliable
    // shared "is this globally the first failure" flag across VUs -- an explicit
    // wall-clock timestamp on every line is what actually lets failures be sorted
    // and correlated against the docker-stats/RDS monitor log afterward, which is
    // the real goal here, not a "first" label.
    console.log(`FAILURE at ${new Date().toISOString()} VU=${__VU} status=${res.status} duration=${res.timings.duration}ms`);
  }
}
