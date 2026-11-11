#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

= Other Things to Be Highlighted

This chapter gathers what the other chapters leave scattered: where each remark from the 21 October presentation is answered, how the briefing's success criteria are met, what the cloud actually cost, what only the real cloud revealed, what the platform measures, and where the system can be seen working.

#include "06-1-remarks.typ"
#include "06-2-success-criteria.typ"
#include "06-3-cost.typ"
#include "06-4-real-cloud.typ"
#include "06-5-analytics.typ"
#include "06-6-system-working.typ"
