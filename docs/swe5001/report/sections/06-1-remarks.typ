// Assessor Remarks and Where They Are Answered: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Assessor Remarks and Where They Are Answered

Every remark from the presentation of 21 October 2026 is answered in the report, and @tbl-remarks shows where. The lecturers gave twelve written remarks (F1–F11 and F13); the report framework adds F12, on the logical diagram, which was given orally. The table quotes each remark, gives its state and names the section and the artefact that answer it.

#figure(
  table(
    columns: (0.35fr, 1.9fr, 0.9fr, 0.7fr, 2.2fr),
    table.header([ID], [Remark (21 October 2026, verbatim)], [State], [Section], [Artefact that answers it]),
    [F1], [What are the metrics for load testing?], [Answered: metrics defined; V3 R4 met the k6 gates on AWS, analyser verdict missing], [§4.1], [Metric definitions and pass thresholds (@tbl-performance-metrics), each run with its verdict (@tbl-performance-runs), then the R4 results and scale-out (@tbl-performance-r4, @tbl-performance-r4-scaling)],
    [F2], [Does CI/CD scan container image after building?], [Answered: tested in CI and in the promotions on AWS], [§5.2], [Image chain with the scan between build and export (@fig-d10a-ci-image-chain), the delivery jobs that depend on it (@fig-d10b-delivery), the gate rule and the scan evidence (@tbl-image-scan-evidence)],
    [F3], [How did we do context mapping?], [Answered], [§3.1.4], [EventStorming findings to subdomains to contexts (@tbl-es-to-context), then the context map with a named pattern on every edge (@fig-d4-context-map, @tbl-context-map)],
    [F4], [How are the aggregates arrived at?], [Answered], [§3.1.4], [Command, invariant, consistency boundary and aggregate, one row per root (@tbl-aggregates, 11 aggregate roots)],
    [F5], [Resultant Detailed object design], [Answered: designed; names checked against the code], [§3.2.4], [Analysis-to-design mapping tables (@tbl-uc4-map-summary, @tbl-expiry-map-summary) and class diagrams with real class names for UC4 and for team expiry with refund (@fig-d11-uc4-design-classes, @fig-d13-expiry-design-classes)],
    [F6], [Database should be in its subnet], [Answered: verified on AWS, 10 Nov], [§3.2, §4.3, §5.3], [Disjoint data subnets with local-only routes, security groups and required connectivity, verified on AWS on 10 November for that session (#src("docs/swe5001/evidence/20261110-subnet-verification/README.md")[10 November record]; @fig-d6-network, AD-15, AISS-07); not failover or negative packet isolation. §5.3 records the same session's pool experiment and its end],
    [F7], [Should should groupbuy user flow (for two users)], [Answered: designed; walkthrough recorded on the SWE5006 monolith, 24 Oct], [§3.2.4, §6.6], [Two-buyer sequence with the refusal branch when the last seat is already taken (@fig-d12a-two-buyers-start, @fig-d12b-two-buyers-join); the recorded steps are in @tbl-two-buyer-walkthrough],
    [F8], [Revisit the architectural diagrams taught in the Architecting Software Solutions course and aim to provide more perspectives of the system - e.g., deployment.], [Answered], [§1.3, §1.4, §3.1, §3.2], [View catalogue (@tbl-views): context (@fig-d1-context), use case (@fig-d2-use-cases), logical (@fig-d3-logical-view, @fig-d3b-logical-events-data), logical deployment (@fig-d5b-logical-deployment), physical deployment (@fig-d5-deployment, @fig-d5a-communication-paths) and network (@fig-d6-network)],
    [F9], [Detailed DDD diagrams are expected. Revisit the Platform Engineering course.], [Answered], [§3.1.4], [Context map, aggregate class diagrams (@fig-d7a-aggregates, @fig-d7b-aggregates, @fig-d7c-aggregates), lifecycles (@fig-st-team, @fig-st-intent, @fig-st-payment), domain and integration event views (@fig-d8-domain-events, @fig-d8b-integration-events)],
    [F10], [Detailed design diagrams (class & sequence diagrams) at class/object level for 1 or 2 critical use cases are expected. Revisit the SAD and SDP courses.], [Answered: designed; names checked against the code], [§3.2.4], [Class and sequence diagrams for UC4 and for team expiry with refund (@fig-d11-uc4-design-classes to @fig-d14b-refund-retry), the two-buyer walkthrough (@tbl-two-buyer-walkthrough) and the patterns used (@tbl-patterns-324)],
    [F11], [Data schemas/models for the data stores are expected.], [Answered], [§3.2.3], [Data model per database as UML class diagrams with «table» (@fig-d9a-identity-data, @fig-d9b-notification-data, @fig-d9c-commerce-team-data, @fig-d9d-commerce-payment-data), Valkey key model (@tbl-valkey), event envelope (@tbl-envelope) and data classification (@tbl-classification)],
    [F12], [The logical architecture diagram on the slide is not standard (oral, paraphrased)], [Answered], [§3.1.3], [Redrawn technology-neutral UML component views (@fig-d3-logical-view, @fig-d3b-logical-events-data)],
    [F13], [How did DDD (Eventstorming) help us arrive at our proposed solution.], [Answered], [§3.1.4], [EventStorming timeline (@tbl-d4a-eventstorming), the trace from each finding to a decision (@tbl-es-traceability) and what EventStorming did not decide],
  ),
  kind: table,
  caption: [Presentation remarks, their state and their answers. Source: #iss(596) (verbatim) and the #src("docs/swe5001/report/README.md")[report framework] (F12).],
) <tbl-remarks>
