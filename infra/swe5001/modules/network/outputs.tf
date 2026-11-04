output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "VPC CIDR"
  value       = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "Public subnet IDs (ALB)"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private application subnet IDs (ECS tasks only)"
  value       = aws_subnet.private[*].id
}

output "nat_public_ip" {
  description = "Elastic IP that all private-subnet egress leaves from"
  value       = aws_eip.nat.public_ip
}

output "data_subnet_ids" {
  description = "Data subnet IDs (RDS, ElastiCache only); no route out of the VPC"
  value       = aws_subnet.data[*].id
}

output "private_subnet_cidrs" {
  description = "Private application subnet CIDRs (known at plan time; used by tests)"
  value       = aws_subnet.private[*].cidr_block
}

output "data_subnet_cidrs" {
  description = "Data subnet CIDRs (known at plan time; used by tests)"
  value       = aws_subnet.data[*].cidr_block
}
