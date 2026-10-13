# Pinned, not left open-ended -- a Terraform/provider upgrade should be a deliberate,
# reviewed bump to this file, not something that silently changes behavior on whoever's
# machine happens to run `terraform init` next.
terraform {
  required_version = "~> 1.16"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Local state for now, deliberately -- see README.md's "State" section for why, and
  # what moving to a remote (S3 + lock table) backend later would look like. Nothing
  # about the resource definitions below needs to change to make that move.
}
