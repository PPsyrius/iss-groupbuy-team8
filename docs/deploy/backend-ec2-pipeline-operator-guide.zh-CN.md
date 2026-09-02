# BlocBuy 后端 CI/CD 到 AWS EC2 完整操作手册

本文是一份由操作者逐项执行的落地手册。目标是从当前仓库状态出发，实现以下自动化链路：

```text
功能分支提交 PR 到 dev
  -> 质量、契约、镜像、安全门禁
  -> 审查并合并 PR
  -> dev 分支再次执行后端门禁
  -> 门禁全部通过
  -> Bitovi Action 创建或更新 AWS 资源
  -> 将仓库复制到 EC2 并运行 Docker Compose
  -> ELB 检查 /actuator/health
  -> 自动部署完成
```

本文部署开发环境 `backend-dev`。PostgreSQL、Redis 和后端运行在同一台 EC2，适用于课程项目、
开发和演示环境，不是高可用生产架构。

## 1. 当前基线与待完成事项

远程仓库为 `PPsyrius/iss-groupbuy-team8`，后端集成分支为 `dev`。

当前已经具备：

- `.github/workflows/ci-backend.yml`
  - `build-quality`：格式、Checkstyle、测试、ArchUnit、SpotBugs、JaCoCo；
  - `contract-check`：OpenAPI 漂移和破坏性变更检查；
  - `sonar`：仅配置 `SONAR_HOST_URL` 后执行；
  - `docker`：构建镜像，以 PostgreSQL 18、Redis 8.1 冒烟测试，并进行 Trivy 镜像扫描。
- `.github/workflows/security.yml`：Gitleaks、Trivy 依赖扫描、可选 CodeQL。
- `backend/Dockerfile`：Java 21、多阶段构建、非 root 用户、端口 `8589`。
- Docker Spring Profile：连接 `postgres:5432` 和 `redis:6379`。
- 健康检查：`GET /actuator/health`。

还需由你完成：

- 创建根目录 `docker-compose.yaml` 和 `.gha-ignore`；
- 把 EC2 部署 job 接入现有 `ci-backend.yml`；
- 创建 AWS 部署身份、预算和凭证；
- 创建 GitHub Environment、Secrets、Variables；
- 配置 `dev` 分支保护；
- 提交 PR、合并、观察自动部署并验收。

最终依赖关系：

```text
ci-backend.yml
├── build-quality ─────┬── docker ──────────────┐
├── contract-check ────┼────────────────────────┤
└── sonar（成功或跳过）┴────────────────────────┤
                                                └── deploy-ec2（仅 push dev）

security.yml
├── gitleaks       ── PR 必需检查
└── trivy-deps     ── PR 必需检查
```

## 2. 固定部署参数

执行前记录下表。资源标识符和 Terraform 状态名一旦使用，不要随意修改。

| 参数 | 建议值 |
|---|---|
| GitHub Environment | `backend-dev` |
| 自动部署分支 | `dev` |
| AWS 区域 | `ap-southeast-1` |
| AWS 可用区 | `ap-southeast-1a` |
| AWS 资源标识符 | `ppsyrius-iss-groupbuy-team8-backend-dev` |
| Terraform 状态名 | `ppsyrius-iss-groupbuy-team8-backend-dev` |
| VPC CIDR | `10.20.0.0/16` |
| 公有子网 | `10.20.10.0/24` |
| EC2 | `t3.medium`、30 GiB 根卷 |
| 数据库 | PostgreSQL 18，数据库名 `Blocbuy` |
| Redis | Redis 8.1 |
| 后端端口 | `8589` |

必须确认可用区属于所选区域、公有子网属于 VPC CIDR。PostgreSQL 必须为 18，因为当前迁移使用
`uuidv7()`。

## 3. AWS 侧操作

### 3.1 账户安全

1. 登录 AWS Console；
2. 为根用户启用 MFA；
3. 确认根用户没有 Access Key；
4. 后续使用专用 IAM 身份，不使用根用户。

AWS 官方建议不要为根用户创建访问密钥，并优先使用临时凭证。Bitovi 当前接口接收 Access Key
ID、Secret Access Key，以及可选 Session Token，因此必须使用专用部署身份或实验室临时凭证。

### 3.2 创建费用预算

打开 **Billing and Cost Management -> Budgets -> Create budget**：

1. 选择 **Cost budget** 和 **Monthly**；
2. 输入团队可接受的月度上限；
3. 添加 50%、80%、100% Actual cost 邮件告警；
4. 填写团队邮箱并确认通知。

EC2、EBS、Classic Load Balancer、S3、CloudWatch 和流量都可能收费。

### 3.3 PowerUserAccess 的限制

当前操作者通过 IAM Identity Center 使用账户 `402178232802` 的 `PowerUserAccess`。该 AWS 托管
策略允许管理大部分业务资源，但明确排除绝大多数 `iam:*`；仅凭此权限通常不能：

- 创建 GitHub OIDC Identity Provider；
- 创建或修改 GitHub Actions 部署 Role 及其 trust policy；
- 创建 EC2 Instance Role/Instance Profile；
- 授予 `iam:PassRole`；
- 修改自己的 IAM Identity Center Permission Set。

因此不要尝试从 PowerUserAccess 会话导出短期凭证并长期保存到 GitHub。它会过期，也没有解决
Action 创建/传递 IAM Role 所需的权限。需要 AWS 账户管理员执行一次 IAM 引导配置。

### 3.4 请管理员创建 GitHub OIDC Provider

如果账户中尚不存在，管理员创建：

```text
Provider URL: https://token.actions.githubusercontent.com
Audience:     sts.amazonaws.com
```

OIDC Provider 是账户级资源；已经存在时复用，不能重复创建。

### 3.5 请管理员创建 EC2 运行 Role/Profile

建议名称均使用 `BlocBuyEc2InstanceRole`：

- Role trust principal：`ec2.amazonaws.com`；
- Instance Profile：`BlocBuyEc2InstanceRole`，包含同名 Role；
- 附加 `AmazonSSMManagedInstanceCore`，用于 Session Manager；
- 附加 `CloudWatchAgentServerPolicy`，供实例写入 CloudWatch 日志。

这样部署 Action 可以使用现有 Instance Profile，不需要动态创建 IAM Role。

### 3.6 请管理员创建 GitHub 部署 Role

建议名称：`GitHubActionsBlocBuyBackendDev`。

其 OIDC trust policy 必须限制为本仓库和 GitHub Environment `backend-dev`。该私有仓库创建于
2026-08-24，GitHub API 返回 Owner ID `19505219`、Repository ID `1344446887`，因此使用新的
不可变 subject：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::402178232802:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:environment:backend-dev"
        }
      }
    }
  ]
}
```

可用已登录 GitHub CLI 复核这些值：

```bash
gh api repos/PPsyrius/iss-groupbuy-team8 \
  --jq '{repository_id: .id, owner_id: .owner.id, created_at: .created_at}'
```

不要使用 `repo:*` 或只限制组织的通配信任。因为 job 引用了 Environment，OIDC subject 使用
`environment:backend-dev`，不是 `ref:refs/heads/dev`；同时必须在 GitHub Environment 中把允许部署
分支限制为 `dev`。

部署 Role 可附加 `PowerUserAccess` 管理 EC2/VPC/ELB/S3/CloudWatch 等资源，并额外增加仅针对预建
EC2 Role/Profile 的 IAM 读取和 PassRole 权限：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PassBlocBuyEc2Role",
      "Effect": "Allow",
      "Action": ["iam:GetRole", "iam:PassRole"],
      "Resource": "arn:aws:iam::402178232802:role/BlocBuyEc2InstanceRole"
    },
    {
      "Sid": "ReadBlocBuyInstanceProfile",
      "Effect": "Allow",
      "Action": "iam:GetInstanceProfile",
      "Resource": "arn:aws:iam::402178232802:instance-profile/BlocBuyEc2InstanceRole"
    },
    {
      "Sid": "ListInstanceProfiles",
      "Effect": "Allow",
      "Action": [
        "iam:ListInstanceProfiles",
        "iam:ListInstanceProfilesForRole",
        "iam:ListAttachedRolePolicies"
      ],
      "Resource": "*"
    }
  ]
}
```

管理员最终返回给你以下两个非秘密值即可，不需要 Access Key：

```text
Deploy Role ARN: arn:aws:iam::402178232802:role/GitHubActionsBlocBuyBackendDev
Instance Profile: BlocBuyEc2InstanceRole
```

## 4. 准备仓库文件

### 4.1 更新功能分支

```bash
git status
git fetch origin
git checkout feature/backend-structure-20260830
git pull --ff-only origin feature/backend-structure-20260830
```

若团队已建立其他部署功能分支，使用实际分支。不要覆盖工作区已有修改。

### 4.2 创建根目录 `docker-compose.yaml`

```yaml
name: blocbuy-backend

services:
  postgres:
    image: postgres:18
    restart: unless-stopped
    environment:
      POSTGRES_DB: Blocbuy
      POSTGRES_USER: ${DB_USERNAME:?DB_USERNAME is required}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
    networks: [data]

  redis:
    image: redis:8.1
    restart: unless-stopped
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
    command:
      - sh
      - -c
      - exec redis-server --appendonly yes --requirepass "$$REDIS_PASSWORD"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli --no-auth-warning -a \"$$REDIS_PASSWORD\" ping | grep -q PONG"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 10s
    networks: [data]

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    restart: unless-stopped
    env_file:
      - .env
    ports:
      - "8589:8589"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    stop_grace_period: 30s
    networks:
      - edge
      - data

networks:
  edge:
  data:
    internal: true

volumes:
  postgres-data:
  redis-data:
```

说明：PostgreSQL/Redis 无宿主机端口；后端 `8589:8589` 供 ELB；不单独指定 Compose 日志驱动，
以便使用 Action 配置的 CloudWatch 日志；命名卷不等于备份；禁止启用 `docker_full_cleanup`。

### 4.3 创建根目录 `.gha-ignore`

```gitignore
.git/**
.codegraph/**
.idea/**
.agents/**
frontend/**
docs/**
contracts/**
backend/**/target/**
**/.DS_Store
```

不要排除 Compose、Dockerfile、Maven Wrapper、POM 或任一后端模块。

### 4.4 本地验证

生成两个不同密码：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

在根目录创建仅供本地使用、不得提交的 `.env`：

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=<第一个随机密码>
REDIS_PASSWORD=<第二个随机密码>
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

执行：

```bash
docker compose config --quiet
docker compose build backend
docker compose up -d
docker compose ps
curl --fail --show-error http://localhost:8589/actuator/health
```

成功标准：PostgreSQL/Redis healthy、backend running、健康接口返回 `{"status":"UP"}`。失败时：

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
```

验证后执行 `docker compose down`。不要使用 `--volumes`。提交前再次确认 `.env` 被忽略。

## 5. 接入现有 `ci-backend.yml`

### 5.1 扩充触发路径

把 `.github/workflows/ci-backend.yml` 顶部的 `on` 替换为：

```yaml
on:
  push:
    branches: [dev]
    paths:
      - "backend/**"
      - "contracts/**"
      - "docker-compose.yaml"
      - ".gha-ignore"
      - ".github/workflows/ci-backend.yml"
  pull_request:
    branches: [dev]
    paths:
      - "backend/**"
      - "contracts/**"
      - "docker-compose.yaml"
      - ".gha-ignore"
      - ".github/workflows/ci-backend.yml"
```

否则只改 Compose 时不会触发 CI 和部署。

### 5.2 在文件末尾追加 `deploy-ec2`

它必须与现有四个 job 同级，不能放在 `docker.steps` 内。

```yaml
  deploy-ec2:
    name: deploy-ec2
    needs:
      - build-quality
      - contract-check
      - sonar
      - docker
    if: >-
      always() &&
      github.event_name == 'push' &&
      github.ref == 'refs/heads/dev' &&
      needs.build-quality.result == 'success' &&
      needs.contract-check.result == 'success' &&
      needs.docker.result == 'success' &&
      (needs.sonar.result == 'success' || needs.sonar.result == 'skipped')
    runs-on: ubuntu-latest
    timeout-minutes: 45
    permissions:
      contents: read
      id-token: write
    environment:
      name: backend-dev
      url: ${{ steps.deploy.outputs.vm_url }}
    steps:
      - id: aws-credentials
        name: Assume AWS deployment role with GitHub OIDC
        uses: aws-actions/configure-aws-credentials@v6.2.3
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}
          allowed-account-ids: "402178232802"
          role-session-name: BlocBuy-${{ github.run_id }}
          output-credentials: true

      - id: deploy
        name: Provision EC2 and deploy Docker Compose
        uses: bitovi/github-actions-deploy-docker-to-ec2@v1
        with:
          aws_access_key_id: ${{ steps.aws-credentials.outputs.aws-access-key-id }}
          aws_secret_access_key: ${{ steps.aws-credentials.outputs.aws-secret-access-key }}
          aws_session_token: ${{ steps.aws-credentials.outputs.aws-session-token }}
          aws_default_region: ${{ vars.AWS_REGION }}

          aws_resource_identifier: ppsyrius-iss-groupbuy-team8-backend-dev
          tf_state_file_name: ppsyrius-iss-groupbuy-team8-backend-dev
          aws_additional_tags: '{"Project":"blocbuy","Environment":"dev","ManagedBy":"github-actions"}'

          aws_ec2_instance_type: t3.medium
          aws_ec2_instance_root_vol_size: 30
          aws_ec2_instance_root_vol_preserve: true
          aws_ec2_instance_public_ip: true
          aws_ec2_iam_instance_profile: BlocBuyEc2InstanceRole

          aws_vpc_create: true
          aws_vpc_cidr_block: 10.20.0.0/16
          aws_vpc_public_subnets: 10.20.10.0/24
          aws_vpc_availability_zones: ${{ vars.AWS_AVAILABILITY_ZONE }}

          aws_elb_create: true
          aws_elb_app_port: 8589
          aws_elb_app_protocol: HTTP
          aws_elb_listen_port: 80
          aws_elb_listen_protocol: HTTP
          aws_elb_healthcheck: HTTP:8589/actuator/health

          docker_repo_app_directory: /
          docker_repo_app_directory_cleanup: true
          docker_backup_retention: 5
          docker_remove_orphans: true
          docker_full_cleanup: false
          ansible_start_docker_timeout: 900

          env_ghs: ${{ secrets.BACKEND_DOT_ENV }}

          docker_cloudwatch_enable: true
          docker_cloudwatch_retention_days: 14
```

其行为是：PR 时跳过；只有 push 到 `dev` 才部署；三个必需门禁必须成功；Sonar 未配置时允许
skipped，配置后必须成功；OIDC step 为本次 job 换取短期 AWS 凭证，再通过 step output 传给
Bitovi，因此 GitHub 不保存长期 AWS Key。

`configure-aws-credentials@v6.2.3` 和 Bitovi `@v1` 与本文核对时的上游文档一致。开发验证稳定后，
建议把两个第三方 Action 都固定到团队审查过的完整 commit SHA。

### 5.3 扩充 `security.yml` 路径

在 `.github/workflows/security.yml` 的 `push.paths` 和 `pull_request.paths` 都加入：

```yaml
      - "docker-compose.yaml"
      - ".github/workflows/ci-backend.yml"
```

每个事件的完整路径段建议为：

```yaml
    paths:
      - "backend/**"
      - "docker-compose.yaml"
      - ".gitleaks.toml"
      - ".github/workflows/ci-backend.yml"
      - ".github/workflows/security.yml"
```

安全工作流独立运行，不能被另一个工作流直接 `needs`；通过分支保护确保它在合并前成功。

## 6. 创建 GitHub Environment

进入 **Settings -> Environments -> New environment**，名称填写 `backend-dev`。

1. Deployment branches and tags 仅允许 `dev`；
2. 完全自动部署时不配置 Required reviewers；
3. 若配置 reviewer，流水线会自动触发但停在等待批准；
4. 某些 GitHub 套餐限制私有仓库的 Environment secrets/reviewers；界面不可用时可退回
   Repository Actions secrets，但必须保留分支保护和 deploy job 的 push/dev 条件。

## 7. 配置 GitHub Variables 和 Secrets

进入 **Settings -> Environments -> backend-dev**。

### 7.1 Variables

| Name | Value |
|---|---|
| `AWS_REGION` | `ap-southeast-1` |
| `AWS_AVAILABILITY_ZONE` | `ap-southeast-1a` |
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::402178232802:role/GitHubActionsBlocBuyBackendDev` |

选择其他区域时必须同时修改 `AWS_REGION` 和 `AWS_AVAILABILITY_ZONE`。

### 7.2 AWS 凭证

OIDC 方案不创建 `AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY` 或 `AWS_SESSION_TOKEN` GitHub
Secrets。Role ARN 不是秘密，保存在上述 Environment Variable 即可。每次 job 由 GitHub OIDC
获取短期凭证，job 结束后自然失效。

### 7.3 应用 Secret

创建 `BACKEND_DOT_ENV`，内容为：

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=<长随机数据库密码>
REDIS_PASSWORD=<另一个长随机 Redis 密码>
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

要求：密码不同、没有占位符、行首没有 `export`、等号两侧没有空格。Stripe 未接入时留空；接入
后先用 test mode。Webhook 路径为 `/api/v1/webhooks/stripe`。

Bitovi 会把 `env_ghs` 合并为 EC2 上的 `.env`。不要提交真实 `repo_env` 或 `.env`。

### 7.4 可选 Sonar/CodeQL

- 不设置 `SONAR_HOST_URL`：Sonar skipped，部署继续；
- 设置后还要配置 `SONAR_TOKEN`，SonarCloud 还需 `SONAR_ORGANIZATION`；
- Sonar Quality Gate 失败会阻止部署；
- `ENABLE_CODEQL=true` 才启用 CodeQL，私有仓库通常需要 GitHub Advanced Security。

不要只配置一半 Sonar 参数。

## 8. Actions 权限与分支保护

### 8.1 Actions 权限

进入 **Settings -> Actions -> General**。确认 Actions 已启用。若只允许选定 Action，至少允许：

- `actions/checkout`、`actions/setup-java`、`actions/cache`、`actions/upload-artifact`；
- `aws-actions/configure-aws-credentials`；
- `aquasecurity/trivy-action`；
- `github/codeql-action`；
- `gitleaks/gitleaks-action`；
- `bitovi/github-actions-deploy-docker-to-ec2`。

### 8.2 `dev` 分支保护

进入 **Settings -> Rules -> Rulesets**，为 `dev` 配置：

1. 必须通过 PR 合并；
2. 至少 1 名审批者；
3. 必需状态检查；
4. 合并前分支必须最新；
5. 阻止普通成员直接 push 到 `dev`；
6. 建议禁止无记录绕过。

先让本次 PR 跑过一次，再选择准确的检查名。至少要求：

```text
build-quality
contract-check
docker
gitleaks
trivy-deps
```

完整配置 Sonar 后再把 `sonar` 设为必需。不要把默认 skipped 的 CodeQL 设为必需。

## 9. 提交配置 PR

先确认没有秘密进入 Git：

```bash
git status --short
git status --ignored --short
git diff --check
git diff -- docker-compose.yaml .gha-ignore .github/workflows/ci-backend.yml .github/workflows/security.yml
```

然后：

```bash
git add docker-compose.yaml .gha-ignore
git add .github/workflows/ci-backend.yml .github/workflows/security.yml
git add docs/deploy/backend-ec2-pipeline-operator-guide.zh-CN.md
git diff --cached --check
git diff --cached
git commit -m "feat(ci): deploy backend to EC2 after quality gates"
git push origin feature/backend-structure-20260830
```

若使用其他功能分支，替换最后一行分支名。在 GitHub 创建 PR：base 为 `dev`，compare 为实际功能
分支。PR 记录 AWS 区域、固定资源标识符、本地健康检查结果、费用风险和回滚责任人，但绝不记录
凭证。

## 10. PR 阶段验收

PR 创建后应看到：

| Job | 预期 |
|---|---|
| `build-quality` | 成功 |
| `contract-check` | 成功 |
| `docker` | 构建、健康检查、Trivy 成功 |
| `sonar` | 未配置时 skipped；配置后成功 |
| `gitleaks` | 成功 |
| `trivy-deps` | 成功 |
| `deploy-ec2` | **skipped** |

部署在 PR 阶段跳过是正确的。Gitleaks 检出真实秘密时应立即轮换；只从最新 commit 删除仍不足以
消除 Git 历史泄漏。

## 11. 合并并触发首次自动部署

合并前确认：

- Budget 已创建；
- `backend-dev` Environment 存在；
- `AWS_DEPLOY_ROLE_ARN`、Region/AZ Variables 和 `BACKEND_DOT_ENV` 已保存；
- OIDC Provider、部署 Role、EC2 Instance Profile 已由管理员完成；
- PR 必需检查全部成功；
- 没有另一条部署在执行。

合并到 `dev` 后：

1. GitHub 产生 push 事件；
2. `ci-backend` 从头执行；
3. `deploy-ec2` 等待四个依赖 job；
4. Bitovi 创建 VPC、ELB、EC2、状态/日志资源；
5. Action 安装 Docker、复制仓库、生成 `.env` 并运行 Compose；
6. 成功后 GitHub Environment 显示 `vm_url`。

首次运行需要创建基础设施、下载镜像、解析 Maven 依赖并在 EC2 构建，通常明显慢于后续部署。
不要因为慢而修改资源标识符重跑。

## 12. 部署后验收

在 **Actions -> ci-backend -> 最新 dev push -> deploy-ec2** 记录 `vm_url`。然后执行：

```bash
curl --fail --show-error "http://<ELB_HOST>/actuator/health"
curl --fail --show-error "http://<ELB_HOST>/v3/api-docs" -o /tmp/blocbuy-openapi.json
curl --fail --show-error --head "http://<ELB_HOST>/swagger-ui.html"
```

健康接口必须返回 `{"status":"UP"}`。

在 AWS Console 检查：

1. EC2 实例 running，类型/磁盘/标签正确；
2. Classic ELB HTTP 80 转发 8589，健康检查为 `HTTP:8589/actuator/health`；
3. 实例在 ELB 中为 healthy/in service；
4. 安全组未向公网开放 5432、6379，也没有手工添加 `0.0.0.0/0:22`；
5. CloudWatch Docker 日志组存在、保留 14 天、日志无秘密；
6. S3 Terraform 状态/日志桶启用 Block Public Access；
7. 不手工删除 Terraform 状态对象。

记录非敏感部署证据：Git SHA、Actions Run URL、区域、EC2 ID、ELB DNS、验收时间和操作者。

## 13. 常见失败处理

| 现象 | 处理 |
|---|---|
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | 核对 OIDC Provider、aud、实际 sub 格式、Environment 名称和 Role trust policy |
| `AccessDenied` | 记录被拒绝 API/ARN，请管理员补最小权限；不要改资源名绕过 |
| 可用区失败 | 确认 Region/AZ 匹配且支持 `t3.medium` |
| Compose 找不到 | 确认文件在根目录、`docker_repo_app_directory: /`、未被 `.gha-ignore` 排除 |
| 构建被 kill/超时 | 检查 EC2 内存、磁盘、网络；保留 900 秒超时，必要时升级规格 |
| 无法解析 postgres/redis | 确认 Docker profile、服务名和 `data` 网络 |
| ELB unhealthy | 核对 Spring、Dockerfile、Compose、ELB 都使用 8589，并查看后端启动日志 |
| Sonar 导致部署 skipped | 配齐 Token/Organization 并修复 Gate；暂不使用则删除 `SONAR_HOST_URL` |

数据库密码只在空卷首次初始化时生效。以后修改 GitHub Secret 不会自动修改 PostgreSQL 角色密码；
必须先在数据库内受控修改，再更新 Secret，不能通过删除数据卷解决。

## 14. EC2 诊断、备份与回滚

优先使用 Systems Manager Session Manager。若必须 SSH，只临时允许操作者单一公网 IP，结束后删除
规则。应用目录常用命令：

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
docker stats --no-stream
df -h
docker system df
```

禁止运行 `docker compose down --volumes` 或带 `--volumes` 的 `docker system prune`，也不要输出
Action 生成的 `.env`。

高风险发布前备份：

```bash
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "blocbuy-$(date +%Y%m%d-%H%M%S).dump"
```

备份必须复制到加密、版本化、限制访问的 S3，不能只留在同一 EC2 根卷，并应定期恢复演练。

代码回滚：找到最近成功 SHA，对故障 commit 创建 `git revert`，通过 PR 合并到 `dev`，让流水线
重新部署。保持资源标识符和状态名不变。代码回滚不会回滚 Flyway，数据库迁移必须另有方案。

## 15. 日常自动部署流程

首次配置完成后：

1. 功能分支向 `dev` 提 PR；
2. 必需检查和审查通过；
3. 合并 PR；
4. `dev` push 自动运行 `ci-backend`；
5. `deploy-ec2` 自动更新同一 EC2 环境；
6. 检查 Actions、ELB health 和代表性 API；
7. 记录 SHA 与验收结果。

当前 CI 并发设置会取消同一 ref 的旧运行。避免多人连续合并高风险部署变更；等待当前部署完成。

## 16. HTTPS 第二阶段

先完成 HTTP 首次部署。域名托管在 Route 53 时，再在 deploy job 加入：

```yaml
          aws_r53_enable: true
          aws_r53_domain_name: example.com
          aws_r53_sub_domain_name: api-dev
          aws_r53_create_sub_cert: true

          aws_elb_app_port: 8589
          aws_elb_app_protocol: HTTP
          aws_elb_listen_port: 443
          aws_elb_listen_protocol: SSL
          aws_elb_healthcheck: HTTP:8589/actuator/health
```

替换真实域名。Bitovi 文档提示 Terraform 管理的证书会随 stack 销毁；切换证书来源要遵循其
两阶段流程。Stripe Webhook 应在 HTTPS 完成后配置为：

```text
https://api-dev.example.com/api/v1/webhooks/stripe
```

## 17. 安全销毁

销毁前必须确认资源标识符、完成异地数据库备份、获得团队批准并记录需要保留的 EBS/S3/日志。
Bitovi 用 `tf_stack_destroy: true` 销毁，销毁工作流必须使用与部署相同的区域、资源标识符和状态名。

销毁后仍要检查并处理继续计费的 EBS 卷、快照、S3 桶、CloudWatch 日志、Secrets Manager、ACM
证书、Elastic IP 和 IAM Instance Profile。不要直接删除未知 Terraform 状态，否则剩余资源会失去
管理关系。

## 18. 最终完成清单

- [ ] 根用户 MFA、GitHub OIDC Provider、两个专用 IAM Role、AWS Budget 已完成；
- [ ] `docker-compose.yaml` 本地构建和健康检查通过；
- [ ] `.env`、AWS Key、数据库密码、Stripe Key 未进入 Git；
- [ ] `ci-backend.yml` 已加入 Compose 路径和 `deploy-ec2`；
- [ ] `security.yml` 已覆盖部署配置变更；
- [ ] `backend-dev` Environment、Role ARN、`BACKEND_DOT_ENV`、Region/AZ 已配置；
- [ ] `dev` 分支保护及五个必需检查已配置；
- [ ] PR 阶段 `deploy-ec2` 正确 skipped；
- [ ] 合并后 `deploy-ec2` 自动运行成功；
- [ ] ELB healthy，公网健康接口返回 `UP`；
- [ ] 5432/6379 未暴露，CloudWatch 无秘密；
- [ ] 首次部署证据、OIDC Role 权限复审、数据库备份和回滚责任已记录。

## 参考资料

- [Bitovi Deploy Docker to AWS EC2](https://github.com/bitovi/github-actions-deploy-docker-to-ec2)
- [GitHub Deployments and Environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [AWS Root User Best Practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html)
- [AWS Secure Access Keys](https://docs.aws.amazon.com/IAM/latest/UserGuide/securing_access-keys.html)
- [AWS 创建 Cost Budget](https://docs.aws.amazon.com/cost-management/latest/userguide/create-cost-budget.html)
- [本仓库后端流水线说明](../ci/backend-pipeline.md)
- [本仓库后端质量门禁](../quality/quality-gates.md)
- [英文 EC2 部署参考](backend-ec2-bitovi.md)

第三方 Action 参数和默认值可能变化。修改 `@v1`、启用 Route 53/ALB/WAF/EFS/RDS 或执行
`tf_stack_destroy` 前，应重新核对 Bitovi 上游文档并先在开发环境验证。
