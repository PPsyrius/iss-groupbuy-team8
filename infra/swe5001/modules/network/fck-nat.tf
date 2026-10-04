# fck-nat (https://fck-nat.dev) instead of a NAT gateway: about US$4 a month instead of
# about US$43. The private route tables point at a static ENI with an Elastic IP; an Auto
# Scaling group of one launches the instance, which attaches the ENI at boot, so a failed
# instance is replaced without touching the routes.

data "aws_ami" "fck_nat" {
  most_recent = true
  owners      = ["568608671756"] # fck-nat publisher account

  filter {
    name   = "name"
    values = ["fck-nat-al2023-*-arm64-ebs"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

resource "aws_security_group" "nat" {
  name        = "${var.name_prefix}-nat"
  description = "fck-nat: forwards traffic from the private subnets"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.name_prefix}-nat" }
}

resource "aws_vpc_security_group_ingress_rule" "nat_from_private" {
  for_each = toset(var.private_subnet_cidrs)

  security_group_id = aws_security_group.nat.id
  description       = "All traffic from private subnet ${each.value}"
  ip_protocol       = "-1"
  cidr_ipv4         = each.value
}

# A NAT instance forwards to any internet destination by definition.
# tfsec:ignore:aws-0104
resource "aws_vpc_security_group_egress_rule" "nat_to_internet" {
  security_group_id = aws_security_group.nat.id
  description       = "Forwarded traffic to the internet"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_network_interface" "nat" {
  subnet_id         = aws_subnet.public[0].id
  security_groups   = [aws_security_group.nat.id]
  source_dest_check = false

  tags = { Name = "${var.name_prefix}-nat" }
}

resource "aws_eip" "nat" {
  # checkov:skip=CKV2_AWS_19: attached to the static ENI, which the fck-nat instance attaches at boot
  domain            = "vpc"
  network_interface = aws_network_interface.nat.id

  tags = { Name = "${var.name_prefix}-nat" }

  depends_on = [aws_internet_gateway.this]
}

data "aws_iam_policy_document" "nat_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# The workload boundary limits both actions to resources tagged Course=swe5001, so the
# ENI and the instance (tag_specifications below) must carry that tag.
data "aws_iam_policy_document" "nat" {
  statement {
    sid       = "DescribeNetworkInterfaces"
    actions   = ["ec2:DescribeNetworkInterfaces"]
    resources = ["*"]
  }

  statement {
    sid       = "AttachStaticEni"
    actions   = ["ec2:AttachNetworkInterface", "ec2:ModifyNetworkInterfaceAttribute"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Course"
      values   = [var.instance_tags["Course"]]
    }
  }
}

resource "aws_iam_role" "nat" {
  name                 = "${var.name_prefix}-nat"
  path                 = var.role_path
  permissions_boundary = var.permissions_boundary_arn
  assume_role_policy   = data.aws_iam_policy_document.nat_assume.json
}

resource "aws_iam_role_policy" "nat" {
  name   = "attach-static-eni"
  role   = aws_iam_role.nat.id
  policy = data.aws_iam_policy_document.nat.json
}

resource "aws_iam_instance_profile" "nat" {
  name = "${var.name_prefix}-nat"
  path = var.role_path
  role = aws_iam_role.nat.name
}

resource "aws_launch_template" "nat" {
  # checkov:skip=CKV_AWS_88: a NAT instance needs a public IP on its primary interface (see below)
  name          = "${var.name_prefix}-nat"
  image_id      = data.aws_ami.fck_nat.id
  instance_type = var.nat_instance_type

  iam_instance_profile {
    arn = aws_iam_instance_profile.nat.arn
  }

  # The primary interface needs a public IP to call the EC2 API before the static ENI
  # is attached.
  network_interfaces {
    device_index                = 0
    associate_public_ip_address = true
    security_groups             = [aws_security_group.nat.id]
    delete_on_termination       = true
  }

  block_device_mappings {
    device_name = data.aws_ami.fck_nat.root_device_name

    ebs {
      encrypted             = true
      delete_on_termination = true
      volume_type           = "gp3"
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  user_data = base64encode(<<-EOT
    #!/bin/sh
    echo "eni_id=${aws_network_interface.nat.id}" > /etc/fck-nat.conf
    service fck-nat restart
  EOT
  )

  dynamic "tag_specifications" {
    for_each = ["instance", "volume", "network-interface"]

    content {
      resource_type = tag_specifications.value
      tags          = merge(var.instance_tags, { Name = "${var.name_prefix}-nat" })
    }
  }
}

resource "aws_autoscaling_group" "nat" {
  name                = "${var.name_prefix}-nat"
  min_size            = 1
  max_size            = 1
  desired_capacity    = 1
  vpc_zone_identifier = [aws_subnet.public[0].id]
  health_check_type   = "EC2"
  force_delete        = true

  launch_template {
    id      = aws_launch_template.nat.id
    version = aws_launch_template.nat.latest_version
  }

  dynamic "tag" {
    for_each = merge(var.instance_tags, { Name = "${var.name_prefix}-nat" })

    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }

  # The instance attaches the ENI at boot, so the role must be complete first.
  depends_on = [aws_iam_role_policy.nat, aws_eip.nat]
}
