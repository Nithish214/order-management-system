variable "aws_region" {
  description = "Every resource in this project lives in eu-west-1 -- see DEPLOYMENT.md."
  type        = string
  default     = "eu-west-1"
}

variable "aws_account_id" {
  description = "Used to build exact ARNs (IAM policy resources) that must match the live account."
  type        = string
  default     = "244689414185"
}

# No default, deliberately -- this repo is public on GitHub. A hardcoded home/office IP
# here would mean anyone could read it straight off github.com, which a value sitting in
# a gitignored terraform.tfvars (see terraform.tfvars.example) doesn't. Terraform refuses
# to plan/apply without this being set one way or another.
variable "ssh_allowed_cidrs" {
  description = "CIDRs allowed to SSH into the EC2 instance on port 22. Set in terraform.tfvars (gitignored), never here."
  type        = list(string)
}
