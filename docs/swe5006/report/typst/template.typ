// Page setup after the course template ("SWE5006 - Project Report Template for Practice
// Module.docx"): values from word/styles.xml, document.xml, header1.xml, footer1.xml and
// numbering.xml, cited inline. A4, 1-inch margins, Arial 12 pt, numbered headings, the logo
// header and "Page X of Y" footer on every page, title page, then the contents.
#import "uml.typ": *

#let marker(kind, note) = box(
  fill: rgb("#FDECEA"), stroke: 0.5pt + rgb("#E8402A"), inset: (x: 2pt, y: 1pt), radius: 1.5pt,
  text(size: 0.85em, fill: rgb("#B02E1B"), weight: "bold", kind + if note != "" { ": " + note } else { "" }),
)

// Figures float to the top or bottom of a page so they leave no half-empty pages behind,
// and a drawing taller than the page is scaled down to fit it.
#let report-figure(num, body, caption, float: true) = {
  let content = layout(size => {
    let framed = block(width: 100%, stroke: 0.4pt + rgb("#D8D2C8"), inset: 6pt, body)
    let h = measure(framed, width: size.width).height
    let max = 20cm
    let shown = if h > max { block(width: 100%, stroke: 0.4pt + rgb("#D8D2C8"), inset: 6pt, align(center, scale(max / h * 100%, reflow: true, body))) } else { framed }
    block(width: 100%, breakable: false, {
      align(center, shown)
      v(0.3em)
      set text(size: 9pt)
      set par(justify: false)
      [*Figure #num:* #caption]
    })
  })
  if float { place(auto, float: true, clearance: 1.4em, content) } else { block(above: 1em, below: 1.2em, content) }
}

// A figure on its own landscape page (images marked {.landscape} in the Markdown).
#let landscape-figure(num, body, caption) = place(auto, float: true, clearance: 1.4em,
  align(center, rotate(-90deg, reflow: true, block(width: 23cm, report-figure(num, body, caption, float: false)))))

#let figure-frame(body) = block(width: 100%, breakable: false, inset: 6pt, stroke: 0.4pt + rgb("#D8D2C8"), body)

#let table-caption(body) = block(below: 0.4em, sticky: true, text(size: 9pt, body))

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

// sectPr: pgSz 11906 x 16838 twips (A4), pgMar 1440 (2.54 cm) all round, header and footer 708
// twips (1.25 cm) from the edge, one section with no titlePg, so header and footer are on every
// page, the title page included.
#let header-dist = 1.25cm
// header1.xml: the logo, right-aligned, wp:extent 1479550 x 544959 EMU (4.11 x 1.51 cm). It is
// taller than the space above the margin, so Word pushes the text down below it; the top margin
// grows to match when a logo is supplied.
#let logo-height = 1.51cm

#let setup(logo: none, body) = {
  set page(
    paper: "a4",
    margin: (x: 2.54cm, bottom: 2.54cm, top: if logo != none { header-dist + logo-height + 0.3cm } else { 2.54cm }),
    header-ascent: 0.3cm,
    header: if logo != none { align(right + bottom, image(logo, height: logo-height)) },
    // footer1.xml: right-aligned "Page {PAGE} of {NUMPAGES}" in the Footer style (Normal, Arial 12 pt).
    footer: context align(right)[Page #counter(page).display() of #counter(page).final().first()],
  )
  // Normal: Arial, sz 24 (12 pt), left-aligned; docDefaults spacing after 160 (8 pt), line 259 auto.
  set text(font: ("Arial", "Helvetica"), size: 12pt, lang: "en", region: "gb")
  set par(justify: false, leading: 0.72em, spacing: 8pt)
  set list(indent: 0.6em, spacing: 0.6em)
  set enum(indent: 0.6em, spacing: 0.6em)
  // numbering.xml abstractNum of numId 1: lvlText "%1." then "%1.%2", "%1.%2.%3", "%1.%2.%3.%4".
  set heading(numbering: (..n) => {
    let v = n.pos()
    if v.len() == 1 { str(v.first()) + "." } else { v.map(str).join(".") }
  })
  // Headings are keepNext/keepLines with spacing after 0; Normal's 8 pt follows each one.
  show heading: set block(below: 8pt, sticky: true)
  // Heading 1: bold, sz 32 (16 pt), spacing before 240 (12 pt); every chapter starts on a new page
  // (document.xml has a page break before each heading 1).
  show heading.where(level: 1): it => {
    pagebreak(weak: true)
    set text(size: 16pt, weight: "bold")
    block(above: 12pt, below: 8pt, it)
  }
  // Heading 2: sz 26 (13 pt), regular; Heading 3: Normal size (12 pt), regular; spacing before 40 (2 pt)
  // after the preceding paragraph's 8 pt. Level 4 has no template style: 12 pt italic.
  show heading.where(level: 2): set text(size: 13pt, weight: "regular")
  show heading.where(level: 2): set block(above: 10pt)
  show heading.where(level: 3): set text(size: 12pt, weight: "regular")
  show heading.where(level: 3): set block(above: 10pt)
  show heading.where(level: 4): set text(size: 12pt, weight: "regular", style: "italic")
  show heading.where(level: 4): set block(above: 10pt)
  show raw.where(block: false): set text(size: 0.92em)
  show raw.where(block: true): it => block(width: 100%, fill: rgb("#F4F2EE"), inset: 6pt, text(size: 8pt, it))
  show link: set text(fill: rgb("#3E5CD9"))
  body
}

// One section alone, for scripts/figure.mjs previews.
#let preview(body) = setup(body)

#let report(meta: (:), logo: none, body) = setup(logo: logo, {
  set document(title: meta.at("kind", default: "Project Report") + " for " + meta.at("title", default: ""))
  // Title page: one left-aligned block, centred vertically on the page, set as a label column and
  // a value column. The course template centres every line; the cover keeps its content (project,
  // module, team, members) and adds the repository and dev environment links as the last rows.
  let ink = rgb("#1B1A18")
  let muted = rgb("#6B665E")
  let accent = rgb("#E8402A")
  let label(body) = pad(top: 2.5pt, text(size: 8.5pt, weight: "bold", tracking: 0.8pt, fill: muted, upper(body)))
  let person(s) = {
    let m = if type(s) == str { s.match(regex("^(.*?)\s*\((.*)\)$")) }
    if m != none [#m.captures.at(0) #text(fill: muted)[(#m.captures.at(1))]] else { s }
  }
  v(1fr)
  block(width: 100%, {
    set par(spacing: 0pt, leading: 0.55em)
    block(width: 2.2cm, height: 4pt, fill: accent)
    v(22pt)
    text(size: 13pt, weight: "bold", tracking: 1.5pt, fill: accent, upper(meta.at("kind", default: "")))
    v(10pt)
    text(size: 52pt, weight: "bold", tracking: -1.5pt, fill: ink, meta.at("title", default: ""))
    v(14pt)
    text(size: 14pt, fill: ink, meta.at("subtitle", default: ""))
    v(44pt)
    line(length: 100%, stroke: 0.5pt + rgb("#D8D2C8"))
    v(14pt)
    grid(columns: (3.6cm, 1fr), row-gutter: 16pt,
      label[Team], text(weight: "bold", meta.at("team", default: "")),
      label[Members], stack(spacing: 7pt, ..meta.at("members", default: ()).map(person)),
      label[Repository], text(size: 10pt, meta.at("repository", default: "")),
      label[Dev environment], text(size: 10pt, meta.at("environment", default: "")),
    )
  })
  v(1fr)
  pagebreak()
  // Contents: "CONTENTS" in the template's notHelp style (bold, sz 28 = 14 pt, after 180 = 9 pt);
  // toc 1 in capitals, toc 2 indented 284 twips, toc 3 567 twips, all with dot leaders, single
  // line and 9 pt after.
  {
    block(below: 9pt, text(size: 14pt, weight: "bold")[CONTENTS])
    show outline.entry: set block(spacing: 9pt)
    show outline.entry.where(level: 1): it => upper(it)
    outline(title: none, depth: 3, indent: n => n * 0.5cm)
  }
  pagebreak()
  body
})
