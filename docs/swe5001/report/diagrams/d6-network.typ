// D6: network view in the AWS architecture icons convention (group boxes for the VPC,
// availability zones and subnets; no service icons), in the report's UML kit palette (ink,
// muted, role tints from style.typ) and at the kit's 8.5 pt label size. Source of the §3.2
// network figure. The security-group rules are a separate table: d6-security-groups.typ.
// Every CIDR, route and placement is from infra/swe5001 (runtime/main.tf, modules/network).
#import "style.typ": ink, grey, platform, platform-fill, business

#let lbl = 8.5pt
// AWS group colours, toned to the kit palette: VPC purple, availability zone dashed blue,
// public subnet green, private subnet blue.
#let vpc-c = rgb("#7D4FC9")
#let az-c = rgb("#2F6DB5")
#let pub-c = rgb("#5E8A1E")
#let pub-fill = rgb("#EEF4E1")

// A resource placed in a subnet; `sg` names its security group.
#let res(name, sg: none) = box(
  inset: (x: 4pt, y: 2.5pt), radius: 1.5pt, stroke: 0.6pt + ink, fill: white,
  text(size: lbl)[#name#if sg != none [ #text(fill: grey)[(sg #raw(sg))]]],
)
// One subnet: tag, CIDR, route table and what runs there.
#let subnet(tag, cidr, routes, body, stroke: grey, fill: white, warn: none) = block(
  width: 100%, inset: 5pt, radius: 2pt, fill: fill, stroke: 0.9pt + stroke,
  {
    set text(size: lbl, fill: ink)
    set par(leading: 0.45em, justify: false)
    text(weight: "bold", fill: stroke, tag)
    h(1fr)
    raw(cidr)
    linebreak()
    text(fill: grey)[Route table: #routes]
    if warn != none { linebreak(); text(fill: business, weight: "bold", warn) }
    v(3pt)
    body
  },
)
#let zone(name, pub, app, data) = block(
  width: 100%, inset: 6pt, radius: 3pt, stroke: (paint: az-c, thickness: 0.9pt, dash: "dashed"),
  stack(spacing: 5pt,
    text(size: lbl, weight: "bold", fill: az-c)[Availability zone #name],
    pub, app, data,
  ),
)

#let d6 = {
  set text(font: ("Arial", "Helvetica"), size: lbl, fill: ink)
  show raw: set text(size: lbl)
  let pub-rt = [`0.0.0.0/0` → internet gateway; local]
  let app-rt = [`0.0.0.0/0` → fck-nat ENI; S3 → gateway endpoint; local]
  let data-rt = [`10.50.0.0/16` local only]
  let no-out = [No route out of the VPC: no NAT, no internet gateway, no endpoint]
  stack(spacing: 5pt,
    // Outside the VPC: who may enter, and the two ways out.
    grid(columns: (1fr, 1fr, 1fr), gutter: 6pt,
      res[CloudFront origin-facing prefix list],
      res[Internet gateway],
      res[S3 gateway endpoint],
    ),
    block(width: 100%, inset: 7pt, radius: 3pt, stroke: 1.2pt + vpc-c, {
      text(weight: "bold", fill: vpc-c)[VPC #raw("10.50.0.0/16"), Region ap-southeast-1]
      h(1fr)
      text(fill: grey)[Only the prefix list reaches the ALB, on TCP 80]
      v(4pt)
      grid(columns: (1fr, 1fr), gutter: 7pt,
        zone("ap-southeast-1a",
          subnet([Public], "10.50.0.0/24", pub-rt, stroke: pub-c, fill: pub-fill,
            [#res([ALB node], sg: "alb") #res([fck-nat, EIP], sg: "nat")]),
          subnet([Private application], "10.50.10.0/24", app-rt, stroke: platform, fill: platform-fill,
            [#res([ECS tasks], sg: "svc") #res([db-init], sg: "dbinit")]),
          subnet([Data], "10.50.20.0/24", data-rt, stroke: platform, fill: white, warn: no-out,
            [#res([Valkey node], sg: "cache")]),
        ),
        zone("ap-southeast-1b",
          subnet([Public], "10.50.1.0/24", pub-rt, stroke: pub-c, fill: pub-fill,
            [#res([ALB node], sg: "alb")]),
          subnet([Private application], "10.50.11.0/24", app-rt, stroke: platform, fill: platform-fill,
            [#res([ECS tasks], sg: "svc") #res([db-init], sg: "dbinit")]),
          subnet([Data], "10.50.21.0/24", data-rt, stroke: platform, fill: white, warn: no-out,
            [#res([RDS instance], sg: "rds")]),
        ),
      )
      v(4pt)
      set par(leading: 0.45em, justify: false)
      text(fill: grey)[ECS places each task in either private application subnet; db-init runs once per session. RDS (one zone) and Valkey (one node) sit in the zone AWS chooses; each subnet group holds both data subnets. Both private route tables point at the one fck-nat in zone a.]
    }),
  )
}
