// How the Briefing's Success Criteria Are Met: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

== How the Briefing's Success Criteria Are Met

The briefing gives five success criteria for the platform. @tbl-success shows how BlocBuy meets each one and what kind of evidence backs it; together with §4.1–§4.4 it answers briefing Q5, which asks for the technical and non-technical benefits to be demonstrated.

#figure(
  table(
    columns: (1.6fr, 2.6fr, 1fr),
    table.header([Success criterion (briefing)], [How BlocBuy meets it], [Evidence]),
    [Identify the seed, producers and consumers of the platform], [Seed: curated team-priced offers; producers: the platform administrator, who publishes listings on sellers' behalf, team leaders and sharers (sellers do not use BlocBuy directly; seller self-service is out of scope, §1.4.2); consumers: joining buyers (§1.3, @tbl-platform-roles, @fig-d1-context)], [Designed and built],
    [Identify the use cases that can expand the ecosystem], [Starting a team (UC3) and sharing an invitation (UC7) that credits the sharer for each referred join (UC12) turn a consumer into a producer who brings in the next buyers], [Built in SWE5006; unchanged in the services],
    [Show how the platform scales the number of customers and producers], [Browsing and paying scale out on `commerce-svc` without touching identity or notification. On 19 October on AWS, commerce scaled from one to three tasks under load (§4.1); the run is invalid as acceptance evidence because the load generator dropped iterations (AISS-12)], [Scale-out verified on AWS],
    [Support more seeds in future], [A new vertical is a new service from the golden-path map entry, reusing sign-in, notification and settlement, and a new consumer is one queue entry (§4.4)], [Built; not yet exercised with a second seed],
    [Identify and perform analytics that improve the platform], [Operational analytics are in use and set the scaling target; business analytics are designed on data already stored (§6.5)], [Operational: dashboard verified on AWS (16 October), alarms configured but never exercised; business: designed],
  ),
  kind: table,
  caption: [Briefing success criteria (briefing V2.6, pp. 10–11).],
) <tbl-success>
