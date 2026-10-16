# 07. DigitalOcean comparison

Status: proposal for team review, 2026-09-26. Question: would the microservice target in `02-proposed-architecture.md` be **easier and cheaper** on DigitalOcean (DO) than on AWS?

## 1. Question and verdict

**Cheaper, yes: roughly half the AWS cost. Easier, no: not for this team in this window. Recommendation: stay on AWS.**

- DO is simpler resource by resource. But the target in 02 §4, §8 and §10 relies on managed services that DO does not offer: SNS/SQS, WAF, workload IAM with GitHub OIDC, managed logs and traces, and Firehose/Athena.
- Moving would also mean rebuilding the AWS delivery assets that already work. That is about **6-10 agent-days** (§6), more than half of the 11.7 agent-day critical path in 05 §4. It would land right before the 8-20 Oct conduct window and earn no marks.
- Part of the cost gap disappears on AWS by itself, through the levers in 04 (§10).

## 2. Service mapping: AWS to DO (Singapore, SGP1)

DO's regional availability page lists all of these products in SGP1: DOKS, App Platform, PostgreSQL, Valkey, Kafka, Spaces, Container Registry, load balancers, NAT, Functions and VPC.

| AWS (02 §10) | DO equivalent | Price and notes |
| --- | --- | --- |
| ECS Fargate | **DOKS**: free control plane, HA control plane +$40/mo. **App Platform**: one component per service | DOKS nodes from $12 (1 vCPU, 2 GB). App Platform shared sizes: $5 (0.5 GB), $12 (1 GB), $25 (2 GB), $50 (2 vCPU, 4 GB). Dedicated sizes: $29-$392. **App Platform autoscaling works only on dedicated sizes.** |
| EC2 | Droplets | Basic 2 GB $12; 4 GB / 2 vCPU $24 ($0.03571/h); 8 GB / 4 vCPU $48. CPU-optimised 2 vCPU $42 |
| RDS PostgreSQL db.t4g.small | Managed PostgreSQL | 1 GiB $15.15; 2 GiB $30.45. PostgreSQL 18 is supported. |
| ElastiCache Valkey | Managed Valkey | 1 GiB $15.00 |
| ALB | Load Balancer | $12/mo |
| Service Connect | App Platform internal networking (`http://<component>`); DOKS ClusterIP services | App Platform route rules can send `/api/` to a service and `/` to a static site on one domain. |
| CloudFront + S3 SPA | Spaces with built-in CDN ($5 for 250 GiB storage and 1 TiB transfer), **or** an App Platform static site | The Spaces CDN has **no index.html/SPA fallback** on the CDN or a custom domain. App Platform static sites have a catch-all document; the first 3 are free, then $3 each. |
| ECR | Container Registry | Free tier: 500 MiB, 1 repo. Basic: $5 for 5 repos and 5 GiB. |
| SNS + SQS | **Gap.** Managed Kafka starts at $148.80/mo. | Cheaper alternatives: a Postgres outbox with polling consumers ($0), or self-hosted NATS JetStream or RabbitMQ on DOKS or a droplet ($0 extra). |
| Lambda | Functions | 90,000 GiB-s free, no per-invocation charge |
| CloudWatch / X-Ray | Free droplet metrics and alerts, one Uptime check; App Platform insights and log forwarding (assumed) | **No managed log search or tracing.** Use Grafana Cloud free or kube-prometheus/Loki in the cluster (assumed). |
| WAF | **Gap.** Cloud Firewalls (layer 4) and DDoS protection only | Put Cloudflare Free in front (assumed $0). |
| Secrets Manager / SSM | **Gap.** App Platform encrypted env vars, Kubernetes Secrets (assumed) | |
| NAT gateway | NAT gateway $40 per unit, includes 100 GiB | Optional, because droplets and DOKS nodes have public IPs |
| Egress | Droplets include 500-5,000 GiB; App Platform 50-900 GiB | Overage: $0.01/GiB (droplets), $0.02/GiB (App Platform) |
| Terraform, GitHub OIDC | `digitalocean/digitalocean` Terraform provider (assumed) | **No native GitHub OIDC federation.** CI uses scoped API tokens. The only OIDC option is a digitalocean-labs proof of concept. |

## 3. Priced options (USD per month; module window = 1.545 months)

The workload is the academic-demo basis from 04, sized to the 02 service catalogue.

| Line | A. DOKS | B. App Platform | C. One droplet |
| --- | ---: | ---: | ---: |
| Compute | 2 x 4 GB nodes: 48.00 | team-purchase 2 GB 25 + catalog 1 GB 12 + notification 0.5 GB 5 = 42.00 | 8 GB / 4 vCPU, Compose or k3s: 48.00 |
| Load balancer | 12.00 | included | 0 (Caddy) |
| PostgreSQL | 2 GiB: 30.45 | 30.45 | 1 GiB: 15.15 |
| Valkey | 15.00 | 15.00 | 0 (container) |
| Messaging | NATS in cluster: 0 | Outbox + worker components: 0 | NATS container: 0 |
| SPA | App Platform static site: 3.00 | 0 | Caddy: 0 |
| Registry | 5.00 | 5.00 | GHCR: 0 (assumed) |
| Spaces (analytics, backups) | 5.00 | 5.00 | 0 |
| WAF, observability | Cloudflare and Grafana free: 0 | 0 | 0 |
| **Per month** | **118.45** | **97.45** (119.45 with catalog on a dedicated 1 GB to show autoscaling) | **63.15** |
| **Module window** | **183.01** | **150.56** / 184.55 | **97.57** |
| Burst per 8-hour load-test day | about 1.45 (2 extra nodes 0.57 + PostgreSQL uplift about 0.36, assumed + k6 droplet 0.50) | about 2.53 (3 catalog + 2 team-purchase instances pro-rated, assumed) | about 1.10 (extra droplet 0.57 + 0.50; no autoscaling) |

Optional extras: DOKS HA control plane +$40/mo, NAT gateway +$40/mo.

**AWS baselines from 04:**

| AWS variant (04 §1) | USD / month | Module window |
| --- | ---: | ---: |
| A. Fargate steady state, 24x7 | 220.77 | 341.14 |
| Recommended plan | n/a | 271.93 |
| Recommended plan, torn down after the presentations | n/a | 191.79 |
| D1. Current monolith | 87.81 | 135.68 |
| D2. EKS + Argo CD | 296.55 | 458.23 |

**This is not like-for-like.** The DO options leave out managed messaging, a WAF, managed logs and traces, and the analytics pipeline, which AWS variant A pays for. Much of the gap is the AWS NAT gateway (43.07/month) and the idle fixed floor of about 118/month (04 §1, §8). Removing the NAT and tearing down after the presentations brings AWS to 191.79 for the window without any migration.

## 4. Credits

- **GitHub Student Developer Pack: no longer available.** DO left the pack. The last redemption date was 31 Jul 2026, and all pack credits expired on 1 Aug 2026.
- **New-account trial: $200 for 60 days**, for new customers with a card who have never paid or taken the trial. DO's main pricing page currently mentions a "$5 free credit … for 90 days", so **check which offer applies at sign-up.** If the $200 offer applies, an account opened on 1 Oct covers the window to 30 Nov, and options A, B and C each fit under $200.

## 5. Ease comparison

Target: 3 services + outbox + messaging + autoscaling + observability + security controls (02).

| Criterion | ECS Fargate (03 verdict) | DO App Platform | DOKS + Argo CD | Droplets |
| --- | --- | --- | --- | --- |
| Learning curve, 5 people, about 2 weeks | Low-moderate (03 §2: 2-4 man-days) | **Lowest** | Moderate (about 5-8 man-days; easier than EKS: free control plane, load balancer integrated) | Low, but everything is DIY |
| Messaging | SNS/SQS + DLQ, managed | None managed. Kafka ($148.80), or Redis Streams with hand-built DLQ and redrive | Same, plus self-run NATS/RabbitMQ (needs approval under the backend constraints) | Same |
| Analytics (P2.6 Firehose -> S3 -> Athena) | No code | Lost; build a substitute | Lost | Lost |
| Autoscaling demonstration | CPU, requests per target, SQS depth | CPU only, dedicated sizes only | HPA + KEDA + cluster autoscaler (strongest) | Weak |
| WAF | AWS WAF managed rules | None; Cloudflare | None; ModSecurity or Cloudflare | None |
| Workload identity and secrets | Task IAM roles, SSM / Secrets Manager | Encrypted env vars | Kubernetes Secrets / Sealed Secrets | Files |
| Tracing | ADOT -> X-Ray | Third party | Self-hosted OpenTelemetry stack | Third party |
| Terraform maturity | Mature; extends existing stacks | Mature provider, small surface | Provider + Helm/Argo CD | Mature |
| CI/CD fit | `aws ecs update-service`, same pattern as today | `doctl` with a static token | A second control loop plus a manifest repo | SSH |
| Course fit | Many named managed services to cite | Opaque (like App Runner in 03) | Best "Kubernetes + GitOps" recognition | Weakest |

**Course fit.** The briefing (`docs/SWE5001/01+Briefing.pdf`, slides 3 and 5) names no cloud. It asks for "Cloud native … appropriate services of Cloud architectures" and a scalability demonstration, and the report template asks for "cloud services that you use if any". Nothing found requires AWS, but it is not known which cloud the course labs use, so ask the lecturer. "Appropriate cloud services" is easier to evidence on AWS. On DO, most of the controls in 02 §8 become software the team runs itself.

**Effect on 03.** DOKS's free control plane removes 03's *cost* argument against Kubernetes (USD 73/month for the EKS control plane; USD 296.55/month for EKS + Argo CD in 04 D2). It does not remove the *time* argument: Helm, Argo CD, secrets, KEDA and observability still sit on the critical path, and the conduct window closes on 20 Oct.

## 6. AWS assets that would be rebuilt

Unit: the agent-day from 05 §2. For infrastructure work, one agent-day is about 1-1.5 engineer-days.

| Asset (source) | Evidence tied to it | DO equivalent and gap | Agent-days |
| --- | --- | --- | ---: |
| CloudFront + CSP response-headers policy + SPA fallback function + S3 with OAC (`infra/terraform/frontend-stack/*`, `deployment/frontend/cloudfront/*`, frontend ADR 0005/0007) | NFR-16, NFR-33, NFR-36 (9 imports, plan exit 0, PR #190) | Spaces CDN has no edge functions, no custom headers and no `/api/*` route to a second origin, which breaks the same-origin cookie auth in frontend ADR 0002. Needs an App Platform static site + ingress, or Cloudflare. | 1.5-3 |
| ALB with `X-Origin-Secret` rule and CloudFront prefix-list security group (`infra/terraform/backend-app-stack/network.tf`) | NFR-37 path | DO load balancers cannot match headers or use a managed prefix list. The check moves into the app or ingress. | 0.5-1 |
| GitHub OIDC roles (`github-oidc.tf`, `frontend-stack/iam.tf`) | NFR-04 "no stored AWS keys" | No GitHub OIDC federation on DO: a static scoped API token, so NFR-04 must be downgraded. | 0.5 |
| SSM `send-command` deploy by digest with auto-rollback (`.github/actions/deploy-ec2`, 482 lines; `deployment/backend/bin/*`; `rollback-backend.yml`) | NFR-08, NFR-14 | App Platform or DOKS rollout, or SSH on droplets | 1-2 |
| ECR + lifecycle policy | none | DO Container Registry, trivial | 0.25 |
| CloudWatch logs, trace-id filter, 14-day retention | NFR-09, NFR-10 | No native log search: log forwarding to a third party, or Loki | 1-2 |
| Terraform state (S3 + DynamoDB) and runbooks (`docs/deploy/*`, about 2,000 lines; `.claude/skills/deployment-operations/SKILL.md`) | Sprint-log evidence | Rewrite | 1.5-2 |
| **Total** | **7 NFRs re-evidenced** | | **about 6-10** |

Caveat: the dev backend EC2 and ALB are still hand-managed, and `backend-app-stack` has been planned but not applied (`infra/terraform/README.md`). AWS does not yet have a finished backend in Terraform either.

## 7. Gaps and risks compared with AWS

- **Messaging.** There is no SNS/SQS equivalent. The choices are Kafka (too expensive) or a broker the team runs itself, with DLQ and retry logic it builds. That weakens the "managed fan-out" argument in 02 §4.
- **WAF.** None native. The security evidence depends on Cloudflare, a third-party dependency.
- **IAM.** API tokens have custom scopes (assumed), but there is no resource-level IAM and no task roles. Pods and apps hold database credentials directly.
- **OIDC.** CI must store a long-lived DO token, against the repo's AWS OIDC posture (NFR-04).
- **Observability.** No managed logs, metrics or traces comparable to CloudWatch and X-Ray. Expect roughly one or two days to set up Grafana, Loki and Tempo (assumed).
- **Analytics.** No Firehose or Athena. Use a Postgres table, or Spaces with DuckDB.
- **App Platform.** Autoscaling needs dedicated sizes ($29+ each), and components have no persistent volumes (assumed), so a broker cannot run there.
- **Existing work.** The Terraform, CloudFront and CSP setup is AWS-specific and would need rework (§6).
- **SGP1 availability.** Every product needed is listed in SGP1, so this is not a risk.

## 8. Hybrid options

| Option | Assessment |
| --- | --- |
| H1. Keep CloudFront + S3 on AWS; point the `/api/*` origin at DO | Viable. It keeps frontend ADR 0002/0005/0007, NFR-16/33/36 and frontend OIDC. App Platform's default TLS gives an HTTPS origin, which closes NFR-37. The prefix-list protection is lost, and the backend still has no managed messaging or WAF. |
| H2. Stay on AWS with a cheaper pattern | **Best overall.** Use the cost levers in 04 §1/§8: NAT (43.07), RDS (37.23) and the fixed floor of about 118. Remove the NAT with public-subnet tasks or VPC endpoints, use Fargate Spot, run the B2 schedule and tear down on 27 Oct. |
| H3. DOKS + Argo CD as the P5.6 side demo instead of k3s on EC2 | Good value. Visible Kubernetes and GitOps evidence without putting the main runtime at risk. The free control plane plus the new-account credit (§4) make it nearly free. |
| H4. Backend on DO, consuming SNS/SQS across clouds | Reject. It needs static IAM keys on DO. |

## 9. When DigitalOcean wins

DO is the better choice only if **all** of these hold:

- The team drops the broker (Option A+, 01 §8) or accepts Redis Streams.
- Static-token deploys are accepted and NFR-04 is rewritten.
- CPU-only App Platform scaling is judged enough scalability evidence, or the team deliberately wants Kubernetes + Argo CD as the headline on DOKS.
- There are no AWS credits, and cost is the binding constraint.
- The lecturer confirms there is no AWS expectation.
- The switch is decided before the proposal (4 Oct), so the 6-10 agent-day rebuild lands before the conduct window.

## 10. Recommendation

1. **Stay on AWS (H2) and trim the cost:** drop the NAT gateway, use Fargate Spot, keep the B2 off-hours schedule, and tear down on 27 Oct.
2. **Optional: run the Kubernetes + Argo CD side demo on DOKS (H3)** instead of k3s on EC2 (P5.6), if the team wants GitOps evidence.
3. **Consider H1** only if cost becomes the binding constraint.

## 11. Sources

All retrieved 2026-09-26. Items marked "assumed" above were not confirmed on a DO page.

- DO regional availability: https://docs.digitalocean.com/platform/regional-availability/
- DOKS pricing: https://www.digitalocean.com/pricing/kubernetes
- App Platform pricing: https://docs.digitalocean.com/products/app-platform/details/pricing/
- App Platform URL rewrites and routing: https://docs.digitalocean.com/products/app-platform/ (url-rewrites)
- Droplet pricing: https://www.digitalocean.com/pricing/droplets
- Managed databases pricing: https://www.digitalocean.com/pricing/managed-databases
- PostgreSQL versions: https://docs.digitalocean.com/products/databases/postgresql/
- General pricing (load balancers, Spaces, Container Registry, NAT, Functions): https://www.digitalocean.com/pricing
- GitHub Student Pack change: https://github.com/orgs/community/discussions/201240
- New-account trial: https://www.digitalocean.com/community/questions/ ("sign up and get $200 in credit for your first 60 days")
- DO OIDC proof of concept: https://github.com/digitalocean-labs/droplet-oidc-poc
- AWS baselines: `04-aws-cost-estimate.md` (AWS Price List API, 2026-09-25)
