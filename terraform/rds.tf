# The one database every backend service uses. Stopped most of the time (see
# scripts/rds-idle-auto-stop/ and the CloudSwitch app) -- Terraform's job here is "does
# this instance exist with the right shape," same as the EC2 instance; it has no opinion
# on whether it's currently running, see aws_instance.app's own comment for why.
resource "aws_db_instance" "orders" {
  identifier     = "order-management-db"
  engine         = "postgres"
  engine_version = "16.15"
  instance_class = "db.t3.micro"

  db_name  = "orderdb"
  username = "orderadmin"
  # No `password` here at all, deliberately -- AWS never returns the real one, so
  # import can't populate it, and this repo is public (a placeholder value sitting in a
  # .tf file would be actively misleading, not just unused). Changing it for real still
  # works the normal way (the AWS console/CLI, or RDS's own ModifyDBInstance), outside
  # Terraform entirely -- ignore_changes below is what stops that from showing as
  # permanent drift on every single `plan`.
  lifecycle {
    ignore_changes = [password]
  }

  allocated_storage = 20
  storage_type      = "gp2"
  storage_encrypted = false

  # AWS's own auto-created defaults for this VPC (created the first time anything used
  # Postgres 16 / RDS here without specifying one explicitly) -- data sources below, not
  # resources, same reasoning as the default VPC/subnet in security-groups.tf.
  db_subnet_group_name = data.aws_db_subnet_group.default.name
  parameter_group_name = data.aws_db_parameter_group.postgres16.name

  # option_group_name deliberately NOT set here at all -- there's no "aws_db_option_group"
  # DATA source in this provider (only a managed resource type, which would mean
  # Terraform trying to OWN an AWS-managed default group, not just read it), and the
  # attribute is Optional+Computed on aws_db_instance: left out of config, Terraform
  # keeps whatever's already in state from import ("default:postgres-16") rather than
  # trying to null it out.

  # References the EC2 security group directly (aws_security_group.ec2_app, defined in
  # security-groups.tf) rather than a hardcoded id -- this is the one place in the whole
  # setup where two resources are actually wired to each other instead of just existing
  # side by side, matching the live rule exactly: Postgres is reachable from that one
  # security group, nothing else, see rds-security-groups.tf's own sg for the inbound
  # rule itself.
  vpc_security_group_ids = [aws_security_group.rds.id]

  multi_az                   = false
  publicly_accessible        = false
  backup_retention_period    = 1
  copy_tags_to_snapshot      = false
  auto_minor_version_upgrade = true

  # Terraform-only settings with no AWS-side equivalent (same category as
  # aws_security_group.revoke_rules_on_delete) -- pinned explicitly rather than left to
  # whatever the provider's default happens to be. skip_final_snapshot = false is the
  # SAFE choice (a real snapshot gets taken if this is ever destroyed through Terraform),
  # not the common "true" shortcut some guides use to make `destroy` faster/quieter.
  skip_final_snapshot       = false
  final_snapshot_identifier = "order-management-db-final"
  deletion_protection       = false
}

# This security group -- unlike the EC2 one, which came from a console wizard -- was
# actually named and described deliberately when this project set it up ("Allows
# Postgres access only from the app EC2 instance"). Captured as a real resource, not a
# data source, same as aws_security_group.ec2_app.
resource "aws_security_group" "rds" {
  name        = "rds-postgres-sg"
  description = "Allows Postgres access only from the app EC2 instance"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2_app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

data "aws_db_subnet_group" "default" {
  name = "default"
}

data "aws_db_parameter_group" "postgres16" {
  name = "default.postgres16"
}
