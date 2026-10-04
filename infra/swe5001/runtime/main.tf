provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.tags
  }
}

locals {
  tags = {
    Project   = "blocbuy"
    Course    = "swe5001"
    Stack     = "runtime"
    ManagedBy = "terraform"
  }

  name_prefix   = "blocbuy-swe5001"
  ssm_prefix    = "/blocbuy/swe5001"
  ssm_arn       = "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:parameter${local.ssm_prefix}"
  iam_role_path = "/swe5001/"
  ecr_registry  = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"

  # Created by foundation. Built from its name because the apply role cannot list IAM
  # policies; a missing boundary fails the first CreateRole call.
  workload_boundary_arn = "arn:aws:iam::${var.aws_account_id}:policy/${local.name_prefix}-workload-boundary"

  # Execution-role reads: shared values plus one scope per database (which is also the
  # service's scope for its own secrets, e.g. /blocbuy/swe5001/identity/GOOGLE_CLIENT_ID).
  parameter_scopes = distinct(concat(["shared"], var.databases))
}

# Operator-seeded (../README.md). The value is in the runtime state, as in the SWE5006
# backend-app-stack: an ALB rule condition cannot reference SSM, and the header only keeps
# direct traffic off the ALB.
data "aws_ssm_parameter" "origin_secret" {
  name = "${local.ssm_prefix}/shared/ORIGIN_SECRET"
}

module "network" {
  source = "../modules/network"

  name_prefix              = local.name_prefix
  vpc_cidr                 = "10.50.0.0/16"
  availability_zones       = ["${var.aws_region}a", "${var.aws_region}b"]
  public_subnet_cidrs      = ["10.50.0.0/24", "10.50.1.0/24"]
  private_subnet_cidrs     = ["10.50.10.0/24", "10.50.11.0/24"]
  role_path                = local.iam_role_path
  permissions_boundary_arn = local.workload_boundary_arn
  instance_tags            = local.tags
}
