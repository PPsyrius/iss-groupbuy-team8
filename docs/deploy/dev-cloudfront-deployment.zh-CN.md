# Dev 环境入口层改造：CloudFront + ALB 教程（去公网 8589 端口）

**状态：** 待执行
**环境：** `backend-dev`（现有 EC2 实例）
**目标：** 后端端口 8589 不再对公网开放；前端部署在 S3 + CloudFront；API 流量统一走
CloudFront 分发的 `https://<cloudfront-domain>/api/*`。全程**不需要自有域名**——CloudFront
自带的 `*.cloudfront.net` 证书负责用户侧 HTTPS。

## 0. 目标架构

```text
用户 ──HTTPS──► https://xxxx.cloudfront.net (CloudFront 自带证书)
                  ├── /*      → S3 前端 bucket（OAC 私有访问）
                  └── /api/*  → ALB (HTTP:80, 仅接受 CloudFront 来源 + secret header)
                                    └─► EC2:8589（SG 只放行 ALB SG）
                                          └─► blocbuy-backend（host 网络, context-path=/api）
```

| 变更点 | 之前 | 之后 |
|---|---|---|
| 用户访问后端 | `http://<EC2公网IP>:8589` | `https://<cloudfront-domain>/api/...` |
| EC2 安全组 8589 入站 | `0.0.0.0/0` | 仅 ALB 安全组 |
| CORS | 需要配置跨域 | 前后端同域（`/api` 前缀），无需 CORS |
| 用户侧 TLS | 无（明文 HTTP） | CloudFront 终止 TLS |
| 源站防护 | 依赖安全组 | CloudFront managed prefix list + `X-Origin-Secret` header |

## 1. 前提条件

- dev EC2 实例已按 [operator guide](backend-ec2-pipeline-operator-guide.zh-CN.md) 运行后端；
- 本机或 operator 环境有 AWS CLI v2（以下命令均由有管理员权限的 operator 执行）；
- dev VPC 中有**两个不同 AZ 的子网**（ALB 强制要求）；
- 已执行本仓库的配套改动（`compose.yml` 的 `/api` context-path、workflow 健康检查路径），
  即当前 `dev` 分支包含本教程同批提交。

## 2. 收集并导出变量

```bash
export AWS_REGION=ap-southeast-1            # 换成实际 Region
export ACCOUNT_ID=402178232802
export INSTANCE_ID=i-0852b6c3475b19f05               # dev EC2 实例 ID
export VPC_ID=$(aws ec2 describe-instances --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[0].Instances[0].VpcId' --output text)
export BACKEND_SG_ID=$(aws ec2 describe-instances --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' --output text)

# 两个不同 AZ 的子网（ALB 需要）
aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" \
  --query 'Subnets[?MapPublicIpOnLaunch==`true`].[SubnetId,AvailabilityZone]' --output table
export SUBNET_A=subnet-aaaa
export SUBNET_B=subnet-bbbb

# 防绕过 secret：CloudFront → ALB 必须携带，ALB 校验后才转发
export ORIGIN_SECRET=$(openssl rand -hex 32)
echo "$ORIGIN_SECRET"   # 后面多处要用，保存好；不要提交到仓库
0e8899d8c2976fea237b8e93e4d919837e78db650c2feb87822d5e3091005d4e
```

如果实例有多个安全组，`SecurityGroups[0]` 可能取错，请用
`aws ec2 describe-instances --query '...Instances[0].SecurityGroups'` 确认监听 8589 的那一个。

## 3. 安全组

### 3.1 创建 ALB 安全组（只接受 CloudFront 来源）

CloudFront 的回源 IP 由 AWS 官方 managed prefix list 维护，直接引用即可自动更新：

```bash
# 注意：CloudFront 的 prefix list 只能从 describe-managed-prefix-lists 查到，
# 旧的 describe-prefix-lists 只返回 S3/DynamoDB 等 legacy 列表，会得到 None。
export CF_PREFIX_LIST=$(aws ec2 describe-managed-prefix-lists \
  --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
  --query 'PrefixLists[0].PrefixListId' --output text)
echo "$CF_PREFIX_LIST"   # 应为 pl- 开头；若是 None 说明查询失败，先排查再继续
pl-31a34658

aws ec2 create-security-group \
  --group-name blocbuy-dev-alb-sg \
  --description "BlocBuy dev ALB - CloudFront origin only" \
  --vpc-id "$VPC_ID"
export ALB_SG_ID=$(aws ec2 describe-security-groups \
  --filters Name=group-name,Values=blocbuy-dev-alb-sg Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)

# 简化语法不支持 prefix list（没有 --source-prefix-list 选项），必须用 --ip-permissions
aws ec2 authorize-security-group-ingress \
  --group-id "$ALB_SG_ID" \
  --ip-permissions "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=$CF_PREFIX_LIST}]"
```

> prefix list 是 **global**（`com.amazonaws.global.cloudfront.origin-facing`），条目很多，
> 引用 ID 即可，不要展开成 CIDR 列表。

### 3.2 放行 ALB → EC2:8589（暂不动现有公网规则，验证后再关）

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --source-group "$ALB_SG_ID"
```

## 4. 创建 Target Group 与 ALB

```bash
aws elbv2 create-target-group \
  --name blocbuy-dev-backend \
  --protocol HTTP --port 8589 --vpc-id "$VPC_ID" --target-type instance \
  --health-check-path /api/actuator/health \
  --health-check-protocol HTTP --health-check-port traffic-port \
  --health-check-interval-seconds 15 --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
  --matcher HttpCode=200
export TG_ARN=$(aws elbv2 describe-target-groups --names blocbuy-dev-backend \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

aws elbv2 register-targets --target-group-arn "$TG_ARN" --targets Id="$INSTANCE_ID"

aws elbv2 create-load-balancer \
  --name blocbuy-dev-backend \
  --subnets "$SUBNET_A" "$SUBNET_B" \
  --security-groups "$ALB_SG_ID" \
  --scheme internet-facing --type application --ip-address-type ipv4
export ALB_ARN=$(aws elbv2 describe-load-balancers --names blocbuy-dev-backend \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
export ALB_DNS=$(aws elbv2 describe-load-balancers --names blocbuy-dev-backend \
  --query 'LoadBalancers[0].DNSName' --output text)
```

### 4.1 Listener：默认 403，仅放行带 secret header 的请求

没有域名时 ALB 上拿不到 ACM 证书，只建 HTTP:80 listener（TLS 由 CloudFront 负责）。
默认动作返回 403，只有命中 `X-Origin-Secret` 规则的流量才转发到后端——
即使有人拿到 ALB DNS 直连，也进不来：

```bash
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" --protocol HTTP --port 80 \
  --default-actions Type=fixed-response,FixedResponseConfig="{StatusCode=403,ContentType=text/plain,MessageBody=forbidden}"
export LISTENER_ARN=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" \
  --query 'Listeners[0].ListenerArn' --output text)

aws elbv2 create-rule \
  --listener-arn "$LISTENER_ARN" --priority 10 \
  --conditions Field=http-header,HttpHeaderConfig="{HttpHeaderName=X-Origin-Secret,Values=[$ORIGIN_SECRET]}" \
  --actions Type=forward,TargetGroupArn="$TG_ARN"
```

### 4.2 验证源站链路（此时 8589 公网规则还在，仅验证 ALB）

Target Group 健康状态应变为 healthy（需要后端已带 `/api` context-path 重新部署，见第 5 步）：

```bash
aws elbv2 describe-target-health --target-group-arn "$TG_ARN"
curl --fail --silent -H "X-Origin-Secret: $ORIGIN_SECRET" "http://$ALB_DNS/api/actuator/health"
# 应返回 {"status":"UP",...}
curl -s -o /dev/null -w '%{http_code}\n' "http://$ALB_DNS/api/actuator/health"   # 不带 secret 应为 403
```

## 5. 部署带 `/api` context-path 的后端

本仓库已把 `SERVER_SERVLET_CONTEXT_PATH: /api` 与
`SERVER_FORWARD_HEADERS_STRATEGY: framework` 写入 `deployment/backend/compose.yml`，并把
`health-check.sh` 默认 URL 改为 `http://127.0.0.1:8589/api/actuator/health`。

部署包（compose.yml + 脚本）由每次部署 workflow 经 SSM 下发，所以只需**照常触发一次部署**
（合并改动到 `dev` 或手动 re-run `ci-backend` 的 deploy job，确保 `ENABLE_EC2_DEPLOY=true`）。
compose 环境变量变化会触发容器 force-recreate，自动生效。

部署完成后在 EC2 上验证：

```bash
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --parameters commands='curl --fail --silent http://127.0.0.1:8589/api/actuator/health'
```

## 6. 前端 S3 bucket + OAC

```bash
export S3_BUCKET=blocbuy-dev-frontend-$ACCOUNT_ID
aws s3 mb "s3://$S3_BUCKET" --region "$AWS_REGION"
aws s3api put-public-access-block --bucket "$S3_BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# OAC：让 CloudFront 以私有方式读 bucket
cat > /tmp/oac.json <<EOF
{
  "Name": "blocbuy-dev-frontend-oac",
  "Description": "OAC for blocbuy dev frontend bucket",
  "SigningProtocol": "sigv4",
  "SigningBehavior": "always",
  "OriginAccessControlOriginType": "s3"
}
EOF
aws cloudfront create-origin-access-control --origin-access-control-config file:///tmp/oac.json
export OAC_ID=$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='blocbuy-dev-frontend-oac'].Id" --output text)
```

bucket policy 依赖 distribution ARN，所以在第 7 步创建 distribution **之后**回填（见 7.2）。

## 7. 创建 CloudFront Distribution

### 7.1 Distribution 配置

```bash
cat > /tmp/cloudfront-dist.json <<EOF
{
  "CallerReference": "blocbuy-dev-$(date +%s)",
  "Comment": "BlocBuy dev - frontend S3 + backend ALB (/api)",
  "Enabled": true,
  "DefaultRootObject": "index.html",
  "HttpVersion": "http2",
  "IsIPV6Enabled": true,
  "PriceClass": "PriceClass_All",
  "Origins": {
    "Quantity": 2,
    "Items": [
      {
        "Id": "s3-frontend",
        "DomainName": "$S3_BUCKET.s3.$AWS_REGION.amazonaws.com",
        "OriginAccessControlId": "$OAC_ID",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      },
      {
        "Id": "alb-backend",
        "DomainName": "$ALB_DNS",
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "http-only",
          "OriginSslProtocols": {"Quantity": 1, "Items": ["TLSv1.2"]}
        },
        "CustomHeaders": {
          "Quantity": 1,
          "Items": [{"HeaderName": "X-Origin-Secret", "HeaderValue": "$ORIGIN_SECRET"}]
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "s3-frontend",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"], "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}},
    "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6",
    "Compress": true
  },
  "CacheBehaviors": {
    "Quantity": 1,
    "Items": [
      {
        "PathPattern": "/api/*",
        "TargetOriginId": "alb-backend",
        "ViewerProtocolPolicy": "redirect-to-https",
        "AllowedMethods": {
          "Quantity": 7,
          "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
          "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}
        },
        "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
        "OriginRequestPolicyId": "b689b0a8-53d0-40ab-baf2-68738e2966ac",
        "Compress": true
      }
    ]
  },
  "ViewerCertificate": {
    "CloudFrontDefaultCertificate": true,
    "MinimumProtocolVersion": "TLSv1.2_2021",
    "SSLSupportMethod": "vip"
  },
  "Restrictions": {"GeoRestriction": {"RestrictionType": "none", "Quantity": 0}}
}
EOF

aws cloudfront create-distribution --distribution-config file:///tmp/cloudfront-dist.json
export DIST_ID=$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='BlocBuy dev - frontend S3 + backend ALB (/api)'].Id" --output text)
export DIST_DOMAIN=$(aws cloudfront get-distribution --id "$DIST_ID" \
  --query 'Distribution.DomainName' --output text)
export DIST_ARN=$(aws cloudfront get-distribution --id "$DIST_ID" \
  --query 'Distribution.ARN' --output text)
echo "$DIST_DOMAIN"   # 形如 dxxxxxxxxxxxx.cloudfront.net
```

说明：

- `4135ea2d-...` 是 managed **CachingDisabled**（API 必须 `POST`/`PUT`，不能缓存）；
- `b689b0a8-...` 是 managed **AllViewerExceptHostHeader**，把浏览器请求头（含
  `X-Forwarded-Proto`）透传给后端，配合 `SERVER_FORWARD_HEADERS_STRATEGY=framework` 生效；
- `OriginProtocolPolicy: http-only` 是无域名时的关键：CloudFront 自带证书做用户侧 TLS，
  回源段走内网 HTTP，安全性由 prefix list SG + secret header + ALB→EC2 私网路径保证。

### 7.2 回填 S3 bucket policy（OAC）

```bash
cat > /tmp/bucket-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowCloudFrontOAC",
    "Effect": "Allow",
    "Principal": {"Service": "cloudfront.amazonaws.com"},
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::$S3_BUCKET/*",
    "Condition": {"StringEquals": {"AWS:SourceArn": "$DIST_ARN"}}
  }]
}
EOF
aws s3api put-bucket-policy --bucket "$S3_BUCKET" --policy file:///tmp/bucket-policy.json
```

### 7.3 （可选）SPA 路由回退

前端若使用 history 模式路由（`/groupbuy/123` 这类路径），需要把非静态资源请求回退到
`index.html`。创建一个 CloudFront Function 并挂到默认行为：

```bash
cat > /tmp/spa-fn.js <<'EOF'
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (uri.endsWith('/')) { request.uri += 'index.html'; }
  else if (!uri.includes('.')) { request.uri = '/index.html'; }
  return request;
}
EOF
aws cloudfront create-function \
  --name blocbuy-dev-spa-fallback \
  --runtime cloudfront-js-2.0 \
  --function-config Comment="SPA fallback",Autopublish=true \
  --function-code fileb:///tmp/spa-fn.js
```

`Autopublish=true` 会自动发布到 LIVE；若不加，需再执行
`aws cloudfront publish-function`。然后把它关联到默认行为：

```bash
ETAG=$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text)
aws cloudfront get-distribution-config --id "$DIST_ID" \
  --query DistributionConfig --output json > /tmp/dist-config.json
# 在 DefaultCacheBehavior 中加入：
#   "FunctionAssociations": {"Quantity": 1, "Items": [{"EventType": "viewer-request",
#     "FunctionARN": "<上面 create-function 返回的 FunctionMetadata.FunctionARN>"}]}
# 编辑后用 python/jq 校验 JSON，再 update：
python3 -c "import json;d=json.load(open('/tmp/dist-config.json'));print(json.dumps(d))" > /tmp/dist-config-flat.json
aws cloudfront update-distribution --id "$DIST_ID" --if-match "$ETAG" \
  --distribution-config file:///tmp/dist-config-flat.json
```

## 8. 端到端验证后再关闭 8589 公网入站

```bash
# 1) API 路径（等 distribution 部署完成，Status=Deployed，通常 3-5 分钟）
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health" | grep '"status":"UP"'

# 2) 正确性：不带 secret 的 ALB 直连必须 403（前面 4.2 已验证）

# 3) 关闭公网 8589 —— 本教程的核心收益
aws ec2 revoke-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --cidr 0.0.0.0/0

# 4) 确认：公网直连 8589 应超时/拒绝，但 ALB 链路仍正常
curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 "http://<EC2公网IP>:8589/api/actuator/health" || echo " blocked (预期)"
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health" | grep '"status":"UP"'
```

再触发一次 `ci-backend` 部署或 `rollback-backend`，workflow 内置的
"Verify public backend health" 步骤现在会走
`https://<cloudfront-domain>/api/actuator/health`（见第 9 步），全绿即完成。

## 9. 更新 GitHub Variables

```bash
gh variable set BACKEND_URL_DEV --body "https://$DIST_DOMAIN"
```

`BACKEND_URL_DEV` 只用于 workflow 的公网健康验证与 environment URL 展示；部署主链路
（ECR → SSM → host 本机健康检查）不依赖它，所以这一步即使配错也不影响发布本身。

## 10. 前端构建与发布约定

- API 客户端一律使用**相对路径** `/api/...`（例如 `fetch('/api/actuator/health')`），不要写
  绝对地址——同域部署下没有 CORS，将来绑自定义域名也不用改代码；
- 构建：

  ```bash
  cd frontend && npm ci && npm run build
  ```

- 发布（静态资源长缓存，index.html 不缓存）：

  ```bash
  aws s3 sync dist/ "s3://$S3_BUCKET" \
    --cache-control "public,max-age=31536000,immutable" \
    --exclude "index.html"
  aws s3 cp dist/index.html "s3://$S3_BUCKET/index.html" \
    --cache-control "no-cache"
  aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths "/index.html"
  ```

## 11. 影响范围与注意事项

| 事项 | 说明 |
|---|---|
| Swagger | 地址变为 `https://<cloudfront-domain>/api/swagger-ui.html`（context-path 前缀） |
| Stripe webhook | 回调 URL 需带 `/api` 前缀（若有公网 webhook 配置） |
| 本地开发 | 不受影响：`application.yml` 未改，`/api` 前缀只存在于 EC2 compose 环境变量中 |
| CI 冒烟测试 | `ci-backend.yml` 的 docker 冒烟测试走 `localhost:8589/actuator/health`，无 context-path，不受影响 |
| 直连 ALB | ALB DNS 可以拿到（属于公开 DNS），但默认 403 + prefix list SG 双重防护，无法访问业务 |
| prod 环境 | prod Terraform 指南中的 ALB/target-group health check path 应同步改为 `/api/actuator/health` |

## 12. 买了域名之后的升级路径

1. Route53 建托管区，给 CloudFront 加 Alternate Domain Name（CNAME）；
2. ACM（**必须在 us-east-1**）给该域名签证书，挂到 CloudFront 的 ViewerCertificate；
3. （可选）给 ALB 加 ACM 证书（Region 侧），CloudFront 回源改为 `https-only`，
   ALB 新增 443 listener 并把 80 listener 改为 redirect；
4. 架构无需推翻，只是把"无域名临时方案"的 HTTP 回源段升级为 HTTPS。

## 13. 回滚与清理

入口层回滚（重新开放 8589 即恢复旧访问方式）：

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --cidr 0.0.0.0/0
```

整体清理（确认不再需要 CloudFront 方案后）：

```bash
aws cloudfront disable-distribution --id "$DIST_ID"
# 等 Status=Deployed 后：
aws cloudfront delete-distribution --id "$DIST_ID" --if-match "$(aws cloudfront get-distribution --id $DIST_ID --query ETag --output text)"
aws s3 rb "s3://$S3_BUCKET" --force
aws elbv2 delete-load-balancer --load-balancer-arn "$ALB_ARN"
aws elbv2 delete-target-group --target-group-arn "$TG_ARN"
aws ec2 delete-security-group --group-id "$ALB_SG_ID"
```

> ALB/CloudFront 计费按小时+流量，dev 长期不用请及时 disable/删除。
