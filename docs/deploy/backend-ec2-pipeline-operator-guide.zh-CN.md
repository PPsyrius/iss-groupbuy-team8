# 现有 EC2 后端部署运维指南

**状态：** 当前实现

**环境：** `backend-dev`

**基础设施策略：** 复用已存在的 EC2，第一阶段不运行 Terraform 或 Bitovi

**交付路径：** GitHub Actions -> Amazon ECR -> AWS Systems Manager Run Command

本文是 BlocBuy 后端部署的中文标准操作手册。EC2、PostgreSQL 和 Redis 已由团队提前创建；流水线
只更新后端容器，不创建、修改或删除 VPC、EC2、EBS、安全组、负载均衡、数据库、Redis 或数据卷。

## 1. 架构与边界

```text
Pull Request
  ├── ci-backend：构建、测试、契约、启动冒烟测试、Trivy 镜像扫描
  └── security：密钥、依赖、配置、Workflow 与 Shell 策略检查

合并/推送到 dev（全部检查通过且 ENABLE_EC2_DEPLOY=true）
  -> GitHub OIDC 临时承担 ECR 发布角色
  -> 推送已测试镜像并固定 sha256 digest
  -> backend-dev 受保护环境
  -> GitHub OIDC 临时承担 SSM 部署角色
  -> SSM 把无密钥部署包安装到 /opt/blocbuy/backend
  -> deploy.sh 只替换 blocbuy-backend
  -> 健康检查；失败时自动恢复上一镜像

现有 EC2
  ├── PostgreSQL       127.0.0.1:5432
  ├── Redis            127.0.0.1:6379
  ├── backend          host network，端口 8589
  └── 运行时密钥       /etc/blocbuy/backend.env，root:root 0600
```

CI 会强制部署 Compose 只有一个 `backend` 服务，并对 SSM 压缩包执行精确文件白名单检查。因此新增
数据库、Redis、数据卷、端口或意外的 `.env` 文件都会使检查失败。

## 2. 代码库中的部署资产

| 路径 | 用途 |
|---|---|
| [`.github/workflows/ci-backend.yml`](../../.github/workflows/ci-backend.yml) | 质量门、镜像发布、自动部署 |
| [`.github/workflows/security.yml`](../../.github/workflows/security.yml) | 密钥、依赖、配置、Workflow 与脚本检查 |
| [`.github/workflows/rollback-backend.yml`](../../.github/workflows/rollback-backend.yml) | 受控手工回滚 |
| [`deployment/backend/compose.yml`](../../deployment/backend/compose.yml) | 仅后端的 EC2 运行定义 |
| [`deployment/backend/bin/`](../../deployment/backend/bin/) | 预检、部署、健康检查、回滚脚本 |
| [`deployment/backend/backend.env.example`](../../deployment/backend/backend.env.example) | 不含真实密钥的主机模板 |
| [`deployment/backend/iam/`](../../deployment/backend/iam/) | 使用前必须替换占位符的最小权限 IAM 示例 |

## 3. 先记录配置值

| 值 | 示例 |
|---|---|
| AWS Account ID | `123456789012` |
| Region | `ap-southeast-1` |
| 现有 EC2 Instance ID | `i-0123456789abcdef0` |
| ECR Repository | `blocbuy-backend` |
| 后端 URL | `https://api-dev.example.com`，也可暂时留空 |
| EC2 Runtime Role | `BlocBuyEc2Runtime` |
| GitHub ECR Role | `BlocBuyGitHubEcrPush` |
| GitHub Deploy Role | `BlocBuyGitHubBackendDeploy` |

数据库、Redis、Stripe 等真实密钥只能保存在 EC2 的 `/etc/blocbuy/backend.env`，不能保存到 GitHub
Variables、Workflow 输入、IAM JSON 或 SSM 参数。

## 4. 配置 ECR

```bash
aws ecr create-repository \
  --repository-name blocbuy-backend \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true \
  --region ap-southeast-1

aws ecr put-lifecycle-policy \
  --repository-name blocbuy-backend \
  --lifecycle-policy-text file://deployment/backend/ecr-lifecycle-policy.json \
  --region ap-southeast-1
```

若 Repository 已存在，使用 `describe-repositories` 检查，并通过 `put-image-tag-mutability` 设置为
`IMMUTABLE`。流水线实际部署 `repository@sha256:...`，不会用可变 tag。

## 5. 配置 EC2 Instance Role

1. 给 EC2 Role 附加 AWS 托管策略 `AmazonSSMManagedInstanceCore`；若团队统一使用 Default Host
   Management Configuration，则遵循账号标准。
2. 复制
   [`ec2-ecr-pull-policy.json.example`](../../deployment/backend/iam/ec2-ecr-pull-policy.json.example)，
   替换 `AWS_REGION`、`AWS_ACCOUNT_ID`、`ECR_REPOSITORY`，然后作为 Customer Managed Policy 附加。
3. 确认对应 Instance Profile 已绑定到这台现有 EC2。

不要使用 `AmazonEC2ContainerRegistryFullAccess` 或 `AmazonSSMFullAccess`。EC2 只需要 SSM Agent
通信权限和指定 ECR Repository 的 pull 权限。

```bash
aws ssm describe-instance-information \
  --filters Key=InstanceIds,Values=i-0123456789abcdef0 \
  --region ap-southeast-1
```

启用部署前，Instance 必须显示为 Online。

## 6. 配置 GitHub OIDC 与两个 AWS Role

在 AWS IAM 中只创建一次 GitHub OIDC Provider：

- Provider URL：`https://token.actions.githubusercontent.com`
- Audience：`sts.amazonaws.com`

分别创建两个 Role：

| Role | Trust 范围 | Permission 示例 |
|---|---|---|
| ECR Publisher | 本 Repository 的 `dev` branch | [`github-ecr-push-policy.json.example`](../../deployment/backend/iam/github-ecr-push-policy.json.example) |
| EC2 Deploy/Rollback | 本 Repository 的 `backend-dev` environment | [`github-ssm-deploy-policy.json.example`](../../deployment/backend/iam/github-ssm-deploy-policy.json.example) |

同目录提供两个 Trust Policy 示例。替换所有大写占位符，并用 IAM Access Analyzer 验证。2026 年
7 月 15 日之后创建或启用了 immutable OIDC subject 的 Repository，其 `sub` 会包含 Owner ID 和
Repository ID；必须按本 Repository 的真实 subject 精确修改，不能改成宽泛通配符。

部署 Role 的 OIDC subject 使用 Environment 名称，不包含 branch。因此 GitHub 的 `backend-dev`
Environment 必须额外限制为只有 `dev` 可以部署。

SSM Role 只允许对一个 Instance 使用 `AWS-RunShellScript`。该权限仍属于高权限：Linux 上 SSM Agent
以 root 执行命令，所以不能让 PR、Fork、普通 IAM User 或其他 Repository 承担此 Role。

## 7. 初始化现有 EC2

主机必须具备：Linux、在线 SSM Agent、Docker Engine、Docker Compose Plugin、AWS CLI v2、Bash、
常用 GNU 工具，并且 `/opt/blocbuy/backend` 至少有 2 GiB 空间。PostgreSQL/Redis 必须可通过
`127.0.0.1:5432` 和 `127.0.0.1:6379` 访问。

首次部署会通过 SSM 自动安装 Compose 与脚本；EC2 不需要 clone Repository。管理员只需安全创建
本机环境文件：

```bash
sudo install -d -m 0750 /etc/blocbuy
sudo install -m 0600 /dev/null /etc/blocbuy/backend.env
sudo chown root:root /etc/blocbuy/backend.env
sudoedit /etc/blocbuy/backend.env
```

按照 [`backend.env.example`](../../deployment/backend/backend.env.example) 填写并替换所有占位符。

```bash
sudo stat -c '%U:%G %a %n' /etc/blocbuy/backend.env
sudo ss -lnt | grep -E '127\.0\.0\.1:(5432|6379)'
```

文件权限必须是 `root:root 600`。安全组不能向公网开放 PostgreSQL/Redis。端口 `8589` 只允许来自
反向代理/负载均衡安全组或团队测试 CIDR。

首次 Workflow 安装部署包后可执行：

```bash
sudo /opt/blocbuy/backend/bin/preflight.sh
```

## 8. 配置 GitHub

创建 `backend-dev` Environment，只允许 `dev` branch。若团队希望部署前人工审批，则添加 Required
Reviewer。

### Repository Actions Variables

ECR 发布 Job 不引用 Environment，所以下列值必须是 Repository Variables：

| Variable | 示例 | 必需 |
|---|---|---|
| `AWS_ACCOUNT_ID` | `123456789012` | 是 |
| `AWS_REGION` | `ap-southeast-1` | 是 |
| `ECR_REPOSITORY` | `blocbuy-backend` | 是 |
| `AWS_ECR_PUSH_ROLE_ARN` | `arn:aws:iam::123456789012:role/BlocBuyGitHubEcrPush` | 是 |
| `ENABLE_EC2_DEPLOY` | 验证完成前 `false`，之后 `true` | 是 |

### `backend-dev` Environment Variables

| Variable | 示例 | 必需 |
|---|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::123456789012:role/BlocBuyGitHubBackendDeploy` | 是 |
| `BACKEND_EC2_INSTANCE_ID` | `i-0123456789abcdef0` | 是 |
| `BACKEND_URL` | `https://api-dev.example.com` | 否 |

GitHub 中不需要任何长期 AWS Access Key，也不需要 `BACKEND_DOT_ENV`。AWS 凭证由 OIDC 临时签发。

### Branch Protection

保护 `dev`：必须 PR、CODEOWNERS Review、禁止 Force Push，并要求以下检查：

- `build-quality`、`contract-check`、`docker`；
- `workflow-lint`、`deployment-bundle`、`gitleaks`、`trivy-deps`、`trivy-config`；
- `sonar`、`codeql` 只在团队已稳定启用对应服务后设为 Required。

Push-only 的部署 Job 不应设置为 PR Required Check。

## 9. 第一次部署

1. 验证 ECR、IAM、SSM 与主机期间保持 `ENABLE_EC2_DEPLOY=false`。
2. 通过 PR 把部署改动合并到受保护的 `dev`。
3. 确认 CI 与 Security 全绿。
4. 设置 `ENABLE_EC2_DEPLOY=true`。
5. 合并或推送一个后端/部署改动到 `dev`。
6. 如配置了 Reviewer，审批 `backend-dev`。
7. 检查 `deploy-existing-ec2` 的 Summary、digest 和 SSM 输出。

```bash
curl --fail --silent https://api-dev.example.com/actuator/health
```

当前 digest 记录在 `/opt/blocbuy/backend/state/current-image`。部署不会重启或删除 PostgreSQL/Redis。

## 10. 回滚与冻结

在 **Actions -> rollback-backend -> Run workflow**：

1. 选择 `dev` branch；
2. `target_image` 留空会使用主机记录的上一 digest；也可填同一 ECR Repository 中经过评审的
   `repository@sha256:...`；
3. 填写事故/变更原因；
4. 勾选确认；
5. 完成 Environment 审批；
6. 检查 resulting image、SSM command ID 与健康状态。

部署和回滚共用同一个不可取消的 Concurrency Group，不会同时修改主机。应用回滚不能逆转 Flyway
Migration；所有自动部署的 Migration 必须向后兼容。

事故冻结时把 `ENABLE_EC2_DEPLOY=false`。CI、Security 与镜像冒烟测试仍运行，但不会发布或部署新
镜像；Manual Rollback 仍可用于恢复。

## 11. 常见故障

| 现象 | 检查项 |
|---|---|
| ECR Role 无法 Assume | OIDC Audience、精确 branch `sub`、Repository Variable ARN |
| Deploy Role 无法 Assume | 精确 environment `sub`、`backend-dev` branch rule |
| SSM SendCommand 被拒绝 | Instance/Region、SSM Document ARN、单 Instance ARN |
| Instance 不在线 | SSM Agent、Instance Profile、NAT/VPC Endpoint |
| ECR Pull 被拒绝 | EC2 Role 的 Token 与指定 Repository Pull 权限 |
| Preflight 失败 | env 文件、`root:root 600`、必需键、占位符、磁盘空间 |
| 后端不健康 | SSM stdout/stderr、`docker logs blocbuy-backend`、Flyway、凭证 |
| Bundle Policy 失败 | 只能包含 `compose.yml` 与四个已评审脚本 |
| 没有 Previous Image | 手工提供同一 ECR 的已评审 immutable digest |

不要把 `/etc/blocbuy/backend.env`、Docker Credential 或应用密钥粘贴到 Issue/Workflow Log。

## 12. DevSecOps 证据与后续 IaC

建议保留：PR/Review、Branch Protection、绿色质量与安全门、SARIF、ECR digest、Environment 审批、
SSM Command ID、CloudTrail AssumeRole/SendCommand 记录、回滚结果，以及数据库 Backup/Restore 演练。

当前阶段把应用交付、策略门和 IAM 示例纳入版本控制，但不负责创建 EC2/VPC，因此仍存在基础设施
手工漂移风险。所有主机、IAM、网络变更都要写入团队 Change Log。只有在状态归属、备份、Import 与
破坏性变更审批方案明确后，才单独启动 Terraform 阶段。

英文完整版与官方参考链接见
[`backend-ec2-pipeline-operator-guide.md`](backend-ec2-pipeline-operator-guide.md)。
