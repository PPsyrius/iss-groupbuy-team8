// D5: physical deployment view (UML deployment diagram). Source of Figure 3.x in 3.2.
#import "../../../diagrams/style.typ": *

#let d5 = {
  set text(font: ("Arial", "Helvetica"), size: 8pt, fill: ink)
  diagram(
    spacing: (14mm, 9mm),
    node-stroke: 0.6pt + grey,
    node-corner-radius: 2pt,
    edge-stroke: 0.6pt + ink,
    // Outside the VPC
    node((0, 0), box-label("device", [Browser], extra: [React SPA]), fill: white, name: <browser>),
    node((2, 0), box-label("node", [CloudFront], extra: [HTTPS, `*.cloudfront.net`]), fill: infra-fill, name: <cf>),
    node((4, 0), box-label("node", [S3 bucket], extra: [SPA files, CloudFront OAC only]), fill: infra-fill, name: <s3>),
    node((0, 1), box-label("external system", [Stripe]), fill: white, name: <stripe>),
    node((4.9, 1), box-label("external system", [Google sign-in]), fill: white, name: <google>),
    node((5.1, 5.7), box-label("node", [SNS topic → SQS], extra: [notification + audit queues, 2 DLQs]), fill: messaging-fill, stroke: messaging, name: <mq>),
    // Inside the VPC
    node((2, 2), box-label("node", [Application Load Balancer], extra: [public subnets · HTTP :80]), fill: infra-fill, name: <alb>),
    node((3.9, 2), box-label("node", [fck-nat], extra: [t4g.nano · public subnet]), fill: infra-fill, name: <nat>),
    node((0.8, 3.3), box-label("execution environment", [`identity-svc`], extra: [Fargate · 1 task]), fill: platform-fill, stroke: platform, name: <id>),
    node((2, 3.3), box-label("execution environment", [`commerce-svc`], extra: [Fargate · 2–6 tasks]), fill: business-fill, stroke: business, name: <com>),
    node((3.2, 3.3), box-label("execution environment", [`notification-svc`], extra: [Fargate · 1–4 tasks]), fill: platform-fill, stroke: platform, name: <ntf>),
    node((3.0, 4.6), box-label("database", [RDS PostgreSQL 18], extra: [db.t4g.micro · 3 databases]), fill: white, name: <rds>),
    node((1.0, 4.6), box-label("database", [ElastiCache Valkey 8.0], extra: [cache.t4g.micro · sessions]), fill: white, name: <cache>),
    node(enclose: (<alb>, <nat>, <id>, <com>, <ntf>, <rds>, <cache>), stroke: (paint: grey, dash: "dashed"), fill: none, inset: 10pt,
      align(bottom + right, text(size: 7pt, fill: grey)[«node» VPC 10.50.0.0/16 · two zones])),
    // Communication paths
    edge(<browser>, <cf>, "-|>", [HTTPS]),
    edge(<cf>, <s3>, "-|>", [static files]),
    edge(<stripe>, <cf>, "-|>", [webhook (HTTPS)], label-side: right),
    edge(<cf>, <alb>, "-|>", [`/api/*` · HTTP + origin secret]),
    edge(<alb>, <id>, "-|>"),
    edge(<alb>, <com>, "-|>", [path rules]),
    edge(<alb>, <ntf>, "-|>"),
    edge(<com>, <id>, "-|>", [Service Connect], stroke: 0.9pt + business, label-side: left),
    edge(<id>, <rds>, "-|>"),
    edge(<com>, <rds>, "-|>"),
    edge(<ntf>, <rds>, "-|>", [TLS :5432], label-side: left),
    edge(<com>, <cache>, "-|>", [TLS + AUTH :6379], label-side: right),
    edge(<com>, (2, 5.7), <mq>, "-|>", [publish (outbox relay)], label-pos: 0.75),
    edge(<mq>, <ntf>, "-|>", [long poll], label-side: right),
    edge(<ntf>, <nat>, "-|>", [egress], label-side: right),
    edge(<nat>, <google>, "-|>"),
  )
}
