# RDS idle auto-stop

A small scheduled Lambda that stops the project's RDS instance
(`order-management-db`, `eu-west-1`) once nobody's actually **visited the site for 7
days straight** — pure cost automation, nothing about the app itself changes.

## Why CloudFront requests, not RDS connections

The obvious first signal to use was RDS's own `DatabaseConnections` metric -- and it's
wrong, found by actually reasoning through what else touches this database. It's never
genuinely zero while the backend is running, regardless of real visitors:
`docker-compose.prod.yml`'s healthchecks hit `order-service`/`inventory-service`'s
`/actuator/health` roughly every 30 seconds, which pings the database as part of that
check, and HikariCP (Spring Boot's connection pool) keeps several idle connections open
per service the whole time it's running anyway. So this instead checks the CloudFront
distribution's own `Requests` metric -- nonzero only because an actual browser asked for
the actual site, independent of whatever the backend does to itself in the background.

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
  scoped to this one instance's ARN only, `cloudwatch:GetMetricStatistics` (needs `*`
  too -- covers both the eu-west-1 RDS calls and the us-east-1 CloudFront call, since
  IAM permissions aren't region-scoped by the policy itself), and the standard `logs:*`
  trio scoped to this function's own log group.

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
  --environment "Variables={DB_INSTANCE_ID=order-management-db,IDLE_DAYS=7,DISTRIBUTION_ID=E1MH9X3BUX6CH5}" \
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
  --environment "Variables={DB_INSTANCE_ID=order-management-db,IDLE_DAYS=14,DISTRIBUTION_ID=E1MH9X3BUX6CH5}" \
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
