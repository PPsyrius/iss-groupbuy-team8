# SWE5006 final report

Team 8's BlocBuy final report for SWE5006 (umbrella #463, schedule and submission #695). It carries 80% of the project mark. All committed text is English ([`CONTRIBUTING.md`](../../../CONTRIBUTING.md)).

## The rubric governs the content

[`RUBRIC.md`](RUBRIC.md) is the specification for this report: what each template section must contain, the team rulings, the claims that are easy to get wrong, the figure rules and the open decisions. The report is graded against it by separate agents. Read it before drafting, editing or reviewing anything here, and follow the [`swe5006-report-rubric`](../../../.claude/skills/swe5006-report-rubric/SKILL.md) skill for the procedure.

The per-bullet content and its sources are in the report plan: the index comment on #463 and the chapter plans #675 (front matter, 1), #676 (2), #677 (3.1–3.3), #678 (3.4), #679 (3.5, 3.6), #680 (4) and #681 (5, appendices). The rubric says what a section must achieve; the plan says what to put in it and where each fact comes from. When the rubric and anything else in this folder disagree, the rubric wins; fix the other file in the same change.

## Facts

| | |
|---|---|
| Due | 31 Oct 2026, 23:59 (#695). Draft 27 Oct EOD, vetting 28–29 Oct, freeze 30 Oct EOD |
| Editor | Lim Jiechao (integration, chapters 1, 2, 3.3, 5) |
| Owners | Eric 3.2 (half), 3.4.1, 3.5.1 · Yorik 3.2 (half), 3.4.2, 3.5.2 · Pan 3.4.3, 3.5.3, chapter 4, Appendix A · Lim 3.4.4, 3.5.4, chapters 1, 2, 3.3, 5 · Liu Jin 3.1, 3.4.5, 3.5.5, 3.6 |
| Inputs | Report drafts in [`../report-drafts/`](../report-drafts/README.md); deck figures in [`../presentation/src/diagrams/`](../presentation/src/diagrams/); deck text in `../presentation/src/content.js`; evidence registers listed in the drafts README |
| Course documents | The template, briefing deck and proposal are kept out of the repository; ask the editor for them. The template's text is summarised in the rubric's Source column |
| Output | `dist/swe5006-report.docx` (gitignored), built from `sections/` into the template; the submitted PDF is exported from it in Word |

## Where things go

```
docs/swe5006/report/
  RUBRIC.md                 specification
  AGENTS.md                 this file
  sections/NN-*.md          one file per template section or member subsection, in report order
  figures/                  figures made for the report: editable source next to its export
  scripts/check.mjs         structure, marker, figure and language checks
  scripts/build.mjs         assembles sections/ into the .docx template with pandoc
  scripts/strip-numbers.lua pandoc filter: the template numbers headings itself
```

Each member's 3.4.N and 3.5.N is its own file, so five people can work in parallel without editing the same file. File names sort into report order; the build concatenates them in that order.

## Section file shape

Every file opens with a header comment that the check script reads:

```markdown
<!-- report
rubric: 3.4.1, 3.4-A..E, G4
owner: Wang Ziang (Eric)
plan: #678
sources: ../report-drafts/eric-wang-contribution.md
status: missing | draft | ready
-->
```

- Headings carry the template number and title verbatim (`### 3.4.1 UC3 and UC4 by Wang Ziang (Eric)`); the build strips the numbers because the template's heading styles number themselves. Never renumber a template section.
- Below a template section, add `####` subheadings only. The 3.4 and 3.5 files keep the fixed part headings from the skeleton (`#### A Analysis class diagram`, `#### 1 Problem`, …) because the check script looks for them.
- Figures: `![Figure 3.4: Analysis class diagram of UC4. Boundaries face the buyer; the control owns the join rule.](../figures/uc4-analysis-class.svg)`. Reuse a deck figure by its path (`../../presentation/src/diagrams/...`) rather than copying it; copy it into `figures/` only to change it for the report.
- Mark anything unknown, unverified or awaiting a decision with `{{FILL: …}}`, `{{VERIFY: …}}` or `{{CONFIRM: D# …}}`. All must be gone at the freeze; the strict check fails while any remain.
- Keep the template's guidance out of the text; the rubric already holds it.

## Writing rules

1. **Answer first.** Each section opens with one or two sentences that answer the template's question; detail follows.
2. **Write for the marker.** They read the PDF and do not open GitHub (rubric G8). Name work by what it does; define BlocBuy terms at first use.
3. **State the evidence kind** of every claim (rubric Evidence kinds) and never upgrade a result. Check each claim against the rubric's "easy to get wrong" table.
4. **Real names.** Every class, method, table and event in text or a design diagram exists at the freeze SHA (`rg -n "class GroupDealExpiryService" backend`).
5. **One fact, one place.** Other sections cite it by section number.
6. **Plain language.** Short sentences, active voice, the team's ubiquitous language.

## Workflow

1. Pick the section file, read its rubric rows and its plan issue.
2. Hydrate from the plan bullets and their sources; carry over ready drafts from `../report-drafts/` rather than rewriting them.
3. `npm --prefix docs/swe5006/report run check` and fix every error it prints.
4. Run the skill's review on your section and fix every Missing and Conflict row.
5. Open a pull request into `dev` titled `docs(report): …` referencing #463; another owner reviews it against the rubric. Add the day's entry under `docs/agile/sprint-04/log/` (see `.claude/skills/agile-evidence-capture/SKILL.md`).

## Commands

From the repository root (Node 20+; the build also needs `pandoc`, and `rsvg-convert` from librsvg for SVG figures):

```sh
npm --prefix docs/swe5006/report run check            # structure and markers, warnings only for markers
npm --prefix docs/swe5006/report run check:strict     # freeze gate: markers are errors
REPORT_TEMPLATE="/path/to/SWE5006 - Project Report Template for Practice Module.docx" \
  npm --prefix docs/swe5006/report run build          # dist/swe5006-report.docx
```

After building, open the .docx in Word, update the contents table and fields, remove the automatic chapter number from the appendix headings (the template's Heading 1 numbers itself), check page breaks and figure sizes, then export the PDF for submission. The front matter is pandoc metadata (title, subtitle, author lines, date line) so it lands before the contents table, as in the template.
