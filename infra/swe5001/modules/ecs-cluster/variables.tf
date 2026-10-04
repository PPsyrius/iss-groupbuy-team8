variable "name" {
  description = "Cluster name; also the prefix for the execution role"
  type        = string
}

variable "service_connect_namespace" {
  description = "Cloud Map HTTP namespace used by ECS Service Connect"
  type        = string
}

variable "role_path" {
  description = "IAM path for the execution role; the apply role may only create roles here"
  type        = string
}

variable "permissions_boundary_arn" {
  description = "Workload permissions boundary from the foundation stack"
  type        = string
}

variable "parameter_arns" {
  description = "SSM parameter ARN patterns the execution role may read to inject secrets"
  type        = list(string)
}
