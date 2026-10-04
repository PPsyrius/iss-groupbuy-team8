# One-node ElastiCache for Valkey for Sa-Token sessions and caches (AWS plan, section 6.5).
# It holds no durable state, so snapshots are off.

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

# The replication group has no write-only auth_token argument in provider 5.x, so this
# token is in the runtime state. It is accepted: the node is reachable only from the
# service security group, and the token is regenerated with every new environment.
resource "random_password" "auth" {
  length           = 32
  override_special = "-_"
}

resource "aws_ssm_parameter" "auth" {
  # checkov:skip=CKV_AWS_337: encrypted with the AWS-managed aws/ssm key; no customer-managed keys in a disposable stack
  name        = "${var.ssm_prefix}/shared/REDIS_PASSWORD"
  description = "Valkey AUTH token"
  type        = "SecureString"
  value       = random_password.auth.result
}

resource "aws_elasticache_subnet_group" "this" {
  name       = var.replication_group_id
  subnet_ids = var.subnet_ids
}

resource "aws_elasticache_replication_group" "this" {
  # checkov:skip=CKV_AWS_191: AWS-managed encryption key; no customer-managed keys in a disposable stack
  # checkov:skip=CKV_AWS_134: sessions and caches only; nothing to back up
  # checkov:skip=CKV2_AWS_50: one node by design (plan section 3.3); sessions are lost on failure, which the demo accepts
  replication_group_id = var.replication_group_id
  description          = "BlocBuy SWE5001 sessions and caches"
  engine               = "valkey"
  engine_version       = var.engine_version
  node_type            = var.node_type
  num_cache_clusters   = 1
  port                 = 6379

  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = var.security_group_ids

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.auth.result

  snapshot_retention_limit = 0
  apply_immediately        = true
}
