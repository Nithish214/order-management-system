provider "aws" {
  region = var.aws_region

  # No access key/secret here -- picks up whatever credentials the AWS CLI already has
  # configured locally (the same ones every `aws` command run against this project uses),
  # exactly as intended: this is a developer's own machine running Terraform by hand, not
  # a CI pipeline that would need its own separate credential story.

  # No default_tags block, deliberately, for now -- every resource below was created
  # before Terraform existed, with whatever tags (or none) it already has. Adding
  # default_tags here would mean the very first `terraform plan` after import already
  # wants to CHANGE something live, which defeats this first pass's one job: describe
  # reality exactly, with `terraform plan` reporting no changes at all. A consistent
  # tagging scheme is a reasonable next step, but a deliberate, separate, reviewed one --
  # not a side effect of adopting Terraform.
}
