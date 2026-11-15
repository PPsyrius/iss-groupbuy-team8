// How the Briefing's Success Criteria Are Met: part of 06-highlights.typ.
#import "../lib.typ": *

== How the Briefing's Success Criteria Are Met

Two of the briefing's five success criteria are met by built work: the seed, producers and consumers, and the use cases that expand the ecosystem. Scaling is shown on AWS for browse traffic and experimental expiry notifications (§4.1), and the distributed paid two-buyer journey passed once on AWS on 15 November, with the fixture and parity limits in @tbl-followup-15nov. Support for more seeds has a built path that no second seed has used yet. Analytics are operational, and business analytics are designed. @tbl-success shows each criterion's evidence; with §4.1–§4.4 it answers briefing Q5.

#figure(
  table(
    columns: (1.6fr, 2.6fr, 1fr),
    table.header([Success criterion (briefing)], [How BlocBuy meets it], [Evidence]),
    [Identify the seed, producers and consumers of the platform], [Seed: curated team-priced offers; producers: the platform administrator, who publishes listings on sellers' behalf, team leaders and sharers (sellers do not use BlocBuy directly; seller self-service is out of scope, §1.4.2); consumers: buyers and downstream event subscribers (§1.3, @tbl-platform-roles, @fig-d1-context)], [Designed and built],
    [Identify the use cases that can expand the ecosystem], [Starting a team (UC3) and sharing an invite link (UC7) that credits the sharer for each referred join (UC12) turn a consumer into a producer who brings in the next buyers], [Built in SWE5006; unchanged in the services],
    [Show how the platform scales the number of customers and producers], [Commerce scales on its own, without touching `identity-svc` or `notification-svc`. On 11 Nov on AWS, V3 r4 met the browse k6 gates while commerce scaled out (1 vCPU and 2 GiB tasks, `db.t4g.small`, a target of 6,000 requests per target per minute), with no analyser verdict. On 14 Nov, V4 R5, with an experimental expiry configuration, showed real expiries driving `notification-svc` scale-out under the unchanged policy (§4.1). Neither shows maximum capacity, a consumer ceiling or paid-journey scaling], [AWS browse result; experimental notification-scaling pass],
    [Support more seeds in future], [A new seed is four steps: a thin assembly module registered in `backend/pom.xml` with its database grants; one entry in the services map with its ECR repository, image-workflow and start-up-script entries; a listener rule for its API paths, which the golden-path module creates; and reuse of sign-in, notification and settlement through the shared modules and events (§4.4). The catalogue and team-price rules are specific to curated team offers and are not reused], [Mechanisms built; path not yet used],
    [Identify and perform analytics that improve the platform], [Operational analytics are in use and set the scaling target; the real DLQ alarm fired on 14 Nov in V7 R4 and its email reached one of us (§4.2, §6.5). Business analytics are designed on data already stored], [Operational: dashboard, DLQ alarm and its email verified on AWS. Business: designed],
  ),
  kind: table,
  caption: [Briefing success criteria and how BlocBuy meets them. Source: briefing V2.6, pp. 10–11.],
) <tbl-success>

@tbl-briefing-questions maps the eight briefing questions to their answers.

#figure(
  table(
    columns: (0.35fr, 1.4fr, 0.7fr, 2.2fr, 1fr),
    table.header([Q], [Briefing question], [Section], [Answer in one line], [Evidence kind]),
    [Q1], [Pain points], [§1.2], [Pain points per stakeholder and what BlocBuy does instead (@tbl-pain-points)], [Designed],
    [Q2], [Platform architecture, common services and reusable frameworks], [§3.1, §4.4], [Three services on shared COLA modules whose boundaries fail the build when crossed (@tbl-arch-rules); the common services (@tbl-common-services) and the extension seams (@tbl-seams)], [Built; boundaries tested in CI],
    [Q3], [Benefit of building on the platform], [§4.4], [A new consumer's subscription is one element in the `consumers` set and one filter line in the messaging module, plus its worker, dashboard entry and test and probe updates; a new service is one services-map entry and a thin assembly, plus its image, start-up and grant entries (@tbl-seams)], [Counted in the repository; time not measured],
    [Q4], [Scale supported, and the proof], [§4.1], [V3 r4 (11 Nov) served the tested 600 req/s with one to six commerce tasks of 1 vCPU and 2 GiB on `db.t4g.small` (§4.1); one monolith task served 200 req/s. V4 R5 (14 Nov), with an experimental expiry configuration, passed real notification scale-out; none is a maximum or a consumer-capacity ceiling], [On AWS; V3 r4 has no analyser verdict; V4 passed with an experimental expiry configuration (§4.1)],
    [Q5], [Technical and non-technical benefits, demonstrated], [§6.2, §4.1–§4.4], [Technical: each service scales on its own signal (V3 r4, V4 R5, §4.1); a new consumer needs no producer change (audit queue, §4.4); payment initiation fails closed (R11, §4.2). Non-technical: refunds for unfilled teams need no manual work (UC5, UC6), and the disposable runtime limits idle charges (overruns and allocation limits in §6.3)], [On AWS (scaling, R11, disposable runtime); counted in the repository (seams); designed and tested locally (refunds)],
    [Q6], [Value of cloud-native design], [§6.3], [Disposable runtime rebuilt from zero, pay per session, scale-out, managed services (@tbl-cloud-value)], [On AWS; cost calculated (one term estimated)],
    [Q7], [Lifecycle parts automated and effort saved], [§5.3], [Build, scan, create, smoke, release and destroy are automated (@tbl-delivery-timings); no manual baseline was timed, so effort saved is not measured], [On AWS (promotion runs); saving not measured],
    [Q8], [Effort within about ten person-days each], [§2.3], [Estimated effort is 77.5 person-days, above the 50-day guideline; additional validation and report work account for the overrun (@tbl-effort)], [Estimated],
  ),
  kind: table,
  caption: [Briefing questions Q1–Q8 and where they are answered. Source: briefing V2.6.],
) <tbl-briefing-questions>
