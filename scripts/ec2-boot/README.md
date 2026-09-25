# EC2 boot recovery

Runs on the production instance **every time it boots** (started from the CloudSwitch app,
`scripts/start-all.ps1`, or the AWS console) and does what would otherwise need a manual fix:

1. Repoints the DuckDNS hostname at the instance's new public IP (there is no Elastic IP).
2. Waits until order-service and inventory-service answer.
3. Restarts `api-gateway`, which caches the other services' internal Docker IPs and goes stale
   when they restart during a cold EC2 + RDS start.

Results are logged to `/home/ubuntu/fix-containers-on-boot.log`.

## Files

| File | Where it runs from |
|---|---|
| `fix-containers-on-boot.sh` | Straight from the repo checkout at `/home/ubuntu/order-management/scripts/ec2-boot/`, so a `git pull` is all it takes to update it |
| `fix-containers-on-boot.service` | Copied to `/etc/systemd/system/` (systemd does not read from the repo) |
| `/etc/order-management/duckdns.env` | **Not in the repo.** Holds the DuckDNS token; exists only on the server |

## Install / reinstall

```bash
# 1. The secret (once). The token is `$DuckDnsToken` in scripts/config.ps1 on your machine.
sudo mkdir -p /etc/order-management
echo 'DUCKDNS_TOKEN=<your token>' | sudo tee /etc/order-management/duckdns.env >/dev/null
sudo chmod 600 /etc/order-management/duckdns.env

# 2. The service
cd /home/ubuntu/order-management && git pull
sudo cp scripts/ec2-boot/fix-containers-on-boot.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable fix-containers-on-boot.service
```

## Check it worked

```bash
sudo systemctl restart fix-containers-on-boot.service   # re-runs it now (restarts api-gateway briefly)
tail -6 /home/ubuntu/fix-containers-on-boot.log
```

You want to see `DuckDNS update -> <ip>, result: OK`, both services `healthy`, then
`api-gateway restarted`. Note that `systemctl start` on an already-active oneshot does nothing --
use `restart` to re-trigger it.

## Rotating the token

Generate a new one on duckdns.org, replace the value in `/etc/order-management/duckdns.env` and in
`scripts/config.ps1`, then `sudo systemctl restart fix-containers-on-boot.service`.
