# Copy this file to config.ps1 (already gitignored) and fill in your own values.
# start-all.ps1 / stop-all.ps1 dot-source config.ps1 to load these.

$InstanceId = "i-xxxxxxxxxxxxxxxxx"
$DbId = "your-rds-instance-identifier"
$KeyPath = "C:\path\to\your-key.pem"
$SshUser = "ubuntu"
$FrontendBucket = "your-frontend-bucket-name"
$CloudFrontDistributionId = "EXXXXXXXXXXXXX"
$CloudFrontDomain = "dxxxxxxxxxxxxx.cloudfront.net"
# Free dynamic DNS (duckdns.org) so the backend Gateway has a stable hostname despite
# the EC2 instance getting a new public IP on every restart (no Elastic IP -- see
# DEPLOYMENT.md's cost breakdown for why). $DuckDnsToken is a real credential -- never
# commit it; config.ps1 is gitignored specifically so this can live here safely.
$DuckDnsDomain = "your-subdomain"
$DuckDnsToken = "your-duckdns-token"
$GatewayDomain = "your-subdomain.duckdns.org"
