"""Stops the project's RDS instance once nobody's actually loaded the site in IDLE_DAYS
days -- measured via CloudFront's own Requests metric on the frontend distribution, NOT
RDS's own DatabaseConnections metric.

That's a deliberate fix, not the original design: DatabaseConnections looked like the
obvious signal, but it's poisoned by the app's own internal chatter and is never
actually zero while the backend is running, regardless of real visitors --
docker-compose.prod.yml's healthchecks hit order-service/inventory-service's
/actuator/health roughly every 30s, which pings the database as part of that check, and
HikariCP (Spring Boot's connection pool) keeps several idle connections open per service
the whole time it's running anyway. CloudFront's request count, on the other hand, is
only ever nonzero because an actual browser asked for the actual site -- it's the one
metric this project already has that genuinely answers "did anyone visit," independent
of whatever the backend does to itself in the background.

Runs on a daily EventBridge schedule (see README.md in this folder for the exact rule),
not in response to anything -- there's no "just went idle" event to react to, so polling
once a day and checking a trailing window is the only real option.

Deliberately re-checks (and re-stops) on every run rather than only acting on a
state transition: RDS itself force-restarts a manually-stopped instance after 7 days
(AWS's own hard cap, to apply pending maintenance -- see
https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html), whether
anyone wanted it running again or not. Without this daily re-check, that forced restart
would silently undo this automation and leave the instance running indefinitely. Simply
running the same idle check again the next day and re-stopping it if still idle is
simpler than trying to detect and specifically counteract that one AWS behavior.
"""

import os
from datetime import datetime, timedelta, timezone

import boto3

DB_INSTANCE_ID = os.environ["DB_INSTANCE_ID"]
DISTRIBUTION_ID = os.environ["DISTRIBUTION_ID"]
IDLE_DAYS = int(os.environ.get("IDLE_DAYS", "7"))

rds = boto3.client("rds")
# CloudFront is a global service -- its own CloudWatch metrics are only ever published
# to us-east-1, regardless of which region the distribution "lives in" or which region
# this Lambda itself runs in (eu-west-1, same as everything else in this project).
# Getting this region wrong doesn't error -- it just silently returns zero datapoints,
# which would look identical to "genuinely idle" and stop the database by mistake.
cloudfront_cloudwatch = boto3.client("cloudwatch", region_name="us-east-1")


def lambda_handler(event, context):
    instance = rds.describe_db_instances(DBInstanceIdentifier=DB_INSTANCE_ID)["DBInstances"][0]
    status = instance["DBInstanceStatus"]

    # Only "available" can actually be stopped. Every other status (already "stopped",
    # mid-"stopping"/"starting", or the brief window right after AWS's own forced
    # restart) just needs to wait for tomorrow's run rather than erroring here today.
    if status != "available":
        print(f"{DB_INSTANCE_ID} is '{status}', not 'available' -- nothing to do this run.")
        return

    end = datetime.now(timezone.utc)
    start = end - timedelta(days=IDLE_DAYS)

    # One bucket spanning the whole window, not IDLE_DAYS separate daily buckets -- the
    # only question that matters is "was there ANY request at all in this window", which
    # a single Sum answers in one API call.
    response = cloudfront_cloudwatch.get_metric_statistics(
        Namespace="AWS/CloudFront",
        MetricName="Requests",
        Dimensions=[
            {"Name": "DistributionId", "Value": DISTRIBUTION_ID},
            {"Name": "Region", "Value": "Global"},
        ],
        StartTime=start,
        EndTime=end,
        Period=IDLE_DAYS * 86400,
        Statistics=["Sum"],
    )

    datapoints = response.get("Datapoints", [])
    # No datapoints at all means CloudFront has nothing to report for this window (e.g.
    # a genuinely quiet distribution) -- treated the same as "confirmed zero requests",
    # both mean nobody's visited.
    total_requests = sum(dp["Sum"] for dp in datapoints) if datapoints else 0

    if total_requests > 0:
        print(
            f"{DISTRIBUTION_ID} served {total_requests:.0f} request(s) in the last "
            f"{IDLE_DAYS} days -- leaving {DB_INSTANCE_ID} up."
        )
        return

    print(
        f"{DISTRIBUTION_ID} has served 0 requests for {IDLE_DAYS} days -- "
        f"stopping {DB_INSTANCE_ID}."
    )
    rds.stop_db_instance(DBInstanceIdentifier=DB_INSTANCE_ID)
