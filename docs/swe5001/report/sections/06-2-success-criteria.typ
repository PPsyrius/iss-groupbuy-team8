// How the Briefing's Success Criteria Are Met: part of 06-highlights.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== How the Briefing's Success Criteria Are Met

BlocBuy meets four of the briefing's five success criteria with built work; the fifth, analytics that improve the platform, is met for operations and designed for the business. @tbl-success shows how BlocBuy meets each criterion and what kind of evidence backs it; together with §4.1–§4.4 it answers briefing Q5, which asks for the technical and non-technical benefits to be demonstrated. The paid buyer journey (pay, admit, refund) has not been run on the three-service runtime (§2.2).

#figure(
  table(
    columns: (1.6fr, 2.6fr, 1fr),
    table.header([Success criterion (briefing)], [How BlocBuy meets it], [Evidence]),
    [Identify the seed, producers and consumers of the platform], [Seed: curated team-priced offers; producers: the platform administrator, who publishes listings on sellers' behalf, team leaders and sharers (sellers do not use BlocBuy directly; seller self-service is out of scope, §1.4.2); consumers: members (§1.3, @tbl-platform-roles, @fig-d1-context)], [Designed and built],
    [Identify the use cases that can expand the ecosystem], [Starting a team (UC3) and sharing an invite link (UC7) that credits the sharer for each referred join (UC12) turn a consumer into a producer who brings in the next buyers], [Built in SWE5006; unchanged in the services],
    [Show how the platform scales the number of customers and producers], [Browsing and paying scale out on `commerce-svc` without touching `identity-svc` or `notification-svc`. On AWS, commerce scaled from one to three tasks on 19 Oct, to four on 10 Nov and to six on 11 Nov (V3 r1: one, two, four, six) (§4.1); every run failed acceptance, because of dropped iterations or the error gate (AISS-12)], [Scale-out verified on AWS; acceptance failed],
    [Support more seeds in future], [A new seed is four steps: a thin assembly module registered in `backend/pom.xml` with its database grants; one entry in the services map with its ECR repository and image-workflow entry; a listener rule for its API paths, which the golden-path module creates; and reuse of sign-in, notification and settlement through the shared modules and events (§4.4). The catalogue and team-price rules are specific to curated team offers and are not reused], [Built; not yet exercised with a second seed],
    [Identify and perform analytics that improve the platform], [Operational analytics are in use and set the scaling target; business analytics are designed on data already stored (§6.5)], [Operational: dashboard verified on AWS (16 Oct); alarms configured, never exercised. Business: designed],
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
    [Q3], [Benefit of building on the platform], [§4.4], [A new consumer is one element in the `consumers` set and one filter line in the messaging module; a new service is one services-map entry and a thin assembly (@tbl-seams)], [Counted in the repository; time not measured],
    [Q4], [Scale supported, and the proof], [§4.1], [One 0.5 vCPU commerce task saturated at about 280 req/s and three tasks served about 455 req/s (19 Oct); scale-out to six tasks on 11 Nov; every V3 run failed acceptance, so capacity and linear scaling are unproven; next limits: the database connection budget and the load-generator path], [On AWS; acceptance failed],
    [Q5], [Technical and non-technical benefits, demonstrated], [§6.2, §4.1–§4.4], [@tbl-success; the paid buyer journey was not run on the three-service runtime], [Mixed, per row of @tbl-success],
    [Q6], [Value of cloud-native design], [§6.3], [Disposable runtime rebuilt from zero, pay per session, scale-out, managed services (@tbl-cloud-value)], [On AWS; cost calculated],
    [Q7], [Lifecycle parts automated and effort saved], [§5.3], [Build, scan, create, smoke, release and destroy are automated (@tbl-delivery-timings); no manual baseline was timed, so effort saved is not measured], [On AWS (promotion runs); saving not measured],
    [Q8], [Effort within about ten person-days each], [§2.3], [About 50 person-days to 19 Oct; two members above ten; phase D not estimated (@tbl-effort)], [Agreed estimate],
  ),
  kind: table,
  caption: [Briefing questions Q1–Q8 and where they are answered. Source: briefing V2.6.],
) <tbl-briefing-questions>
