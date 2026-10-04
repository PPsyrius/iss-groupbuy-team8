# Single-AZ PostgreSQL for the three service databases (AWS plan, section 6.5). Every
# password is generated as an ephemeral value and written through write-only arguments,
# so none reaches the Terraform state that the plan role can read. db-init reads them
# from SSM to create the databases and roles.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

locals {
  # One owner (Flyway) and one app (runtime) role per database.
  role_passwords = { for pair in setproduct(var.databases, ["OWNER", "APP"]) : "${pair[0]}/${pair[1]}" => {
    database = pair[0]
    kind     = pair[1]
  } }
}

resource "aws_db_subnet_group" "this" {
  name       = var.identifier
  subnet_ids = var.subnet_ids
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.identifier}-pg${var.engine_major_version}"
  family = "postgres${var.engine_major_version}"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

ephemeral "random_password" "master" {
  length           = 32
  override_special = "-_"
}

resource "aws_db_instance" "this" {
  # checkov:skip=CKV_AWS_118: enhanced monitoring is off for cost; CloudWatch basic metrics suffice for the demo
  # checkov:skip=CKV_AWS_157: single-AZ by design (plan section 3.3); the instance lives about an hour
  # checkov:skip=CKV_AWS_293: deletion protection would block the destroy at the end of every run
  # checkov:skip=CKV_AWS_353: Performance Insights is not needed for a disposable demo database
  # checkov:skip=CKV_AWS_354: Performance Insights is off
  # checkov:skip=CKV_AWS_129: log exports would create log groups outside /blocbuy/swe5001/ that outlive the destroy
  # checkov:skip=CKV_AWS_133: backups are off; the data is demo seed data recreated by every run
  # checkov:skip=CKV2_AWS_30: query logging is off for cost and to keep passwords set by db-init out of logs
  identifier     = var.identifier
  engine         = "postgres"
  engine_version = var.engine_major_version
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage_gib
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = var.security_group_ids
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = false

  username            = "blocbuy_admin"
  password_wo         = ephemeral.random_password.master.result
  password_wo_version = 1

  iam_database_authentication_enabled = true
  auto_minor_version_upgrade          = true
  copy_tags_to_snapshot               = true
  backup_retention_period             = 0
  skip_final_snapshot                 = true
  deletion_protection                 = false
  apply_immediately                   = true
}

# Written after the instance exists: if the instance create fails, the next run generates
# a fresh password for both instead of leaving SSM with a password the database never had.
resource "aws_ssm_parameter" "master_password" {
  # checkov:skip=CKV_AWS_337: encrypted with the AWS-managed aws/ssm key; no customer-managed keys in a disposable stack
  name             = "${var.ssm_prefix}/shared/DB_MASTER_PASSWORD"
  description      = "RDS master password; read by the db-init task only"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.master.result
  value_wo_version = 1

  depends_on = [aws_db_instance.this]
}

ephemeral "random_password" "role" {
  for_each = local.role_passwords

  length           = 32
  override_special = "-_"
}

# db-init sets each role's password from these parameters on every run, so they never drift.
resource "aws_ssm_parameter" "role_password" {
  # checkov:skip=CKV_AWS_337: encrypted with the AWS-managed aws/ssm key; no customer-managed keys in a disposable stack
  for_each = local.role_passwords

  name             = "${var.ssm_prefix}/${each.value.database}/DB_${each.value.kind}_PASSWORD"
  description      = "Password of role blocbuy_${each.value.database}_${lower(each.value.kind)}"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.role[each.key].result
  value_wo_version = 1
}
