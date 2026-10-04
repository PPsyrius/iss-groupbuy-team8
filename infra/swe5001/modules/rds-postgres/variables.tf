variable "identifier" {
  description = "DB instance identifier, e.g. blocbuy-swe5001"
  type        = string
}

variable "engine_major_version" {
  description = "PostgreSQL major version; 18 if RDS offers it in the region, otherwise 17 (AWS plan, section 10)"
  type        = string
  default     = "18"
}

variable "instance_class" {
  description = "Instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage_gib" {
  description = "gp3 storage in GiB"
  type        = number
  default     = 20
}

variable "subnet_ids" {
  description = "Private subnets"
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups for the instance"
  type        = list(string)
}

variable "databases" {
  description = "Databases created by db-init; each gets an owner (Flyway) and an app (runtime) role password in SSM"
  type        = list(string)
}

variable "ssm_prefix" {
  description = "SSM path prefix, e.g. /blocbuy/swe5001"
  type        = string
}
