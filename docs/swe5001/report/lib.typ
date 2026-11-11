// Shared styles and scaffold helpers for the SWE5001 final report.
// Remove `prompt`, `todo`, `owner`, `diagram-slot` and `pending` calls once a section is written and reviewed.

#let accent = rgb("#0a6fa8")
#let muted = rgb("#5f6b78")
#let hdr = rgb("#dde8f2")
#let todo-fill = rgb("#fff4e0")

#let style(body) = {
  set document(title: "Project Report for BlocBuy", author: "SWE5001 Team 8")
  set page(
    paper: "a4",
    margin: (x: 2.2cm, top: 2.3cm, bottom: 2.3cm),
    footer: context {
      if counter(page).get().first() > 1 {
        set text(8pt, fill: muted)
        let hs = query(heading.where(level: 1).before(here()))
        let ch = if hs.len() == 0 [] else {
          let hd = hs.last()
          if hd.numbering == none [#hd.body] else [#numbering(hd.numbering, ..counter(heading).at(hd.location())) | #hd.body]
        }
        [#ch #h(1fr) Page #counter(page).display() of #counter(page).final().first()]
      }
    },
  )
  set text(font: ("Arial", "Helvetica"), size: 10.5pt, lang: "en")
  set par(justify: true, leading: 0.62em, spacing: 0.95em)
  set heading(numbering: "1.1")
  // Figures and tables are numbered per chapter ("Table 3.2"); table captions sit above the table.
  set figure(numbering: (..n) => numbering("1.1", counter(heading).get().first(), ..n))
  show figure.where(kind: table): set figure.caption(position: top)
  show heading.where(level: 1): it => {
    counter(figure.where(kind: image)).update(0)
    counter(figure.where(kind: table)).update(0)
    pagebreak(weak: true)
    set text(16pt, fill: accent)
    it
    v(0.3em)
  }
  show heading.where(level: 2): it => {
    v(0.4em)
    set text(12.5pt, fill: accent)
    it
  }
  show heading.where(level: 3): it => {
    v(0.25em)
    set text(11pt)
    it
  }
  show raw: set text(font: ("Menlo", "Courier New"), size: 8.5pt)
  set table(
    stroke: 0.5pt + rgb("#a7b4c2"),
    inset: (x: 5pt, y: 4.5pt),
    fill: (x, y) => if y == 0 { hdr },
    align: left + horizon,
  )
  show table.cell.where(y: 0): set text(weight: "bold")
  show table: set text(8.8pt)
  show table: set par(justify: false, leading: 0.5em)
  // A long code name in a table cell (`OAuthUserTxService.registerNewUser`,
  // `internal_controllers_are_hidden_and_distributed_only`) may break after a dot, underscore,
  // slash, opening parenthesis or comma, and between the words of a camelCase name, so it wraps
  // inside its column instead of running into the next one or past the page edge. As in the
  // SWE5006 template's `report-table` and the appendix's `lib-a.typ`.
  show table: it => {
    show raw.where(block: false): r => {
      show regex("[_./(,]"): m => m.text + sym.zws
      show regex("[a-z][A-Z]"): m => m.text.at(0) + sym.zws + m.text.at(1)
      r
    }
    it
  }
  show figure: set block(breakable: true)
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
