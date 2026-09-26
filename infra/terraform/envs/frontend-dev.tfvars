# Existing dev frontend edge (issue #161). IDs and repository identity match the
# account-holder read-only checks; no credentials or origin secret belong here.

aws_account_id  = "402178232802"
aws_region      = "ap-southeast-1"
github_owner    = "PPsyrius"
github_owner_id = "19505219"
github_repo     = "iss-groupbuy-team8"
github_repo_id  = "1344446887"

# Existing CloudFront and ALB origins; do not create replacements during import.
oac_id  = "E2WEBPIBHXOW90"
alb_dns = "blocbuy-dev-backend-1247042341.ap-southeast-1.elb.amazonaws.com"

# Supply the existing X-Origin-Secret privately as TF_VAR_origin_secret.
