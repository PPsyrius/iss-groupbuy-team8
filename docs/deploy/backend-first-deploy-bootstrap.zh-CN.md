# 首次部署一次性配置清单

**状态：** 当前实现的可执行操作清单

**适用场景：** 从零配置部署环境并完成第一次流水线部署。全程只需执行一次。

**权威参考：** 策略、原理与安全约束见
[现有 EC2 后端部署运维指南](backend-ec2-pipeline-operator-guide.zh-CN.md)。本清单与其冲突时以该指南为准。

**环境边界：** 复用已有 EC2 及其上的 PostgreSQL/Redis；本清单不创建、不修改任何基础设施资源。

按顺序执行 0–6 节。第 0–3、5 节在**管理员工作站**上执行（需要 IAM/EC2/ECR 管理权限的 AWS 凭证）；
第 4 节在 **EC2 主机**上执行；第 6 节在 **GitHub** 上操作。

## 0. 准备：记录配置值并设置变量

先在本机 shell 中定义变量，后续命令可直接复制粘贴：

```bash
ACCOUNT_ID=402178232802            # 替换：AWS Account ID
REGION=ap-southeast-1              # 替换：区域
INSTANCE_ID=i-0852b6c3475b19f05    # 替换：现有 EC2 Instance ID
ECR_REPOSITORY=dev/blocbuy
EC2_ROLE=BlocBuyEc2Runtime
ECR_PULL_POLICY=BlocBuyEc2EcrPull
GITHUB_ECR_ROLE=BlocBuyGitHubEcrPush
GITHUB_DEPLOY_ROLE=BlocBuyGitHubBackendDeploy
```

| 值 | 本清单示例 |
|---|---|
| AWS Account ID | `123456789012` |
| Region | `ap-southeast-1` |
| 现有 EC2 Instance ID | `i-0123456789abcdef0` |
| ECR Repository | `dev/blocbuy` |
| EC2 Runtime Role | `BlocBuyEc2Runtime` |
| GitHub ECR Role | `BlocBuyGitHubEcrPush` |
| GitHub Deploy Role | `BlocBuyGitHubBackendDeploy` |

同时准备一个仓库外的工作目录存放待替换的 IAM JSON（IAM 示例不应生成后留在仓库里）：

```bash
mkdir -p ~/blocbuy-bootstrap && cd ~/blocbuy-bootstrap
cp <仓库路径>/deployment/backend/iam/*.json.example .
```

Shell 变量不跨会话、不跨主机（CloudShell 与本地工作站各需一份）。把第 0 节变量持久化到同
目录的 `env.sh`，每个新会话 source 一次：

```bash
cat > ~/blocbuy-bootstrap/env.sh <<'EOF'
export ACCOUNT_ID=402178232802
export REGION=ap-southeast-1
export INSTANCE_ID=i-0852b6c3475b19f05
export ECR_REPOSITORY=dev/blocbuy
export EC2_ROLE=BlocBuyEc2Runtime
export ECR_PULL_POLICY=BlocBuyEc2EcrPull
export GITHUB_ECR_ROLE=BlocBuyGitHubEcrPush
export GITHUB_DEPLOY_ROLE=BlocBuyGitHubBackendDeploy
EOF

source ~/blocbuy-bootstrap/env.sh
# 需要每个新会话自动加载时（CloudShell 为 bash，本地 Mac 为 zsh）：
# echo 'source ~/blocbuy-bootstrap/env.sh' >> ~/.bashrc
```

每次执行后续章节命令前先确认变量已定义——变量为空时 `${ECR_REPOSITORY}` 展开成空串，
sed 不会报错但会产出残缺策略：

```bash
printf 'account=%s region=%s repo=%s\n' "402178232802" "ap-southeast-1" "dev/blocbuy"
```

## 1. 创建 ECR 仓库

- [ ] 仓库存在、tag 不可变、开启扫描、套用生命周期策略

```bash
aws ecr create-repository \
  --repository-name "dev/blocbuy" \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true \
  --region "ap-southeast-1"

aws ecr put-lifecycle-policy \
  --repository-name "dev/blocbuy" \
  --lifecycle-policy-text file://<仓库路径>/deployment/backend/ecr-lifecycle-policy.json \
  --region "ap-southeast-1"
```

若仓库已存在：用 `aws ecr describe-repositories` 核对，并用 `aws ecr put-image-tag-mutability`
补设 `IMMUTABLE`。流水线只按 `repository@sha256:...` 部署，不使用可变 tag。

## 2. 配置 EC2 Instance Role

### 2.0 判断现有状态

- [ ] 确定走路径 A 还是路径 B

```bash
aws ec2 describe-instances \
  --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[].Instances[].IamInstanceProfile' \
  --region "ap-southeast-1"
```

返回 `null` → 没有挂载 Role，走 **2.1B**；返回 Instance Profile ARN → 查出 Role 名后走 **2.1A**：

```bash
aws iam get-instance-profile \
  --instance-profile-name <ProfileName> \
  --query 'InstanceProfile.Roles[0].RoleName' --output text
```

### 2.1A 已有 Role：附加 SSM 策略

- [ ] `AmazonSSMManagedInstanceCore` 已挂到现有 Role

```bash
aws iam attach-role-policy \
  --role-name <已有Role名> \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
```

### 2.1B 没有 Role：完整新建

- [ ] Role、Instance Profile 已创建并关联到 EC2

```bash
cat > ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ec2.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role --role-name "BlocBuyEc2Runtime" \
  --assume-role-policy-document file://ec2-trust.json

aws iam attach-role-policy --role-name "BlocBuyEc2Runtime" \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

aws iam create-instance-profile --instance-profile-name "BlocBuy-dev"
# 两条命令间偶尔需要等待数秒传播，报 NoSuchEntity 时稍候重试
aws iam add-role-to-instance-profile \
  --instance-profile-name "BlocBuy-dev" --role-name "BlocBuyEc2Runtime"

aws ec2 associate-iam-instance-profile \
  --instance-id "i-0852b6c3475b19f05" \
  --iam-instance-profile Name="BlocBuy-dev" \
  --region "ap-southeast-1"
```

### 2.2 附加 ECR Pull 策略

SSM 策略只负责 Agent 通信，EC2 拉镜像还需要只针对本仓库的 pull 权限。

- [ ] Customer Managed Policy 已替换占位符并挂载

```bash
sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s|ECR_REPOSITORY|dev/blocbuy|g" \
    ec2-ecr-pull-policy.json.example > ec2-ecr-pull-policy.json

aws iam create-policy \
  --policy-name "BlocBuyEc2EcrPull" \
  --policy-document file://ec2-ecr-pull-policy.json

aws iam attach-role-policy --role-name "BlocBuyEc2Runtime" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyEc2EcrPull"
```

`ECR_REPOSITORY` 的替换用 `|` 作 sed 分隔符：ECR 仓库名可以含 `/`（如 `dev/blocbuy`），
用默认 `/` 分隔符会报 `unknown option to 's'`。替换值必须与第 1 节实际创建的仓库名完全一致，
否则 EC2 拉镜像时会被 ECR 拒绝。走 2.1A 路径时，把 attach 命令里的 `BlocBuyEc2Runtime` 换成已有 Role 名。

**禁止**使用 `AmazonSSMFullAccess`、`AmazonEC2ContainerRegistryFullAccess` 或其它宽权限。

### 2.3 验证 Instance 在 SSM 中 Online

- [ ] `PingStatus` 为 `Online`

```bash
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=i-0852b6c3475b19f05" \
  --region "ap-southeast-1" \
  --query 'InstanceInformationList[].{id:InstanceId,ping:PingStatus,agent:AgentVersion}'
```

刚挂载 Role 后 Agent 需要数分钟刷新凭证。超过 5 分钟仍 `ConnectionLost` 时，登录实例执行
`sudo systemctl restart amazon-ssm-agent`（Ubuntu snap 安装为
`snap.amazon-ssm-agent.amazon-ssm-agent`）后重试。

## 3. 配置 GitHub OIDC 与两个 AWS Role

### 3.1 创建 OIDC Provider（整个账号只创建一次）

- [ ] Provider 存在

```bash
# 先检查是否已存在（每个账号最多一个同名 Provider）
aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn \
    "arn:aws:iam::402178232802:oidc-provider/token.actions.githubusercontent.com" \
  && echo "已存在，跳过创建"

aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

创建后输出：

```json
{
    "OpenIDConnectProviderArn": "arn:aws:iam::402178232802:oidc-provider/token.actions.githubusercontent.com"
}
```

### 3.2 创建 ECR 发布 Role（trust 限定本仓库 `dev` 分支）

- [ ] Role 与权限策略已创建

```bash
sed "s/AWS_ACCOUNT_ID/402178232802/g" \
  github-ecr-push-trust-policy.json.example > github-ecr-push-trust-policy.json

sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s|ECR_REPOSITORY|dev/blocbuy|g" \
    github-ecr-push-policy.json.example > github-ecr-push-policy.json

aws iam create-role --role-name "BlocBuyGitHubEcrPush" \
  --assume-role-policy-document file://github-ecr-push-trust-policy.json

aws iam create-policy --policy-name "BlocBuyGitHubEcrPushPolicy" \
  --policy-document file://github-ecr-push-policy.json

aws iam attach-role-policy --role-name "BlocBuyGitHubEcrPush" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyGitHubEcrPushPolicy"

# 记录 ARN，第 5 节要填进 GitHub Variables
aws iam get-role --role-name "BlocBuyGitHubEcrPush" --query 'Role.Arn' --output text
# 输出
arn:aws:iam::402178232802:role/BlocBuyGitHubEcrPush
```

示例文件的 `sub` 已填本仓库的 `repo:PPsyrius/iss-groupbuy-team8:ref:refs/heads/dev`。
2026 年 7 月 15 日之后创建或启用 immutable OIDC subject 的仓库，`sub` 会改为包含 Owner ID 与
Repository ID —— 创建 Role 前必须按仓库真实 subject 核对修正，不允许放宽为通配符。

### 3.3 创建 SSM 部署 Role（trust 限定 `backend-dev` environment）

- [ ] Role 与权限策略已创建

```bash
sed "s/AWS_ACCOUNT_ID/402178232802/g" \
  github-deploy-trust-policy.json.example > github-deploy-trust-policy.json

sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s/BACKEND_EC2_INSTANCE_ID/i-0852b6c3475b19f05/g" \
    github-ssm-deploy-policy.json.example > github-ssm-deploy-policy.json

aws iam create-role --role-name "BlocBuyGitHubBackendDeploy" \
  --assume-role-policy-document file://github-deploy-trust-policy.json

aws iam create-policy --policy-name "BlocBuyGitHubBackendDeployPolicy" \
  --policy-document file://github-ssm-deploy-policy.json

aws iam attach-role-policy --role-name "BlocBuyGitHubBackendDeploy" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyGitHubBackendDeployPolicy"

# 记录 ARN，第 5 节要填进 GitHub Environment Variables
aws iam get-role --role-name "BlocBuyGitHubBackendDeploy" --query 'Role.Arn' --output text
# 输出
arn:aws:iam::402178232802:role/BlocBuyGitHubBackendDeploy
```

- [ ] 两个 Trust Policy 已用 IAM Access Analyzer 校验

不要把 ECR 发布策略挂到部署 Role 上，也不要把两个 GitHub Role 合并成一个。SSM 部署 Role 的
`ssm:SendCommand` 以 root 在实例上执行，属于高权限，不能共享给其它仓库、Fork 或 PR。

## 4. 初始化 EC2 主机

以下命令**在 EC2 上执行**（SSH 或 SSM Session 均可）。主机前置条件：Linux、Docker Engine、
Docker Compose plugin、AWS CLI v2、SSM Agent 在线，`/opt/blocbuy/backend` 至少 2 GiB 空闲，
PostgreSQL/Redis 已监听 `127.0.0.1:5432/6379`。

- [ ] 主机具备上述前置条件
- [ ] `/etc/blocbuy/backend.env` 已创建，权限 `root:root 600`，无遗留占位符

```bash
sudo install -d -m 0750 /etc/blocbuy
sudo install -m 0600 /dev/null /etc/blocbuy/backend.env
sudo chown root:root /etc/blocbuy/backend.env
sudoedit /etc/blocbuy/backend.env   # 按 deployment/backend/backend.env.example 填写全部密钥
```

`backend.env.example` 中的键：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、
`REDIS_PORT`、`REDIS_PASSWORD`、`STRIPE_API_KEY`、`STRIPE_WEBHOOK_SECRET`。`BACKEND_IMAGE`
不属于此文件，由部署脚本以 digest 注入。

```bash
# 验证
sudo stat -c '%U:%G %a %n' /etc/blocbuy/backend.env   # 必须 root:root 600
sudo ss -lnt | grep -E '127\.0\.0\.1:(5432|6379)'      # 必须只监听 loopback
grep -r 'replace-with' /etc/blocbuy/backend.env        # 必须无输出
```

安全组核对：PostgreSQL/Redis 不对公网开放；`8589` 仅允许反向代理/负载均衡安全组或团队测试 CIDR。

部署包（`compose.yml` 与脚本）会在**第一次 Workflow 运行时**由 SSM 自动安装到
`/opt/blocbuy/backend`，EC2 不需要 clone 仓库。安装完成后可手动执行预检：

```bash
sudo /opt/blocbuy/backend/bin/preflight.sh
```

- [ ] 所有主机、IAM、网络变更已记录到团队 Change Log

## 5. 配置 GitHub

### 5.1 创建 `backend-dev` Environment

- [ ] Environment 已创建，Deployment branches 限定为 `dev`；如需人工审批，添加 Required Reviewer

Settings → Environments → New environment → `backend-dev`。

### 5.2 配置 Repository Variables（Settings → Secrets and variables → Actions → Variables）

- [ ] 以下变量已创建（ECR 发布 Job 不引用 Environment，必须放 Repository 级别）

| Variable | 值 | 必需 |
|---|---|---|
| `AWS_ACCOUNT_ID` | 第 0 节的 Account ID | 是 |
| `AWS_REGION` | 第 0 节的 Region | 是 |
| `ECR_REPOSITORY_DEV` | `dev/blocbuy` | 是 |
| `AWS_ECR_PUSH_ROLE_ARN` | 3.2 节输出的 Role ARN | 是 |
| `ENABLE_EC2_DEPLOY` | 验证完成前 `false` | 是 |

### 5.3 配置 Environment Variables（`backend-dev` 环境内）

- [ ] 以下变量已创建

| Variable                      | 值 | 必需 |
|-------------------------------|---|---|
| `AWS_DEPLOY_ROLE_ARN_DEV`     | 3.3 节输出的 Role ARN | 是 |
| `BACKEND_EC2_INSTANCE_ID_DEV` | 第 0 节的 Instance ID | 是 |
| `BACKEND_URL_DEV`             | 后端公网地址，如 `https://api-dev.example.com` | 否 |

GitHub 上不需要任何长期 AWS Access Key，也不需要 `BACKEND_DOT_ENV`。真实密钥只存在于 EC2 的
`/etc/blocbuy/backend.env`。

### 5.4 保护 `dev` 分支

- [ ] Branch Protection 已启用：要求 PR、CODEOWNERS Review、禁止 Force Push，并勾选 Required Checks

Required Checks 勾选：`build-quality`、`contract-check`、`docker`、`workflow-lint`、
`deployment-bundle`、`gitleaks`、`trivy-deps`、`trivy-config`。
`sonar`、`codeql` 仅在团队稳定启用对应服务后设为 Required。
Push-only 的部署 Job（`deploy-existing-ec2`）不要设为 PR Required Check。

## 6. 首次部署演练

按顺序执行，任何一步异常先排障再继续：

- [ ] 1. `ENABLE_EC2_DEPLOY=false` 保持不变，通过 PR 把部署改动合并进受保护的 `dev`
- [ ] 2. 确认 `ci-backend` 与 `security` 两个工作流全绿
- [ ] 3. 将 Repository Variable `ENABLE_EC2_DEPLOY` 改为 `true`
- [ ] 4. 合并或推送一个后端/部署改动到 `dev` 触发流水线
- [ ] 5. 如配置了 Required Reviewer，审批 `backend-dev` Environment
- [ ] 6. 检查 `deploy-existing-ec2` Job：镜像 digest、SSM command 输出、健康检查通过
- [ ] 7. 业务验证：

```bash
curl --fail --silent "https://<后端地址>/actuator/health"
```

当前部署的镜像 digest 记录在 EC2 的 `/opt/blocbuy/backend/state/current-image`。
部署不会重启或删除 PostgreSQL/Redis。

## 回滚与冻结（首次部署后确认可用）

- **回滚**：Actions → `rollback-backend` → Run workflow → 选 `dev` → `target_image` 留空使用主机
  记录的上一 digest（或填同一 ECR 仓库的 `repository@sha256:...`）→ 填写原因 → 勾选确认 → 完成
  Environment 审批。
- **冻结**：把 `ENABLE_EC2_DEPLOY=false`。CI 与安全扫描照常运行，但不发布、不部署；手工回滚仍可用。
- 应用回滚不能逆转 Flyway Migration；所有自动部署的 Migration 必须向后兼容。

## 常见故障速查

详细排查见[运维指南第 11 节](backend-ec2-pipeline-operator-guide.zh-CN.md#11-常见故障)。

| 现象 | 最常见原因 |
|---|---|
| ECR Role 无法 Assume | OIDC audience 错误 / `sub` 与分支不匹配 / Repository Variable 里 ARN 抄错 |
| Deploy Role 无法 Assume | `sub` 未用 environment 格式 / `backend-dev` 未限定 `dev` 分支 |
| SSM SendCommand 被拒绝 | Region 不一致 / 部署策略里 Instance ID 占位符未替换 |
| Instance 不在线 | SSM Agent 未运行 / Instance Profile 未绑定 / 缺 NAT 或 VPC Endpoint |
| ECR Pull 被拒绝 | 2.2 节策略未挂或占位符未替换 |
| Preflight 失败 | env 文件权限不是 `root:root 600` / 有占位符残留 / 磁盘不足 |
| Bundle Policy 失败 | 部署包混入白名单外文件（只允许 `compose.yml` 与四个脚本） |

**安全红线：** 不要把 `/etc/blocbuy/backend.env`、Docker 凭证或应用密钥粘贴到 Issue、PR、
Workflow Log 或本仓库任何文件中；IAM 示例替换后的 JSON 只保存在仓库外的私有目录。
