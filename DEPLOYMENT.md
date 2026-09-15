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
| Kafka/Redpanda | $0 — self-hosted on the EC2 instance you already have | N/A — this was the whole point of not using MSK |
| **t3.small (if resized, see Phase 7)** | **Not free-tier eligible at any account age** | ~$0.0208/hour in eu-west-1 (~$15/month at 24/7) — a real, ongoing cost, only taken on after directly proving t3.micro's 1GB RAM was causing genuine multi-second stalls |
| CloudWatch Logs | 5GB ingestion + 5GB storage, **always-free** (not time-limited) | Set retention (see above) — indefinite retention slowly costs more over time |
| CloudWatch metrics/alarms | 10 custom metrics, 10 alarms, always-free | Stick to EC2's free defaults; only add the Agent if you specifically want memory metrics |
| Elastic IP (if you allocate one) | Free **only while attached to a running instance** | An allocated-but-unattached Elastic IP bills hourly — a classic surprise-bill trap. Either don't allocate one (the default public IP is fine and simpler for a learning project) or remember to release it |
| Cognito User Pool (Essentials tier) | **10,000 MAUs/month free, always-free** — not time-limited, not tied to account age ([AWS's own pricing page](https://aws.amazon.com/cognito/pricing/)) | A "MAU" only counts if a user does something (sign-in, token refresh, etc.) that month — a couple of test users will never come close to 10,000 |

*AWS's free-tier terms have shifted for newer accounts — check your own account's **Billing → Free Tier** page to confirm exactly what applies to you before assuming these numbers. Specifically: accounts created after AWS's mid-2024 policy change get a **fixed signup credit (commonly $200, valid 6 months)** instead of 12 months of always-free hours on services like EC2/RDS — usage still shows as "free" in the sense that a credit automatically offsets it (confirmed on this project's own account via `aws ce get-cost-and-usage --group-by Type=DIMENSION,Key=RECORD_TYPE`, which shows a `Credit` line exactly offsetting a `Usage` line each month), but it's drawing down a finite balance, not a renewing monthly allowance.

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
# USER_PASSWORD_AUTH enabled so InitiateAuth (below) works without a browser redirect.
# --prevent-user-existence-errors ENABLED (added in Phase 8, originally missing from this
# command) makes Cognito respond with the same NotAuthorizedException for "wrong password"
# and "no such email" alike, instead of a distinct UserNotFoundException for the latter --
# without it, AuthController's login error mapping (which only maps NotAuthorizedException
# to 401) sends a nonexistent email down its generic 400 path instead. It also happens to
# be the anti-enumeration hardening AWS itself recommends: a caller probing logins can no
# longer tell which emails are actually registered from the response alone.
aws cognito-idp create-user-pool-client --user-pool-id <pool-id> \
  --client-name order-management-app-client \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --prevent-user-existence-errors ENABLED

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

---

# Phase 5 — Frontend hosting (S3 + CloudFront)

The React app (`frontend/`) is a static build (Vite outputs plain HTML/CSS/JS — no
server-side rendering), so it doesn't need EC2 or a container. It's hosted on S3 +
CloudFront instead: cheaper than another EC2 instance, and gets HTTPS + a CDN for free.

## Why S3 + CloudFront over EC2

- **Cost**: S3 storage for a ~270KB build is fractions of a cent; CloudFront's free
  tier (1TB/month data transfer out, 10M requests) comfortably covers a learning
  project. A second EC2 instance would eat further into the same combined free-tier
  hours budget the backend instance already uses (see the Phase 3 cost breakdown).
- **HTTPS out of the box**: CloudFront gives you a `*.cloudfront.net` HTTPS endpoint
  automatically. Getting HTTPS onto a bare EC2 instance means running your own TLS
  terminator (Caddy/nginx) and managing certificates yourself.
- **No server to keep patched or restart**: it's just files behind a CDN — nothing to
  crash, no JVM/Node process to babysit.

## How it's set up

- **S3 bucket** (`order-management-frontend-<account-id>`) — all public access
  blocked. It is *not* a public website bucket; only CloudFront can read from it.
- **CloudFront Origin Access Control (OAC)** — the modern replacement for the older
  "Origin Access Identity." Lets CloudFront authenticate to S3 with its own identity,
  so the bucket can stay fully private.
- **Bucket policy** — grants `s3:GetObject` to the `cloudfront.amazonaws.com` service
  principal, scoped with a `AWS:SourceArn` condition to this specific distribution's
  ARN (not "any CloudFront distribution in the account").
- **CustomErrorResponses mapping 403 and 404 → `/index.html` (200)** — the key bit of
  config that makes client-side routing work. React Router handles a URL like
  `/orders/5` entirely in the browser; there's no real `orders/5` object in S3, so
  without this rewrite, refreshing that page (or opening it directly) would 404. With
  it, CloudFront serves `index.html` instead, letting React Router take over and render
  the right page.

## Redeploying after the backend IP changes

`frontend/.env`'s `VITE_GATEWAY_URL` is baked into the JS bundle at **build time**
(Vite inlines `import.meta.env.*` — there's no runtime config file to edit after the
fact). Since the EC2 instance doesn't have an Elastic IP (see the cost breakdown above —
an idle Elastic IP bills hourly), every stop/start cycle gives it a new public IP, which
means the frontend needs a full rebuild + republish, not just a config tweak.

`scripts/deploy-frontend.ps1` automates this: looks up the current EC2 IP, rewrites
`.env`, runs `npm run build`, syncs `dist/` to S3, and creates a CloudFront invalidation
(without this last step, CloudFront would keep serving the previous cached build for up
to a day). Run it after `start-all.ps1` any time the EC2 instance has been restarted.

## Known limitation: mixed content

The frontend is served over HTTPS (via CloudFront) but talks to the backend Gateway
over plain HTTP (`http://<ec2-ip>:8080`) — there's no TLS on the Gateway yet. Most
browsers block this as mixed content on an HTTPS page. Fixing it properly means adding
HTTPS to the Gateway itself (a TLS terminator in front of it, or a load balancer) — see
Phase 6 below, where this gets fixed.

---

# Phase 6 — HTTPS for the Gateway (free domain + Let's Encrypt)

Fixes the mixed-content limitation from Phase 5: the Gateway now has a real HTTPS
endpoint at `https://<your-subdomain>.duckdns.org`, backed by a genuine, browser-trusted
Let's Encrypt certificate — at zero cost.

## Why not an Elastic IP + a paid domain?

Two ongoing costs stack up in the "obvious" approach: an Elastic IP bills hourly once
it's not attached to a running instance (see the Phase 3 cost breakdown — this project
deliberately avoids allocating one), and Let's Encrypt won't issue a certificate for a
bare IP anyway — it needs a real domain name to prove ownership of. **DuckDNS** solves
both: it's a free dynamic-DNS service giving a stable `*.duckdns.org` hostname that gets
re-pointed at the instance's current (changing) public IP via a simple HTTP call — no
domain purchase, no Elastic IP.

## How it's wired up

- **`scripts/start-all.ps1`** now calls DuckDNS's update API (`$DuckDnsDomain`,
  `$DuckDnsToken` — read from `config.ps1`, gitignored, since the token is a real
  credential) with the instance's freshly-fetched public IP, every time the instance
  starts. So the domain always points at wherever the instance currently is, without
  anything needing to run on the EC2 box itself.
- **Let's Encrypt cert + reverse proxy**: this EC2 instance already runs `nginx` +
  `certbot` for an unrelated personal site sharing the box — rather than adding a second
  reverse proxy fighting over ports 80/443 (a container like Caddy would conflict with
  that existing nginx), the Gateway got a **second nginx server block** for its own
  domain, proxying to `api-gateway` on `localhost:8080`, secured with its own independent
  cert via `sudo certbot --nginx -d <your-subdomain>.duckdns.org --redirect`. Certbot's
  own systemd timer (already installed for the other site) handles renewal for both
  certs going forward — nothing extra to maintain.
  ```nginx
  # /etc/nginx/sites-available/<your-subdomain>.duckdns.org (before certbot edits it in
  # a second pass to add the SSL server block + redirect)
  server {
      listen 80;
      listen [::]:80;
      server_name <your-subdomain>.duckdns.org;

      location / {
          proxy_pass http://127.0.0.1:8080;
          proxy_http_version 1.1;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
      }
  }
  ```
  (If this were the only site on the box, a container-based reverse proxy like Caddy —
  which auto-manages its own Let's Encrypt cert with zero nginx/certbot setup — would
  actually be the simpler choice. The nginx route here is specifically because port
  80/443 were already spoken for.)
- **Security group**: port 80 opened (`0.0.0.0/0`) in addition to the existing 443 — the
  ACME HTTP-01 challenge needs it briefly during cert issuance/renewal, and nginx also
  uses it to redirect plain `http://` requests to `https://`.
- **`frontend/.env`**: `VITE_GATEWAY_URL` now points at `https://<your-subdomain>.duckdns.org`
  instead of `http://<ec2-ip>:8080`. Since this is a stable domain instead of an IP that
  changes on every restart, `scripts/deploy-frontend.ps1` no longer needs to rewrite it —
  that script now only rebuilds and republishes when the frontend's own code changes.

---

# Phase 7 — Resizing to t3.small and replacing Kafka with Redpanda

Two changes made together, after directly proving (not guessing) that the t3.micro's 1GB
RAM was causing real, multi-second request stalls.

## How this was actually diagnosed

A live request occasionally took **10+ seconds** to respond (`POST /orders`, `GET /stock`),
with no obvious cause at the application level. Rather than assume a cause, each step was
verified against real evidence before acting on it:

1. **Ruled out RDS/network**: the health check's own DB connectivity check kept succeeding
   throughout, so the database itself wasn't the bottleneck.
2. **Ruled out disk space**: `df -h` showed 6.9GB free, nowhere close to a problem.
3. **Found the real signal by checking something unrelated on purpose**: Kafka's consumer
   connections (nothing to do with the slow endpoint) were *also* dropping at the same
   moment — evidence pointing at something systemic, not a bug in one specific code path.
4. **Confirmed via `free -h`**: heavy swap usage (995Mi of 1Gi in use).
5. **Got direct proof, not just correlation**: Kafka is the one service that already had
   JVM GC logging enabled. Its actual GC log showed the smoking gun --
   `Pause Young (Allocation Failure)` events up to **4952ms long** -- genuine, multi-second,
   whole-JVM freezes caused by the heap being too small for the box's available memory.

## What changed

**EC2 resized from t3.micro (1GB RAM) to t3.small (2GB)** -- requires stopping the instance
first (`aws ec2 stop-instances`), changing its type
(`aws ec2 modify-instance-attribute --instance-type t3.small`), then starting it again; it
gets a new public IP each time, so `scripts/start-all.ps1`'s DuckDNS update step matters
even more here.

**Kafka replaced with Redpanda** in `docker-compose.prod.yml`. Redpanda speaks the identical
Kafka wire protocol -- `KafkaTemplate`, `@KafkaListener`, topic names, consumer groups, the
outbox pattern, the idempotent-consumer pattern -- none of the Java code changed at all,
only the broker container. The reason for the swap specifically: Redpanda is written in
C++, not Java, so it has no JVM and no garbage collector -- the exact mechanism the GC log
proved was causing multi-second pauses structurally cannot happen in it, regardless of how
tight memory gets.

## Measured results (before -> after, on the live system)

| Metric | Before (t3.micro, Kafka) | After (t3.small, Redpanda) |
|---|---|---|
| `POST /orders` worst case | 10.89s | under 1s |
| Outbox-to-broker latency | 1.9s-4.8s | 39-629ms |
| Broker memory | ~332MB | ~63MB |
| Broker process count | 97 | 3 |
| Swap in use | 995Mi/1Gi (nearly full) | ~88Mi/1Gi |

Full event-driven pipeline re-verified end-to-end against Redpanda: order creation ->
confirmation, restock, and cancellation-with-stock-release all working correctly.

## Cost impact

t3.small costs real money continuously (no free-tier hours the way t3.micro nominally had) --
roughly $0.0208/hour in eu-west-1, so ~$15/month at 24/7, less with the existing
stop/start habit. See the cost breakdown above for why this account's actual free-tier
situation (a fixed signup credit, not renewing monthly hours) makes this an easy trade
against a $150+ balance rather than a new recurring expense from zero.

# Phase 8 — BFF for the refresh token

## The problem

Cognito's login response (`InitiateAuth`) always includes three things: an Access token,
an ID token, and a Refresh token. Since Phase 4, the frontend only ever kept the Access
token, in a plain React state variable (see `AuthContext.jsx`'s original comment) --
deliberately not `localStorage`, to keep it out of reach of an XSS payload. The Refresh
token was simply thrown away every time: there was nowhere safe to put it in a pure
static frontend (an S3/CloudFront bucket, no server of its own at all). The accepted
consequence was that reloading the page always logged you out -- the Access token dies
with the React state that held it, and there was no Refresh token left to silently get a
new one.

## The fix: a small Backend-For-Frontend, folded into the existing Gateway

A BFF is just "a server that sits between the browser and the real backend/identity
provider, specifically so it can hold something the browser itself isn't safe to hold."
Here, that something is the Refresh token. Rather than stand up a new service (another
JVM, more memory on an already resource-watched t3.small), this was added directly to
api-gateway, which was already the frontend's one and only entry point:

- **`AuthController`** (new) -- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`.
  These are unauthenticated by nature (login has no token yet; refresh only ever has the
  cookie; logout must work even against an already-expired Access token), so
  `SecurityConfig` permits them explicitly, the same way it already did for
  `/actuator/health`.
- **`CognitoAuthClient`** (new) -- a server-side version of the same plain Cognito JSON
  API call the frontend used to make directly (`InitiateAuth` for both
  `USER_PASSWORD_AUTH` and, new, `REFRESH_TOKEN_AUTH`; `GlobalSignOut` on logout). Uses
  `WebClient`, not a new dependency, since Spring Cloud Gateway already runs on the
  reactive WebFlux stack.
- On login, the Gateway sets the Refresh token as an **HttpOnly, Secure, SameSite=None**
  cookie (`refresh_token`, scoped to `Path=/auth`) and returns only the Access token in
  the JSON body -- the Access token is still handled exactly as before (kept in React
  state, attached as a Bearer header). `SameSite=None` (not the default `Lax`) is
  required because the frontend (CloudFront) and the Gateway (DuckDNS) are genuinely
  different sites from the browser's point of view.
- `POST /auth/refresh` reads that cookie (never exposed to JavaScript -- the browser
  attaches it automatically) and exchanges it for a new Access token via
  `REFRESH_TOKEN_AUTH`. The frontend calls this in two places: once on every app
  load/reload (`AuthContext`'s bootstrap effect -- this is what actually fixes "reload
  logs you out"), and reactively from `useApiFetch` whenever the Gateway returns 401,
  before falling back to a real logout.
- `POST /auth/logout` clears the cookie and, best-effort, calls Cognito's `GlobalSignOut`
  so a leaked/stolen cookie can't be replayed after an explicit logout.

## Side effect: CORS could no longer be `allowedOrigins("*")`

A credentialed (cookie-carrying) cross-site request is exactly what CORS's
`allowCredentials(true)` + a wildcard origin combination is designed to forbid (Spring
Security refuses to even start with that combination). `SecurityConfig`'s
`corsConfigurationSource()` now lists the frontend's exact origins
(`ALLOWED_ORIGINS` env var: the CloudFront domain in production, plus
`http://localhost:5173` for local dev) instead.

## What didn't change

The Access token is still a plain Bearer header on every proxied API call
(`/orders`, `/stock`, etc.) -- this BFF only ever touches the Refresh token. `signUp`/
`confirmSignUp` still call Cognito directly from the browser (`cognito.js`) -- neither
involves a token, so there was never a reason to route them through the Gateway.
