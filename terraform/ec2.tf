# The one instance running every backend service (order-service, inventory-service,
# payment-service, api-gateway, Redis, Redpanda) via Docker Compose -- see
# docker-compose.prod.yml and scripts/ec2-boot/. No Elastic IP by design (see
# scripts/ec2-boot/README.md's own cost-tradeoff comment); fix-containers-on-boot.sh
# handles pointing DNS at whatever IP this gets on each start, which Terraform has no
# part in and isn't meant to.
resource "aws_instance" "app" {
  ami                    = "ami-06468be052a4195a6"
  instance_type          = "t3.medium"
  key_name               = "nithish-ec2" # Existing key pair; Terraform never generates or holds the private key.
  subnet_id              = data.aws_subnet.default_az1a.id
  vpc_security_group_ids = [aws_security_group.ec2_app.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_app.name
  ebs_optimized          = true
  monitoring             = false

  root_block_device {
    volume_size           = 25
    volume_type           = "gp3"
    iops                  = 3000
    delete_on_termination = true
  }

  # "ResumeSite" -- captured exactly as the live instance already has it, not something
  # this project set deliberately; flagging as a loose end to sort out (it reads like a
  # tag meant for the OTHER resume-site project, possibly applied here by accident, or a
  # leftover from an AWS Instance Scheduler setup) rather than silently dropping or
  # "fixing" it during import.
  tags = {
    Schedule = "ResumeSite"
  }

  # Stop/start state, and the instance's current public IP, are runtime operational
  # concerns this project already has a purpose-built tool for (the CloudSwitch app /
  # scripts/start-all.ps1 / scripts/stop-all.ps1) -- Terraform's job is "does this
  # instance exist with the right shape," never "is it currently on." Prevents a
  # `terraform apply` run while the box happens to be stopped from trying to start it
  # back up as a side effect.
  lifecycle {
    ignore_changes = [
      user_data,
    ]
  }
}

# The specific subnet the instance actually runs in (eu-west-1a, inside the default VPC)
# -- a data source, not something this project provisions.
data "aws_subnet" "default_az1a" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = "eu-west-1a"
  default_for_az    = true
}
