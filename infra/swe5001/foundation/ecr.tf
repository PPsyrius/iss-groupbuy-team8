resource "aws_ecr_repository" "image" {
  for_each = var.images

  name                 = "swe5001/${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  # AWS-managed aws/ecr key: no customer-managed key to pay for or schedule for deletion.
  encryption_configuration {
    encryption_type = "KMS"
  }
}

# Keep the last 10 images per repository (refactoring plan, section 3.3). Releases are
# referenced by digest from SSM, and one promotion per sprint needs far fewer.
resource "aws_ecr_lifecycle_policy" "image" {
  for_each = aws_ecr_repository.image

  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain the newest 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
