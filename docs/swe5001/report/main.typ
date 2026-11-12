// SWE5001 Team 8 — Final Project Report
// Structure follows "02 Project Report Template for Practice Project" exactly; the look follows
// the SWE5006 report (docs/swe5006/report/typst/template.typ, through lib.typ `style`).
// Build (from the repository root):
//   mkdir -p docs/swe5001/report/build
//   typst compile --root . docs/swe5001/report/main.typ docs/swe5001/report/build/report.pdf

#import "lib.typ": *
#show: style

// Cover page: the SWE5006 title page (template.typ `report`, the block after `v(1fr)`),
// replicated here because `report` always prints a "Dev environment" row and puts its contents
// straight after the cover, while this report has no live environment to name and adds the
// lists of figures and tables. Values, sizes and colours are the template's.
#{
  let ink = rgb("#1B1A18")
  let muted = rgb("#6B665E")
  let red = rgb("#E8402A")
  let label(body) = pad(top: 2.5pt, text(size: 8.5pt, weight: "bold", tracking: 0.8pt, fill: muted, upper(body)))
  let person(s) = {
    let m = s.match(regex("^(.*?)\s*\((.*)\)$"))
    if m != none [#m.captures.at(0) #text(fill: muted)[(#m.captures.at(1))]] else { s }
  }
  let members = ("Lim Jiechao", "Wang Ziang (Eric)", "Liu Jin", "Yu Qianshuo (Yorick)", "Panpakorn Siripanich (Pan)")
  v(1fr)
  block(width: 100%, {
    set par(spacing: 0pt, leading: 0.55em)
    block(width: 2.2cm, height: 4pt, fill: red)
    v(22pt)
    text(size: 13pt, weight: "bold", tracking: 1.5pt, fill: red, upper("Project Report"))
    v(10pt)
    text(size: 52pt, weight: "bold", tracking: -1.5pt, fill: ink, "BlocBuy")
    v(14pt)
    text(size: 14pt, fill: ink, "Practice Module for Graduate Certificate in Architecting Scalable Systems")
    v(44pt)
    line(length: 100%, stroke: 0.5pt + rgb("#D8D2C8"))
    v(14pt)
    grid(columns: (3.6cm, 1fr), row-gutter: 16pt,
      label[Team], text(weight: "bold", "Team 8"),
      label[Members], stack(spacing: 7pt, ..members.map(person)),
      // The template prints the repository as plain text; here it is also a link, in body ink.
      label[Repository], text(size: 10pt, link(repo, text(fill: ink, "github.com/PPsyrius/iss-groupbuy-team8"))),
    )
  })
  v(1fr)
  pagebreak()
}

// Front matter: Contents, List of figures, List of tables, each titled in the face of the
// SWE5006 Contents title (template.typ `report`: 16 pt bold heading ink, 12 pt above, 18 pt
// below; a plain block, not a heading, so it has no number, no contents entry and no running
// head), entries 9 pt apart with dot leaders. Contents: level 1 in capitals, indent 0.5 cm a
// level, depth 3, as the template.
#let front-title(body) = block(above: 12pt, below: 18pt, text(size: 16pt, weight: "bold", fill: tpl.heading-ink, body))
#{
  front-title[Contents]
  show outline.entry: set block(spacing: 9pt)
  show outline.entry.where(level: 1): it => upper(it)
  outline(title: none, depth: 3, indent: n => n * 0.5cm)
}
#pagebreak()
// The lists of figures and tables print only the first sentence of each caption (S-D10): the
// caption's content up to its first ". " (a text ending in "." followed by a space counts too),
// with code, links and emphasis before that point kept as they are. A caption with no such point
// prints whole. The figure itself keeps its full caption.
#let first-sentence(body) = {
  let kids = if body.func() == [].func() { body.children } else { (body,) }
  let out = ()
  for (i, k) in kids.enumerate() {
    if k.func() == text {
      let t = k.text
      let at = t.position(". ")
      if at != none { out.push(text(t.slice(0, at + 1))); return out.join() }
      let next = kids.at(i + 1, default: none)
      if t.ends-with(".") and (next == none or next == [ ]) { out.push(k); return out.join() }
    }
    out.push(k)
  }
  out.join()
}
// The entry is rebuilt like the built-in one (a link around prefix, body, leader and page), in
// body ink as in the Contents (the template's `show link` would turn the whole entry blue); a
// link inside the caption stays blue. Typst may break a line on either side of an inline box,
// even next to a no-break space, so word joiners tie the caption's last word, the leader and
// the page number together: a full line wraps the caption text under its hanging indent and
// the page number never sits alone on a line.
#let short-entry(it) = context {
  let el = it.element
  let ink = text.fill
  let body = if el.caption == none { it.body() } else { first-sentence(el.caption.body) }
  let leader = if it.fill == none { h(1fr) } else { box(width: 1fr, it.fill) }
  link(el.location(), { set text(fill: ink); it.indented(it.prefix(), [#body#sym.wj~#sym.wj#leader#sym.wj~#it.page()]) })
}
#{
  front-title[List of figures]
  show outline.entry: set block(spacing: 9pt)
  show outline.entry: short-entry
  outline(title: none, target: figure.where(kind: image))
}
#pagebreak()
#{
  front-title[List of tables]
  show outline.entry: set block(spacing: 9pt)
  show outline.entry: short-entry
  outline(title: none, target: figure.where(kind: table))
}

#heading(numbering: none, outlined: true)[Abbreviations]

#figure(
  table(
    columns: (0.9fr, 3fr),
    table.header([Term], [Meaning]),
    [AD-nn], [Architectural decision nn (§3.1.1, §3.2.1 and §3.3)],
    [AISS-nn], [Architectural limitation nn (§3.4)],
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
    [NFR-nn], [Quality requirement nn in the #src("docs/quality/nfr-register.md")[NFR register]],
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
    [V1–V8], [Verification scenarios (@tbl-verification-scenarios, §2.2)],
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
// The detailed design source book (sections/source-book.typ) is the team's design reference, not part of the report.
