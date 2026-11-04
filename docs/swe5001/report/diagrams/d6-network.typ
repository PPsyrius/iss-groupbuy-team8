// D6: network and security-group view (AWS architecture diagram style: VPC, zones,
// subnets and security groups as nested boxes; no icons). Source of the 3.2 figure.
#import "style.typ": *

#let subnet(title, cidr, route, body, fill: white, stroke: grey) = block(
  width: 100%, inset: 5pt, radius: 2pt, fill: fill, stroke: 0.6pt + stroke,
  [
    #text(weight: "bold", size: 7.5pt, title) #h(1fr) #text(size: 7pt, raw(cidr)) \
    #text(size: 6.5pt, fill: grey)[route table: #route] \
    #v(2pt)
    #set text(size: 7pt)
    #body
  ],
)
#let res(s) = box(inset: (x: 3pt, y: 1.5pt), radius: 1.5pt, stroke: 0.4pt + grey, fill: white, s)

#let d6 = {
  set text(font: ("Arial", "Helvetica"), size: 8pt, fill: ink)
  let zone(z, pub, app, data, extra-pub) = block(
    width: 100%, inset: 6pt, radius: 3pt, fill: zone-fill, stroke: (paint: grey, dash: "dashed"),
    stack(spacing: 5pt,
      text(size: 7.5pt, fill: grey)[Availability zone ap-southeast-1#z],
      subnet([Public], pub, [0.0.0.0/0 → internet gateway],
        [#res[ALB node] #extra-pub], fill: infra-fill),
      subnet([Private application], app, [0.0.0.0/0 → fck-nat ENI; S3 gateway endpoint],
        [#res[`identity-svc`] #res[`commerce-svc`] #res[`notification-svc`] #res[db-init (one-off)]],
        fill: platform-fill, stroke: platform),
      subnet([Data], data, [local VPC route only — no NAT, no internet, no endpoint],
        [#res[RDS subnet group] #res[Valkey subnet group] \ #text(size: 6.5pt, fill: grey)[one RDS instance and one Valkey node, each in a zone AWS chooses]],
        fill: white, stroke: business),
    ),
  )
  block(width: 100%, inset: 7pt, radius: 3pt, stroke: 0.8pt + ink, [
    #text(weight: "bold")[VPC 10.50.0.0/16] #h(1fr) #text(size: 7pt, fill: grey)[ingress: CloudFront origin-facing prefix list → ALB :80 only]
    #v(4pt)
    #grid(columns: (1fr, 1fr), gutter: 7pt,
      zone("a", "10.50.0.0/24", "10.50.10.0/24", "10.50.20.0/24", res[fck-nat (EIP)]),
      zone("b", "10.50.1.0/24", "10.50.11.0/24", "10.50.21.0/24", []),
    )
    #v(5pt)
    #set text(size: 7pt)
    #table(
      columns: (auto, 1fr, 1fr),
      stroke: 0.4pt + grey,
      [Security group], [Inbound], [Outbound],
      [`alb`], [TCP 80 from CloudFront prefix list], [TCP 8589 to `svc`],
      [`svc`], [TCP 8589 from `alb` and from `svc` (Service Connect)], [all (via fck-nat, VPC)],
      [`rds`], [TCP 5432 from `svc` and `dbinit`], [none],
      [`cache`], [TCP 6379 from `svc`], [none],
      [`dbinit`], [none], [TCP 5432 to `rds`],
      [`nat`], [all from the private application CIDRs], [all to the internet],
    )
  ])
}
