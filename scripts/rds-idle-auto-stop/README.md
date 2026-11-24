# RDS idle auto-stop

A small scheduled Lambda that stops the project's RDS instance
(`order-management-db`, `eu-west-1`) once nobody's actually **visited the site for 7
days straight** — pure cost automation, nothing about the app itself changes.

## Why api-gateway's own traffic count, not RDS connections or CloudFront requests

Third design for this signal, not the first or second:

1. RDS's own `DatabaseConnections` metric was the original design. Wrong -- never
   genuinely zero while the backend is running, regardless of real visitors:
   `docker-compose.prod.yml`'s healthchecks hit `order-service`/`inventory-service`'s
   `/actuator/health` roughly every 30 seconds, which pings the database as part of
   that check, and HikariCP (Spring Boot's connection pool) keeps several idle
   connections open per service the whole time it's running anyway.
2. CloudFront's `Requests` metric fixed that, but has its own blind spot: it counts
   every edge hit, including search/SEO crawlers and vulnerability scanners that fetch
   the SPA's static HTML/JS shell but never execute it. Filtering those by User-Agent
   keyword (against CloudFront's own access logs) was the next attempt -- and *that*
   turned out to be defeated by the exact traffic it targeted: real log samples showed
   datacenter IPs hitting `/` cold, no referer, with a User-Agent deliberately spoofed
   to look like a real Chrome/Windows browser specifically to blend past keyword
   filters. A blocklist can't win that game.
3. This version gives up on anything CloudFront-edge-visible (IP, UA, referer -- all
   spoofable) and instead reads `GET /system/traffic-count` on api-gateway -- a
   deliberately public, single-number endpoint backed by
   `SystemHealthSampler#currentRealTrafficCount`, counting only order-service's and
   inventory-service's own (monitoring-excluded) request totals. A scanner that only
   fetches static HTML never executes the SPA's React code, so it never calls this
   backend at all, no matter how convincing its headers look -- this is a signal bots
   are structurally incapable of faking, not one they merely haven't bothered to fake
   yet.

That count is cumulative since the gateway's last restart and this Lambda only runs
once a day, so rather than reconstruct a sliding 7-day window, it tracks a
consecutive-idle-days streak in S3 (`idle-tracker/state.json` in the same bucket this
folder's earlier CloudFront-logging attempt used -- logging on the distribution is now
turned back off, since it's no longer read by anything): each day the count hasn't
moved since the last check, the streak goes up by one; any movement resets it to zero.
Reaching `IDLE_DAYS` consecutive no-movement days is what triggers the stop.

## Why a Lambda, not just "stop it and leave it"

RDS's own manual stop only lasts up to 7 days — AWS **automatically restarts** a
stopped instance after that, to apply pending maintenance, whether you wanted it
running again or not (see
[AWS's own docs](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html)).
Running this check daily means that forced restart gets undone again on the very
next run, instead of silently leaving the instance running (and billing) indefinitely.

## What it costs

Effectively nothing: one Lambda invocation per day is far inside the always-free
tier (1M requests/month), and the EventBridge rule triggering it is free. This
exists specifically to reduce your *RDS* instance-hours, not add a new cost.

## What it does NOT do

It does not touch the EC2 instance, the frontend, or anything else — only this one
RDS instance's running/stopped state. While it's stopped, the deployed app's
database calls will fail (the site isn't usable) until either you start it manually
or real traffic resumes and you start it back up — this automation only ever stops
it, it never automatically starts it back up in response to traffic, since Lambda
has no way to know "someone's about to visit" ahead of the request that needs it.

## Files

- `lambda_function.py` — the actual check-and-stop logic.
- `trust-policy.json` — lets the Lambda service assume this function's execution role.
- `permissions-policy.json` — least-privilege: `DescribeDBInstances` (needs `*`, RDS
  doesn't support resource-level permission on Describe calls), `StopDBInstance`
  scoped to this one instance's ARN only, `s3:GetObject`/`s3:PutObject` scoped to just
  the `idle-tracker/*` key (the streak-tracking state file), and the standard `logs:*`
  trio scoped to this function's own log group. Notably does NOT need any CloudFront
  or EC2 permission -- `GET /system/traffic-count` is a plain public HTTPS call, not an
  AWS API call.

## Depends on

`api-gateway`'s `GET /system/traffic-count` (`SystemHealthController`) must actually be
deployed and reachable at `TRAFFIC_COUNT_URL` for this to work -- until then, every
daily run logs a fetch failure and safely no-ops (leaves RDS up, touches no state)
rather than guessing.

## How it was deployed

```bash
# IAM role the Lambda runs as
aws iam create-role \
  --role-name rds-idle-auto-stop-role \
  --assume-role-policy-document file://trust-policy.json

aws iam put-role-policy \
  --role-name rds-idle-auto-stop-role \
  --policy-name rds-idle-auto-stop-policy \
  --policy-document file://permissions-policy.json

# The function itself
zip lambda.zip lambda_function.py
aws lambda create-function \
  --function-name rds-idle-auto-stop \
  --runtime python3.12 \
  --role arn:aws:iam::244689414185:role/rds-idle-auto-stop-role \
  --handler lambda_function.lambda_handler \
  --zip-file fileb://lambda.zip \
  --timeout 30 \
  --environment "Variables={DB_INSTANCE_ID=order-management-db,IDLE_DAYS=7,TRAFFIC_COUNT_URL=https://shop-api.nithishnarravula.dev/system/traffic-count,STATE_BUCKET=order-management-cf-logs-244689414185,STATE_KEY=idle-tracker/state.json}" \
  --region eu-west-1

# Daily trigger, 3am UTC
aws events put-rule \
  --name rds-idle-auto-stop-daily \
  --schedule-expression "cron(0 3 * * ? *)" \
  --region eu-west-1

aws lambda add-permission \
  --function-name rds-idle-auto-stop \
  --statement-id AllowEventBridgeInvoke \
  --action lambda:InvokeFunction \
  --principal events.amazonaws.com \
  --source-arn arn:aws:events:eu-west-1:244689414185:rule/rds-idle-auto-stop-daily \
  --region eu-west-1

aws events put-targets \
  --rule rds-idle-auto-stop-daily \
  --targets "Id"="1","Arn"="arn:aws:lambda:eu-west-1:244689414185:function:rds-idle-auto-stop" \
  --region eu-west-1
```

## Turning it off

```bash
aws events remove-targets --rule rds-idle-auto-stop-daily --ids 1 --region eu-west-1
aws events delete-rule --name rds-idle-auto-stop-daily --region eu-west-1
aws lambda delete-function --function-name rds-idle-auto-stop --region eu-west-1
aws iam delete-role-policy --role-name rds-idle-auto-stop-role --policy-name rds-idle-auto-stop-policy
aws iam delete-role --role-name rds-idle-auto-stop-role
```

## Changing the idle threshold

```bash
aws lambda update-function-configuration \
  --function-name rds-idle-auto-stop \
  --environment "Variables={DB_INSTANCE_ID=order-management-db,IDLE_DAYS=14,TRAFFIC_COUNT_URL=https://shop-api.nithishnarravula.dev/system/traffic-count,STATE_BUCKET=order-management-cf-logs-244689414185,STATE_KEY=idle-tracker/state.json}" \
  --region eu-west-1
```

## Starting the database back up manually

```bash
aws rds start-db-instance --db-instance-identifier order-management-db --region eu-west-1
```

Takes a few minutes; check status with:

```bash
aws rds describe-db-instances --db-instance-identifier order-management-db \
  --query "DBInstances[0].DBInstanceStatus" --region eu-west-1
```
