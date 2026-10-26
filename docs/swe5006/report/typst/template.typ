// Page setup after the course template: A4, 1-inch margins, Arial, numbered headings,
// "Page X of Y" footer, title page, then the contents.
#import "uml.typ": *

#let marker(kind, note) = box(
  fill: rgb("#FDECEA"), stroke: 0.5pt + rgb("#E8402A"), inset: (x: 2pt, y: 1pt), radius: 1.5pt,
  text(size: 0.85em, fill: rgb("#B02E1B"), weight: "bold", kind + if note != "" { ": " + note } else { "" }),
)

#let report-figure(num, body, caption) = block(width: 100%, breakable: false, above: 1em, below: 1.2em, {
  align(center, block(width: 100%, stroke: 0.4pt + rgb("#D8D2C8"), inset: 6pt, body))
  v(0.3em)
  set text(size: 9pt)
  set par(justify: false)
  [*Figure #num:* #caption]
})

#let figure-frame(body) = block(width: 100%, breakable: false, inset: 6pt, stroke: 0.4pt + rgb("#D8D2C8"), body)

#let table-caption(body) = block(below: 0.4em, text(size: 9pt, body))

#let report-table(n, ..cells) = {
  set text(size: 9pt)
  set par(justify: false, leading: 0.5em)
  block(width: 100%, above: 0.6em, below: 1em, table(
    columns: n,
    inset: (x: 4pt, y: 3.5pt),
    stroke: 0.4pt + rgb("#8A8378"),
    fill: (_, y) => if y == 0 { rgb("#ECE6DC") },
    ..cells.pos(),
  ))
}

#let setup(logo: none, body) = {
  set page(
    paper: "a4",
    margin: 2.54cm,
    header: context if counter(page).get().first() > 1 and logo != none {
      align(right, image(logo, height: 0.9cm))
    },
    footer: context if counter(page).get().first() > 1 {
      set text(size: 9pt)
      align(center)[Page #counter(page).display() of #counter(page).final().first()]
    },
  )
  set text(font: ("Arial", "Helvetica"), size: 11pt, lang: "en", region: "gb")
  set par(justify: true, leading: 0.6em, spacing: 0.9em)
  set list(indent: 0.6em, spacing: 0.5em)
  set enum(indent: 0.6em, spacing: 0.5em)
  set heading(numbering: (..n) => {
    let v = n.pos()
    if v.len() == 1 { str(v.first()) + "." } else { v.map(str).join(".") }
  })
  show heading: set block(above: 1.4em, below: 0.8em)
  show heading.where(level: 1): it => {
    pagebreak(weak: true)
    set text(size: 16pt, weight: "bold")
    block(above: 0pt, below: 1em, it)
  }
  show heading.where(level: 2): set text(size: 13pt, weight: "bold")
  show heading.where(level: 3): set text(size: 11.5pt, weight: "bold")
  show heading.where(level: 4): set text(size: 11pt, weight: "bold", style: "italic")
  show raw.where(block: false): set text(size: 0.92em)
  show raw.where(block: true): it => block(width: 100%, fill: rgb("#F4F2EE"), inset: 6pt, text(size: 8pt, it))
  show link: set text(fill: rgb("#3E5CD9"))
  body
}

// One section alone, for scripts/figure.mjs previews.
#let preview(body) = setup(body)

#let report(meta: (:), logo: none, body) = setup(logo: logo, {
  set document(title: meta.at("title", default: "Project Report"))
  // Title page
  {
    if logo != none { align(right, image(logo, height: 1.4cm)) }
    v(5cm)
    align(center, {
      text(size: 24pt, weight: "bold", meta.at("title", default: ""))
      v(1.2em)
      text(size: 14pt, meta.at("subtitle", default: ""))
      v(2.5em)
      set text(size: 13pt)
      for a in meta.at("author", default: ()) { a; linebreak() }
      v(2em)
      set text(size: 10pt)
      meta.at("date", default: "")
    })
  }
  pagebreak()
  outline(title: [CONTENTS], depth: 3, indent: auto)
  pagebreak()
  body
})
