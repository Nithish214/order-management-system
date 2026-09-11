# Stops both the EC2 instance and the RDS database for the Order Management System.
# Order doesn't matter much for stopping -- both are safe to stop in either sequence.
#
# Reads $InstanceId / $DbId from config.ps1 (copy config.example.ps1 -> config.ps1 and
# fill in your own values -- config.ps1 is gitignored, never committed).

. "$PSScriptRoot\config.ps1"

Write-Host "Stopping EC2 ($InstanceId)..." -ForegroundColor Cyan
aws ec2 stop-instances --instance-ids $InstanceId | Out-Null

Write-Host "Stopping RDS ($DbId)..." -ForegroundColor Cyan
aws rds stop-db-instance --db-instance-identifier $DbId | Out-Null

Write-Host "Waiting for EC2 to fully stop..." -ForegroundColor Cyan
aws ec2 wait instance-stopped --instance-ids $InstanceId
Write-Host "EC2 stopped." -ForegroundColor Green

Write-Host "Waiting for RDS to fully stop (this is usually the slower one)..." -ForegroundColor Cyan
do {
    Start-Sleep -Seconds 15
    $DbStatus = aws rds describe-db-instances --db-instance-identifier $DbId --query "DBInstances[0].DBInstanceStatus" --output text
    Write-Host "  RDS status: $DbStatus"
} while ($DbStatus -ne "stopped")

Write-Host ""
Write-Host "Both stopped. Nothing running, nothing billing beyond storage." -ForegroundColor Green
Write-Host "Remember: RDS auto-resumes on its own after 7 days if left stopped that long."
