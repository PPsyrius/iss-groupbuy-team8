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
  description = "Private subnet IDs (ECS tasks, RDS, ElastiCache)"
  value       = aws_subnet.private[*].id
}

output "nat_public_ip" {
  description = "Elastic IP that all private-subnet egress leaves from"
  value       = aws_eip.nat.public_ip
}
