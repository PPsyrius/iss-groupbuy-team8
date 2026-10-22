# Delivery metrics (DORA) of dev, snapshot 22 October 2026

*DevSecOps Engineering and Automation*, module 16 (Business Metrics), lists four delivery metrics on PDF p. 3: deployment frequency, deployment lead time ("from commit to running in production"), mean time to recover ("to restore service when an incident occurs") and change success rate ("percentage of changes that successfully rolled out"). Its example on p. 4 tracks each metric with a unit, a current value and a target. This page measures the SWE5006 dev environment from GitHub Actions history with `.github/scripts/dora-metrics.py`, whose docstring holds the exact definitions. History was fetched on 22 October 2026 at 12:39 UTC; every figure below comes from one cached fetch:

```bash
DORA_CACHE=/tmp/dora-cache.json python .github/scripts/dora-metrics.py PPsyrius/iss-groupbuy-team8 2026-09-01 2026-10-23
```

The same command with another window (for example `2026-09-18 2026-10-23`) reuses the cache and counts only deployments that finished inside the window.

## What counts

| Term | Meaning here |
|---|---|
| Deployment | A deploy job that ran to success or failure on a push to `dev`. Backend: `deploy-existing-ec2` in `ci-backend.yml` (EC2 via SSM; a failed health check rolls back automatically and the job fails). Frontend: `deploy-s3-dev` in `ci-frontend.yml` (S3 and CloudFront). A skipped job (the merge did not touch that part) or a cancelled run is not a deployment. |
| Deployment lead time | From the first commit of the merged pull request to the end of the first successful deployment of that part at or after the pull request's own deployment attempt |
| Change success rate | Successful deployments over all deployments; manual `rollback-backend` runs would count as failed backend changes (there were none) |
| Failed deployment recovery time | From a failed deployment to the next successful deployment of the same part. This stands in for the course's mean time to recover, which starts at an incident: there is no incident record, and a failed backend deployment rolls back automatically where a previous image exists, so the previous version keeps serving while the change is fixed. |

## Results, 1 September to 22 October 2026

The last deployments in the fetch ran on 21 October (backend) and 20 October (frontend).

| Metric | Backend | Frontend |
|---|---|---|
| Deployments (successful / all) | 123 / 143 | 160 / 160 |
| Deployment frequency | 16.6 per week over the window | 21.5 per week over the window; 37.3 per week from the first frontend deployment on 23 September |
| Deployment lead time | median 8.6 h, p90 62.6 h (143 changes) | median 6.7 h, p90 33.8 h (160 changes) |
| Change success rate | 86 % | 100 % |
| Failed deployment recovery time | median 10.9 h, p90 64.9 h (5 recoveries) | none needed |
| Manual rollbacks | 0 | n/a |

**All 20 failed backend deployments fall between 5 and 17 September**, the first two weeks of the EC2 pipeline: the first deployment ran on 5 September, and the time went into bringing up the host, the SSM bundle and the health check.

| Backend | 5-17 September | 18 September-22 October |
|---|---|---|
| Deployments (successful / all) | 21 / 41 | 102 / 102 |
| Deployment frequency | 11.3 per week | 20.4 per week |
| Lead time (median, p90) | 3.1 h, 65.1 h | 7.1 h, 61.4 h |
| Change success rate | 51 % | 100 % |
| Failed deployment recovery time (median) | 6.1 h (4 recoveries) | none needed |

## Current values in the course's layout

The layout follows the DSO 16 p. 4 example. No targets are set: they are the team's to agree, and none has been agreed.

| Metric | Unit | Current, backend since 18 September | Target |
|---|---|---|---|
| Deployment frequency | per week | 20.4 | not set |
| Deployment lead time | hours, median | 7.1 | not set |
| Change success rate | % | 100 | not set |
| Failed deployment recovery time | hours, median | no failure since 18 September (6.1 during bring-up) | not set |

## Limits of the measurement

- **Dev, not production.** SWE5006 has only the dev environment, so "running in production" means deployed to dev here. The `backend-prod` deployment path is defined but has never run.
- **Each part counts its own changes.** A pull request that touches only the frontend has no backend deployment and appears only in the frontend figures.
- **Recovery is calendar time to the next good deployment**, not service downtime. Nobody is on call, so a failure late in the day or before a weekend recovers on the next working day, which is what the p90 of about 65 hours shows; it also includes the time to write the fix.
- **Long-lived branches dominate the p90 lead time**, for example the Sprint 2 payment slice and stacked series. The Sprint 3 limit of about 1,500 changed lines per PR ([Sprint 3 Definition of Done](../agile/sprint-03/plan.md#8-definition-of-done)) targets this.
- **Not measured.** The SWE5001 ECS sessions are created and destroyed by hand, so their deployments are out of scope.
