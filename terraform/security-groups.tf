# The one security group on the app's EC2 instance -- still named "launch-wizard-1",
# AWS's own auto-generated default from when it was first created through the console,
# before this project existed as Terraform. Left as-is rather than renamed: a rename
# forces a replace (AWS treats the name as immutable), which would mean a real, live
# network interruption just to tidy up a cosmetic name. Captured here exactly as it
# already is -- see README.md's import section.
#
# None of the four rules below have a `description` set on the live resource (plain
# `terraform plan` right after import showed that clearly: adding description text here
# read as a real attribute change, not just a cosmetic no-op) -- what each rule is FOR is
# explained in the comment above it instead, left off the rule itself to keep this
# import's first plan genuinely clean. Adding real AWS-side descriptions is a reasonable,
# easy follow-up -- just a deliberate one, not a side effect of adopting Terraform.
resource "aws_security_group" "ec2_app" {
  name                   = "launch-wizard-1"
  description            = "launch-wizard-1 created 2026-07-06T23:08:00.290Z" # AWS's own default text; kept verbatim, not rewritten
  vpc_id                 = data.aws_vpc.default.id
  revoke_rules_on_delete = false # Terraform-only setting (no AWS-side equivalent) -- pinned explicitly to the provider's own default so it never shows as a pending change.

  # The two source IPs this actually gets used from (see terraform.tfvars, never
  # committed: this repo is public). Not 0.0.0.0/0 -- the one rule here that genuinely is
  # restricted, unlike the three below.
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidrs
  }

  # nginx on the EC2 box, fronting the Let's Encrypt-redirected traffic for
  # shop-api.nithishnarravula.dev.
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # The real traffic path once nginx's redirect sends it here.
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # api-gateway's own port, reachable directly (not just via nginx) -- the CloudSwitch
  # app's /health/services check and the frontend both call this, see api-gateway's own
  # docker-compose port mapping.
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# The account's default VPC -- this project was never given its own custom VPC, it runs
# in whatever AWS provisions by default in eu-west-1. A data source, not a resource:
# Terraform should never create or destroy the default VPC.
data "aws_vpc" "default" {
  default = true
}
