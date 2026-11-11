// Assessor Remarks and Where They Are Answered: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== Assessor Remarks and Where They Are Answered

The briefing asks the report to incorporate the remarks gathered at the presentation. #iss(596) lists the lecturers' twelve remarks of 21 October 2026, and the report framework records a thirteenth, F12, on the logical diagram. @tbl-remarks gives each remark the section and the artefact that answer it, so that an assessor can check every one.

#figure(
  table(
    columns: (0.35fr, 2.2fr, 0.75fr, 2.4fr),
    table.header([ID], [Remark (21 October 2026)], [Section], [Artefact that answers it]),
    [F1], [What are the metrics for load testing?], [§4.1], [Metric definitions and pass thresholds, then the results of each run with its validity limits],
    [F2], [Does CI/CD scan the container image after building?], [§5.2], [Pipeline diagram (D10) with the image scan between build and export, and the gate rule],
    [F3], [How did we do context mapping?], [§3.1.4.3], [EventStorming → subdomains → contexts, then the context map with a named pattern on every context-to-context edge (@fig-d4-context-map, @tbl-context-map)],
    [F4], [How are the aggregates arrived at?], [§3.1.4.4], [Command → invariant → consistency boundary → aggregate, one row per aggregate (@tbl-aggregates)],
    [—], [How did EventStorming help us arrive at the proposed solution?], [§3.1.4.1], [Event timeline for UC3–UC6 and the trace from each finding to a decision (@fig-d4a-timeline, @tbl-es-traceability)],
    [F5], [Resultant detailed object design], [§3.2.4], [Class diagrams with real class names for the two critical use cases (@d11-paid, @d13-expiry)],
    [F6], [The database should be in its own subnet], [§3.2, §4.3], [AD-15's disjoint application/data subnets, local-only data routes, security groups and required connectivity were verified in the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-subnet-verification/README.md")[10 November SGT AWS record]. This answers placement/connectivity for that session (AISS-07), not failover or negative packet isolation],
    [F7], [Show the group-buy user flow for two users], [§3.2.4], [Two-buyer sequence with the refusal branch when the last seat is already taken (@d12-paid)],
    [F8], [More perspectives from Architecting Software Solutions, for example deployment], [§3.1, §3.2], [Logical view (@fig-d3-logical-view), deployment view (@fig-d5-deployment), network view (@fig-d6-network), plus the context (@fig-d1-context) and use case (@fig-d2-use-cases) views],
    [F9], [Detailed DDD diagrams (Platform Engineering)], [§3.1.4], [Context map, aggregate diagrams with invariants and life cycles (@fig-d7-aggregates), domain event catalogue (@fig-d8-domain-events)],
    [F10], [Class and sequence diagrams at class or object level for one or two critical use cases (SAD, SDP)], [§3.2.4], [Class and sequence diagrams for UC4 and for team expiry with refund (@d11-paid to @d14-expiry), with the design patterns used],
    [F11], [Data schemas and models for the data stores], [§3.2.3], [ER diagram per database (@fig-d9-identity, @fig-d9-commerce-team), Valkey key model, event envelope, data classification (@tbl-classification)],
    [F12], [The logical architecture diagram is not standard; revise it], [§3.1.3], [Redrawn technology-neutral UML component view (@fig-d3-logical-view)],
  ),
  kind: table,
  caption: [Presentation remarks and their answers. Wording condensed from #iss(596) and the #src("docs/swe5001/report/README.md")[report framework].],
) <tbl-remarks>

#pending[Status on 8 November: F1 (§4.1) and F2 (§5.2) are still scaffolds; check this table against the final text of every section at the reconcile.]
