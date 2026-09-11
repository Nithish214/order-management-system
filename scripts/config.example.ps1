# Copy this file to config.ps1 (already gitignored) and fill in your own values.
# start-all.ps1 / stop-all.ps1 dot-source config.ps1 to load these.

$InstanceId = "i-xxxxxxxxxxxxxxxxx"
$DbId = "your-rds-instance-identifier"
$KeyPath = "C:\path\to\your-key.pem"
$SshUser = "ubuntu"
$FrontendBucket = "your-frontend-bucket-name"
$CloudFrontDistributionId = "EXXXXXXXXXXXXX"
$CloudFrontDomain = "dxxxxxxxxxxxxx.cloudfront.net"
