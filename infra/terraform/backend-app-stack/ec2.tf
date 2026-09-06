resource "aws_iam_role" "ec2_runtime" {
  name        = "BlocBuyEc2RuntimeProd"
  description = "Prod backend instance: SSM management, prod ECR pull, prod secret parameters"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  # ECR pull scoped to exactly the prod repository — resource reference, no placeholder.
  inline_policy {
    name = "ecr-pull-prod-repository"
    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [{
        Sid      = "AuthenticateToEcr"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
        }, {
        Sid    = "PullOnlyBlocBuyBackendProd"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = aws_ecr_repository.backend.arn
      }]
    })
  }

  # First-boot user-data and fetch-env.sh read the prod secret parameters
  # (implementation plan §1.2; design doc §6.7).
  inline_policy {
    name = "ssm-read-prod-parameters"
    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [{
        Sid      = "ReadBlocBuyProdParameters"
        Effect   = "Allow"
        Action   = "ssm:GetParameters"
        Resource = "arn:aws:ssm:${var.aws_region}:${var.aws_account_id}:parameter/blocbuy/prod/*"
      }]
    })
  }
}

# SSM management permissions for the instance role.
resource "aws_iam_role_policy_attachment" "ec2_runtime_ssm" {
  role       = aws_iam_role.ec2_runtime.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_runtime" {
  name = "BlocBuy-prod"
  role = aws_iam_role.ec2_runtime.name
}

resource "aws_instance" "backend" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  iam_instance_profile   = aws_iam_instance_profile.ec2_runtime.name
  vpc_security_group_ids = [aws_security_group.backend.id]

  # Explicit public IP: the default VPC has no NAT gateway, and first boot needs
  # outbound internet (apt, docker pull, SSM agent). The security group is the
  # actual control — inbound is 8589 from the ALB SG only, never 22/5432/6379.
  # tfsec:ignore:aws-ec2-no-public-ip
  associate_public_ip_address = true # NOSONAR

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    aws_region = var.aws_region
  })
  user_data_replace_on_change = false

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required" # IMDSv2 only
  }

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  key_name = var.key_name != "" ? var.key_name : null

  tags = {
    Name = "blocbuy-prod-backend"
  }

  depends_on = [aws_ecr_repository.backend]
}
