# Local Jenkins CI/CD

Runs Jenkins locally in Docker, building every deployable artifact (4 backend Docker
images, the frontend bundle) on this machine rather than on the app's own EC2 instance
(already known to be memory-tight even at idle -- see the root README). EC2 only ever
receives finished artifacts and runs them.

Deploying is a manual, explicit step — every push builds and packages automatically, but
nothing reaches EC2 or S3 without you clicking "Deploy" in the Jenkins UI. See the
`Jenkinsfile` at the repo root for the actual pipeline; this file is only the one-time
setup that has to happen by hand, since none of it can be scripted from outside a browser.

## 1. Start Jenkins

```bash
cd jenkins
docker compose up -d --build
```

Builds the custom image (`jenkins/Dockerfile` — adds the Docker CLI and an SSH client to
the stock Jenkins image) and starts it on **http://localhost:8090** (not 8080 — that's
already `api-gateway`'s port during local dev).

## 2. Unlock Jenkins

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Paste that into the setup wizard at http://localhost:8090.

## 3. Install plugins

Choose **"Install suggested plugins"**, then go to **Manage Jenkins → Plugins →
Available** and additionally install:

- **SSH Agent** — the `Jenkinsfile`'s deploy stage uses `sshagent(...)`, which this
  plugin provides; it's not always part of the suggested set.

Create your admin user when prompted.

## 4. Add credentials

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**, twice:

| Kind | ID (must match exactly) | Username | Secret |
|---|---|---|---|
| SSH Username with private key | `ec2-ssh-key` | `ubuntu` | Paste the full contents of your EC2 `.pem` file |
| Username with password | `aws-frontend-deploy` | The `jenkins-frontend-deploy` IAM user's Access Key ID | Its Secret Access Key |

The IAM user for the second one was created specifically for this pipeline, scoped to
exactly two things: read/write on the frontend S3 bucket, and `CreateInvalidation` on
its CloudFront distribution — nothing else. If you don't have its keys handy, generate a
fresh pair:

```bash
aws iam create-access-key --user-name jenkins-frontend-deploy
```

## 5. Create the pipeline job

**New Item → name it (e.g. `order-management-deploy`) → Pipeline → OK**, then under
**Pipeline**:

- Definition: **Pipeline script from SCM**
- SCM: **Git**
- Repository URL: `https://github.com/Nithish214/order-management-system.git` (public —
  no credentials needed to check it out)
- Branch: `*/main`
- Script Path: `Jenkinsfile`

Optional, under **Build Triggers**: check **"Poll SCM"** with a schedule like `H/5 * * *
*` (every ~5 minutes). This machine has no public IP for GitHub to webhook directly, so
polling is the practical alternative to "build the instant something's pushed."

## 6. Run it

**Build Now**. Watch it move through checkout → change detection → backend image builds
→ frontend lint/build, then **pause** at "Approve deploy" — nothing touches EC2 or S3
until you click **Deploy** in that stage.

## Stopping Jenkins

```bash
cd jenkins
docker compose stop     # keeps the container and its data, just not running
# or
docker compose down     # removes the container too; the jenkins_home VOLUME (jobs,
                         # credentials, plugins) survives either way unless you also
                         # pass -v
```
