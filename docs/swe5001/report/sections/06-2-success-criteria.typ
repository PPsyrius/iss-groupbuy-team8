// How the Briefing's Success Criteria Are Met: part of 06-highlights.typ.
#import "../lib.typ": *

== How the Briefing's Success Criteria Are Met

Two of the briefing's five success criteria are met by built work: the seed, producers and consumers, and the use cases that expand the ecosystem. Scaling is shown on AWS for browse traffic and, in the 14 November experimental V4 R5 configuration, real expiry notifications (§4.1). Support for more seeds has a built path that no second seed has used yet. Analytics are operational, and business analytics are designed. @tbl-success shows how BlocBuy meets each criterion and what kind of evidence backs it; together with §4.1–§4.4 it answers briefing Q5, which asks for the technical and non-technical benefits to be demonstrated. The paid buyer journey (pay, admit, refund) has not been run on the three-service runtime (§2.2); individual drill passes do not establish whole-campaign acceptance.

#figure(
  table(
    columns: (1.6fr, 2.6fr, 1fr),
    table.header([Success criterion (briefing)], [How BlocBuy meets it], [Evidence]),
    [Identify the seed, producers and consumers of the platform], [Seed: curated team-priced offers; producers: the platform administrator, who publishes listings on sellers' behalf, team leaders and sharers (sellers do not use BlocBuy directly; seller self-service is out of scope, §1.4.2); consumers: buyers and downstream event subscribers (§1.3, @tbl-platform-roles, @fig-d1-context)], [Designed and built],
    [Identify the use cases that can expand the ecosystem], [Starting a team (UC3) and sharing an invite link (UC7) that credits the sharer for each referred join (UC12) turn a consumer into a producer who brings in the next buyers], [Built in SWE5006; unchanged in the services],
    [Show how the platform scales the number of customers and producers], [Commerce scales on its own, without touching `identity-svc` or `notification-svc`. On 11 Nov on AWS, V3 r4 met the browse k6 gates while commerce scaled out, without a passing canonical analyser verdict. On 14 Nov, experimental V4 R5 passed real expiry/event effects and automatic notification scale-out under the unchanged policy (§4.1). Neither establishes maximum capacity, a consumer ceiling or paid-journey scaling], [AWS browse result; qualified experimental notification-scaling pass],
    [Support more seeds in future], [A new seed is four steps: a thin assembly module registered in `backend/pom.xml` with its database grants; one entry in the services map with its ECR repository, image-workflow and start-up-script entries; a listener rule for its API paths, which the golden-path module creates; and reuse of sign-in, notification and settlement through the shared modules and events (§4.4). The catalogue and team-price rules are specific to curated team offers and are not reused], [Mechanisms built; path not yet used],
    [Identify and perform analytics that improve the platform], [Operational analytics are in use and set the scaling target; the real DLQ alarm and a matching human email receipt were verified in qualified V7 R4 on 14 Nov (§4.2, §6.5). Business analytics are designed on data already stored], [Operational: dashboard and qualified DLQ alarm/email verified on AWS. Business: designed],
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
    [Q4], [Scale supported, and the proof], [§4.1], [V3 r4 (11 Nov) served the tested 600 req/s with one to six commerce tasks; one monolith task served 200 req/s. Experimental V4 R5 (14 Nov) passed real notification scale-out; none is a maximum or a consumer-capacity ceiling], [On AWS; V3 canonical acceptance incomplete; V4 qualified pass (§4.1)],
    [Q5], [Technical and non-technical benefits, demonstrated], [§6.2, §4.1–§4.4], [@tbl-success; the paid buyer journey was not run on the three-service runtime], [Mixed, per row of @tbl-success],
    [Q6], [Value of cloud-native design], [§6.3], [Disposable runtime rebuilt from zero, pay per session, scale-out, managed services (@tbl-cloud-value)], [On AWS; cost calculated (one term estimated)],
    [Q7], [Lifecycle parts automated and effort saved], [§5.3], [Build, scan, create, smoke, release and destroy are automated (@tbl-delivery-timings); no manual baseline was timed, so effort saved is not measured], [On AWS (promotion runs); saving not measured],
    [Q8], [Effort within about ten person-days each], [§2.3], [About 50 person-days to 19 Oct and about 71.5 in total; phase D (the remarks and the report) took the total above the guideline (@tbl-effort)], [Agreed estimate],
  ),
  kind: table,
  caption: [Briefing questions Q1–Q8 and where they are answered. Source: briefing V2.6.],
) <tbl-briefing-questions>
