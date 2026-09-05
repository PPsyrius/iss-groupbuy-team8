# Archived Bitovi EC2 Deployment Approach

**Status:** retired; do not use for current deployments.

The project previously evaluated `bitovi/github-actions-deploy-docker-to-ec2`, which would create and
manage EC2, networking and supporting resources through Terraform. The team subsequently decided to
reuse its existing EC2 instance and its existing PostgreSQL and Redis services.

The active workflows contain no Bitovi action and perform no Terraform apply or destroy operation.
The current operational source of truth is:

- [Existing EC2 Backend Deployment Operator Guide](backend-ec2-pipeline-operator-guide.md)
- [中文：现有 EC2 后端部署运维指南](backend-ec2-pipeline-operator-guide.zh-CN.md)
- [EC2 backend runtime bundle](../../deployment/backend/README.md)

The earlier procedure remains available in Git history only as decision evidence. Do not restore its
`BACKEND_DOT_ENV`, Terraform-state, VPC, ELB, or stack-destroy instructions into the active pipeline.

Terraform may be considered later as a separate infrastructure-import/migration project. That work
must first define resource ownership, remote state, backups, drift handling and destructive-change
approval; it is not part of the current backend deployment method.
