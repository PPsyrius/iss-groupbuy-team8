# Partial backend configuration: the bucket is account-specific and passed via
# `-backend-config bucket=... -backend-config region=...` at init time (see ../README.md).
# SWE5001 keys live under `swe5001/` and use S3-native locking, not the DynamoDB table
# of the SWE5006 stacks.
terraform {
  backend "s3" {
    key          = "swe5001/foundation.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}
