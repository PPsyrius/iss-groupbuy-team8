# Partial backend configuration, as in ../foundation: the bucket is passed at init time
# (`-backend-config bucket=... -backend-config region=...`, see ../README.md).
terraform {
  backend "s3" {
    key          = "swe5001/runtime.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}
