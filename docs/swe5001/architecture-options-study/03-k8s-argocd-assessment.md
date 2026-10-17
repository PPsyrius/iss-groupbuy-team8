# 03. Kubernetes (EKS) + Argo CD assessment

Status: proposal for team review, 2026-09-25. Question: should the services in `02-proposed-architecture.md` run on Kubernetes (EKS) with Argo CD GitOps, or on another AWS runtime?

## 1. Starting point

- Delivery today:
  - GitHub Actions `ci-backend.yml` handles build, contract check, Sonar and the Docker image, publishes to ECR, and deploys to EC2 through OIDC and SSM `send-command`, pinned to an image digest. A failed health check rolls back automatically (NFR-08). Production needs two approvers (NFR-14).
  - `rollback-backend.yml` covers manual rollback.
  - `security.yml` runs Gitleaks, Trivy, CodeQL and actionlint.
- Infrastructure: Terraform in `infra/terraform/backend-app-stack` (ECR, GitHub OIDC roles, ALB, EC2, SSM). The frontend deploys to S3 + CloudFront through OIDC.
- Team: 5 people and about 10 man-days each. The build (P1-P4) is about 36 agent-days, delivered by one supervised AI agent per member (see `05-effort-estimate.md` §4). Nobody on the team operates a Kubernetes cluster today. This is an assumption, and the team should confirm it.

## 2. Options

| Criterion | **EKS + Argo CD** | **ECS Fargate + GitHub Actions** (rolling with circuit breaker; CodeDeploy blue/green optional) | ECS on EC2 | App Runner | Lambda (+ API Gateway) | k3s on one EC2 + Argo CD (minimal K8s) |
| --- | --- | --- | --- | --- | --- | --- |
| Learning curve, 5 people, about 2 weeks | Steep. You need VPC CNI, IRSA/Pod Identity, the AWS Load Balancer Controller, ingress, Helm/Kustomize, Argo CD apps, External Secrets and Karpenter or managed node groups. That is realistically 8-12 man-days before the first service is live. | Low to moderate. Task definitions, services, target groups and Service Connect. 2-4 man-days with a shared Terraform module. | Moderate. As Fargate, plus ASG, capacity providers and AMI patching. | Lowest. It has no VPC-internal service-to-service discovery of its own, and needs a VPC connector for RDS. | High for this codebase. Spring Boot cold starts need SnapStart. Leased jobs, SQS pollers and long webhook transactions must be rewritten. | Moderate to steep. The same K8s concepts, but you run the cluster yourselves. |
| Fixed cost floor | **Control plane USD 0.10/h ≈ USD 73/month per cluster** (standard support; extended support is USD 0.50/h). Nodes (for example 2 x t4g.medium) or Fargate pods come on top, plus the ALB. | None beyond the tasks. You pay per vCPU-hour and GB-hour, and ARM is about 20% cheaper. | EC2 instances (can use Spot) | Per active vCPU/GB, plus provisioned memory when idle. Cost is close to Fargate. | Near zero when idle | 1 x t4g.medium (or t4g.large) + EBS. No control-plane fee. |
| Operational burden | High: cluster upgrades every about 14 months of standard support, add-on versions, node AMIs, RBAC, Argo CD itself | Low. AWS runs the control plane and hosts, and you only patch images. | Medium | Very low | Low | High. Single node, you own upgrades, etcd/sqlite backup and certificates. |
| GitOps value | High. Declarative desired state, drift detection, UI, app-of-apps, easy rollbacks by `git revert` | Medium. Terraform + task definitions in git. The deploy is imperative (`update-service`) but pinned to a digest and auditable in Actions. Drift is detected by `terraform plan`. | Medium | Low | Medium (SAM/Terraform) | High (same as EKS) |
| Fit with GitHub Actions + OIDC + Terraform | Needs a second deployment control loop (Argo CD pulls from git). The CI must update image tags in a manifest repo. OIDC still covers ECR push. | **Direct.** It is the same OIDC role pattern as today, with SSM `send-command` replaced by `aws ecs update-service`. Terraform modules extend the existing stack. | Direct | Direct | Direct | Needs Argo CD and a manifest repo, like EKS |
| Scalability demonstration | Strong (HPA, Karpenter, cluster autoscaling) | Strong (ECS service autoscaling on CPU, requests or SQS depth; Fargate removes node capacity planning) | Strong, but you manage capacity | Limited control (concurrency-based), no SQS consumers | Strong (per request), but a different architecture | Weak. One node is a single point of failure, so it cannot show horizontal node scaling. |
| Keeps the existing Spring Boot, leased jobs and long-polling consumers | Yes | Yes | Yes | Partly (no background workers when idle) | No | Yes |
| Course demonstration value (Platform Engineering / Cloud Native) | Highest recognition ("K8s + GitOps") | High. A managed container orchestrator is cloud native, and the scaling metrics are the same. | Medium | Low (too opaque) | Medium to high, but off-topic for this codebase | Medium. It shows GitOps, but not a production-shaped platform. |
| Risk to the 20-Oct conduct deadline | **High** | Low | Medium | Low | High | Medium |

Cost note: the EKS figures come from the AWS Price List API for ap-southeast-1, retrieved 2026-09-25 (`04-aws-cost-estimate.md` §2): USD 0.10 per cluster-hour for standard Kubernetes version support (730 h x 0.10 = USD 73/month) and USD 0.50 per cluster-hour for extended support. EKS Auto Mode adds a management fee per instance on top of EC2. The full EKS + Argo CD variant costs USD 296.55 per month against USD 220.77 on Fargate (04 §6, baseline D2).

See also `07-digitalocean-comparison.md` §5: DigitalOcean Kubernetes (DOKS) has a free control plane, which removes the *cost* argument against Kubernetes but not the *time* argument. 07 §8 H3 suggests DOKS for the optional side demo.

## 3. Verdict

**Do not adopt EKS + Argo CD for this module. Run the services on Amazon ECS with AWS Fargate (ARM64), deployed by GitHub Actions through OIDC and defined in Terraform.**

Reasons:

1. **Time.** Standing up EKS, its add-ons and Argo CD would take 8-12 days of effort before any business capability moves, on a critical path that already uses the whole conduct window (05 §6). The briefing marks the platform's *business* scalability and ecosystem value, not the orchestrator.
2. **Cost.** The EKS control plane alone (USD 73/month) is more than the whole academic-demo compute bill on Fargate at 1 task per service (USD 62.97/month, 04 §3). Nodes and the add-ons' own pods come on top.
3. **Fit.** The pipeline already deploys by immutable digest with OIDC, health-gated rollback and environment approvals. Moving from `send-command` to `ecs update-service` keeps every one of those NFRs (04, 08, 14) with small edits. Argo CD would add a second control loop and a manifest repo.
4. **The scalability evidence is the same.** ECS service autoscaling on CPU, ALB requests per target and SQS depth produces the same graphs as an HPA.

**Keep GitOps as a principle, not a product.** Desired state lives in git:

- the Terraform modules;
- the task definitions rendered from templates;
- the image digests recorded by CI in a `deploy/<env>/<service>.json` manifest committed by the pipeline, or in SSM parameters.

`terraform plan` in CI is the drift detector. This is described in the report as "GitOps-style, push-based".

**If the team or the assessors want visible Kubernetes experience,** do it as an **optional, time-boxed side demo** (section 5), never as the runtime for the main demo.

## 4. How to realise the services on ECS Fargate

### 4.1 Runtime layout

| Item | Setting |
| --- | --- |
| Cluster | 1 ECS cluster `blocbuy-<env>`, capacity providers `FARGATE` and `FARGATE_SPOT` (Spot weight for notification and for catalog tasks above the first) |
| Services | `team-purchase`, `catalog`, `notification`, (`identity` stretch). One task definition family each, `runtimePlatform` ARM64/Linux, `awsvpc` networking in private app subnets. |
| Task sizes (demo) | team-purchase 1 vCPU / 2 GB. catalog 0.5 vCPU / 1 GB. notification 0.25 vCPU / 0.5 GB. identity 0.5 vCPU / 1 GB. JVM `-XX:MaxRAMPercentage=75`. |
| Ingress | One ALB (existing pattern: CloudFront prefix list + `X-Origin-Secret`, default action 403). Listener rules by path, in priority order: `/api/v1/webhooks/*`, `/api/v1/payments/*`, `/api/v1/orders/*`, `/api/v1/group-deals/*` -> team-purchase. `/api/v1/products*`, `/api/v1/team-price-*`, `/api/v1/admin/products*` -> catalog. `/api/v1/notifications*` -> notification. `/api/v1/auth/*` -> identity (or team-purchase until P5). Each target group health-checks `/actuator/health/readiness`. |
| Service discovery | **ECS Service Connect** with a Cloud Map namespace `blocbuy.internal`. team-purchase calls `http://catalog:8080/internal/...`. Envoy gives retries and timeouts, and exports connection metrics to CloudWatch. |
| Config and secrets | Task definition `environment` + `secrets` from SSM Parameter Store SecureString (existing SSM pattern). One IAM task execution role and one task role per service, least privilege. |
| Autoscaling | Application Auto Scaling. catalog: target tracking CPU 60%, min 1, max 4. team-purchase: `ALBRequestCountPerTarget` 200, min 1, max 3. notification: step scaling on SQS visible messages (0 -> 1 at > 0, -> 2 at > 500), min 0. |
| Deployment | Rolling update, `minimumHealthyPercent` 100, `maximumPercent` 200, **deployment circuit breaker with rollback** (replaces the custom SSM rollback script). Optional: CodeDeploy blue/green for team-purchase, with a test listener and a 5-minute bake, to show a zero-downtime cut-over on the payment path. |
| Jobs | Stay inside team-purchase/catalog as leased `@Scheduled` jobs, so any number of tasks is safe (`sys_scheduler_lock`). Switched on by `BLOCBUY_SCHEDULING_ENABLED` per service. |
| Logs, traces | `awslogs` driver -> `/blocbuy/<env>/<service>` (14-day retention, NFR-10). ADOT collector sidecar (or the X-Ray daemon) at 5% sampling. |

### 4.2 Terraform structure

```
infra/terraform/
  modules/
    network/          # VPC, 2 AZ subnets, NAT, S3 gateway endpoint, SGs
    data/             # RDS PostgreSQL, ElastiCache, DB roles bootstrap
    messaging/        # SNS topic, SQS queue + DLQ per consumer, subscriptions, filter policies, Firehose
    ecs-cluster/      # cluster, capacity providers, Service Connect namespace, ALB + listener
    ecs-service/      # reusable: ECR repo, task def, service, TG, listener rule, autoscaling, IAM roles, log group
    github-oidc/      # existing roles, extended with ecs:UpdateService, iam:PassRole scoped to task roles
  envs/
    dev/main.tf       # instantiates modules; one ecs-service block per service
    prod.tfvars
```

The state backend is the existing S3/DynamoDB backend. `terraform plan` runs on PRs that touch `infra/`, and `apply` needs the `dev` environment approval.

### 4.3 CI/CD (GitHub Actions)

1. `detect-changes` (already present) produces a list of the services that changed, based on the Maven module paths.
2. A **matrix** job per changed service runs `./mvnw -pl <service> -am verify`, then contract-check (OpenAPI per service + event schemas), then Sonar, then a Docker build (ARM64, `buildx`), then a Trivy image scan, then a push to `ecr/<service>` and records the **digest**.
3. The deploy job per service (OIDC role `github-deploy-<env>`):
   - renders the task definition with the digest (`aws-actions/amazon-ecs-render-task-definition`);
   - deploys it (`aws-actions/amazon-ecs-deploy-task-definition`, with `wait-for-service-stability: true`);
   - relies on the circuit breaker to roll back automatically;
   - runs a smoke test against `/api/v1/.../health`.
4. **Promotion.** `dev` deploys automatically on merge to `dev`. `prod`, when used, re-deploys **the same digest** after the GitHub environment approval with two reviewers (unchanged NFR-14). No rebuild happens between environments.
5. The frontend pipeline is unchanged.

The `ecs-service` Terraform module and the reusable workflow are the "platform" artefacts. Adding a service means about 20 lines of Terraform and one matrix entry. This answers the briefing's question about what a new use case gains from the platform.

## 5. Optional: minimal Kubernetes side demo (only if time remains after P4)

| Variant | Content | Cost (ap-southeast-1, estimate to verify) | Effort |
| --- | --- | --- | --- |
| **k3s on 1 x t4g.medium + Argo CD** | k3s single node. Argo CD syncs a `deploy/k8s` Kustomize folder. Deploy notification-service only, consuming the same SQS queue through a node IAM role. HPA on CPU. | USD 36.52/month at 24x7 (t4g.medium + 20 GB gp3 + public IPv4), or about USD 6 for 5 demo days if Terraform creates and destroys it (04 §6, D3) | About 2.6 agent-days (05, P5.6) |
| EKS (1 cluster, 2 x t4g.medium managed nodes) + Argo CD | Same content on EKS | USD 73 control plane + USD 61.90 nodes + USD 3.84 EBS = **about USD 139/month** on top of the main platform. It cannot be paused, only destroyed and recreated. | 6-8 man-days (not sized in 05) |

The recommended variant is k3s, created and destroyed by Terraform around the demo, so the report can show a GitOps pull model beside the push model and compare them. It is not on the critical path.

## 6. Decision record row (for the report's AD table)

| ID | Decision | Alternatives | Trade-offs | Rationale |
| --- | --- | --- | --- | --- |
| AD-08 | ECS Fargate (ARM64), Terraform modules per service, GitHub Actions OIDC matrix deploy by digest with ECS circuit-breaker rollback | EKS + Argo CD; ECS on EC2; App Runner; Lambda; k3s + Argo CD | + no cluster to operate, no fixed control-plane fee, reuses the current pipeline and NFR-04/08/14. - push-based GitOps only, less Kubernetes-specific learning. | Fits the 2-week conduct window and the academic-demo cost basis. Same autoscaling evidence. |
