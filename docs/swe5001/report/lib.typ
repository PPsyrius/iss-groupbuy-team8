// Shared styles and scaffold helpers for the SWE5001 final report.
// Remove `prompt`, `todo` and `owner` calls from a section once it is written.

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
        [BlocBuy — SWE5001 Project Report #h(1fr) Page #counter(page).display()]
      }
    },
  )
  set text(font: ("Arial", "Helvetica"), size: 10.5pt, lang: "en")
  set par(justify: true, leading: 0.62em, spacing: 0.95em)
  set heading(numbering: "1.1")
  show heading.where(level: 1): it => {
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
  show raw: set text(font: ("Menlo", "Courier New"), size: 0.88em)
  set table(
    stroke: 0.5pt + rgb("#a7b4c2"),
    inset: (x: 5pt, y: 4.5pt),
    fill: (x, y) => if y == 0 { hdr },
    align: left + horizon,
  )
  show table.cell.where(y: 0): set text(weight: "bold")
  show table: set text(8.8pt)
  show table: set par(justify: false, leading: 0.5em)
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
