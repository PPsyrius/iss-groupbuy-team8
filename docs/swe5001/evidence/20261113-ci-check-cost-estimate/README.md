# Development CI baseline and duplicate image-check estimate

Read-only GitHub Actions readback on 13 November 2026 for #802 / #462. The [generated receipt](development-baseline.json) records all available `ci-backend.yml` dev-push runs, their qualifying deliveries and the latest attempt's executed jobs. This establishes an actual development-period backend delivery baseline, separately from the small estimate for repeating image checks. It does not cover all repository workflows or claim an observed invoice reduction.

## Full 147-delivery runner-time baseline

All 147 qualifying runs were inspected, not just the three image-check samples below. Sum each executed job's `completed_at − started_at`, excluding skipped jobs and queue time. Parallel jobs consume separate runners and are added, not replaced with elapsed workflow duration. Retained Sonar coverage/verification, packaging, publication and deployment are included rather than treated as avoided work.

| Period | Qualifying deliveries | Summed execution minutes | Reconstructed whole-job rounded minutes | Cost equivalent at $0.006/min |
| --- | ---: | ---: | ---: | ---: |
| September | 70 | 518.72 | 691 | $4.15 |
| October | 69 | 753.85 | 944 | $5.66 |
| November, through 11 Nov | 8 | 76.22 | 98 | $0.59 |
| Total | 147 | 1,348.78 | 1,733 | $10.40 |

Mean summed execution time is **9.18 runner-minutes per delivery**; median is **8.95**. The total is `80,927 / 60 = 1,348.78` execution minutes. Summing `ceil(job_seconds / 60)` over executed jobs gives 1,733 rounded minutes, worth `$10.398` at the published Linux rate. These are job-time measurements and a rate-based cost equivalent, not a billing export. Only `ubuntu-latest` was observed. Pull-request, merge-queue, tag, frontend and security workflows, storage, other repositories and older attempts of rerun workflows are outside this baseline, so it must not be equated to the account's whole Actions bill.

The current pipeline still runs verification inside post-merge Sonar. For example, [PR run 43003764874](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/43003764874) spent 7m14s in `build-quality`, 2m01s in `docker` and 57s in Sonar; [dev run 43008442277](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/43008442277) still spent 8m10s in Sonar, 52s in packaging and 72s in publication. Consequently neither the baseline nor all pre-merge test time can be claimed as avoided duplicate validation. The estimate below covers only the omitted image-check steps.

## Timing sample

Three successful `docker` jobs on `ubuntu-latest`, sampled from 10–11 November. Durations are calculated from the Jobs API's step timestamps, excluding image build, runner setup and unrelated jobs. The delivery path still builds its image, so that build is not counted as avoided work.

| Run / job | Smoke (s) | Trivy DB restore (s) | Scan (s) | SARIF upload (s) | Total (s) |
| --- | ---: | ---: | ---: | ---: | ---: |
| [43003764874 / 130469586781](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/43003764874/job/130469586781) | 19 | 20 | 19 | 2 | 60 |
| [42821232703 / 129851270978](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42821232703/job/129851270978) | 21 | 19 | 13 | 1 | 54 |
| [42817847787 / 129838914594](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42817847787/job/129838914594) | 20 | 22 | 13 | 1 | 56 |

Calculated mean: `(60 + 54 + 56) / 3 = 56.67 seconds`, or `0.9444 runner-minutes` per delivery. This is a three-run ballpark, not a controlled before/after experiment.

## Development-period volume and dollar estimate

The full paginated workflow-runs history available on 13 November returned **166** `ci-backend.yml` runs for `event=push`, `branch=dev`, from **5 September to 11 November 2026**. Per-run Jobs API checks identify **128** successful `publish-ecr-dev` jobs and **19** earlier runs with no separate publisher job but successful `docker` and `deploy-existing-ec2` jobs. Count those disjoint groups as **147 successful delivery opportunities**. Failed, cancelled or otherwise non-qualifying runs are excluded. The oldest retained run is not proof that all project work began on 5 September.

| Month | Dev-push runs | Successful separate image publications | Earlier successful Docker + EC2 deliveries | Eligible total |
| --- | ---: | ---: | ---: | ---: |
| September | 89 | 51 | 19 | 70 |
| October | 69 | 69 | 0 | 69 |
| November, through 11 Nov | 8 | 8 | 0 | 8 |
| Total | 166 | 128 | 19 | 147 |

Two empty job responses were checked again: run `37411326383` returned its successful `publish-ecr-dev` job on retry; run `33952366644` still returned zero jobs and is excluded. Earlier workflows actually ran Docker checks after merge. This is therefore a full-period volume scenario applying the current no-duplicate-check policy and November timings, not a claim that all 147 historical deliveries avoided those checks.

Using GitHub's published [Linux 2-core x64 rate](https://docs.github.com/en/billing/reference/actions-runner-pricing) of **US$0.006 per minute**, read on 13 November:

- Per delivery: `56.67 / 60 × $0.006 = $0.00567`, about 0.6 US cents.
- Across 147 deliveries: `147 × (170 / 3) / 60 = 138.83 runner-minutes`; `138.83 × $0.006 = $0.833`, approximately **139 runner-minutes / US$0.83 for the development period**.
- October comparison: `69 × (170 / 3) / 60 = 65.17 runner-minutes`; `65.17 × $0.006 = $0.391`, approximately **US$0.40**.
- At 100 deliveries: approximately `94.44 runner-minutes`, or **US$0.57**.

GitHub rounds each job's duration up to a whole minute, not each step. These dollar figures prorate the avoided execution time; actual marginal billed minutes depend on the containing job's duration and billing account terms. No separate duplicate job, artifact transfer saving, Maven test saving, frontend saving, storage cost or invoice reconciliation is included. Parallel jobs must be counted separately; the sampled steps run sequentially in one job. The calculation supports a small expected saving for this specific seam, not a large reduction in the overall Actions bill.

## Reproduce the readback

```powershell
python docs/swe5001/evidence/20261113-ci-check-cost-estimate/collect_baseline.py --output docs/swe5001/evidence/20261113-ci-check-cost-estimate/development-baseline.json
gh api repos/PPsyrius/iss-groupbuy-team8/actions/runs/43003764874/jobs
gh api repos/PPsyrius/iss-groupbuy-team8/actions/runs/42821232703/jobs
gh api repos/PPsyrius/iss-groupbuy-team8/actions/runs/42817847787/jobs
gh api repos/PPsyrius/iss-groupbuy-team8/actions/workflows/ci-backend.yml/runs --method GET -f event=push -f branch=dev -f per_page=100 --paginate
```

For every returned run, query `actions/runs/<id>/jobs?per_page=100`. Count a run once if `publish-ecr-dev` succeeded, or if no job with that name exists and both `docker` and `deploy-existing-ec2` succeeded. Group by the run's UTC creation month. These are read-only requests; no workflow or deployment was triggered.
