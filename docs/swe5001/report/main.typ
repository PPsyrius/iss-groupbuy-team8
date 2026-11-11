// SWE5001 Team 8 — Final Project Report
// Structure follows "02 Project Report Template for Practice Project" exactly.
// Build (from the repository root):
//   mkdir -p docs/swe5001/report/build
//   typst compile --root . docs/swe5001/report/main.typ docs/swe5001/report/build/report.pdf

#import "lib.typ": *
#show: style

// Cover page (template: title, module, team, members)
#page(footer: none)[
  #v(1fr)
  #image("../../nus-iss-logo.png", width: 5cm)
  #v(1.5cm)
  #text(24pt, weight: "bold", fill: accent)[Project Report for BlocBuy]
  #v(0.3cm)
  #text(13pt)[A Scalable Community Group-Buying Platform]
  #v(1cm)
  #text(12pt)[Practice Module for Graduate Certificate in Architecting Scalable Systems]
  #v(0.4cm)
  #text(12pt)[Team 8]
  #v(0.6cm)
  #text(11pt)[
    *Members:* \
    Lim Jiechao \
    Liu Jin \
    Panpakorn Siripanich \
    Wang Ziang \
    Yu Qianshuo
  ]
  #v(0.6cm)
  #text(10pt)[Repository: #link("https://github.com/PPsyrius/iss-groupbuy-team8")]
  #v(1fr)
]

#outline(title: [Contents], depth: 3, indent: auto)
#outline(title: [List of figures], target: figure.where(kind: image))
#outline(title: [List of tables], target: figure.where(kind: table))

#heading(numbering: none, outlined: true)[Abbreviations]

#figure(
  table(
    columns: (0.9fr, 3fr),
    table.header([Term], [Meaning]),
    [AD-nn], [Architectural decision nn (§3.3 and the AD register)],
    [AISS-nn], [Architectural issue nn (§3.4)],
    [ALB], [Application Load Balancer],
    [ARN], [Amazon Resource Name],
    [AZ], [Availability Zone],
    [CI], [Continuous integration],
    [CIDR], [Classless inter-domain routing (an IP address range)],
    [COLA], [Clean Object-Oriented and Layered Architecture (Alibaba)],
    [COTS], [Commercial off-the-shelf],
    [DDD], [Domain-driven design],
    [DLQ], [Dead-letter queue],
    [ECR], [Amazon Elastic Container Registry],
    [ECS], [Amazon Elastic Container Service],
    [ENI], [Elastic network interface],
    [F1–F13], [Assessor remarks on the presentation (§6.1)],
    [HS-n], [EventStorming hot spot n (§3.1.4.1)],
    [IaC], [Infrastructure as code],
    [IAM], [AWS Identity and Access Management],
    [KV], [Key–value (store)],
    [NAT], [Network address translation],
    [NFR-nn], [Non-functional requirement nn of the proposal],
    [OIDC], [OpenID Connect],
    [PCI], [Payment Card Industry (data security standard)],
    [PII], [Personally identifiable information],
    [PR], [Pull request],
    [RBAC], [Role-based access control],
    [RDS], [Amazon Relational Database Service],
    [SaaS], [Software as a service],
    [SARIF], [Static Analysis Results Interchange Format],
    [SCP], [Service control policy],
    [SGT], [Singapore time (UTC+8)],
    [SNS], [Amazon Simple Notification Service],
    [SPA], [Single-page application],
    [SQS], [Amazon Simple Queue Service],
    [SSM], [AWS Systems Manager],
    [TLS], [Transport Layer Security],
    [TTL], [Time to live],
    [UC1–UC12], [Use cases in scope (§1.4.1)],
    [V1–V8], [Verification scenarios (§4.1)],
    [VO], [Value object],
    [VPC], [Virtual private cloud],
    [WAF], [Web application firewall],
  ),
  kind: table,
  numbering: none,
  outlined: false,
  caption: [Abbreviations and identifier families used in this report.],
) <tbl-abbreviations>

#include "sections/01-introduction.typ"
#include "sections/02-project-conduct.typ"
#include "sections/03-solution-overview.typ"
#include "sections/04-quality-attributes.typ"
#include "sections/05-devops.typ"
#include "sections/06-highlights.typ"
// Appendix A (sections/07-appendix-a.typ) is the team's design source book, not part of the report.
