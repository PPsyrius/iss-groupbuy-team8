// UML drawing kit for the report: class, state and sequence diagrams.
// scripts/typst.mjs translates the Mermaid fences in sections/*.md into calls to these functions.
#import "@preview/fletcher:0.5.8" as fletcher: diagram, node, edge
#import "@preview/cetz:0.3.4"

#let ink = rgb("#1B1A18")
#let muted = rgb("#6B655C")
// Fills follow docs/presentation-kit/diagrams/STYLE.md: boundary = platform blue,
// control = business red, entity = neutral, external = neutral grey.
#let fills = (
  boundary: rgb("#E3E8FB"),
  control: rgb("#FBDFD9"),
  entity: rgb("#FFF6E2"),
  interface: rgb("#F4F2EE"),
  abstract: rgb("#F4F2EE"),
  external: rgb("#ECE6DC"),
  default: white,
)
#let fill-for(stereo) = {
  if stereo == none { return fills.default }
  let s = lower(stereo)
  for (k, v) in fills { if s.contains(k) { return v } }
  fills.default
}

// 9 pt keeps edge and fragment labels (set up to 1 pt smaller) at the 8 pt minimum of rubric F4.
#let uml-size = 9pt

// Allow long CamelCase and ":Type" names to wrap at word boundaries instead of shrinking.
#let breakable(name) = name.replace(regex("([a-z0-9])([A-Z])"), m => m.captures.at(0) + "\u{200B}" + m.captures.at(1)).replace(":", ":\u{200B}")

// Scale a drawing down to the text width when it is wider; never scale up.
#let fit(body) = layout(size => {
  let m = measure(body)
  if m.width > size.width { scale(size.width / m.width * 100%, reflow: true, body) } else { body }
})

// Edge label that wraps beyond a maximum width.
#let wrap-label(t, max: 38mm) = context {
  set par(justify: false)
  let c = text(size: uml-size - 0.5pt, t)
  if measure(c).width > max { box(width: max, align(center, c)) } else { c }
}

// One class box: name compartment, then attributes and operations when present.
#let class-box(name, stereo: none, attrs: (), ops: (), abstract: false) = {
  set text(size: uml-size)
  set par(leading: 0.35em, justify: false)
  let head = align(center, {
    if stereo != none { text(size: uml-size - 0.5pt, "«" + stereo + "»"); linebreak() }
    text(weight: "bold", style: if abstract { "italic" } else { "normal" }, name)
  })
  let cells = (head,)
  if attrs.len() > 0 or ops.len() > 0 { cells.push(align(left, attrs.join("\n"))) }
  if ops.len() > 0 { cells.push(align(left, ops.join("\n"))) }
  box(table(
    columns: 1,
    inset: (x: 4pt, y: 3pt),
    stroke: 0.6pt + ink,
    fill: fill-for(stereo),
    ..cells.map(c => if type(c) == str { text(c) } else { c }),
  ))
}

// Relationship kinds -> line style and end marks, following UML notation:
// generalisation and realisation end in a hollow triangle (solid and dashed line),
// composition starts with a filled diamond, aggregation with a hollow one,
// navigable association and dependency end in an open arrowhead (dependency dashed).
// Fletcher's "|>" is always filled and a white fill has no outline, so the UML hollow
// triangle is drawn here; the line stops at its base.
#let hollow-triangle = (
  size: 11, tip-origin: 0, tail-origin: -11,
  draw: mark => cetz.draw.line((0, 0), (-mark.size, mark.size * 0.5), (-mark.size, -mark.size * 0.5), close: true, fill: white, stroke: 0.6pt),
)
#let rel-style = (
  "assoc": (dash: none, marks: (none, none)),
  "nav": (dash: none, marks: (none, ">")),
  "dep": (dash: "dashed", marks: (none, ">")),
  "dashed": (dash: "dashed", marks: (none, none)),
  "inherit": (dash: none, marks: (none, hollow-triangle)),
  "realize": (dash: "dashed", marks: (none, hollow-triangle)),
  "compose": (dash: none, marks: ((inherit: "<>", fill: ink, scale: 160%), none)),
  "aggregate": (dash: none, marks: ((inherit: "<>", fill: white, stroke: 0.6pt + ink, scale: 160%), none)),
)

// classes: array of (id, name, stereo, attrs, ops, abstract, pos: (x, y))
// rels: array of (from, to, kind, label, m-from, m-to)
#let class-diagram(classes, rels, spacing: (14mm, 15mm)) = fit({
  set text(size: uml-size)
  diagram(
    spacing: spacing,
    node-stroke: none,
    node-inset: 0pt,
    edge-stroke: 0.6pt + ink,
    mark-scale: 100%,
    ..classes.map(c => node(c.pos, class-box(c.name, stereo: c.stereo, attrs: c.attrs, ops: c.ops, abstract: c.abstract), name: label(c.id))),
    ..rels.map(r => {
      let style = rel-style.at(r.kind, default: rel-style.assoc)
      let args = (label(r.from), label(r.to))
      let bend = r.at("bend", default: 0deg)
      let out = ()
      out.push(edge(..args, marks: style.marks, stroke: (paint: ink, thickness: 0.6pt, dash: style.dash), bend: bend, label: if r.label != none { wrap-label(r.label) }, label-side: center, label-fill: white))
      // Multiplicities sit beside the line near each end; the edge is cropped at the node
      // boundaries, so 0.1 and 0.9 of its length stay clear of the boxes.
      if r.at("mfrom", default: none) != none {
        out.push(edge(label(r.from), label(r.to), "-", bend: bend, stroke: none, label: text(size: uml-size - 1pt, r.mfrom), label-pos: 0.1, label-side: right, label-sep: 2pt))
      }
      if r.at("mto", default: none) != none {
        out.push(edge(label(r.from), label(r.to), "-", bend: bend, stroke: none, label: text(size: uml-size - 1pt, r.mto), label-pos: 0.9, label-side: right, label-sep: 2pt))
      }
      out
    }).flatten(),
  )
})

// states: array of (id, name, kind: "state" | "start" | "end" | "choice", pos, body)
// trans: array of (from, to, label)
#let state-diagram(states, trans, spacing: (26mm, 24mm)) = fit({
  set text(size: uml-size)
  diagram(
    spacing: spacing,
    edge-stroke: 0.6pt + ink,
    mark-scale: 70%,
    ..states.map(s => {
      if s.kind == "start" {
        node(s.pos, none, shape: circle, radius: 3.5pt, fill: ink, stroke: none, name: label(s.id))
      } else if s.kind == "end" {
        node(s.pos, circle(radius: 2.2pt, fill: ink), shape: circle, radius: 4.5pt, stroke: 0.6pt + ink, inset: 0pt, name: label(s.id))
      } else if s.kind == "choice" {
        node(s.pos, none, shape: fletcher.shapes.diamond, width: 10pt, height: 10pt, stroke: 0.6pt + ink, fill: white, name: label(s.id))
      } else {
        node(s.pos, {
          set par(leading: 0.35em)
          align(center, text(weight: "bold", s.name))
          if s.at("body", default: none) != none { v(-2pt); line(length: 100%, stroke: 0.4pt + muted); align(left, text(size: uml-size - 0.5pt, s.body)) }
        }, shape: fletcher.shapes.rect, corner-radius: 5pt, width: auto, stroke: 0.6pt + ink, fill: rgb("#F1F4FD"), inset: 5pt, name: label(s.id))
      }
    }),
    ..trans.map(t => edge(label(t.from), label(t.to), "->", label: if t.label != none { wrap-label(t.label) }, label-fill: white, bend: t.at("bend", default: 0deg))),
  )
})

// ---- Sequence diagrams -------------------------------------------------------

#let arrow-head(x, y, dir, open: false, stroke: 0.6pt + ink) = {
  // dir = 1 points right, -1 points left
  let l = 5pt
  let h = 2.6pt
  if open {
    place(top + left, line(start: (x, y), end: (x - dir * l, y - h), stroke: stroke))
    place(top + left, line(start: (x, y), end: (x - dir * l, y + h), stroke: stroke))
  } else {
    place(top + left, polygon(fill: ink, stroke: none, (x, y), (x - dir * l, y - h), (x - dir * l, y + h)))
  }
}

#let stick(x, y) = {
  let s = 0.5pt + ink
  place(top + left, dx: x - 3pt, dy: y, circle(radius: 3pt, stroke: s))
  place(top + left, line(start: (x, y + 6pt), end: (x, y + 13pt), stroke: s))
  place(top + left, line(start: (x - 5pt, y + 8.5pt), end: (x + 5pt, y + 8.5pt), stroke: s))
  place(top + left, line(start: (x, y + 13pt), end: (x - 4pt, y + 19pt), stroke: s))
  place(top + left, line(start: (x, y + 13pt), end: (x + 4pt, y + 19pt), stroke: s))
}

// parts: array of (id, name, kind: "actor" | "participant" | stereotype string)
// steps: array of dictionaries:
//   (t: "msg", from, to, label, style: "sync" | "async" | "return")
//   (t: "frag", kind: "alt" | "opt" | "loop" | "par" | "critical" | "break" | "rect", cond)
//   (t: "else", cond)   (t: "end")   (t: "note", over: (ids), text)
#let sequence-diagram(parts, steps) = layout(size => {
  set text(size: uml-size)
  set par(leading: 0.35em, justify: false)
  let W = size.width
  let n = parts.len()
  let cw = W / n
  let idx = (:)
  for (i, p) in parts.enumerate() { idx.insert(p.id, i) }
  let px(id) = cw * (idx.at(id) + 0.5)
  let head(p) = {
    let stereo = if p.kind != "participant" and p.kind != "actor" { "«" + p.kind + "»" } else { none }
    let nm = text(weight: "bold", size: uml-size - 0.5pt, breakable(p.name))
    align(center + horizon, { if stereo != none { text(size: uml-size - 1pt, stereo); linebreak() }; nm })
  }
  let head-h = calc.max(30pt, ..parts.filter(p => p.kind != "actor").map(p => measure(box(width: cw - 10pt, head(p))).height + 8pt))
  let prims = ()
  let frames = ()
  let y = head-h + 10pt
  for s in steps {
    if s.t == "msg" {
      let x1 = px(s.from)
      let x2 = px(s.to)
      let lab = text(size: uml-size - 0.5pt, s.label)
      let dashed = s.style == "return"
      let st = if dashed { (paint: ink, thickness: 0.6pt, dash: "dashed") } else { 0.6pt + ink }
      if s.from == s.to {
        let w = 12pt
        prims.push(place(top + left, line(start: (x1, y), end: (x1 + w, y), stroke: st)))
        prims.push(place(top + left, line(start: (x1 + w, y), end: (x1 + w, y + 9pt), stroke: st)))
        prims.push(place(top + left, line(start: (x1 + w, y + 9pt), end: (x1 + 1pt, y + 9pt), stroke: st)))
        prims.push(arrow-head(x1 + 1pt, y + 9pt, -1, open: s.style != "sync"))
        let lb = box(width: calc.max(cw * 1.4, 50pt), lab)
        let lh = measure(lb).height
        prims.push(place(top + left, dx: x1 + w + 3pt, dy: y - 2pt, lb))
        y += calc.max(lh, 9pt) + 12pt
      } else {
        let dir = if x2 > x1 { 1 } else { -1 }
        let lw = calc.max(calc.abs(x2 - x1), cw * 1.6)
        let mid = (x1 + x2) / 2
        let a = calc.max(mid - lw / 2, 0pt)
        let b = calc.min(a + lw, W)
        let lb = box(width: b - a, align(center, lab))
        let lh = measure(lb).height
        y += lh + 4pt
        prims.push(place(top + left, line(start: (x1, y), end: (x2, y), stroke: st)))
        prims.push(arrow-head(x2, y, dir, open: s.style != "sync"))
        prims.push(place(top + left, dx: a, dy: y - lh - 3pt, lb))
        y += 7pt
      }
    } else if s.t == "frag" {
      frames.push((y: y, kind: s.kind, cond: s.cond, elses: ()))
      y += 12pt
    } else if s.t == "else" {
      let f = frames.pop()
      f.elses.push((y: y + 2pt, cond: s.cond))
      frames.push(f)
      y += 14pt
    } else if s.t == "end" {
      let f = frames.pop()
      let d = frames.len() * 4pt + 3pt
      let x0 = d
      let x1 = W - d
      let y1 = y - 4pt
      let tag = if f.kind == "rect" { none } else { f.kind }
      prims.push(place(top + left, dx: x0, dy: f.y, rect(width: x1 - x0, height: y1 - f.y, stroke: 0.5pt + muted, fill: if f.kind == "rect" { rgb("#F1F4FD").transparentize(40%) } else { none })))
      if tag != none {
        prims.push(place(top + left, dx: x0, dy: f.y, box(fill: white, stroke: 0.5pt + muted, inset: (x: 3pt, y: 1.5pt), text(weight: "bold", size: uml-size - 1pt, tag))))
      }
      if f.cond != none and f.cond != "" {
        prims.push(place(top + left, dx: x0 + 34pt, dy: f.y + 2pt, text(size: uml-size - 1pt, style: "italic", "[" + f.cond + "]")))
      }
      for e in f.elses {
        prims.push(place(top + left, line(start: (x0, e.y), end: (x1, e.y), stroke: (paint: muted, thickness: 0.5pt, dash: "dashed"))))
        if e.cond != none and e.cond != "" {
          prims.push(place(top + left, dx: x0 + 4pt, dy: e.y + 2pt, text(size: uml-size - 1pt, style: "italic", "[" + e.cond + "]")))
        }
      }
      y += 6pt
    } else if s.t == "note" {
      let xs = s.over.map(px)
      let a = calc.min(..xs) - cw * 0.4
      let b = calc.max(..xs) + cw * 0.4
      let body = box(width: b - a, inset: 3pt, fill: rgb("#FFF6E2"), stroke: 0.5pt + muted, text(size: uml-size - 0.5pt, s.text))
      let h = measure(body).height
      prims.push(place(top + left, dx: a, dy: y, body))
      y += h + 10pt
    }
  }
  let H = y + 4pt
  box(width: W, height: H, {
    for (i, p) in parts.enumerate() {
      let x = cw * (i + 0.5)
      place(top + left, line(start: (x, head-h), end: (x, H), stroke: (paint: muted, thickness: 0.5pt, dash: "dashed")))
      if p.kind == "actor" {
        stick(x, 0pt)
        place(top + left, dx: x - cw / 2, dy: 20pt, box(width: cw, align(center, text(size: uml-size - 1pt, weight: "bold", breakable(p.name)))))
      } else {
        let b = box(width: cw - 6pt, height: head-h - 4pt, stroke: 0.6pt + ink, fill: fill-for(if p.kind == "participant" { none } else { p.kind }), inset: 2pt, head(p))
        place(top + left, dx: x - cw / 2 + 3pt, dy: 0pt, b)
      }
    }
    for p in prims { p }
  })
})
