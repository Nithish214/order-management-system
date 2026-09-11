# Rebuilds and republishes the React frontend after the EC2 box's public IP has
# changed (every stop/start cycle gives it a new one, since we don't use an Elastic IP
# to avoid the small standing cost of one sitting unattached). Frontend/.env bakes the
# gateway URL in at BUILD time (Vite inlines import.meta.env.* into the JS bundle), so
# a stale .env means a stale build -- there's no way to fix this by just re-uploading
# the old dist/ folder, it has to be rebuilt.
#
# Steps: look up the current EC2 IP -> rewrite frontend/.env -> npm run build ->
# sync dist/ to S3 -> invalidate the CloudFront cache (CloudFront otherwise keeps
# serving the old cached index.html/JS for up to a day).
#
# Reads config from config.ps1 (copy config.example.ps1 -> config.ps1 and fill in your
# own values -- config.ps1 is gitignored, never committed).

. "$PSScriptRoot\config.ps1"

$FrontendDir = Join-Path $PSScriptRoot "..\frontend"
$EnvPath = Join-Path $FrontendDir ".env"

Write-Host "Looking up current EC2 public IP..." -ForegroundColor Cyan
$PublicIp = aws ec2 describe-instances --instance-ids $InstanceId --query "Reservations[].Instances[].PublicIpAddress" --output text
if (-not $PublicIp -or $PublicIp -eq "None") {
    Write-Host "Instance has no public IP right now -- is it running? (see start-all.ps1)" -ForegroundColor Red
    exit 1
}
Write-Host "EC2 public IP: $PublicIp" -ForegroundColor Green

# Preserve every other line in .env (Cognito settings etc.) and only touch the gateway URL,
# rather than overwriting the whole file -- keeps this script safe to run even if someone
# has added extra VITE_ vars locally.
Write-Host "Updating VITE_GATEWAY_URL in frontend/.env..." -ForegroundColor Cyan
$NewGatewayLine = "VITE_GATEWAY_URL=http://${PublicIp}:8080"
if (Test-Path $EnvPath) {
    $EnvLines = Get-Content $EnvPath
    $Found = $false
    $EnvLines = $EnvLines | ForEach-Object {
        if ($_ -match '^VITE_GATEWAY_URL=') { $Found = $true; $NewGatewayLine } else { $_ }
    }
    if (-not $Found) { $EnvLines += $NewGatewayLine }
    Set-Content -Path $EnvPath -Value $EnvLines -Encoding utf8
} else {
    Write-Host ".env not found at $EnvPath -- copy frontend/.env.example first." -ForegroundColor Red
    exit 1
}
Write-Host "frontend/.env now points at http://${PublicIp}:8080" -ForegroundColor Green

Write-Host "Building frontend (npm run build)..." -ForegroundColor Cyan
Push-Location $FrontendDir
npm run build
$BuildExitCode = $LASTEXITCODE
Pop-Location
if ($BuildExitCode -ne 0) {
    Write-Host "Build failed -- aborting before touching S3/CloudFront." -ForegroundColor Red
    exit 1
}

Write-Host "Syncing dist/ to s3://$FrontendBucket ..." -ForegroundColor Cyan
aws s3 sync "$FrontendDir\dist" "s3://$FrontendBucket" --delete

Write-Host "Invalidating CloudFront cache so viewers get the new build immediately..." -ForegroundColor Cyan
$InvalidationId = aws cloudfront create-invalidation --distribution-id $CloudFrontDistributionId --paths "/*" --query "Invalidation.Id" --output text
Write-Host "Invalidation $InvalidationId submitted (usually finishes within a minute or two)." -ForegroundColor Green

Write-Host ""
Write-Host "READY. Frontend is live at: https://$CloudFrontDomain" -ForegroundColor Green
