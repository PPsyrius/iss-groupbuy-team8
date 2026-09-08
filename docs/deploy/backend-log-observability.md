# Backend Log Observability Guide

How to view and search BlocBuy backend runtime logs in the dev environment (EC2 + CloudFront),
from quickest to most powerful. Everything here works without opening any inbound port on the
EC2 security group — all access goes through AWS Systems Manager (SSM) and CloudWatch.

## Architecture

```text
blocbuy-backend container
   │  stdout/stderr
   ▼
Docker awslogs driver ──(instance role: BlocBuyEc2Runtime)──► CloudWatch Logs
                                                               log group: /blocbuy/backend/dev
                                                               stream: backend
                                                               retention: 14 days
   └─ (dual logging: a local ring buffer keeps `docker logs` working on the host)
```

Key facts:

- **Logs outlive the container.** Every deploy force-recreates the backend container, which used to
  destroy the json-file logs with it. CloudWatch Logs is independent of the container lifecycle.
- **Every request carries a traceId.** The backend (`TraceIdFilter`) generates one per request
  (or honors an inbound `X-Trace-Id` header), puts it in every log line via MDC, and echoes it in
  the `X-Trace-Id` response header and in the `traceId` field of the JSON error body. This is your
  primary search key.
- **Spring does not emit access logs** by default, so log volume is low (startup + application
  logs + exception stacks). CloudWatch cost for dev is effectively zero (well under the 5 GB/mo
  free tier).

## 1. Fastest path: find a failed request's logs by traceId

When an API call fails, the response already contains the traceId:

```json
{"code":"COMMON_500_001","message":"Internal server error","traceId":"04eb739b-a076-49df-be50-75226f4bda4e"}
```

### Option A — CloudWatch Logs Insights (web console, recommended)

CloudWatch → Logs → Logs Insights → select log group `/blocbuy/backend/dev`, then:

```sql
fields @timestamp, @message
| filter @message like /04eb739b-a076-49df-be50-75226f4bda4e/
| sort @timestamp desc
```

Other handy queries:

```sql
-- All errors in the selected time range
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 50

-- All unhandled exceptions (the GlobalExceptionHandler logs every 500 this way)
fields @timestamp, @message
| filter @message like /unhandled exception/

-- OAuth login failures
fields @timestamp, @message
| filter @message like /IDENTITY_/
| sort @timestamp desc
```

### Option B — AWS CLI

```bash
# Search by traceId
aws logs filter-log-events \
  --log-group-name /blocbuy/backend/dev \
  --filter-pattern '"04eb739b-a076-49df-be50-75226f4bda4e"'

# Live-tail the stream (Ctrl+C to stop)
aws logs tail /blocbuy/backend/dev --follow

# Errors from the last hour
aws logs filter-log-events \
  --log-group-name /blocbuy/backend/dev \
  --start-time $(( ($(date +%s) - 3600) * 1000 )) \
  --filter-pattern '?ERROR ?Exception'
```

Requires your local AWS identity to have `logs:FilterLogEvents` / `logs:GetLogEvents` /
`logs:StartQuery` on the log group (see "Developer IAM setup" below).

## 2. On-box fallback: SSM shell + docker logs

Useful when CloudWatch ingestion lags or you want host-level context (docker events, disk, etc.).
Thanks to Docker dual logging, `docker logs` still works even though the driver is awslogs —
but remember the local buffer is wiped on every container recreation, so **capture logs right
after reproducing the problem, before any redeploy or restart**.

```bash
# Open an interactive shell on the instance (no SSH key or open port 22 needed)
aws ssm start-session --target i-0852b6c3475b19f05

# On the instance:
sudo docker logs blocbuy-backend --since 30m 2>&1 | grep "<traceId>"
sudo docker logs -f blocbuy-backend          # follow live
```

## 3. Debugging beyond logs: SSM port forwarding

Reach EC2 loopback services directly from your laptop — no security-group changes:

```bash
# Backend (bypass CloudFront/ALB entirely):
aws ssm start-session --target i-0852b6c3475b19f05 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8589"],"localPortNumber":["8589"]}'
# then: curl http://127.0.0.1:8589/api/actuator/health

# PostgreSQL (then connect with DBeaver/DataGrip to 127.0.0.1:5432, db BlocBuy, schema domain):
aws ssm start-session --target i-0852b6c3475b19f05 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["5432"],"localPortNumber":["5432"]}'

# Redis (then: redis-cli -h 127.0.0.1 -a <REDIS_PASSWORD>):
aws ssm start-session --target i-0852b6c3475b19f05 \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["6379"],"localPortNumber":["6379"]}'
```

Each command holds the terminal open; `Waiting for connections...` means the tunnel is up.
Ctrl+C to close. Note the OAuth2 login flow itself must still go through the public CloudFront
URL (the Google redirect URI points there); port forwarding is for pure APIs and data inspection.

## 4. Reproducing locally

The database schema is fully managed by Flyway (`classpath:db/migration`, `default-schema:
domain`), so a local environment is two containers away:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18
docker run -d --name redis -p 6379:6379 redis:8
# create database BlocBuy; then run the app with the `local` profile — Flyway creates
# schema `domain` and all tables automatically.
```

Match the middleware versions to the EC2 host (PostgreSQL 18, Redis 8) to avoid behavior drift.

## Developer IAM setup (one-time)

### Local AWS CLI

Install AWS CLI v2 **and the Session Manager plugin** (`start-session` is provided by the plugin):

```bash
brew install awscli
brew install --cask session-manager-plugin
```

### Permissions

Ask an admin for an IAM user (no console access needed) with these policies:

1. **SSM port forwarding + shell** — customer-managed policy `BlocBuySsmPortForwarding`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "StartPortForwarding",
      "Effect": "Allow",
      "Action": ["ssm:StartSession", "ssm:TerminateSession", "ssm:ResumeSession"],
      "Resource": [
        "arn:aws:ssm:ap-southeast-1::document/AWS-StartPortForwardingSession",
        "arn:aws:ec2:ap-southeast-1:402178232802:instance/i-0852b6c3475b19f05"
      ]
    },
    {
      "Sid": "DescribeSessions",
      "Effect": "Allow",
      "Action": ["ssm:DescribeSessions", "ssm:DescribeInstanceInformation", "ssm:GetConnectionStatus"],
      "Resource": "*"
    }
  ]
}
```

> Gotcha: AWS-managed SSM documents have an **empty account segment** in their ARN
> (`arn:aws:ssm:ap-southeast-1::document/...`). Using the account ID there silently fails with
> AccessDenied.

2. **CloudWatch Logs read** — for CLI log search:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ReadBackendLogs",
    "Effect": "Allow",
    "Action": ["logs:FilterLogEvents", "logs:GetLogEvents", "logs:DescribeLogStreams",
               "logs:StartQuery", "logs:GetQueryResults", "logs:DescribeLogGroups"],
    "Resource": "arn:aws:logs:ap-southeast-1:402178232802:log-group:/blocbuy/backend/*:*"
  }]
}
```

Then configure locally:

```bash
aws configure        # access key, secret, region ap-southeast-1, output json
aws sts get-caller-identity   # expect "Account": "402178232802"
aws ssm describe-instance-information \
  --filters Key=InstanceIds,Values=i-0852b6c3475b19f05 \
  --query 'InstanceInformationList[0].PingStatus'   # expect "Online"
```

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `start-session` → AccessDenied on `...::document/...` | Policy uses account-qualified document ARN; use the empty-account form shown above |
| `TargetNotConnected` | SSM agent offline on the instance (rare; deploys would also fail) |
| `docker logs` empty after a deploy | Expected — local buffer is recreated with the container; use CloudWatch for history |
| No events in `/blocbuy/backend/dev` after deploy | Instance role missing `BlocBuyEc2CloudWatchLogs`; check `aws logs describe-log-streams --log-group-name /blocbuy/backend/dev` and the role's attached policies |
| `MalformedPolicyDocument` when creating policies in CloudShell | Heredoc/paste mangled the JSON; pass the policy as a single-line inline string instead |
| Console policy editor fails with `access-analyzer:ValidatePolicy` denied | The org SCP blocks Access Analyzer validation; apply IAM changes via CLI (`aws iam create-policy-version`) instead of the console editor |

## Cost guardrails

- Log retention is 14 days (`aws logs put-retention-policy --log-group-name /blocbuy/backend/dev
  --retention-in-days 14`).
- Keep the ec2 profile at INFO level; DEBUG on a busy instance is where real cost appears.
- Expected dev spend: under $0.50/month, typically within the 5 GB free tier entirely.

## Related

- [Existing EC2 operator guide](backend-ec2-pipeline-operator-guide.md) — deploy pipeline, rollback, secrets
- [deployment/backend/README.md](../../deployment/backend/README.md) — bundle layout and runtime model
