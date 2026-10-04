# VPC with two public and two private subnets, an S3 gateway endpoint, and fck-nat as the
# single egress path for the private subnets (AWS plan, section 6.1).

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_vpc" "this" {
  # checkov:skip=CKV2_AWS_11: flow logs are off for cost; the VPC exists for about an hour at a time
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name_prefix }
}

# Strip every rule from the default security group; nothing in the VPC uses it.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name_prefix}-default-unused" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = var.name_prefix }
}

resource "aws_subnet" "public" {
  count = 2

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.public_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  # The ALB needs no public IPs on the subnet and fck-nat requests its own.
  map_public_ip_on_launch = false

  tags = { Name = "${var.name_prefix}-public-${var.availability_zones[count.index]}" }
}

resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = { Name = "${var.name_prefix}-private-${var.availability_zones[count.index]}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name_prefix}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count = 2

  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name_prefix}-private-${var.availability_zones[count.index]}" }
}

resource "aws_route" "private_egress" {
  count = 2

  route_table_id         = aws_route_table.private[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_network_interface.nat.id
}

resource "aws_route_table_association" "private" {
  count = 2

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# Free; keeps ECR layer pulls (served from S3) off the NAT instance.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = aws_route_table.private[*].id

  tags = { Name = "${var.name_prefix}-s3" }
}

data "aws_region" "current" {}
