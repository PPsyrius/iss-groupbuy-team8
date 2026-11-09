// Helpers for Appendix A (detailed design specifications). Import in every part file:
//   #import "lib-a.typ": *
// Diagrams come from diagrams/appendix-a/gen/<id>.typ, generated from src/<id>.mmd by
// scripts/appendix-diagrams.mjs (see diagrams/appendix-a/README.md).
#import "../../lib.typ": accent, muted, hdr, pending

// Page geometry of the report (lib.typ: A4, margins x 2.2 cm, top and bottom 2.3 cm).
#let a-text = (width: 21cm - 2 * 2.2cm, height: 29.7cm - 2 * 2.3cm)
// The same page turned a quarter (page(flipped: true) keeps the margins).
#let a-land = (width: 29.7cm - 2 * 2.2cm, height: 21cm - 2 * 2.3cm)
#let cap-gap = 0.65em

// Appendix numbering: the level-1 heading reads "Appendix A: …", parts "A.1", "A.1.1", and
// figures and tables "A.1", "A.2", … from the first figure of the appendix. Level 3 and below
// stay out of the contents, so the contents lists each part once.
#let appendix-a(body) = {
  set heading(numbering: (..n) => {
    let v = n.pos()
    if v.len() == 1 { "Appendix " + numbering("A", v.first()) + ":" } else { numbering("A", v.first()) + "." + v.slice(1).map(str).join(".") }
  })
  show heading.where(level: 3): set heading(outlined: false)
  show heading.where(level: 4): set heading(outlined: false)
  set figure(numbering: n => "A." + str(n))
  show figure.caption: set text(9pt)
  show figure.caption: set par(justify: false)
  counter(heading).update(0)
  counter(figure.where(kind: image)).update(0)
  counter(figure.where(kind: table)).update(0)
  body
}

// Caption as figure() sets it, measured to leave the drawing the rest of the page.
#let cap-height(caption, width) = measure(block(width: width, {
  set text(9pt)
  set par(justify: false)
  [Figure A.99: #caption]
})).height

// A generated UML diagram as a numbered figure.
//   #afig("a-uc04-ac", [UC4 analysis class diagram (UML, BCE stereotypes). …])
//   → Figure A.n, label <fig-a-uc04-ac> (or the label given as `lbl`), referenced @fig-a-uc04-ac.
// The drawing scales to the text width and the page height left under its caption. A class or
// state diagram that would print its labels below 8 pt turns landscape when that helps: the
// figure then takes a flipped page of its own, caption included. Sequence diagrams never turn;
// split a long one instead. Call afig at the top level of a part file, not inside a block, list
// or table: a landscape figure needs a page break.
// orient: auto lets the kit decide (it turns only when landscape prints at least 0.03 larger);
// "landscape" forces a flipped page, for a class or state diagram that prints just under 8 pt
// in portrait but fits in landscape; "portrait" never turns. Check the result with
// scripts/preview-part.sh, which prints each figure's label size.
// float: true lets a portrait figure float to the top or bottom of a page instead of standing in
// the text flow. Off by default, as in the rest of the report: a float can drift before the
// appendix heading or past the end of the document.
#let afig(id, caption, lbl: auto, orient: auto, float: false) = {
  import "/docs/swe5001/report/diagrams/appendix-a/gen/" + id + ".typ": drawing, kind
  let lab = if lbl == auto { label("fig-" + id) } else { lbl }
  assert(orient in (auto, "landscape", "portrait"), message: "afig orient: auto, \"landscape\" or \"portrait\"")
  assert(not (orient == "landscape" and kind == "sequence"), message: id + ": a sequence diagram never turns landscape; split it")
  context {
    let portrait-room = a-text.height - cap-height(caption, a-text.width) - cap-gap.to-absolute() - 2.2em.to-absolute()
    let land-room = a-land.height - cap-height(caption, a-land.width) - cap-gap.to-absolute() - 1em.to-absolute()
    // Forcing works through the room the kit is offered: a portrait room of almost nothing makes
    // landscape the better choice, a landscape room of almost nothing rules it out.
    let portrait-room = if orient == "landscape" { 1cm } else { portrait-room }
    let land-room = if orient == "portrait" { 1cm } else { land-room }
    let host = (probe: true, rotated: false, width: a-text.width, height: portrait-room, land: (width: a-land.width, height: land-room))
    // The probe lays the drawing out once more; a sequence diagram never turns, so it skips it.
    let turned = kind != "sequence" and measure(block(width: a-text.width, drawing(host: host))).height == 1pt
    // Record what the frame decided, for scripts/preview-part.sh (typst query "<a-fig>").
    let note = context {
      let s = query(selector(<uml-scale>).before(here())).at(-1, default: none)
      // page: where the figure is (a float still reports the page it was called on, even when it
      // lands on the next one); anchor: where afig was called.
      let placed = query(lab).at(0, default: none)
      let page = if placed != none { placed.location().page() } else { here().page() }
      [#metadata((id: id, kind: kind, page: page, anchor: here().page(), scale: if s != none { s.value.scale } else { none }, landscape: turned)) <a-fig>]
    }
    if turned {
      page(flipped: true)[
        #v(1fr)
        #figure(drawing(host: host + (probe: false, rotated: true)), caption: caption, kind: image, gap: cap-gap)#lab
        #note
        #v(1fr)
      ]
    } else {
      // A float placed on a page that opens with the appendix heading would print above it.
      let opens = query(heading.where(level: 1)).any(h => h.location().page() == here().page())
      let place-at = if not float { none } else if opens { bottom } else { auto }
      [#figure(drawing(host: host + (probe: false)), caption: caption, kind: image, gap: cap-gap, placement: place-at)#lab]
      note
    }
  }
}

// A numbered table in the report's table style (lib.typ: shaded bold header row), with the
// header repeated on every page it spans.
//   #atable([UC4 flows of events.], <tbl-a-uc04-flows>, columns: (auto, 1fr, auto),
//     header: ([Step], [Actor and system actions], [Trace]),
//     [B1], [The buyer …], [@fig-a-uc04-as-basic],
//   )
// A long code name in a cell (`PaymentBrowserService.createIntent(user: UUID, …)`) may break
// after "_", ".", "/", "(" or "," and between the words of a camelCase name, so it wraps inside
// its column instead of running into the next one (as report-table in the SWE5006 template).
#let atable(caption, lbl, columns: auto, header: (), align: left + top, ..cells) = [
  #show raw.where(block: false): it => {
    show regex("[_./(,]"): m => m.text + sym.zws
    show regex("[a-z][A-Z]"): m => m.text.at(0) + sym.zws + m.text.at(1)
    it
  }
  #set table.cell(breakable: false)
  #figure(
    table(
      columns: columns,
      align: align,
      table.header(..header),
      ..cells.pos(),
    ),
    caption: caption,
    kind: table,
    gap: cap-gap,
  )#lbl
]

// A step tag as used in the flow tables and diagrams ("B4", "A2.1", "E1").
#let step(s) = text(weight: "bold", fill: accent, s)
