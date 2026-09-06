# Production environment values (design doc §4 / runbook section 0).
# The secrets referenced by this stack live in SSM Parameter Store under
# /blocbuy/prod/* — they are never written here.

aws_account_id  = "402178232802"
aws_region      = "ap-southeast-1"
github_owner    = "PPsyrius"
github_owner_id = "19505219"
github_repo     = "iss-groupbuy-team8"
github_repo_id  = "1344446887"

ecr_repository = "prod/blocbuy"
instance_type  = "t3.medium"

# ---- team decisions to confirm before `terraform plan` (runbook section 0) ----

# Branch release tags (v*) are cut from; the ECR push trust policy pins it exactly.
# Must match the branch the first release tag will be pushed from.
release_branch = "dev"

# Pin an Ubuntu 24.04 AMI ID here — a wildcard AMI lookup is deliberately not
# shipped to prod. An empty value fails variable validation on purpose so the
# plan cannot run before a human pins the AMI.
#   aws ssm get-parameters-by-path --path /aws/service/canonical/ubuntu/server/24.04/stable/current \
#     --region ap-southeast-1 --query "Parameters[?starts_with(Name, 'amd64')].Value" --output text
ami_id = ""

# Break-glass SSH key pair; empty = SSM-only access (recommended).
key_name = ""

# Empty = interim HTTP :80 listener on the ALB. Set an ACM certificate ARN to move
# to HTTPS :443 (Stripe live mode requires TLS before real users).
acm_certificate_arn = ""
