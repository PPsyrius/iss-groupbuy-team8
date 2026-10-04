module "postgres" {
  source = "../modules/rds-postgres"

  identifier           = local.name_prefix
  engine_major_version = var.postgres_major_version
  subnet_ids           = module.network.private_subnet_ids
  security_group_ids   = [aws_security_group.rds.id]
  databases            = var.databases
  ssm_prefix           = local.ssm_prefix
}

module "cache" {
  source = "../modules/cache"

  replication_group_id = local.name_prefix
  engine_version       = var.valkey_version
  subnet_ids           = module.network.private_subnet_ids
  security_group_ids   = [aws_security_group.cache.id]
  ssm_prefix           = local.ssm_prefix
}
