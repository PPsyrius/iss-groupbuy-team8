// Page setup after the course template ("SWE5006 - Project Report Template for Practice
// Module.docx"): values from word/styles.xml, document.xml, header1.xml, footer1.xml and
// numbering.xml, cited inline. A4, 1-inch margins, Arial 12 pt, numbered headings, the logo
// header and "Page X of Y" footer on every page, title page, then the contents.
#import "uml.typ": *

#let marker(kind, note) = box(
  fill: rgb("#FDECEA"), stroke: 0.5pt + rgb("#E8402A"), inset: (x: 2pt, y: 1pt), radius: 1.5pt,
  text(size: 0.85em, fill: rgb("#B02E1B"), weight: "bold", kind + if note != "" { ": " + note } else { "" }),
)

// A top float sits above all of its page's flow content, so on a page where a chapter or appendix
// opens it would print above the level-1 heading; such a page takes figures at the bottom only.
// The page is the one the figure lands on, found through a label inside the float: a context
// that yields only a float is located at the next in-flow content, not at the float.
#let page-float(num, content) = context {
  let mark = label("report-float-" + num)
  let landed = query(mark)
  let opens = landed.len() > 0 and query(heading.where(level: 1)).any(h => h.location().page() == landed.first().location().page())
  place(if opens { bottom + center } else { auto }, float: true, clearance: 1.4em, [#metadata(none)#mark#content])
}

// Figures float to the top or bottom of a page so they leave no half-empty pages behind,
// and a drawing taller than the page is scaled down to fit it.
// A floating figure that takes at least `full-page-share` of the text area (caption included)
// gets the page to itself: it is set in a block as tall as the text area less the float
// clearance, drawing centred, so the body text continues on the next page instead of
// squeezing a few stray lines into the gap. Below 75 % the gap holds 8 or more lines, enough
// to read as a paragraph.
#let full-page-share = 0.75
// Holds a float the full height of the page area (size.height inside a float), with its
// content centred, leaving less than a line for body text.
#let full-page(size, content) = block(width: 100%, height: size.height - 1.4em - 1pt, align(center + horizon, content))

#let report-figure(num, body, caption, float: true) = {
  let content = layout(size => {
    let framed = block(width: 100%, stroke: 0.4pt + rgb("#D8D2C8"), inset: 6pt, body)
    let h = measure(framed, width: size.width).height
    let max = 20cm
    let shown = if h > max { block(width: 100%, stroke: 0.4pt + rgb("#D8D2C8"), inset: 6pt, align(center, scale(max / h * 100%, reflow: true, body))) } else { framed }
    let fig = block(width: 100%, breakable: false, {
      align(center, shown)
      v(0.3em)
      set text(size: 9pt)
      set par(justify: false)
      [*Figure #num:* #caption]
    })
    let full = float and measure(fig, width: size.width).height >= full-page-share * size.height
    if full { full-page(size, fig) } else { fig }
  })
  if float { page-float(num, content) } else { block(above: 1em, below: 1.2em, content) }
}

// A figure on its own landscape page (images marked {.landscape} in the Markdown): always a
// full page, so no body text shares it; placed like any other float (page-float).
#let landscape-figure(num, body, caption) = page-float(num, layout(size => full-page(size,
  rotate(-90deg, reflow: true, block(width: 23cm, report-figure(num, body, caption, float: false))))))

#let figure-frame(body) = block(width: 100%, breakable: false, inset: 6pt, stroke: 0.4pt + rgb("#D8D2C8"), body)

#let table-caption(body) = block(above: 14pt, below: 0.4em, sticky: true, text(size: 9pt, body))

// Text area of the A4 page with 2.54 cm margins (set in `setup`).
#let text-area = (width: 21cm - 2 * 2.54cm, height: 29.7cm - 2 * 2.54cm)

#let report-table(n, ..cells) = {
  set text(size: 9pt)
  set par(justify: false, leading: 0.5em)
  // A row never splits across pages, so a cell's text stays beside its key.
  set table.cell(breakable: false)
  let t = table(
    columns: n,
    inset: (x: 4pt, y: 3.5pt),
    stroke: 0.4pt + rgb("#8A8378"),
    fill: (_, y) => if y == 0 { rgb("#ECE6DC") },
    // The first row is a real header: it repeats on each page and never ends a page alone.
    table.header(..cells.pos().slice(0, n.len())),
    ..cells.pos().slice(n.len()),
  )
  // A table up to half a page tall stays on one page. Otherwise a float waiting for the next
  // page (a landscape figure, a tall diagram) leaves slivers that the table's rows fill a few
  // at a time, scattering a short table over several pages between full-page figures.
  context {
    let h = measure(t, width: text-area.width).height
    block(width: 100%, above: 0.6em, below: 1em, breakable: h > text-area.height / 2, t)
  }
}

// sectPr: pgSz 11906 x 16838 twips (A4), pgMar 1440 (2.54 cm) all round, header and footer 708
// twips (1.25 cm) from the edge, one section with no titlePg, so header and footer are on every
// page, the title page included.
#let header-dist = 1.25cm
// header1.xml: the logo, right-aligned, wp:extent 1479550 x 544959 EMU (4.11 x 1.51 cm). The
// template's image (word/media/image1.png, 2344 x 866 px) carries blank margins around the
// artwork: 12.5 % left and right, 25.6 % above and 25.9 % below. The header clips them off and
// prints the artwork at the template's scale, 3.08 x 0.73 cm, so the top margin stays the
// template's 2.54 cm.
#let logo-extent = (4.11cm, 1.51cm)
#let logo-crop = (left: 0.1254, top: 0.2564, right: 0.1246, bottom: 0.2587)
// `logo` is (path: str, cropped: bool); a cropped logo (REPORT_LOGO) is drawn at the artwork's height.
#let logo-mark(logo) = {
  let (w, h) = logo-extent
  if logo.cropped { return image(logo.path, height: h * (1 - logo-crop.top - logo-crop.bottom)) }
  box(clip: true,
    width: w * (1 - logo-crop.left - logo-crop.right),
    height: h * (1 - logo-crop.top - logo-crop.bottom),
    place(top + left, dx: -w * logo-crop.left, dy: -h * logo-crop.top, image(logo.path, width: w, height: h)))
}

// Running head (not in the template): the chapter or appendix current on the page, at the left of
// the footer. A chapter that opens on the page wins; otherwise the last one before
// it. The title and contents pages come before any chapter, so they get none.
// Shown as "2 | Project Conduct" or "Appendix B | ADR Index ...".
#let running-head() = context {
  let pg = here().page()
  let sel = selector(heading.where(level: 1))
  let opening = query(sel).filter(h => h.location().page() == pg)
  let chapter = if opening.len() > 0 { opening.first() } else { query(sel.before(here())).at(-1, default: none) }
  if chapter != none {
    let bar = [#h(0.4em)|#h(0.4em)]
    if chapter.numbering != none {
      [#counter(heading).at(chapter.location()).first()#bar#chapter.body]
    } else {
      // Appendix headings arrive unnumbered as "Appendix X: Title"; the bar replaces the colon.
      let t = if chapter.body.has("text") { chapter.body.text } else { "" }
      let m = t.match(regex("^(Appendix [A-Z]):?\s+(.*)$"))
      if m != none [#m.captures.at(0)#bar#m.captures.at(1)] else { chapter.body }
    }
  }
}

// Dark navy close to Word's Heading 1-3 theme colour (accent 1, darker 50%).
#let heading-ink = rgb("#1F3864")

#let setup(logo: none, body) = {
  set page(
    paper: "a4",
    margin: 2.54cm,
    header-ascent: 0.45cm,
    header: if logo != none { align(right + bottom, logo-mark(logo)) },
    // footer1.xml: right-aligned "Page {PAGE} of {NUMPAGES}" (Footer style, Arial 12 pt). The footer
    // also carries the running head at left; both are set at 9 pt in body ink.
    footer: context {
      set text(size: 9pt)
      grid(columns: (1fr, auto), column-gutter: 1em, running-head(),
        [Page #counter(page).display() of #counter(page).final().first()])
    },
  )
  // Normal: Arial, sz 24 (12 pt), left-aligned; docDefaults spacing after 160 (8 pt), line 259 auto.
  set text(font: ("Arial", "Helvetica"), size: 12pt, lang: "en", region: "gb")
  // Word's line 259 auto on Arial (ascent+descent+gap 1.149 em) gives 14.9 pt from baseline to
  // baseline, so Typst's leading (cap-height to baseline) is 0.52 em; Word adds the 8 pt after a
  // paragraph on top of that line gap, so the gap between paragraphs is leading + 8 pt.
  set par(justify: false, leading: 0.52em, spacing: 0.52em + 8pt)
  // List items are tight (Word's List Paragraph uses contextual spacing); the list as a whole keeps
  // the paragraph gap above and below.
  set list(indent: 0.6em, spacing: 0.52em + 3pt)
  set enum(indent: 0.6em, spacing: 0.52em + 3pt)
  // numbering.xml abstractNum of numId 1: lvlText "%1." then "%1.%2", "%1.%2.%3", "%1.%2.%3.%4".
  set heading(numbering: (..n) => {
    let v = n.pos()
    if v.len() == 1 { str(v.first()) + "." } else { v.map(str).join(".") }
  })
  // Headings are keepNext/keepLines with spacing after 0; Normal's 8 pt follows each one.
  // Word: spacing after 0, so the heading sits one line gap above its text; the space before is the
  // previous paragraph's 8 pt plus the heading's own (2 pt for Heading 2 and 3), plus the line gap.
  show heading: set block(below: 6.2pt, sticky: true)
  // Heading 1: bold, sz 32 (16 pt), spacing before 240 (12 pt); every chapter starts on a new page
  // (document.xml has a page break before each heading 1).
  // Adjacent block spacing collapses to the larger value, so the 18 pt below a chapter title only
  // widens the gap to a paragraph, table or figure that opens the chapter; a Heading 2 straight
  // after the title keeps its own larger space before.
  show heading.where(level: 1): it => {
    pagebreak(weak: true)
    set text(size: 16pt, weight: "bold", fill: heading-ink)
    block(above: 12pt, below: 18pt, it)
  }
  // Heading 2: sz 26 (13 pt); Heading 3: Normal size (12 pt); both regular in the docx; spacing before 40 (2 pt)
  // after the preceding paragraph's 8 pt. Level 4 has no template style.
  // Sizes as the template; weight and colour added so the levels read apart from body text (the
  // docx styles set no colour; Word's built-in heading theme uses a dark accent like this one).
  // No heading is italic. Extra space before H2 and H3 so a section break is wider than a
  // paragraph break; H4 is bold in body ink, set apart from H3 by colour.
  show heading.where(level: 2): set text(size: 13pt, weight: "bold", fill: heading-ink)
  show heading.where(level: 2): set block(above: 8pt + 2pt + 6.2pt + 8pt)
  show heading.where(level: 3): set text(size: 12pt, weight: "bold", fill: heading-ink)
  show heading.where(level: 3): set block(above: 8pt + 2pt + 6.2pt + 4pt)
  show heading.where(level: 4): set text(size: 12pt, weight: "bold", style: "normal")
  show heading.where(level: 4): set block(above: 8pt + 2pt + 6.2pt)
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
