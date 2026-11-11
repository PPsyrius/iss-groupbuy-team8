// Supplemental D5 viewpoints: UML deployment nodes, not a current AWS inventory.
#import "style.typ": *

#let deployed(title, body, fill: infra-fill) = block(
  width: 100%, inset: 7pt, radius: 2pt, stroke: 0.5pt + grey, fill: fill,
)[#align(center)[#text(size: 7pt, fill: grey)[«execution environment»] \ #strong(title) \ #body]]

#let d5-topologies = {
  set text(font: ("Arial", "Helvetica"), size: 8pt)
  grid(
    columns: (1fr, 1fr, 1fr), gutter: 7pt,
    block(inset: 6pt, stroke: 0.6pt + grey)[
      *SWE5006: EC2 monolith* \
      #text(size: 7pt)[Historical recording, 24 Oct]
      #align(center)[Browser → CloudFront / ALB ↓]
      #deployed([EC2 · Docker / Nginx], [One backend container \ All six contexts in-process])
      #align(center)[↓ SQL / session cache]
      #deployed([PostgreSQL · Redis], [Monolith persistence], fill: white)
      #text(size: 7pt)[*Disabled:* remote identity adapter and SNS/SQS notification path. Local calls and in-process domain listeners.]
    ],
    block(inset: 6pt, stroke: 0.6pt + grey)[
      *SWE5001: ECS monolith* \
      #text(size: 7pt)[Configured fallback, ADR 0008]
      #align(center)[Browser → CloudFront / ALB ↓]
      #deployed([Fargate · monolith task], [One bootstrap assembly \ All six contexts in-process])
      #align(center)[↓ SQL / TLS sessions]
      #deployed([RDS monolith DB · Valkey], [Shared monolith persistence], fill: white)
      #text(size: 7pt)[*Disabled:* Service Connect identity RPC, outbox publication and SQS notification consumption. No independent service scaling.]
    ],
    block(inset: 6pt, stroke: 0.6pt + grey)[
      *SWE5001: distributed ECS* \
      #text(size: 7pt)[Selected released configuration]
      #align(center)[Browser → CloudFront / ALB ↓]
      #deployed([Three Fargate services], [Commerce → identity (sync) \ Notification polls SQS])
      #align(center)[↓ SQL / TLS sessions]
      #deployed([RDS: 3 DBs · Valkey], [App-role ownership per service], fill: white)
      #text(size: 7pt)[*Enabled:* commerce outbox → SNS → notification / audit SQS + DLQs. Audit queue has no worker.]
    ],
  )
}

#let d5-zones = {
  set text(font: ("Arial", "Helvetica"), size: 8pt)
  block(width: 100%, inset: 8pt, stroke: 0.6pt + grey)[
    #align(center)[*Historical VPC placement · 10 November SGT* \ ALB eligible in both public subnets; shared regional services outside this allocation]
    #grid(
      columns: (1fr, 1fr), gutter: 9pt,
      block(inset: 7pt, fill: zone-fill)[
        *ap-southeast-1a* \
        #deployed([Public subnet], [NAT instance (configured zone)])
        #deployed([Application subnet], [Identity: 1 · commerce: 1 \ Notification: 1], fill: platform-fill)
        #deployed([Data subnet], [Valkey: single node], fill: white)
      ],
      block(inset: 7pt, fill: zone-fill)[
        *ap-southeast-1b* \
        #deployed([Public subnet], [No second NAT instance])
        #deployed([Application subnet], [Commerce: 1], fill: business-fill)
        #deployed([Data subnet], [RDS: single instance], fill: white)
      ],
    )
    #text(size: 7pt)[Task/data positions are dated observations, not task-placement guarantees or later R4 positions. Both application subnet routes use the one NAT; data routes are VPC-local. A two-zone subnet group does not make RDS or Valkey multi-zone.]
  ]
}
