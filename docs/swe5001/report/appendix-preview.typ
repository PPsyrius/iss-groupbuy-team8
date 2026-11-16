// Preview of Appendix A alone, for review before it joins main.typ. Delete at integration.
// Build (from the repository root):
//   typst compile --root . docs/swe5001/report/appendix-preview.typ docs/swe5001/report/build/appendix-preview.pdf
// Labels from chapters 1-6 do not exist here; the appendix prints fallback text for them.

#import "lib.typ": *
#show: style

// The lists print each caption's first sentence, as main.typ does (S-D10), so the captions can
// be checked as the List of figures and the List of tables will show them.
#let first-sentence(body) = {
  let kids = if body.func() == [].func() { body.children } else { (body,) }
  let out = ()
  for (i, k) in kids.enumerate() {
    if k.func() == text {
      let t = k.text
      let at = t.position(". ")
      if at != none { out.push(text(t.slice(0, at + 1))); return out.join() }
      let next = kids.at(i + 1, default: none)
      if t.ends-with(".") and (next == none or next == [ ]) { out.push(k); return out.join() }
    }
    out.push(k)
  }
  out.join()
}
#let short-entry(it) = context {
  let el = it.element
  let ink = text.fill
  let body = if el.caption == none { it.body() } else { first-sentence(el.caption.body) }
  let leader = if it.fill == none { h(1fr) } else { box(width: 1fr, it.fill) }
  link(el.location(), { set text(fill: ink); it.indented(it.prefix(), [#body#sym.wj~#sym.wj#leader#sym.wj~#it.page()]) })
}
#let front-title(body) = block(above: 12pt, below: 18pt, text(size: 16pt, weight: "bold", fill: tpl.heading-ink, body))

#{
  front-title[Contents]
  show outline.entry: set block(spacing: 9pt)
  show outline.entry.where(level: 1): it => upper(it)
  outline(title: none, depth: 3, indent: n => n * 0.5cm)
  front-title[List of figures]
  show outline.entry: short-entry
  outline(title: none, target: figure.where(kind: image))
  front-title[List of tables]
  outline(title: none, target: figure.where(kind: table))
}

#include "sections/07-appendix-a-walkthrough.typ"
