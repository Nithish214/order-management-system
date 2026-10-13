# Terraform

Describes this project's AWS infrastructure in code, adopted onto resources that already
existed (built by hand, over many sessions, via the console and the AWS CLI) rather than
provisioned fresh. The point isn't to change anything running today -- it's to have a
single, reviewable source of truth for what exists and why, instead of that knowledge
living only in whoever's memory set it up.

## What's covered so far

The EC2 instance and RDS database -- the two things actually running the app -- and
everything each directly needs:

| File | What it describes |
|---|---|
| `ec2.tf` | The one EC2 instance running every backend service via Docker Compose |
| `security-groups.tf` | Its security group (`launch-wizard-1`) and the default VPC/subnet it sits in |
| `iam.tf` | Its IAM role, instance profile, the AWS-managed CloudWatch policy attachment, and its three inline policies (SES send, product-image/video S3 upload) |
| `rds.tf` | The Postgres database, its own security group (`rds-postgres-sg`, referencing the EC2 security group above by id, matching the live inbound rule exactly), and the account's default DB subnet group/parameter group it uses |

**Not yet covered** (each would be its own slice, imported the same way): the frontend S3
bucket, both CloudFront distributions, Cognito, the two Lambdas (`ec2-rds-monitor`,
`rds-idle-auto-stop`) and their API Gateway, ACM, and the Porkbun/Route 53 DNS records.
`nithishnarravula-resume-site` and `resume-website-api-dev-*` (the OTHER project's
resources) are deliberately out of scope entirely -- this is the order-management-system's
own Terraform, not an inventory of the whole AWS account.

## State

Local (`terraform.tfstate`, gitignored) -- deliberate for now, not an oversight. A
single person running this by hand from one machine doesn't need the coordination a
remote backend (S3 + a lock table) exists to solve; adding one is a real, worthwhile
next step once more of the account is under Terraform and there's a real risk of two
people (or a CI job) running `apply` at the same time, but it's not pulling its weight
yet. Moving to one later needs zero changes to the resource blocks themselves, just the
backend configuration in `versions.tf`.

## Secrets / anything specific to this account

This repo is **public** on GitHub -- `terraform.tfvars` (the two real SSH-source IPs) is
gitignored for exactly that reason. `terraform.tfvars.example` is the committed,
fake-value template; copy it and fill in the real one before running anything:

```bash
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars with your real values
```

## Workflow for adopting another existing resource

This is the pattern every slice above followed, and the one to repeat for RDS/S3/
CloudFront/etc.:

1. Pull its exact live configuration via the AWS CLI/console -- every attribute that
   matters, not just the obvious ones (tags, descriptions, and Terraform-only settings
   like `revoke_rules_on_delete` below are easy to miss and show up as "drift" later).
2. Write the resource block to match.
3. `terraform import <resource>.<name> <real-id>`.
4. `terraform plan` -- fix every difference it reports, re-running until it says
   **"No changes. Your infrastructure matches the configuration."** That line is the
   actual proof the `.tf` file is honest, not a guess.

A few things that genuinely came up doing this for the EC2 slice, worth remembering for
the next one:

- **A security group's per-rule `description` isn't optional metadata Terraform invents
  a default for** -- if the live rules were created without one (true here, via the
  console's launch wizard), writing one into the `.tf` reads as a real attribute change.
  Match what's actually there first; add descriptions as a deliberate, separate,
  reviewed `apply` afterward if you want them.
- **Some resource attributes exist only in Terraform's schema, not in AWS at all** (e.g.
  `aws_security_group.revoke_rules_on_delete`) -- import can't populate these from
  anywhere, so pin them explicitly to the provider's documented default, or they show up
  as a pending change forever.
- **Don't "fix" something that looks wrong during import.** The EC2 instance's one tag
  (`Schedule = "ResumeSite"`) looks like it belongs to a different project -- captured
  here exactly as it already is, with a comment flagging it, not silently corrected.
  Reality is reality; cleaning it up (if it even needs cleaning up) is its own separate,
  deliberate change.
- **Never add something account-wide** (like a `default_tags` block on the provider)
  **before the base import is clean.** It turns every single resource's first plan into
  "wants to add a tag," which buries the one diff that actually matters (a real
  mismatch) in noise that doesn't.
- **A sensitive attribute AWS never returns (e.g. `aws_db_instance.password`) can't be
  imported, full stop** -- don't invent a placeholder value for it (actively misleading
  in a public repo) or make it Required. Leave it out of the resource block entirely and
  `lifecycle { ignore_changes = [password] }`; the real password still changes the
  normal way (the AWS console/CLI), just never through this file.
- **Not every AWS object has a matching Terraform data source**, even read-only ones --
  `aws_db_option_group` only exists as a managed *resource* in this provider, not a data
  source. For an Optional+Computed attribute (`option_group_name` here), the fix is
  simpler than it looks: just leave it out of the config entirely. Terraform keeps
  whatever import already put in state instead of trying to null it out.
- **A pending change isn't automatically a problem.** Importing `aws_db_instance`
  surfaced `skip_final_snapshot = true -> false` -- true was never AWS's value (this
  attribute has no AWS-side equivalent at all, same category as
  `revoke_rules_on_delete`), it was just the provider's placeholder on import.
  `terraform apply` for a diff entirely in that category never touches the real
  resource -- confirmed here with `DBInstanceStatus` read before and after (still
  `stopped`, untouched) and Terraform's own `Modifications complete after 0s`, too fast
  to have been a real RDS API call.

## Running it

```bash
terraform init      # first time, or after changing providers/backend
terraform plan       # see what would change -- read this before ever applying
terraform apply      # only once you've read and agree with the plan
```
