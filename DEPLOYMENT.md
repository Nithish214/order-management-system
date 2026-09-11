# Phase 3-4 — AWS Deployment & Authentication

Deploys the existing system (Order Service, Inventory Service, API Gateway, Kafka) to a
single, cost-conscious AWS setup. No new business features. Local dev (Oracle,
`docker-compose.yml`, `mvn spring-boot:run`) is completely unaffected by anything here.

## What's different from local dev

| | Local dev | AWS (this doc) |
|---|---|---|
| Database | Oracle XE in Docker | RDS PostgreSQL (free tier) |
| Kafka | Docker, on your machine | Docker, on the EC2 instance |
| Compute | `mvn spring-boot:run` x3 | Docker Compose on one EC2 instance |
| Config | `application.yml` (default profile) | `application-prod.yml` (`prod` profile) + env vars |

---

## 1. Concepts, up front

A few AWS building blocks you'll see below, explained once so the steps make sense:

- **VPC (Virtual Private Cloud)** — your own private network inside AWS; everything (EC2, RDS) lives inside one. Every AWS account already has a **default VPC** per region, pre-configured with subnets and internet access. This project uses that default VPC — building a custom one is unnecessary complexity at this size.
- **Security Group** — a virtual firewall attached to a resource (an EC2 instance, an RDS instance). It's a list of *allow* rules only (anything not explicitly allowed is denied); rules are stateful (allow a request in, and its response is automatically allowed back out). Crucially, a security group's rule can name **another security group as the source** instead of an IP range — that's how "only the EC2 instance can reach RDS" gets enforced below, without needing to know the EC2 instance's IP in advance.
- **IAM User vs. IAM Role** — a **User** is a persistent identity (for a person or a long-lived credential) with a password and/or access keys you'd copy somewhere. A **Role** is an identity that something else *assumes* temporarily — AWS hands out short-lived, auto-rotating credentials behind the scenes. An EC2 instance should **never** have a person's IAM User access keys pasted into it; instead, you attach a **Role** to the instance (via an "instance profile"), and anything running on it can use those permissions automatically, with no keys to leak. This project's EC2 instance gets a role scoped to exactly one thing: sending logs/metrics to CloudWatch.

---

## 2. Making the code Postgres-compatible

Already done in this repo:

- **`db/migration-postgres/`** (new, in both Order Service and Inventory Service) — Postgres versions of the schema. Oracle's `db/migration/` is untouched and still used for local dev.
- **Type translations**: `NUMBER(19)`→`BIGINT`, `NUMBER(19,4)`→`NUMERIC(19,4)`, `NUMBER(10)`→`INTEGER`, `VARCHAR2(n CHAR)`→`VARCHAR(n)`, `CLOB`→`TEXT`. Sequences (`CREATE SEQUENCE ... START WITH ... INCREMENT BY ...`) needed no change — Postgres supports that exact syntax.
- **No trailing `COMMIT;`** in the Postgres scripts — Flyway manages the migration's transaction itself, and Postgres has real transactional DDL; an explicit `COMMIT` inside the script can conflict with that.
- **Consolidated to one clean `V1`** per service instead of replaying Oracle's exact 3-step history — a brand-new RDS database has no old rows to migrate through.
- **`application-prod.yml`** (new, in both services) — activated via `SPRING_PROFILES_ACTIVE=prod`, overrides just the datasource/dialect/Flyway-location settings; everything else is inherited from the base `application.yml`.
- **`org.postgresql:postgresql`** driver added to both `pom.xml` files (alongside the existing Oracle driver — harmless to have both; `driver-class-name` picks one at runtime).
- **API Gateway's route URIs** are now `${order.service.url:http://localhost:8081}` / `${inventory.service.url:http://localhost:8082}` — defaulting to `localhost` for local dev, overridden by `ORDER_SERVICE_URL` / `INVENTORY_SERVICE_URL` env vars in production, since inside Docker Compose, containers reach each other by **service name**, not `localhost` (each container has its own network namespace).

**Databases**: rather than a second RDS instance (which would double your free-tier hour usage — see the cost section), both services share **one** RDS instance but use **two separate Postgres databases** (`orderdb`, `inventorydb`) under one master user. This preserves the same "each service owns its own tables" isolation as the separate Oracle users used locally — a database is a completely separate namespace in Postgres, no cross-database queries possible at all.

---

## 3. Provisioning RDS (PostgreSQL, free tier)

1. AWS Console → **RDS** → **Create database** → **Standard create**.
2. Engine: **PostgreSQL**. Templates: **Free tier** (this pre-selects free-tier-safe settings for you).
3. DB instance identifier: `order-management-db`.
4. Master username/password: pick a username, set a strong password — **save it**, it becomes `RDS_USERNAME`/`RDS_PASSWORD` later. Don't put it in git.
5. Instance size: leave whatever the Free tier template selected (`db.t3.micro` or `db.t4g.micro`).
6. Storage: 20 GB, **disable storage autoscaling** (so a traffic spike can't silently grow storage past the free 20GB and start costing money).
7. Connectivity: default VPC. **Public access: No** — this keeps RDS off the public internet entirely; only things inside the VPC (i.e., your EC2 instance) can ever reach it. Create a new VPC security group, e.g. `rds-postgres-sg` (leave it with no inbound rules for now — you'll add one once the EC2 instance exists, in step 4 below).
8. Initial database name: `orderdb` (you'll create the second database, `inventorydb`, after EC2 is up — RDS only lets you name one at creation).
9. Create, and wait (~5-10 min) for status **Available**. Note the **endpoint** hostname shown in the console — this is `RDS_HOST`.

---

## 4. Provisioning EC2 (t3.micro, free tier)

1. AWS Console → **EC2** → **Launch instance**.
2. Name: `order-management-ec2`. AMI: **Amazon Linux 2023** (free tier eligible).
3. Instance type: **t3.micro**.
4. Key pair: create a new one, download the `.pem` file, keep it safe — this is SSH key-pair authentication, a different concept from IAM access keys.
5. Network: default VPC, **auto-assign public IP: enabled**.
6. Security group — create new, e.g. `ec2-app-sg`:
   - **SSH (22)** — source: **My IP** (not `0.0.0.0/0` — this stops the whole internet from even attempting to log in).
   - **Custom TCP 8080** — source: `0.0.0.0/0` (the Gateway is the one thing meant to be public).
   - Do **not** open 8081/8082 to the internet — same principle as the Gateway routing decision earlier: the Gateway is the only public door. If you need to hit them directly for debugging, do it from inside the instance itself, or via an SSH tunnel.
7. Storage: 20-30 GB gp3 (the default 8GB is too tight once you're building 3 Docker images on the instance).
8. **IAM instance profile**: attach the role from step 5 below (create the role first, or come back to this after).
9. Launch.

**Back on RDS**: now that the EC2 instance's security group exists, edit `rds-postgres-sg`'s inbound rules → add rule: type **PostgreSQL** (auto-fills port 5432), source = **the EC2 instance's security group** (search by name — this is the "security group as a source" trick from the concepts section). This is what makes "only the EC2 instance can reach RDS, not the whole internet" actually true.

---

## 5. The IAM role — exactly what it needs, and why

1. IAM → **Roles** → **Create role**.
2. Trusted entity type: **AWS service** → **EC2** (this defines *who* can assume the role — only the EC2 service, on your behalf).
3. Attach permissions: search for and attach the AWS-managed policy **`CloudWatchAgentServerPolicy`**. That's the only policy this role needs.
4. Name it `ec2-app-cloudwatch-role`, create it, then attach it to the EC2 instance (during launch, or afterward via **Actions → Security → Modify IAM role**).

**What that one policy actually grants, and nothing more:**
- `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents` — send application logs to CloudWatch Logs.
- `cloudwatch:PutMetricData` — send custom metrics (e.g. memory usage, which EC2 doesn't track by default — see below).
- A few read-only `ec2:Describe*` calls the agent uses to tag its own metrics with instance info.

Deliberately **not** granted: anything about RDS, launching/stopping other EC2 instances, S3, IAM itself, or billing — this role can do exactly one job.

---

## 6. Getting the app onto EC2, and running it

SSH in: `ssh -i your-key.pem ec2-user@<ec2-public-ip>`

```bash
# Docker
sudo dnf update -y
sudo dnf install -y docker git postgresql15
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user   # log out/in (or `newgrp docker`) for this to take effect

# Docker Compose v2 plugin (not bundled with Amazon Linux's docker package)
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o ~/.docker/cli-plugins/docker-compose
chmod +x ~/.docker/cli-plugins/docker-compose

# Swap file -- a real safety net, not optional here. A t3.micro has 1GB RAM total, and
# this stack runs 4 JVMs (Kafka + 3 Spring Boot apps) on it. Swap turns a would-be
# OOM-killed container into a slower-but-surviving one when a JVM briefly spikes.
sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

Clone the repo and create the **second** database (RDS only let you name one at creation):
```bash
git clone <your-repo-url>
cd Learnings

psql -h <RDS_HOST> -U <RDS_USERNAME> -d orderdb -c "CREATE DATABASE inventorydb;"
```

Create `.env` (this file is git-ignored — never commit it):
```bash
cat > .env <<EOF
RDS_HOST=<your-rds-endpoint>
RDS_USERNAME=<your-master-username>
RDS_PASSWORD=<your-master-password>
EOF
```

Bring everything up:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
First build will be slow (Maven compiling 3 projects on a 1-vCPU box) — give it several minutes. Verify:
```bash
curl http://localhost:8080/products
```
From your own machine: `http://<ec2-public-ip>:8080/products`

**To deploy an update later**: `git pull`, then re-run the same `docker compose ... up -d --build` — it rebuilds only what changed and restarts those containers (not zero-downtime, but fine for a single-instance learning setup).

**When would ECR / CI-CD actually be worth it?**
- **ECR (a container registry)** becomes worth it once you're tired of the slow on-instance Maven build competing with your running app for the box's one vCPU — you'd build images on your laptop or in CI instead, push them to ECR, and have EC2 just `docker pull` and run. Much faster deploys.
- **CI/CD** (e.g. GitHub Actions) becomes worth it once you want every push to `main` to test and deploy automatically, rather than SSH-ing in by hand — valuable once you're iterating often or more than one person touches the code.

Neither is built here, per this phase's scope — both are natural next steps once manual deploys start feeling like friction.

---

## 7. Minimum useful CloudWatch setup

**Already free, zero setup**: EC2's default metrics (CPUUtilization, network, disk, status checks) — visible in the EC2 console's Monitoring tab immediately, every 5 minutes.

**The one gotcha**: EC2's default metrics do **not** include memory usage at all — a well-known AWS surprise, and one that matters a lot here given how tight this instance's RAM is. Getting `mem_used_percent` requires either the CloudWatch Agent (more setup) or just watching it manually via `free -h` over SSH when curious. For a project this size, I'd start with the manual check and only install the full agent if you find yourself wanting a historical graph of it.

**For logs, skip the CloudWatch Agent entirely** — it's more setup than this project needs. Docker has a built-in `awslogs` logging driver; add this to any service in `docker-compose.prod.yml`:
```yaml
    logging:
      driver: awslogs
      options:
        awslogs-region: <your-region>
        awslogs-group: /order-management/order-service
        awslogs-create-group: "true"
```
That's the entire log setup — no agent, no config file, just Docker sending each container's stdout straight to CloudWatch Logs (the IAM role from section 5 already covers the permissions this needs).

**Set log retention explicitly.** The default for a new CloudWatch Logs group is **Never expire** — an easy, silent way to slowly accumulate storage cost. Once a log group exists, go to CloudWatch → Log groups → select it → **Edit retention** → set something like 14 days.

**The single most valuable thing to set up, more than any of the above**: **AWS Budgets** (Billing console → Budgets → Create budget), with an email alert at a low threshold like $5. This is free, takes two minutes, and is the actual safety net against a surprise bill — nothing else here substitutes for it.

---

## 8. Cost breakdown

| Item | Free tier covers | Watch out for |
|---|---|---|
| EC2 t3.micro | 750 hrs/month, 12 months from account creation* | Running 24/7 ≈ 730 hrs/month — right at the edge; a *second* EC2 instance anywhere in the account shares this same combined allowance |
| EBS storage (EC2 disk) | 30 GB/month | Don't over-provision past ~20-30GB |
| RDS db.t3/t4g.micro | 750 hrs/month, 20GB storage, 20GB backups, 12 months* | Same combined-hours caveat as EC2; don't leave old RDS instances running from earlier experiments |
| Data transfer out | 100 GB/month | A learning project won't come close |
| Kafka | $0 — self-hosted on the EC2 instance you already have | N/A — this was the whole point of not using MSK |
| CloudWatch Logs | 5GB ingestion + 5GB storage, **always-free** (not time-limited) | Set retention (see above) — indefinite retention slowly costs more over time |
| CloudWatch metrics/alarms | 10 custom metrics, 10 alarms, always-free | Stick to EC2's free defaults; only add the Agent if you specifically want memory metrics |
| Elastic IP (if you allocate one) | Free **only while attached to a running instance** | An allocated-but-unattached Elastic IP bills hourly — a classic surprise-bill trap. Either don't allocate one (the default public IP is fine and simpler for a learning project) or remember to release it |
| Cognito User Pool (Essentials tier) | **10,000 MAUs/month free, always-free** — not time-limited, not tied to account age ([AWS's own pricing page](https://aws.amazon.com/cognito/pricing/)) | A "MAU" only counts if a user does something (sign-in, token refresh, etc.) that month — a couple of test users will never come close to 10,000 |

*AWS's free-tier terms have shifted for newer accounts — check your own account's **Billing → Free Tier** page to confirm exactly what applies to you before assuming these numbers.

**Biggest realistic risk for this specific project**: leaving the EC2 instance (and RDS) running 24/7 for a full month without noticing, out of the habit of just leaving it be since it's "free." **Stop** (not terminate — stopping preserves your EBS volume and RDS data) both when you're not actively using them. Set the AWS Budget alert regardless — it costs nothing and catches the mistake either way.

---

# Phase 4 — Authentication at the Gateway (AWS Cognito)

Adds real authentication in front of the whole system. Before this phase, anyone who
could reach the EC2 instance's public IP could call every endpoint. Now, every route
requires a valid Cognito-issued JWT, and the restock route additionally requires the
caller's token to carry the `admin` group. Order Service and Inventory Service have
**zero code changes** — they remain completely unaware auth exists at all.

## Concepts

- **User Pool, not Identity Pool.** A User Pool is a user directory + authentication
  service (sign-up/sign-in, password policy, groups, issues JWTs). An Identity Pool
  federates a token into temporary *AWS* credentials, for when a client needs to call
  AWS services (S3, DynamoDB) directly. Nothing here touches AWS resources on a user's
  behalf, so only a User Pool is needed.
- **ID token vs. Access token.** Cognito issues both on login. The ID token describes
  the user (for your own app's use); the **Access token** is what's meant to be sent to
  APIs as `Authorization: Bearer <token>` to prove the caller is authorized. Both carry
  `cognito:groups`, but the access token is the correct one here.
- **What `issuer-uri` does.** Spring Security fetches
  `<issuer-uri>/.well-known/openid-configuration` at startup, follows it to Cognito's
  JWKS (public signing keys) endpoint, and uses those keys to verify every token's
  signature — no key is ever hardcoded in the app.
- **Why Order Service/Inventory Service need no changes.** They trust that the only way
  to reach them is through the Gateway. This is only actually true because their ports
  (8081/8082) stay off the public internet at the security-group level (set up back in
  Phase 3) — without that, "trust the Gateway" would just be security theater, not a
  real boundary.

## Provisioning the User Pool (CLI)

```bash
# User Pool — Essentials tier (default), email as username, a real password policy
aws cognito-idp create-user-pool --pool-name order-management-users --region eu-west-1 \
  --policies '{"PasswordPolicy":{"MinimumLength":8,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":true}}' \
  --auto-verified-attributes email \
  --username-attributes email

# App client — no secret (Postman/curl call Cognito directly, not via a confidential backend),
# USER_PASSWORD_AUTH enabled so InitiateAuth (below) works without a browser redirect
aws cognito-idp create-user-pool-client --user-pool-id <pool-id> \
  --client-name order-management-app-client \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH

# The group the restock route checks for
aws cognito-idp create-group --user-pool-id <pool-id> \
  --group-name admin --description "Users allowed to manage inventory (restock)"
```

## Creating test users and getting a JWT

```bash
# Regular user
aws cognito-idp admin-create-user --user-pool-id <pool-id> \
  --username testuser@example.com \
  --user-attributes Name=email,Value=testuser@example.com Name=email_verified,Value=true \
  --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id <pool-id> \
  --username testuser@example.com --password "TestPass123!" --permanent

# Admin user
aws cognito-idp admin-create-user --user-pool-id <pool-id> \
  --username admin@example.com \
  --user-attributes Name=email,Value=admin@example.com Name=email_verified,Value=true \
  --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id <pool-id> \
  --username admin@example.com --password "AdminPass123!" --permanent
aws cognito-idp admin-add-user-to-group --user-pool-id <pool-id> \
  --username admin@example.com --group-name admin
```

`admin-set-user-password ... --permanent` skips Cognito's normal "force password change
on first login" flow — appropriate for test users created by an admin, not how a real
signup would work (a real user would self-register and verify their own email).

Get a token directly from Cognito's API (no browser/hosted UI needed):
```bash
aws cognito-idp initiate-auth --client-id <client-id> --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=testuser@example.com,PASSWORD="TestPass123!" \
  --query "AuthenticationResult.AccessToken" --output text
```
This returns the **Access token** — copy it, it's what goes in the `Authorization` header.
Tokens expire after 1 hour by default; re-run this command to get a fresh one, or use the
returned `RefreshToken` (valid 30 days) with `--auth-flow REFRESH_TOKEN_AUTH` instead.

## Calling the API with a token

**Postman**: Authorization tab → type **Bearer Token** → paste the access token. Or set
the header manually:

```
GET http://<ec2-ip>:8080/products
Authorization: Bearer eyJraWQiOiJ...
```

**curl**:
```bash
TOKEN=$(aws cognito-idp initiate-auth --client-id <client-id> --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME=testuser@example.com,PASSWORD="TestPass123!" \
  --query "AuthenticationResult.AccessToken" --output text)

curl -H "Authorization: Bearer $TOKEN" http://<ec2-ip>:8080/products
```

**What each case returns:**

| Request | Result |
|---|---|
| No `Authorization` header | `401 Unauthorized` |
| Invalid/expired token | `401 Unauthorized` |
| Valid token (any user), normal route | `200` |
| Valid token, **not** in `admin` group, `POST /stock/{id}/restock` | `403 Forbidden` |
| Valid token, **in** `admin` group, `POST /stock/{id}/restock` | `200` |

## Gateway changes, summarized

- `spring-boot-starter-oauth2-resource-server` added to `api-gateway/pom.xml` only —
  deliberately **not** `oauth2-client`/TokenRelay, since there's no frontend doing a
  browser login flow yet; Postman/curl get tokens directly from Cognito and send them
  straight to the Gateway, the textbook Resource Server pattern.
- `spring.security.oauth2.resourceserver.jwt.issuer-uri` in `application.yml`, pointing
  at the User Pool, read from the `COGNITO_ISSUER_URI` env var in production.
- `SecurityConfig.java` — the entire authorization policy in one place: `POST
  /stock/*/restock` requires `hasAuthority("ROLE_admin")` (mapped from the token's
  `cognito:groups` claim via a custom `JwtAuthenticationConverter`); every other route
  just requires `.authenticated()`.
- The restock route, previously **excluded entirely** from the Gateway's routes (Phase
  3's "hide it" approach), is now **back in the route table**, protected by the group
  check instead — real authorization instead of just obscurity.

## Deploying the env var

Add to the EC2 instance's `.env` (alongside the RDS credentials):
```
COGNITO_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>
```
Then `docker compose -f docker-compose.prod.yml up -d --build api-gateway`.
