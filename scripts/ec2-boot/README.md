# EC2 boot recovery

Runs on the production instance **every time it boots** (started from the CloudSwitch app,
`scripts/start-all.ps1`, or the AWS console) and does what would otherwise need a manual fix:

1. Repoints the DuckDNS hostname at the instance's new public IP (there is no Elastic IP).
2. Repoints `shop-api.nithishnarravula.dev` (a plain A record) at the same new IP, via the
   Porkbun API. This is the address the frontend and every browser actually calls, kept
   separate from DuckDNS on purpose: `shop-api` used to be a CNAME to the DuckDNS hostname,
   which meant resolving it still touched `duckdns.org` partway through the CNAME chain --
   and a corporate network's DNS filter blocked that, since dynamic-DNS domains are a common
   blocklist category. DuckDNS itself is unaffected and still used for SSH/Jenkins from this
   project's own PC.
3. Waits until order-service and inventory-service answer.
4. Restarts `api-gateway`, which caches the other services' internal Docker IPs and goes stale
   when they restart during a cold EC2 + RDS start.

Results are logged to `/home/ubuntu/fix-containers-on-boot.log`.

## Files

| File | Where it runs from |
|---|---|
| `fix-containers-on-boot.sh` | Straight from the repo checkout at `/home/ubuntu/order-management/scripts/ec2-boot/`, so a `git pull` is all it takes to update it |
| `fix-containers-on-boot.service` | Copied to `/etc/systemd/system/` (systemd does not read from the repo) |
| `/etc/order-management/duckdns.env` | **Not in the repo.** Holds the DuckDNS token; exists only on the server |
| `/etc/order-management/porkbun.env` | **Not in the repo.** Holds the Porkbun API key/secret; exists only on the server |

## Install / reinstall

```bash
# 1. The secrets (once).
sudo mkdir -p /etc/order-management

# The token is `$DuckDnsToken` in scripts/config.ps1 on your machine.
echo 'DUCKDNS_TOKEN=<your token>' | sudo tee /etc/order-management/duckdns.env >/dev/null
sudo chmod 600 /etc/order-management/duckdns.env

# The API key/secret are from porkbun.com/account/api. That account-level key still needs API
# access turned on for this specific domain too (Domain Management -> nithishnarravula.dev ->
# Details -> API Access), separately from creating the key itself.
cat <<'EOF' | sudo tee /etc/order-management/porkbun.env >/dev/null
PORKBUN_API_KEY=<your api key>
PORKBUN_API_SECRET=<your secret key>
EOF
sudo chmod 600 /etc/order-management/porkbun.env

# 2. The service
cd /home/ubuntu/order-management && git pull
sudo cp scripts/ec2-boot/fix-containers-on-boot.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable fix-containers-on-boot.service
```

The `shop-api` A record itself is a one-time setup done through the Porkbun API or dashboard,
not by this script -- the script only ever edits an *existing* record's content. If it's ever
deleted, recreate it (type A, name `shop-api`, any placeholder IP) before the next boot, or the
Porkbun update step will log an error and do nothing.

## Check it worked

```bash
sudo systemctl restart fix-containers-on-boot.service   # re-runs it now (restarts api-gateway briefly)
tail -8 /home/ubuntu/fix-containers-on-boot.log
```

You want to see `DuckDNS update -> <ip>, result: OK`, `Porkbun A record update (shop-api...) ->
<ip>, result: "status":"SUCCESS"`, both services `healthy`, then `api-gateway restarted`. Note
that `systemctl start` on an already-active oneshot does nothing -- use `restart` to re-trigger
it.

## Rotating a secret

**DuckDNS:** generate a new token on duckdns.org, replace the value in
`/etc/order-management/duckdns.env` and in `scripts/config.ps1`, then
`sudo systemctl restart fix-containers-on-boot.service`.

**Porkbun:** delete the old key on porkbun.com/account/api, create a new one (remembering the
per-domain API Access toggle), replace both values in `/etc/order-management/porkbun.env`, then
restart the service the same way.
