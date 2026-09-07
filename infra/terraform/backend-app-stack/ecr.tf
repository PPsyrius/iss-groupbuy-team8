resource "aws_ecr_repository" "backend" {
  name                 = var.ecr_repository
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# One source of truth for retention: the lifecycle policy already committed for the
# dev deployment (design doc §6.2).
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy     = file("${path.module}/../../../deployment/backend/ecr-lifecycle-policy.json")
}
