# Bulk-fills real photos for every product that's still showing a placehold.co
# placeholder (the 50-product catalog seeded without real photos -- see
# ProductController/ProductImageUploadService for the upload flow this script drives).
#
# It does exactly what an admin clicking "Upload image" 50 times in the UI would do,
# just automated: log in once, then for each placeholder product (1) SEARCH Pexels'
# curated stock-photo library for a photo matching the product's name (not a random
# tag-matched redirect the way LoremFlickr was -- Pexels' /search endpoint actually
# ranks results by relevance to the query, which is what makes this accurate), (2) run
# the top result through the same two-step presigned-upload flow productImages.js uses
# from the browser, then (3) delete the old placehold.co row so the new photo becomes
# the product's sole image (and therefore its thumbnail -- Product.images is
# @OrderBy("id ASC"), so the surviving image, not insertion order, is what decides that).
#
# Needs $PexelsApiKey in config.ps1 -- a free key from https://www.pexels.com/api/.
#
# Usage (run from a PowerShell prompt, NOT this tool -- it prompts for your password):
#   cd scripts
#   .\bulk-upload-product-images.ps1 -AdminEmail you@example.com
#
# Safe to re-run: any product whose only image is already a real (non-placehold.co) one
# is skipped, so a run that gets interrupted partway just picks up where it left off.

param(
    [Parameter(Mandatory = $true)]
    [string]$AdminEmail,

    [securestring]$AdminPassword
)

$ErrorActionPreference = "Stop"

# Reuses the same $GatewayDomain every other ops script in this folder already reads
# from config.ps1 (gitignored -- see config.example.ps1), rather than hardcoding the
# DuckDNS domain a second time here.
. "$PSScriptRoot\config.ps1"
$GatewayUrl = "https://$GatewayDomain"

if (-not $PexelsApiKey) {
    throw "config.ps1 has no `$PexelsApiKey -- get a free one from https://www.pexels.com/api/ and add it there."
}

if (-not $AdminPassword) {
    $AdminPassword = Read-Host -Prompt "Admin password for $AdminEmail" -AsSecureString
}
$plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminPassword)
)

Write-Host "Logging in as $AdminEmail..."
$loginBody = @{ email = $AdminEmail; password = $plainPassword } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/auth/login" `
    -ContentType "application/json" -Body $loginBody
$accessToken = $loginResponse.accessToken
$authHeaders = @{ Authorization = "Bearer $accessToken" }
Write-Host "Logged in."

Write-Host "Fetching product catalog..."
$products = Invoke-RestMethod -Method Get -Uri "$GatewayUrl/products" -Headers $authHeaders
Write-Host "Loaded $($products.Count) products."

# A product only needs a real photo if every image it currently has is a placehold.co
# stand-in (or it has none at all) -- the 7 products that already have a real upload
# from before are left alone, so this script is safe to run alongside normal admin use.
$targets = $products | Where-Object {
    $_.images.Count -eq 0 -or ($_.images | Where-Object { $_.imageUrl -notmatch "placehold\.co" }).Count -eq 0
}
Write-Host "$($targets.Count) products still need a real photo."

$succeeded = 0
$failed = @()

$pexelsHeaders = @{ Authorization = $PexelsApiKey }

foreach ($product in $targets) {
    $label = "[$($product.id)] $($product.name)"
    try {
        # The full product name first ("Wireless Mouse", "Stainless Steel Water Bottle")
        # -- Pexels' /search ranks results by relevance across the whole query, unlike
        # LoremFlickr's single-tag lookup, so keeping the full name gives it more to
        # match against. Falls back to category, then a generic term, only if the name
        # itself returns nothing.
        $queries = @($product.name, $product.category, "product") | Where-Object { $_ }
        $photo = $null
        foreach ($query in $queries) {
            $searchUrl = "https://api.pexels.com/v1/search?query=$([uri]::EscapeDataString($query))&per_page=1&orientation=square"
            $result = Invoke-RestMethod -Method Get -Uri $searchUrl -Headers $pexelsHeaders
            if ($result.photos.Count -gt 0) {
                $photo = $result.photos[0]
                break
            }
        }
        if (-not $photo) {
            throw "No Pexels match for '$($product.name)' or its category"
        }
        # "large" (940px wide) -- plenty of quality for the product-card/detail-page
        # sizes this actually renders at, without pulling the multi-MB "original".
        # -UseBasicParsing: we only ever read raw bytes here, never the parsed HTML DOM
        # Invoke-WebRequest builds by default -- without this switch, Windows PowerShell
        # tries to build that DOM via IE's engine and prompts/fails on a machine where
        # IE's first-run hasn't completed.
        $photoBytes = (Invoke-WebRequest -Uri $photo.src.large -UseBasicParsing).Content

        # Step 1: presign -- same call ProductController.createImageUploadUrl serves the
        # browser.
        $presignBody = @{ contentType = "image/jpeg" } | ConvertTo-Json
        $presign = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/products/$($product.id)/image-upload-url" `
            -Headers $authHeaders -ContentType "application/json" -Body $presignBody

        # Step 2: the actual file, straight to S3 -- no Authorization header, same as
        # productImages.js: the presigned URL itself is what authorizes this PUT.
        Invoke-WebRequest -Method Put -Uri $presign.uploadUrl -Body $photoBytes -ContentType "image/jpeg" -UseBasicParsing | Out-Null

        # Step 3: tell order-service about it.
        $confirmBody = @{ imageUrl = $presign.imageUrl } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "$GatewayUrl/products/$($product.id)/images" `
            -Headers $authHeaders -ContentType "application/json" -Body $confirmBody | Out-Null

        # Now remove the old placehold.co row(s) so the real photo (the only image left)
        # becomes images[0] -- the thumbnail everywhere the frontend shows one image.
        foreach ($oldImage in $product.images) {
            Invoke-RestMethod -Method Delete -Uri "$GatewayUrl/products/$($product.id)/images/$($oldImage.id)" `
                -Headers $authHeaders | Out-Null
        }

        Write-Host "OK   $label (query: $query)"
        $succeeded++
    } catch {
        Write-Host "FAIL $label -- $($_.Exception.Message)" -ForegroundColor Red
        $failed += $label
    }

    # Polite pacing -- also keeps this comfortably inside Pexels' free-tier rate limit
    # (200 requests/hour), since each product can burn up to 3 search calls (name ->
    # category -> generic fallback) plus the photo download itself.
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "Done: $succeeded uploaded, $($failed.Count) failed."
if ($failed.Count -gt 0) {
    Write-Host "Failed products:"
    $failed | ForEach-Object { Write-Host "  - $_" }
}
