# Rebuilds and republishes the React frontend to S3 + CloudFront. Run this any time you
# change frontend code and want it live.
#
# Before the Gateway had a stable HTTPS domain (see DEPLOYMENT.md, Phase 6 --
# nithish-ordermgmt.duckdns.org, kept pointed at the EC2 box's current IP by
# start-all.ps1), this script also had to rewrite frontend/.env's VITE_GATEWAY_URL and
# rebuild every time the instance restarted and got a new IP. Now that the Gateway URL
# is a stable domain instead of a raw IP, .env doesn't go stale on its own -- this script
# only needs to run when the frontend's own code actually changes.
#
# Reads config from config.ps1 (copy config.example.ps1 -> config.ps1 and fill in your
# own values -- config.ps1 is gitignored, never committed).

. "$PSScriptRoot\config.ps1"

$FrontendDir = Join-Path $PSScriptRoot "..\frontend"

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
# --exclude "product-images/*" and "product-videos/*" are not optional. --delete makes
# this command mirror the bucket to match dist/ exactly, deleting anything in the bucket
# that dist/ doesn't have -- and dist/ (the built frontend) never contains either prefix
# at all, since both are uploaded separately, straight to S3, by admins using the app
# itself (see ProductImageUploadService/ProductVideoUploadService). Without the images
# exclusion, a deploy did exactly what --delete is supposed to do and wiped every
# uploaded product photo -- caught and recovered via S3 versioning that first time.
# product-videos/ then shipped without being added to this same exclusion, and hit the
# identical bug: an admin's uploaded video was silently deleted by the very next frontend
# deploy. The fix both times is to make it structurally impossible, not to rely on
# remembering to add every new admin-uploaded-content prefix here by hand.
aws s3 sync "$FrontendDir\dist" "s3://$FrontendBucket" --delete --exclude "product-images/*" --exclude "product-videos/*"

Write-Host "Invalidating CloudFront cache so viewers get the new build immediately..." -ForegroundColor Cyan
$InvalidationId = aws cloudfront create-invalidation --distribution-id $CloudFrontDistributionId --paths "/*" --query "Invalidation.Id" --output text
Write-Host "Invalidation $InvalidationId submitted (usually finishes within a minute or two)." -ForegroundColor Green

Write-Host ""
Write-Host "READY. Frontend is live at: https://$CloudFrontDomain" -ForegroundColor Green
