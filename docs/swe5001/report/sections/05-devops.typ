#import "../lib.typ": *

= DevOps and Development Lifecycle
#owner([Panpakorn Siripanich (Pan)], closes: "F2, briefing Q7", diagrams: "D10")

== Source Control Strategy
#prompt[Describe the strategy that you use to manage your source code and other artefacts in your configuration management. Include your project structures, repository strategy, branching strategy, authentication of developers, etc.]
#todo([Monorepo layout; `dev` and `prod` branches; rulesets; how developers authenticate (\#621).])

== Continuous Integration
#prompt[Describe the pipeline on how you perform continuous integration in your project including the trigger for each integration jobs, the various jobs, tests, etc.]
#todo(
  [Trigger, jobs, tests and gates per job.],
  [Image build → Trivy scan → export as one chain; fixable HIGH and CRITICAL fail the job. CodeQL is off; static analysis is SonarCloud.],
)
#diagram-slot("D10", [CI/CD pipeline (UML activity). The image scan sits after the build and before export.])

== Continuous Delivery
#prompt[Describe the pipeline on how you automate deployment to different environment including how many environments are setup, who is responsible for approving promotion between environments and how deployment get verified.]
#todo(
  [Environments; promotion PR into `prod`; approvals.],
  [Create → deploy → smoke → release → destroy, with timings from the 17 October run.],
)
