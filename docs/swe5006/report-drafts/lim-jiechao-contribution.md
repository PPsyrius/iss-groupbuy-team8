# SWE5006 final report contribution — Lim Jiechao

> Draft for the report editor. The report-ready section below is written for a reader who does not open the repository. The evidence map at the end is editorial material and should not be copied into the submitted report. The use-case section is still to be written by the author.

## Use case

To be written by Lim Jiechao (use case, analysis and design, in the same shape as the other contribution drafts).

## Platform foundation: AWS account governance

Every BlocBuy environment runs in an AWS account that the team does not share with anything else. Before any deployment work started, I created an AWS Organization, created a dedicated member account for the project and placed it in a Sandbox organisational unit, and enabled IAM Identity Center. The management account holds the organization, the identity directory and the guardrails, and runs no workloads; only I can sign in to it.

Team members sign in through Identity Center rather than with IAM users. Two groups map the team's roles onto permission sets with one-hour sessions: administrators (three members) receive AdministratorAccess in the project account, and developers (two members) receive PowerUserAccess, which excludes most identity and access management actions. The command line uses the same single sign-on sessions, so a member's access can be granted or withdrawn in one place. Continuous integration does not hold AWS keys either: GitHub Actions obtain short-lived credentials through an OpenID Connect trust, with separate roles for building images, deploying the backend and deploying the frontend.

Service control policies, attached at the organisation root and on the Sandbox unit, set limits that even an administrator inside the project account cannot lift. They keep all regional work in Singapore, with narrow exceptions for global services and for the CloudFront metrics that live in us-east-1; they stop the account from leaving the organisation, closing itself or creating its own identity directory; they protect the roles that Identity Center manages; and they act as a cost guardrail by denying expensive services and restricting compute and database instances to small burstable classes. In October I consolidated four overlapping policies into this layout, because two separate region pins meant every regional exception had to be made twice and one exception had silently failed.

One exception is recorded rather than hidden: the project account still contains two IAM users whose only project policy allows port forwarding to the development host, and one of them has an active access key. Their permissions are narrow and remain bound by the organisation guardrails.

## Evidence map for the report editor (exclude from the submitted report)

- Account structure, Identity Center assignments, SCP documents, timeline, exceptions and verification: `docs/deploy/aws-account-governance/README.md` and `docs/deploy/aws-account-governance/scp/`.
- GitHub OIDC roles used by the SWE5006 pipelines: `docs/deploy/backend-ec2-pipeline-operator-guide.md`, `docs/deploy/dev-cloudfront-deployment.md`.
- Earlier SCP effects on SWE5006 operations: `docs/deploy/backend-log-observability.md` (troubleshooting table), `docs/agile/sprint-02/log/2026-09-23.md`.
- Accuracy limits: CloudTrail attributes the organisation and Identity Center setup to the management account's root user, not a named person; the guardrails limit services and instance sizes, not spend; AWS Budgets was not checked.
