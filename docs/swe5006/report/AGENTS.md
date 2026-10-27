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
| Output | `dist/swe5006-report.pdf` (gitignored), compiled with Typst from `sections/` in the template's layout (A4, 2.54 cm margins, Arial, numbered headings, "Page X of Y"). Typst was chosen on 26 Oct for the first draft (D14; the editor confirms) |

## Where things go

```
docs/swe5006/report/
  RUBRIC.md                 specification
  AGENTS.md                 this file
  sections/NN-*.md          one file per template section or member subsection, in report order
  figures/                  image figures made for the report (SVG or PNG), editable source beside each
  typst/template.typ        page layout, title page, contents, tables, figure frames
  typst/uml.typ             UML drawing kit: class, state and sequence diagrams
  scripts/typst.mjs         Markdown and Mermaid to Typst translator (no dependencies)
  scripts/check.mjs         structure, marker, figure, Mermaid and language checks
  scripts/build.mjs         compiles all sections to dist/swe5006-report.pdf
  scripts/figure.mjs        renders one section to PNG pages to check figures by eye
  scripts/renumber.mjs      renumbers figures and tables per chapter and rewrites their references
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

- Headings carry the template number and title verbatim (`### 3.4.1 UC3 and UC4 by Wang Ziang (Eric)`); Typst renumbers them itself, and the check script uses the written numbers. Never renumber a template section.
- Below a template section, add `####` subheadings only. The 3.4 and 3.5 files keep the fixed part headings from the skeleton, with these exact titles, because the check script looks for them: 3.4.N `#### A Analysis class diagram`, `#### B Analysis sequence diagrams`, `#### C Design class diagram`, `#### D Design sequence diagrams`, `#### E Compliance with the transition strategies`; 3.5.N `#### 1 Design problem`, `#### 2 Candidate design patterns`, `#### 3 Motivation`, `#### 4 Solution class diagram`, `#### 5 Solution sequence diagrams`, `#### 6 Implementation decisions`.
- UML figures are Mermaid fences in the section itself, so GitHub previews them and the build draws them with `typst/uml.typ`. Supported: `classDiagram` (classes with `<<stereotype>>`, members, all relation arrows, multiplicities, labels), `sequenceDiagram` (`actor`/`participant X as Name (boundary)`, `->>` sync, `-)` async, `-->>` return, `alt`/`else`/`opt`/`loop`/`par`/`critical`/`break`/`rect`/`end`, `Note over`), `stateDiagram-v2` (`[*]`, `state "Name" as X`, `X : entry / …`, `<<choice>>`; a self-transition is drawn as an internal transition). Class and state layouts are automatic; pin a node with a Mermaid comment `%% at ClassName col,row` when the automatic layout crosses lines. Keep message and transition labels short.
- Every figure has a caption paragraph: right after a Mermaid fence, or as the image's alt text: `Figure 3.4: Analysis class diagram of UC4. The control owns the join rule.` Number figures per chapter (`Figure 3.N`, `Figure A.N` in appendices), unique across the report. When several people draft at once, each may number by their own section (`Figure 3.4.3.2`, `Table 3.4.3.1`); the editor then runs `node docs/swe5006/report/scripts/renumber.mjs` once to number everything per chapter and rewrite the references.
- A wide image whose labels would print below 8 pt gets `{.landscape}` after the link: it is drawn rotated on its own page, about 1.6 times larger. Figures float to the top or bottom of a page; refer to them by number, never as "below".
- Image figures: `![Figure 3.1: Logical view. …](../../presentation/src/diagrams/logical-layers.svg)`. Reuse a deck figure by its path rather than copying it; copy it into `figures/` only to change it for the report.
- A brand mark that is not a figure gets `{.logo}` after the link: `![BlocBuy logo](../../../../frontend/src/assets/brand/blocbuy-logo.svg){.logo}`. It is drawn 7 cm wide, centred, with no frame, caption or number; the alt text is required.
- Tables are GitHub pipe tables; a paragraph `Table 2.1: …` just before a table is styled as its caption. Lists nest by two spaces.
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

From the repository root (Node 20+, Typst 0.15 with the `fletcher` 0.5.8 package; nothing else):

```sh
npm --prefix docs/swe5006/report run check                         # structure, figures, Mermaid; lists open markers
npm --prefix docs/swe5006/report run check:strict                  # freeze gate: markers are errors
npm --prefix docs/swe5006/report run figure -- sections/03-4-1-eric.md   # PNG pages in dist/preview/
REPORT_TEMPLATE="/path/to/SWE5006 - Project Report Template for Practice Module.docx" \
  npm --prefix docs/swe5006/report run build -- --out "/path/to/shared/folder"
```

`REPORT_TEMPLATE` is optional: it supplies the NUS-ISS logo for the header, which stays out of the repository with the template; the page setup clips the blank margins of the template's logo image. `REPORT_LOGO=<png>` (optional) replaces it with a logo already cropped to its artwork. The footer carries a running head at left, the current chapter or appendix as "2 | Project Conduct", beside the page number; both are 9 pt. Open markers render as red boxes in the PDF so reviewers see every gap. The front matter in `00-front-matter.md` is a YAML block (`title`, `kind`, `subtitle`, `team`, a `members` list, `repository`, `environment`) that becomes the title page; every key reaches the template as `meta`.
