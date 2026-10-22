# Delivery metrics (DORA) of dev, snapshot 22 October 2026

The four delivery metrics of *DevSecOps Engineering and Automation* module 16 (Business Metrics, p. 3) are deployment frequency, deployment lead time, change success rate and mean time to recover. Here they are measured for the SWE5006 dev environment from GitHub Actions history. The measurement is made by `.github/scripts/dora-metrics.py`, whose docstring holds the exact definitions, and is reproduced by one command:

```bash
DORA_CACHE=/tmp/dora-cache.json python .github/scripts/dora-metrics.py PPsyrius/iss-groupbuy-team8 2026-09-01 2026-10-23
```

## What counts

| Term | Meaning here |
|---|---|
| Deployment | A deploy job that ran to success or failure on a push to `dev`. Backend: `deploy-existing-ec2` in `ci-backend.yml` (EC2 via SSM; a failed health check rolls back automatically and the job fails). Frontend: `deploy-s3-dev` in `ci-frontend.yml` (S3 and CloudFront). A skipped job (the merge did not touch that part) or a cancelled run is not a deployment. |
| Deployment lead time | From the first commit of the merged pull request to the end of the first successful deployment of that part at or after the pull request's own deployment attempt |
| Change success rate | Successful deployments over all deployments; manual `rollback-backend` runs would count as failed backend changes (there were none) |
| Mean time to recover | From a failed deployment to the next successful deployment of the same part |

## Results, 1 September to 22 October 2026

| Metric | Backend | Frontend |
|---|---|---|
| Deployments (successful / all) | 123 / 143 | 160 / 160 |
| Deployment frequency | 16.6 per week (whole window) | 21.5 per week over the whole window; 37 per week since the first frontend deployment on 23 September |
| Deployment lead time | median 8.6 h, p90 62.6 h (143 changes) | median 6.2 h, p90 33.8 h (159 changes) |
| Change success rate | 86 % | 100 % |
| Mean time to recover | median 10.9 h, p90 64.9 h (5 recoveries) | none needed |
| Manual rollbacks | 0 | n/a |

**All 20 failed backend deployments fall between 5 and 17 September.** That was the first week and a half of the EC2 pipeline: the first deployment ran on 5 September, and Sprint 1 and early Sprint 2 were spent bringing up the host, the SSM bundle and the health check. Splitting the window there:

| Backend | 5-17 September | 18 September-22 October |
|---|---|---|
| Deployments (successful / all) | 21 / 41 | 102 / 102 |
| Deployment frequency | 8.6 per week | 20.4 per week |
| Lead time (median, p90) | 3.1 h, 65.1 h | 7.1 h, 61.4 h |
| Change success rate | 51 % | 100 % |
| Mean time to recover (median) | 6.1 h (4 recoveries) | none needed |

## Current values against targets

The layout follows DSO 16 p. 4 (metric, unit, current value, target). The targets are proposed here and need the team's agreement at the Sprint 4 retrospective (#619).

| Metric | Unit | Current (since 18 Sep, backend) | Proposed target |
|---|---|---|---|
| Deployment frequency | per week | 20 | On demand: every merged change that touches the part is deployed |
| Deployment lead time | hours, median | 7.1 | < 24 |
| Change success rate | % | 100 | ≥ 90 |
| Mean time to recover | hours, median | no failure since 18 Sep (6.1 during bring-up) | < 1 during working hours |

## Limits of the measurement

- **Dev, not production.** SWE5006 has only the dev environment, so "running in production" in the course's definition means deployed to dev here. The `backend-prod` path is defined but has never run.
- **Each part counts its own changes.** A pull request that touches only the frontend has no backend deployment and appears only in the frontend figures.
- **Recovery time is calendar time.** Nobody is on call, so a failure late in the day or before a weekend recovers the next working day; that is what the p90 of about 65 hours shows. Recovery also counts the time to write the fix, not only to redeploy.
- **Long-lived branches dominate the p90 lead time.** Examples are the Sprint 2 payment slice and stacked series. The Sprint 3 limit of about 1,500 changed lines per PR ([Sprint 3 Definition of Done](../agile/sprint-03/plan.md#8-definition-of-done)) targets this.
- **Associated pull requests.** One frontend merge commit has no associated pull request on GitHub; it counts as a deployment but has no lead time.
- **Not measured.** The SWE5001 ECS sessions are created and destroyed by hand, so their deployments are out of scope.
