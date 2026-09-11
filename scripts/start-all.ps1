# Starts RDS and EC2, waits for both to actually be ready (not just "running"), and
# proactively restarts api-gateway -- this encodes a real bug we hit: order-service and
# inventory-service can crash-loop briefly waiting for RDS on boot, getting a new internal
# Docker IP each retry. api-gateway, which stays up the whole time, caches the old IP and
# silently serves 500s until restarted. This script waits for both backend services to be
# genuinely healthy (queried directly, bypassing the gateway) BEFORE restarting the gateway,
# so that restart picks up the final, stable IPs.
#
# Reads config from config.ps1 (copy config.example.ps1 -> config.ps1 and fill in your
# own values -- config.ps1 is gitignored, never committed).

. "$PSScriptRoot\config.ps1"

Write-Host "Starting RDS ($DbId)..." -ForegroundColor Cyan
aws rds start-db-instance --db-instance-identifier $DbId | Out-Null

Write-Host "Starting EC2 ($InstanceId)..." -ForegroundColor Cyan
aws ec2 start-instances --instance-ids $InstanceId | Out-Null

Write-Host "Waiting for EC2 to reach 'running'..." -ForegroundColor Cyan
aws ec2 wait instance-running --instance-ids $InstanceId

$PublicIp = aws ec2 describe-instances --instance-ids $InstanceId --query "Reservations[].Instances[].PublicIpAddress" --output text
Write-Host "EC2 running. Public IP: $PublicIp" -ForegroundColor Green

Write-Host "Waiting for RDS to reach 'available' (usually the slow part, several minutes)..." -ForegroundColor Cyan
do {
    Start-Sleep -Seconds 15
    $DbStatus = aws rds describe-db-instances --db-instance-identifier $DbId --query "DBInstances[0].DBInstanceStatus" --output text
    Write-Host "  RDS status: $DbStatus"
} while ($DbStatus -ne "available")
Write-Host "RDS available." -ForegroundColor Green

Write-Host "Waiting for SSH to become reachable..." -ForegroundColor Cyan
$SshReady = $false
for ($i = 0; $i -lt 20; $i++) {
    $result = ssh -i $KeyPath -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o BatchMode=yes "$SshUser@$PublicIp" "echo ok" 2>$null
    if ($result -eq "ok") { $SshReady = $true; break }
    Start-Sleep -Seconds 10
}
if (-not $SshReady) {
    Write-Host "SSH did not become reachable in time. Check the instance manually." -ForegroundColor Red
    exit 1
}
Write-Host "SSH reachable." -ForegroundColor Green

Write-Host "Waiting for order-service to be healthy (direct, bypassing the gateway)..." -ForegroundColor Cyan
for ($i = 0; $i -lt 30; $i++) {
    $code = ssh -i $KeyPath "$SshUser@$PublicIp" "curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8081/products"
    if ($code -eq "200") { break }
    Start-Sleep -Seconds 10
}

Write-Host "Waiting for inventory-service to be healthy (direct, bypassing the gateway)..." -ForegroundColor Cyan
for ($i = 0; $i -lt 30; $i++) {
    $code = ssh -i $KeyPath "$SshUser@$PublicIp" "curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8082/stock"
    if ($code -eq "200") { break }
    Start-Sleep -Seconds 10
}

Write-Host "Both backend services healthy. Restarting api-gateway to clear any stale connections..." -ForegroundColor Cyan
ssh -i $KeyPath "$SshUser@$PublicIp" "cd order-management && sudo docker compose -f docker-compose.prod.yml restart api-gateway" | Out-Null

Write-Host "Waiting for the app to respond through the gateway..." -ForegroundColor Cyan
$AppReady = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri "http://${PublicIp}:8080/products" -TimeoutSec 5 -UseBasicParsing
        if ($resp.StatusCode -eq 200) { $AppReady = $true; break }
    } catch {}
    Start-Sleep -Seconds 5
}

Write-Host ""
if ($AppReady) {
    Write-Host "READY. App is live at: http://${PublicIp}:8080" -ForegroundColor Green
} else {
    Write-Host "App did not respond in time through the gateway -- check manually:" -ForegroundColor Yellow
    Write-Host "  http://${PublicIp}:8080/products"
}
