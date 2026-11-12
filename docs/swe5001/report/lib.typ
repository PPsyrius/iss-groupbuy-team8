// Shared styles and scaffold helpers for the SWE5001 final report.
// Remove `prompt`, `todo`, `owner`, `diagram-slot` and `pending` calls once a section is written and reviewed.
//
// The report looks like the SWE5006 report: page, fonts, headings, lists, links, code, header
// and footer come from `setup` in the SWE5006 template, imported as a module (`tpl`) so none of
// its names (or the UML kit's, which it re-exports) clash with the names below or in the
// sections. Never edit the template from here: SWE5006 renders unchanged by construction.
// Figures, tables and their captions are restyled below by show rules after the template's
// `report-figure`, `report-table` and `table-caption`, so the section files keep plain
// `figure(...)` calls and their per-chapter numbers.
#import "/docs/swe5006/report/typst/template.typ" as tpl

// Accent of the report: the SWE5006 heading ink (template.typ `heading-ink`, #1F3864).
#let accent = tpl.heading-ink
#let muted = rgb("#5f6b78")
// Table header fill (template.typ `report-table`: fill #ECE6DC on row 0).
#let hdr = rgb("#ECE6DC")
#let todo-fill = rgb("#fff4e0")

// The NUS-ISS logo in the page header: docs/nus-iss-logo.png trimmed to the bounding box of its
// artwork (crest, wordmark, divider and ISS mark, 1 px margin), drawn by the template's
// `logo-mark` at the artwork's height (0.73 cm), like SWE5006's REPORT_LOGO.
#let report-logo = (path: "/docs/swe5001/report/nus-iss-logo-cropped.png", cropped: true)

// Figure frame and caption gap, as template.typ `report-figure` (inset 6 pt, stroke 0.4 pt
// #D8D2C8, gap 0.3 em above the caption).
#let figure-inset = 6pt
#let figure-stroke = 0.4pt + rgb("#D8D2C8")
#let figure-gap = 0.3em

#let style(body) = {
  set document(title: "Project Report for BlocBuy", author: "SWE5001 Team 8")
  // Page (A4, 2.54 cm), header logo, footer "running head | Page X of Y", Arial 12 pt,
  // left-aligned paragraphs, lists, heading numbering and faces, raw and links: the SWE5006
  // template's `setup`, unchanged.
  show: tpl.setup.with(logo: report-logo)
  // Figures and tables are numbered per chapter ("Table 3.2"), as in the SWE5006 report.
  set figure(numbering: (..n) => numbering("1.1", counter(heading).get().first(), ..n))
  // Runs before the template's level-1 rule (inner show rules apply first): the counters reset
  // ahead of its weak page break.
  show heading.where(level: 1): it => {
    counter(figure.where(kind: image)).update(0)
    counter(figure.where(kind: table)).update(0)
    it
  }
  // Captions as SWE5006: 9 pt, left-aligned, not justified. A figure caption starts with a bold
  // "Figure n:" (report-figure); a table caption is plain "Table n: ..." (table-caption).
  show figure.caption: it => context {
    set text(size: 9pt)
    set par(justify: false)
    set align(left)
    if it.numbering == none { it.body } else {
      let prefix = [#it.supplement #it.counter.display(it.numbering):]
      if it.kind == image [#strong(prefix) #it.body] else [#prefix #it.body]
    }
  }
  // A figure's own show-set makes every block inside it unbreakable, the block Typst wraps a
  // table in included; this lets a long table break between rows. The rules below set
  // `breakable` explicitly where a figure must stay whole.
  show figure: set block(breakable: true)
  // A drawing in a light frame with its caption below (report-figure: block width 100 %, inset
  // 6 pt, stroke 0.4 pt #D8D2C8, then v(0.3 em) and the caption; in the flow 1 em above and
  // 1.2 em below; a float keeps 1.4 em clearance, as page-float). The figure never splits.
  show figure.where(kind: image): it => {
    let fig = block(width: 100%, breakable: false, {
      block(width: 100%, stroke: figure-stroke, inset: figure-inset, align(center, it.body))
      if it.caption != none {
        v(figure-gap)
        it.caption
      }
    })
    if it.placement != none { place(it.placement, float: true, clearance: 1.4em, fig) } else { block(above: 1em, below: 1.2em, fig) }
  }
  // A table under its caption (table-caption: 14 pt above, 0.4 em below at 9 pt, sticky to the
  // table; report-table: 0.6 em above and 1 em below at 9 pt). As report-table, a table up to
  // half the text area tall stays on one page; a longer one breaks between rows, and its header
  // row repeats.
  show figure.where(kind: table): it => context {
    if it.caption != none { block(width: 100%, above: 14pt, below: 5.4pt, sticky: true, it.caption) }
    let h = measure(it.body, width: tpl.text-area.width).height
    block(width: 100%, above: 5.4pt, below: 9pt, breakable: h > tpl.text-area.height / 2, it.body)
  }
  // Tables as report-table: 9 pt, hyphenated, not justified, leading 0.5 em; inset 4 / 3.5 pt,
  // stroke 0.4 pt #8A8378, header row filled #ECE6DC and bold; rows never split across pages.
  set table(
    stroke: 0.4pt + rgb("#8A8378"),
    inset: (x: 4pt, y: 3.5pt),
    fill: (x, y) => if y == 0 { hdr },
    align: left + top,
  )
  set table.cell(breakable: false)
  show table.cell.where(y: 0): set text(weight: "bold")
  show table: set text(size: 9pt, hyphenate: true)
  show table: set par(justify: false, leading: 0.5em)
  // A long code name in a table cell (`OAuthUserTxService.registerNewUser`,
  // `internal_controllers_are_hidden_and_distributed_only`) may break after a dot, underscore,
  // slash, opening parenthesis or comma, and between the words of a camelCase name, so it wraps
  // inside its column instead of running into the next one or past the page edge. As in the
  // SWE5006 template's `report-table` and `figures.typ`.
  // An AWS region or zone name (`ap-southeast-1`, `ap-southeast-1a`) never breaks at its hyphens.
  show raw.where(block: false): it => if it.text.starts-with("ap-southeast-1") { box(it) } else { it }
  show table: it => {
    show raw.where(block: false): r => {
      show regex("[_./(,]"): m => m.text + sym.zws
      show regex("[a-z][A-Z]"): m => m.text.at(0) + sym.zws + m.text.at(1)
      r
    }
    it
  }
  body
}


// The template's own guidance text (blue italic in the .docx).
#let prompt(body) = block(
  inset: (left: 8pt, y: 3pt),
  stroke: (left: 2pt + accent),
  text(fill: accent, style: "italic", size: 9.5pt, body),
)

// Section owner, feedback items closed and diagrams due.
#let owner(name, closes: none, diagrams: none) = block(
  text(size: 9pt, fill: muted)[
    *Owner:* #name
    #if closes != none [ · *Closes:* #closes]
    #if diagrams != none [ · *Diagrams:* #diagrams]
  ],
)

// What the section must contain before it is done (README "Must contain").
#let todo(..items) = block(
  width: 100%,
  fill: todo-fill,
  inset: 8pt,
  radius: 2pt,
  text(size: 9.5pt)[
    *To write* \
    #list(..items.pos())
  ],
)

// Text pending team review or a cross-part check at the reconcile PR. Red on a light-red
// highlight, so it is not confused with red diagram labels. None may remain at submission.
#let pending-red = rgb("#C62828")
#let pending(body) = highlight(fill: rgb("#FDECEA"), text(fill: pending-red, body))

// Placeholder for a diagram that is not drawn yet.
#let diagram-slot(id, caption) = figure(
  rect(
    width: 100%,
    height: 4cm,
    stroke: (dash: "dashed", paint: muted),
    align(center + horizon, text(fill: muted)[#id — to be drawn; source in `diagrams/`]),
  ),
  kind: image,
  caption: caption,
)

// One set of repository links for the whole report (style.md S-13). Every link is pinned to one
// commit, `freeze`, so a reader sees the files as they were at submission. Until the freeze
// commit exists on dev, `freeze` is dev; set it to the freeze sha before submitting.
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let freeze = "dev"
#let src(path, body) = link(repo + "/blob/" + freeze + "/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + str(id), body)
