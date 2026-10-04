# Security groups reference each other instead of CIDRs (AWS plan, section 6.2). Rules are
# separate resources so the ALB and service groups can reference each other.

data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb"
  description = "ALB: CloudFront origin-facing traffic only"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name_prefix}-alb" }
}

resource "aws_security_group" "svc" {
  name        = "${local.name_prefix}-svc"
  description = "ECS services: ALB and Service Connect traffic"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name_prefix}-svc" }
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "RDS PostgreSQL: services and db-init only"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name_prefix}-rds" }
}

resource "aws_security_group" "cache" {
  name        = "${local.name_prefix}-cache"
  description = "ElastiCache Valkey: services only"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name_prefix}-cache" }
}

resource "aws_security_group" "dbinit" {
  # checkov:skip=CKV2_AWS_5: attached by `aws ecs run-task` in env-up.sh, not by a Terraform resource
  name        = "${local.name_prefix}-dbinit"
  description = "One-off db-init task: no inbound traffic"
  vpc_id      = module.network.vpc_id

  tags = { Name = "${local.name_prefix}-dbinit" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  # checkov:skip=CKV_AWS_260: the source is the CloudFront origin-facing prefix list, not 0.0.0.0/0
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront origin-facing addresses"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_origin.id
}

resource "aws_vpc_security_group_egress_rule" "alb_to_svc" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Forward to service tasks"
  ip_protocol                  = "tcp"
  from_port                    = 8589
  to_port                      = 8589
  referenced_security_group_id = aws_security_group.svc.id
}

resource "aws_vpc_security_group_ingress_rule" "svc_from_alb" {
  security_group_id            = aws_security_group.svc.id
  description                  = "Service port from the ALB"
  ip_protocol                  = "tcp"
  from_port                    = 8589
  to_port                      = 8589
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_ingress_rule" "svc_from_svc" {
  security_group_id            = aws_security_group.svc.id
  description                  = "Service Connect traffic between services"
  ip_protocol                  = "tcp"
  from_port                    = 8589
  to_port                      = 8589
  referenced_security_group_id = aws_security_group.svc.id
}

# Services call Stripe, Google OAuth and AWS APIs through fck-nat, and each other through
# Service Connect; inbound rules are the control.
# tfsec:ignore:aws-0104
resource "aws_vpc_security_group_egress_rule" "svc_to_any" {
  security_group_id = aws_security_group.svc.id
  description       = "Outbound through fck-nat and to other services"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from" {
  for_each = {
    svc    = aws_security_group.svc.id
    dbinit = aws_security_group.dbinit.id
  }

  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from ${each.key}"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = each.value
}

resource "aws_vpc_security_group_ingress_rule" "cache_from_svc" {
  security_group_id            = aws_security_group.cache.id
  description                  = "Valkey from services"
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  referenced_security_group_id = aws_security_group.svc.id
}

resource "aws_vpc_security_group_egress_rule" "dbinit_to_rds" {
  security_group_id            = aws_security_group.dbinit.id
  description                  = "PostgreSQL to RDS"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.rds.id
}

# Image pull from ECR Public, SSM parameters and CloudWatch Logs, through fck-nat.
# tfsec:ignore:aws-0104
resource "aws_vpc_security_group_egress_rule" "dbinit_to_https" {
  security_group_id = aws_security_group.dbinit.id
  description       = "HTTPS to AWS APIs and ECR Public"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}
