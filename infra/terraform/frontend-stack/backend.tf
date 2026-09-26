terraform {
  backend "s3" {
    key            = "frontend-stack/terraform.tfstate"
    dynamodb_table = "blocbuy-tflock"
    encrypt        = true
  }
}
