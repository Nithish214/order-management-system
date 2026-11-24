"""Stops the project's RDS instance once nobody's actually USED the app for IDLE_DAYS
consecutive daily checks -- measured by api-gateway's own GET /system/traffic-count,
not CloudFront's Requests metric and not RDS's own DatabaseConnections metric.

Third design for this signal, not the first or second:

1. DatabaseConnections was the original design. Wrong -- never actually zero regardless
   of real visitors, poisoned by the app's own healthcheck/HikariCP chatter.
2. CloudFront's Requests metric fixed that, but has its own blind spot: it counts every
   edge hit, including search/SEO crawlers, link-preview bots, and vulnerability
   scanners that fetch the SPA's static HTML/JS shell but never execute it.
3. Filtering those out by User-Agent keyword (bot/crawl/spider/...) was the next
   attempt, against CloudFront's own access logs instead of the aggregate metric --
   and THAT turned out to be defeated by the exact traffic it was built for: real
   log samples showed datacenter IPs hitting "/" cold, no referer, with a User-Agent
   deliberately spoofed to look like a real Chrome/Windows browser specifically to
   blend past naive keyword filters. A blocklist can't win that game.

This version gives up on anything CloudFront-edge-visible (IP, UA, referer -- all
trivially spoofable) and instead asks whether the SPA's JavaScript actually ran and
called the real backend. A scanner that only fetches static HTML never executes React,
so it never calls api-gateway's API, no matter how convincing its headers are --
GET /system/traffic-count (SystemHealthController) reports a bot-proof-by-construction
count of real order-service/inventory-service traffic for exactly this reason (see its
own comment and SystemHealthSampler#currentRealTrafficCount).

That count is cumulative since the gateway's last restart, and this Lambda only runs
once a day -- so instead of trying to reconstruct a sliding 7-day window (the previous
log-scanning version's approach), this tracks a simple consecutive-idle-days counter in
S3: each day, if the count didn't move since yesterday's reading, the streak goes up by
one; if it moved at all, the streak resets to zero. Reaching IDLE_DAYS consecutive
no-movement days is what triggers the stop. This also neatly solves the "logging just
turned on, not enough history yet" problem the previous version needed a separate check
for: the streak starts at 0 and necessarily takes IDLE_DAYS real daily checks to reach
the threshold.

Still re-checks (and re-stops) on every run for the same reason as both previous
versions: RDS force-restarts a manually-stopped instance after 7 days
(https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html)
regardless of whether anyone wanted it running again, and this is what undoes that.

One more case this handles, found directly from CloudTrail: the EC2 box hosting
api-gateway is itself started/stopped on demand through a separate app ("CloudSwitch",
a different Lambda -- see SecurityConfig's /health/services comment), not on any
schedule. When it's stopped, GET /system/traffic-count can't be reached at all -- and
treating "unreachable" as merely ambiguous (as every other failure mode here does) was
wrong for THIS specific case: if EC2 is stopped, nobody can possibly be using the app,
which is stronger evidence of idleness than an unchanged traffic count, not weaker.
Without this, a long EC2 stop (observed: 18+ days, nobody noticed) would leave this
Lambda skipping forever and RDS running the entire time, exactly backwards from the
whole point of this automation. So EC2's own state is checked FIRST, before even
attempting the HTTP call -- "not running" counts as a confirmed idle day on its own.
"""

import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone

import boto3

DB_INSTANCE_ID = os.environ["DB_INSTANCE_ID"]
EC2_INSTANCE_ID = os.environ["EC2_INSTANCE_ID"]
TRAFFIC_COUNT_URL = os.environ["TRAFFIC_COUNT_URL"]
STATE_BUCKET = os.environ["STATE_BUCKET"]
STATE_KEY = os.environ.get("STATE_KEY", "idle-tracker/state.json")
IDLE_DAYS = int(os.environ.get("IDLE_DAYS", "7"))

rds = boto3.client("rds")
ec2 = boto3.client("ec2")
s3 = boto3.client("s3")


def lambda_handler(event, context):
    instance = rds.describe_db_instances(DBInstanceIdentifier=DB_INSTANCE_ID)["DBInstances"][0]
    status = instance["DBInstanceStatus"]

    if status != "available":
        print(f"{DB_INSTANCE_ID} is '{status}', not 'available' -- nothing to do this run.")
        return

    ec2_state = _ec2_state()
    if ec2_state != "running":
        print(f"EC2 instance {EC2_INSTANCE_ID} is '{ec2_state}', not 'running' -- nobody "
              f"can be using the app regardless of what the traffic count would say. "
              f"Counting today as confirmed idle without attempting the HTTP check.")
        _advance(reset=False, new_last_count=None)
        return

    current_count = _fetch_traffic_count()
    # Gateway unreachable while EC2 itself IS running (redeploying, container
    # restarting, a transient blip) is genuinely ambiguous -- not "confirmed idle," not
    # "confirmed active." Skipping without touching the saved streak means one bad
    # network blip costs nothing: the next successful run picks the comparison back up
    # from the last good reading.
    if current_count is None:
        print("EC2 is running but GET /system/traffic-count could not be reached this "
              "run -- skipping, leaving streak and RDS state untouched.")
        return

    state = _load_state()
    if state is None or state.get("last_count") is None:
        # No real count to compare against -- either the true first-ever run, or every
        # prior day was counted via the EC2-stopped shortcut above (which deliberately
        # never records a last_count, see _advance). Either way, there's no claim to
        # make about TODAY's traffic from this reading alone, so don't reset whatever
        # streak the EC2-stopped days already built -- just record a baseline to diff
        # against from tomorrow.
        existing_streak = state["consecutive_idle_days"] if state else 0
        print(f"No prior traffic-count baseline found -- recording {current_count} as the "
              f"baseline (streak carried over at {existing_streak}).")
        _persist_and_decide(current_count, existing_streak)
        return

    last_count = state["last_count"]
    if current_count < last_count:
        # The gateway's counter only ever goes up between restarts -- a lower reading
        # than yesterday's means it restarted since then (a redeploy, most likely), so
        # there's no reliable delta to judge. Reset rather than risk crediting idle
        # time to a period we can't actually vouch for.
        print(f"Traffic count dropped from {last_count} to {current_count} -- the gateway "
              f"restarted since the last check.")
        _advance(reset=True, new_last_count=current_count)
    elif current_count > last_count:
        print(f"Traffic count moved from {last_count} to {current_count} -- real usage "
              f"since yesterday's check.")
        _advance(reset=True, new_last_count=current_count)
    else:
        print(f"Traffic count unchanged at {current_count}.")
        _advance(reset=False, new_last_count=current_count)


# EC2 Describe calls don't support resource-level IAM permission any more than RDS's
# do, hence Resource: "*" in permissions-policy.json for this too -- scoped down to one
# specific instance ID here in code instead.
def _ec2_state():
    reservations = ec2.describe_instances(InstanceIds=[EC2_INSTANCE_ID])["Reservations"]
    return reservations[0]["Instances"][0]["State"]["Name"]


def _fetch_traffic_count():
    try:
        with urllib.request.urlopen(TRAFFIC_COUNT_URL, timeout=5) as response:
            return float(response.read().decode("utf-8"))
    except (urllib.error.URLError, ValueError, TimeoutError) as e:
        print(f"GET {TRAFFIC_COUNT_URL} failed: {e}")
        return None


# Shared by every "today counts as idle (or doesn't)" branch -- bumps or resets the
# streak, persists it alongside whatever last_count should carry forward (None when the
# EC2-stopped path skipped the HTTP check entirely, so the next real reading compares
# against the last genuine one rather than a stale placeholder), and hands off to
# _persist_and_decide for the save + stop-or-not call.
def _advance(reset, new_last_count):
    state = _load_state() or {"last_count": None, "consecutive_idle_days": 0}
    streak = 0 if reset else state["consecutive_idle_days"] + 1
    last_count = new_last_count if new_last_count is not None else state["last_count"]
    _persist_and_decide(last_count, streak)


# The common tail: save the new state, then stop RDS if the streak has reached
# IDLE_DAYS. Pulled out separately from _advance because the "just established a
# baseline, no claim about today either way" branch above also needs to act on a
# streak it didn't just compute itself (one carried over from EC2-stopped days) --
# without this, a streak that crossed the threshold while EC2 was stopped would sit
# one extra, unnecessary day before the next real traffic-count reading finally
# triggered the stop.
def _persist_and_decide(last_count, streak):
    _save_state({"last_count": last_count, "consecutive_idle_days": streak})

    if streak >= IDLE_DAYS:
        print(f"{streak} consecutive idle days (>= {IDLE_DAYS}) -- stopping {DB_INSTANCE_ID}.")
        rds.stop_db_instance(DBInstanceIdentifier=DB_INSTANCE_ID)
    else:
        print(f"{streak}/{IDLE_DAYS} consecutive idle days -- leaving {DB_INSTANCE_ID} up.")


def _load_state():
    try:
        body = s3.get_object(Bucket=STATE_BUCKET, Key=STATE_KEY)["Body"].read()
        return json.loads(body)
    except s3.exceptions.NoSuchKey:
        return None


def _save_state(state):
    state = dict(state, last_updated=datetime.now(timezone.utc).isoformat())
    s3.put_object(Bucket=STATE_BUCKET, Key=STATE_KEY, Body=json.dumps(state).encode("utf-8"))
