variable "replication_group_id" {
  description = "Replication group ID, e.g. blocbuy-swe5001"
  type        = string
}

variable "engine_version" {
  description = "Valkey version"
  type        = string
  default     = "8.0"
}

variable "node_type" {
  description = "Node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "subnet_ids" {
  description = "Private subnets"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups for the node"
  type        = list(string)
}

variable "ssm_prefix" {
  description = "SSM path prefix, e.g. /blocbuy/swe5001"
  type        = string
}
