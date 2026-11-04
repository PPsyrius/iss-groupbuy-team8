variable "name_prefix" {
  description = "Name prefix for every resource, e.g. blocbuy-swe5001"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR; must not overlap the default VPC used by the SWE5006 EC2 host"
  type        = string
}

variable "availability_zones" {
  description = "Two availability zones, in the same order as the subnet CIDRs"
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "Exactly two availability zones are expected."
  }
}

variable "public_subnet_cidrs" {
  description = "Public subnets (ALB, fck-nat), one per availability zone"
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "Private application subnets (ECS tasks), one per availability zone"
  type        = list(string)
}

variable "data_subnet_cidrs" {
  description = "Data subnets (RDS, ElastiCache), one per availability zone; no route out of the VPC"
  type        = list(string)

  validation {
    condition     = length(var.data_subnet_cidrs) == 2
    error_message = "Exactly two data subnets are expected (an RDS subnet group needs two zones)."
  }

  validation {
    condition     = length(setintersection(var.data_subnet_cidrs, var.private_subnet_cidrs)) == 0 && length(setintersection(var.data_subnet_cidrs, var.public_subnet_cidrs)) == 0
    error_message = "Data subnets must not reuse an application or public subnet CIDR."
  }
}

variable "nat_instance_type" {
  description = "fck-nat instance type (Graviton)"
  type        = string
  default     = "t4g.nano"
}

variable "role_path" {
  description = "IAM path for the fck-nat instance role; the apply role may only create roles here"
  type        = string
}

variable "permissions_boundary_arn" {
  description = "Workload permissions boundary from the foundation stack"
  type        = string
}

variable "instance_tags" {
  description = "Tags for instances and volumes launched by the fck-nat Auto Scaling group, which provider default_tags do not reach"
  type        = map(string)
}
