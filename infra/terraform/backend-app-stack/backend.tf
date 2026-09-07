# Partial backend configuration: the bucket is account-specific and passed via
# `-backend-config bucket=... region=...` at init time (see ../README.md), so this
# file never hardcodes an account identifier.
terraform {
  backend "s3" {
    key            = "backend-app-stack/terraform.tfstate"
    dynamodb_table = "blocbuy-tflock"
    encrypt        = true
  }
}
