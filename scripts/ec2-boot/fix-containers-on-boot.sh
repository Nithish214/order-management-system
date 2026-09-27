#!/bin/bash
# Runs once per boot (see fix-containers-on-boot.service). Does four things, in this order:
#
#   1. Points the DuckDNS hostname at this instance's NEW public IP. There is no Elastic IP
#      (see start-all.ps1's comment on the cost tradeoff), so the public IP changes on every
#      stop/start -- and this runs no matter WHAT started the instance: the CloudSwitch app,
#      start-all.ps1, or the AWS console.
#   2. Points shop-api.nithishnarravula.dev (a plain A record, via the Porkbun API) at the SAME
#      new IP. This one is for anything the *browser* has to resolve. It used to be a CNAME to
#      the DuckDNS hostname above, piggybacking on step 1 -- which worked, until a corporate
#      network's DNS filter blocked it: resolving shop-api still meant asking for duckdns.org
#      partway through the CNAME chain, and dynamic-DNS domains are a common blocklist category
#      (real domains change IP by editing DNS, not by polling a public API -- the pattern itself
#      reads as suspicious to a filter, DuckDNS itself notwithstanding). A plain A record on our
#      own domain has no such tell. DuckDNS still exists for SSH/Jenkins from this project's own
#      PC, which isn't behind that filter.
#   3. Waits for order-service and inventory-service to actually answer.
#   4. Restarts api-gateway. It caches the other services' internal Docker IPs, and those
#      services can crash-loop (getting a new IP on each retry) while waiting for RDS on a cold
#      EC2+RDS boot -- the same problem start-all.ps1 works around from the outside, done here on
#      the instance itself.
#
# Secrets: neither the DuckDNS token nor the Porkbun API key are in this file. Both are read from
# the environment, which systemd fills from /etc/order-management/duckdns.env and
# /etc/order-management/porkbun.env (both root-only) -- see README.md in this directory.

LOG=/home/ubuntu/fix-containers-on-boot.log
DUCKDNS_DOMAIN="${DUCKDNS_DOMAIN:-nithish-ordermgmt}"
PORKBUN_DOMAIN="${PORKBUN_DOMAIN:-nithishnarravula.dev}"
PORKBUN_SUBDOMAIN="${PORKBUN_SUBDOMAIN:-shop-api}"

echo "$(date -Is): fix-containers-on-boot starting" >> "$LOG"

cd /home/ubuntu/order-management || { echo "$(date -Is): order-management dir not found" >> "$LOG"; exit 1; }

wait_for() {
  local url=$1
  local name=$2
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      echo "$(date -Is): $name healthy after $i attempt(s)" >> "$LOG"
      return 0
    fi
    sleep 10
  done
  echo "$(date -Is): $name never became healthy after 60 attempts" >> "$LOG"
  return 1
}

# Step 1 -- DNS first, before the health waits, so the domain already resolves correctly for the
# rest of this script and for anyone reaching the site from outside. A missing token skips only
# this step: container recovery below matters more than DNS and must still happen.
if [ -z "${DUCKDNS_TOKEN:-}" ]; then
  echo "$(date -Is): DUCKDNS_TOKEN is not set (expected from /etc/order-management/duckdns.env) -- skipping the DNS update" >> "$LOG"
else
  # IMDSv2, not the older unauthenticated IMDSv1: this instance enforces it, so a plain GET on the
  # metadata endpoint silently returns nothing without this token step first.
  IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
  # The URL (which contains the token) goes to curl on STDIN via -K, not as a command-line
  # argument -- arguments are visible to any local user in `ps` while curl runs.
  DUCKDNS_RESULT=$(printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip=%s"\n' \
      "$DUCKDNS_DOMAIN" "$DUCKDNS_TOKEN" "$PUBLIC_IP" | curl -s -K -)
  echo "$(date -Is): DuckDNS update -> $PUBLIC_IP, result: $DUCKDNS_RESULT" >> "$LOG"

  # Step 2 -- same new IP, now for shop-api's own A record. editByNameType, not create: the
  # record already exists (set up once by hand); this only ever changes its content. Requires
  # PUBLIC_IP from the DuckDNS block above, so this has to stay inside this same `if`. The API
  # key/secret go over stdin as the POST body (--data @-), never as a curl argument, for the same
  # reason the DuckDNS URL above goes in via -K -- rather than as an argument: command-line
  # arguments of a running process are visible to any local user via `ps`.
  if [ -z "${PORKBUN_API_KEY:-}" ] || [ -z "${PORKBUN_API_SECRET:-}" ]; then
    echo "$(date -Is): PORKBUN_API_KEY/PORKBUN_API_SECRET not set (expected from /etc/order-management/porkbun.env) -- skipping the shop-api DNS update" >> "$LOG"
  else
    PORKBUN_RESULT=$(printf '{"apikey":"%s","secretapikey":"%s","content":"%s","ttl":"300"}' \
        "$PORKBUN_API_KEY" "$PORKBUN_API_SECRET" "$PUBLIC_IP" | \
        curl -s -X POST "https://api.porkbun.com/api/json/v3/dns/editByNameType/${PORKBUN_DOMAIN}/A/${PORKBUN_SUBDOMAIN}" \
          -H "Content-Type: application/json" --data @- | \
        grep -o '"status":"[A-Z]*"')
    echo "$(date -Is): Porkbun A record update ($PORKBUN_SUBDOMAIN.$PORKBUN_DOMAIN -> $PUBLIC_IP), result: $PORKBUN_RESULT" >> "$LOG"
  fi
fi

# Steps 3 and 4
wait_for "http://localhost:8081/products" "order-service"
wait_for "http://localhost:8082/stock" "inventory-service"

sudo docker compose -f docker-compose.prod.yml restart api-gateway >> "$LOG" 2>&1
echo "$(date -Is): api-gateway restarted" >> "$LOG"
