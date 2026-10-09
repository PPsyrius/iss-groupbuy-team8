# Sprint progress report rules

Rules for writing the SWE5006 sprint progress report (one per sprint). The report is graded by the lecturers, who read the PDF only and do not open GitHub. Use `report-mapping.md` to find the evidence; these rules decide how the report presents it. Agreed with the team on 2026-10-08 for the Sprint 3 report.

## Structure: follow the official template exactly

The template is `SWE5006 - Project Progress Report Template for Practice Module.docx`. Keep its sections, order and wording:

1. Title page: "Project Progress Report for BlocBuy", the Practice Module name, "Team 8", "Members:" with each member's name and email.
2. Contents.
3. **1 Introduction**: 1.1 Project Name & Description; 1.2 Project methodology; 1.3 Project Summary.
4. **2 Project Progress Report**: 2.1 Reporting period (from – to Date); 2.2 Sprint #; 2.3 Sprint objectives; 2.4 Sprint accomplishment (Summary of work plan vs actual); 2.5 Sprint Burn down chart; then problems encountered, action plan and status; then the sprint retrospective with "What went well?", "What could have done better?" and "What will try next?". The last two may be numbered 2.6 and 2.7.
5. **3 Project Team Members Contribution Summary**: one Contribution Summary Table (see below) and nothing else.
6. **4 \<other things to be highlighted\>**: short, optional extras such as the report's measurement scope, unplanned work and carry-over.

Do not add sections the template does not have (for example a separate design chapter, a Definition of Done chapter or a day-by-day plan table). Put design content in the Contribution Summary Table.

## Contribution Summary Table

- Use the template's four columns: Project Member Name, Activity Description, Achievement Status (Completed / In-progress), Remarks.
- Every member has at least one selected use case. The row names the member and the use case clearly.
- For each use case, the Activity Description gives the **Analysis**, the **Design** and the **Design Pattern**, labelled as such.
- Do not list pull-request counts, commit counts or lines changed per member.
- Achievement Status uses only "Completed" or "In-progress".

## Write for a reader who does not open GitHub

- No tracker identifiers: no story IDs (`US23`), issue or PR numbers, ADR or NFR register numbers, workflow run IDs, error codes, environment variable names, script names, branch names or file paths.
- Name work by what it does for a user or operator, for example "admin sets group terms", "buyer pays the quoted price", "unpaid orders expire automatically", "HTTPS between the CDN and the load balancer".
- Keep class names and code identifiers out of the prose. A pattern name ("Optimistic Offline Lock") is fine; the class that implements it is not needed.
- Group the plan-versus-actual table by theme (for example admin and catalog, buying and payment, refunds, performance, quality and security, release and deployment), one row per theme, rather than one row per story.
- Lead 1.3 Project Summary with the goal, a one-line result and a few key numbers (planned versus completed hours, performance results, quality gate status).
- Explain any term a lecturer may not know (for example "bloc", "Sandbox") at first use.
- English only. Aim for seven to eight pages.

## Measurement and honesty

- State the measurement scope in Section 4. The team may measure development progress only (work merged into `dev` counts as complete) and report deployed-environment acceptance runs separately; say so explicitly and list what is pending.
- Mark projections. Work that owners commit to finish after the report date is labelled as projected, and the burndown shows it as a dashed segment.
- When the board estimate field is incomplete, reconstruct the burndown from completion dates and say how in one or two sentences.
- When a story is re-scoped (for example delivered through a different route, or a drill moved to the next sprint), mark it Completed only if the delivered part meets the agreed scope, and state the moved part in the same row.
- Every factual claim (numbers, dates, test results, what runs where) is checked against the repository, the issue tracker, the CI runs or the day logs before it is written. Remove a claim that cannot be checked.
- The retrospective draws on recorded facts; until the Retrospective meeting has happened, treat it as a draft for the team to confirm.

## Build and delivery

- Write the report in Typst, reusing the styling of the previous sprint's report.
- Output: `SWE5006-Team8-Project-Progress-Report-SprintN.pdf` in the team's shared report folder, with the `.typ` source and the NUS-ISS logo in a `sprintN-report/` folder beside it.
- Compile with `typst compile` and review the rendered pages before sharing.
