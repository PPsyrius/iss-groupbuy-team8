// UML drawing kit for the report: class, state and sequence diagrams, drawn to look like the
// deck's Visual Paradigm-style UML (docs/presentation-kit/diagrams/STYLE.md and the deck's
// src/diagrams/*.svg). scripts/typst.mjs translates the Mermaid fences in sections/*.md into
// calls to the three diagram functions at the end of each part below.
#import "@preview/fletcher:0.5.8" as fletcher: diagram, node, edge
#import "@preview/cetz:0.3.4"

// ---- Style -------------------------------------------------------------------
//
// Palette (STYLE.md): ink #1B1A18 for text, lines and outlines; muted #6B655C for the diagram
// frame, lifelines and fragment outlines. Element fills are vertical gradients from white (top)
// to a role tint (bottom), chosen from the element's stereotype:
//   «boundary»                                       -> platform blue  #E3E8FB
//   «control»                                        -> business red   #FBDFD9
//   «event», «domain event», «topic», «queue», «message»
//                                                    -> messaging      #FFF6E2 -> #FFEDC7
//   everything else («entity», «interface», «enumeration», «external», «record», no
//   stereotype)                                      -> neutral        #ECE6DC
// Class boxes carry the tint only in the name compartment; the member compartments stay white.
// States use the neutral tint, sequence lifeline heads their participant's role tint.
#let ink = rgb("#1B1A18")
#let muted = rgb("#6B655C")
#let tints = (
  boundary: (white, rgb("#E3E8FB")),
  control: (white, rgb("#FBDFD9")),
  messaging: (rgb("#FFF6E2"), rgb("#FFEDC7")),
  neutral: (white, rgb("#ECE6DC")),
)
#let role-of(stereo) = {
  if stereo == none { return "neutral" }
  let words = lower(stereo).split(regex("[^a-z]+"))
  // «event source», «event listener» and the like are classes that handle events, not messages.
  let event = "event" in words and not words.any(w => w in ("source", "listener", "publisher", "subscriber", "handler"))
  if "boundary" in words { "boundary" } else if "control" in words { "control" } else if event or words.any(w => w in ("topic", "queue", "message", "messaging")) { "messaging" } else { "neutral" }
}
#let role-fill(stereo) = gradient.linear(..tints.at(role-of(stereo)), dir: ttb)

// Strokes: STYLE.md draws 1.4-1.6 px lines against 15 px text (about 0.1 em); at the 8.5 pt
// print size that is 0.8 pt for lines and arrows and 0.9 pt for element outlines.
#let w-line = 0.8pt
#let w-box = 0.9pt
#let w-thin = 0.6pt

// One text size for every label (names bold): 8.5 pt, so a drawing may be scaled to 94 % and
// still print every label at the 8 pt minimum of rubric F4. The frame heading is 9 pt bold.
#let uml-size = 8.5pt
#let min-print = 8pt
#let min-scale = min-print / uml-size

// Allow long CamelCase and ":Type" names to wrap at word boundaries instead of shrinking.
#let breakable(name) = name.replace(regex("([a-z0-9])([A-Z])"), m => m.captures.at(0) + "\u{200B}" + m.captures.at(1)).replace(":", ":\u{200B}")
// Allow a member signature to wrap after "(", "," and "." as well as at spaces.
#let member-breakable(s) = s.replace(regex("([(.,])(\S)"), m => m.captures.at(0) + "\u{200B}" + m.captures.at(1))

// ---- Diagram frame -----------------------------------------------------------
//
// UML diagram frame: a rectangle with the heading in a pentagon tab at the top left,
// "sd <name>", "class <name>" or "stm <name>".

// Height left for the drawing inside the report figure: template.typ scales a framed figure
// taller than 20 cm (inset 6 pt) down, so the drawing and its frame stay within that.
#let frame-pad = (x: 5pt, top: 5pt, bottom: 4pt)
#let max-figure = 20cm - 2 * 6pt - 2pt

#let frame-title(kind, name) = text(size: 9pt, weight: "bold", fill: ink, kind + if name != none and name != "" { " " + name })

// Draw the frame around an already scaled drawing; width is the frame's outer width.
#let draw-frame(kind, name, width, body) = context {
  let title = frame-title(kind, name)
  let t = measure(title)
  let th = t.height + 7pt
  let cut = 6pt
  let tw = calc.min(t.width + 10pt + cut, width)
  block(width: width, stroke: w-line + muted, inset: (x: frame-pad.x, top: th + frame-pad.top, bottom: frame-pad.bottom), breakable: false, {
    place(top + left, dx: -frame-pad.x, dy: -(th + frame-pad.top), polygon(
      fill: white, stroke: w-line + muted,
      (0pt, 0pt), (tw, 0pt), (tw, th - cut), (tw - cut, th), (0pt, th),
    ))
    place(top + left, dx: -frame-pad.x + 5pt, dy: -(th + frame-pad.top) + 3.5pt, title)
    align(center, body)
  })
}

// Frame a drawing and scale the drawing (never the frame heading) to the space available:
// the text width, and the height left by the report figure.
// build(spacing) returns the drawing for one of the candidate spacings, in order of preference
// (loosest first); the first that fits unscaled wins, otherwise the first that scales the
// drawing (almost) as little as the best one, so a tall or wide drawing closes up its gaps
// before its text shrinks. A drawing that would still have to be scaled below the 8 pt minimum
// turns a quarter (landscape) when that scales it less. fill: the drawing takes the full width
// (sequence diagrams); otherwise it is measured at its natural width.
// Each frame records its kind, name and final scale as <uml-scale> metadata, so a build can
// list the figures whose labels print below 8 pt (typst query ... "<uml-scale>").
#let uml-frame(kind, name, build, candidates, landscape: true, fill: false) = layout(size => context {
  let th = measure(frame-title(kind, name)).height + 7pt
  let px = 2 * frame-pad.x + 2 * w-line
  let py = th + frame-pad.top + frame-pad.bottom + 2 * w-line
  let W = size.width - px
  let H = max-figure - py
  let fits = ()
  for sp in candidates {
    let m = if fill { measure(build(sp), width: W) } else { measure(build(sp)) }
    // Landscape: the drawing's width runs along the page height, its height across the page.
    fits.push((m: m, portrait: calc.min(1.0, W / m.width, H / m.height), land: calc.min(1.0, H / m.width, (size.width - py) / m.height)))
    if fits.last().portrait >= 1.0 { break }
  }
  let pick(key) = {
    let target = calc.min(1.0, calc.max(..fits.map(f => f.at(key)))) - 0.02
    fits.position(f => f.at(key) >= target)
  }
  let i = pick("portrait")
  let j = pick("land")
  let turn = landscape and fits.at(i).portrait < min-scale and fits.at(j).land > fits.at(i).portrait + 0.03
  let k = if turn { j } else { i }
  let s = if turn { fits.at(k).land } else { fits.at(k).portrait }
  let drawing = build(candidates.at(k))
  let shown = if s < 1.0 { scale(s * 100%, reflow: true, drawing) } else { drawing }
  [#metadata((kind: kind, name: name, scale: s, landscape: turn)) <uml-scale>]
  if turn {
    align(center, rotate(-90deg, reflow: true, draw-frame(kind, name, fits.at(k).m.width * s + px, shown)))
  } else {
    draw-frame(kind, name, size.width, shown)
  }
})

// Edge label that wraps beyond a maximum width, on a white ground so lines never strike it.
#let wrap-label(t, max: 40mm) = context {
  set par(justify: false, leading: 0.3em)
  let c = text(size: uml-size, fill: ink, t)
  let w = measure(c).width
  box(fill: white, inset: (x: 1.5pt, y: 1pt), if w > max { box(width: max, align(center, c)) } else { c })
}

// ---- Class diagrams ----------------------------------------------------------

// Widest member line before it wraps (with a hanging indent).
#let member-max = 56mm

// One class box: name compartment (role tint), then attribute and operation compartments,
// drawn even when empty, as Visual Paradigm does.
#let class-box(name, stereo: none, attrs: (), ops: (), abstract: false, max: member-max) = context {
  set text(size: uml-size, fill: ink)
  set par(leading: 0.3em, justify: false)
  let head = align(center, {
    if stereo != none { text("«" + stereo + "»"); linebreak() }
    text(weight: "bold", style: if abstract { "italic" } else { "normal" }, breakable(name))
  })
  let natural = calc.max(
    measure(text(size: uml-size, weight: "bold", name)).width,
    if stereo != none { measure(text(size: uml-size, "«" + stereo + "»")).width } else { 0pt },
    ..(attrs + ops).map(l => measure(text(size: uml-size, l)).width),
  )
  // Never narrower than the longest piece a member cannot break inside, plus the hanging indent.
  let pieces = (attrs + ops).map(l => member-breakable(l).split(regex("[\u{200B} ]"))).flatten()
  let unbreakable = calc.max(
    0pt,
    ..pieces.map(p => measure(text(size: uml-size, p)).width + 0.8 * uml-size),
    ..breakable(name).split("\u{200B}").map(p => measure(text(size: uml-size, weight: "bold", p)).width),
  )
  let inner = calc.max(calc.min(natural, max), calc.min(unbreakable, natural))
  // One paragraph per member, so a wrapped member continues under a hanging indent.
  let lines(items) = {
    set par(hanging-indent: 0.8em, spacing: 0.3em)
    items.map(member-breakable).join(parbreak())
  }
  let comp(items) = if items.len() > 0 { align(left, lines(items)) } else { v(1pt) }
  box(table(
    columns: inner + 9pt,
    inset: (x: 4.5pt, y: 2.5pt),
    stroke: w-box + ink,
    fill: (_, y) => if y == 0 { role-fill(stereo) } else { white },
    head, comp(attrs), comp(ops),
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
  draw: mark => cetz.draw.line((0, 0), (-mark.size, mark.size * 0.5), (-mark.size, -mark.size * 0.5), close: true, fill: white, stroke: w-line + ink),
)
#let rel-style = (
  "assoc": (dash: none, marks: (none, none)),
  "nav": (dash: none, marks: (none, ">")),
  "dep": (dash: "dashed", marks: (none, ">")),
  "dashed": (dash: "dashed", marks: (none, none)),
  "inherit": (dash: none, marks: (none, hollow-triangle)),
  "realize": (dash: "dashed", marks: (none, hollow-triangle)),
  "compose": (dash: none, marks: ((inherit: "<>", fill: ink, scale: 160%), none)),
  "aggregate": (dash: none, marks: ((inherit: "<>", fill: white, stroke: w-line + ink, scale: 160%), none)),
)

// classes: array of (id, name, stereo, attrs, ops, abstract, pos: (x, y))
// rels: array of (from, to, kind, label, mfrom, mto, bend)
// A wide or tall diagram first closes up its gaps, then wraps its members at narrower widths.
#let class-diagram(classes, rels, frame: none, min-gap: (0pt, 0pt)) = {
  let boxes(max) = classes.map(c => node(c.pos, class-box(c.name, stereo: c.stereo, attrs: c.attrs, ops: c.ops, abstract: c.abstract, max: max), name: label(c.id)))
  let edges = rels.map(r => {
    let style = rel-style.at(r.kind, default: rel-style.assoc)
    let args = (label(r.from), label(r.to))
    let bend = r.at("bend", default: 0deg)
    let mfrom = r.at("mfrom", default: none)
    let mto = r.at("mto", default: none)
    let out = ()
    out.push(edge(..args, marks: style.marks, stroke: (paint: ink, thickness: w-line, dash: style.dash), bend: bend))
    // Labels are drawn as separate invisible edges on layer 1, above the boxes, so a label on a
    // short or crowded edge can never be hidden under a box. A role or name label sits on the
    // line when the edge has no multiplicities, otherwise on its left, away from them.
    if r.label != none {
      out.push(edge(..args, "-", bend: bend, stroke: none, layer: 1, label: wrap-label(r.label), label-side: if mfrom == none and mto == none { center } else { left }, label-sep: 3pt, label-fill: none))
    }
    // Multiplicities sit beside the line near each end, on the right-hand side walking from
    // the source, on a white ground. Each sits its own (estimated) half width plus 3 pt from its
    // end, plus the length of the end's mark (diamond, triangle, arrowhead), so it never
    // reaches into the box or onto the mark, however short the edge.
    let mult(t, pos) = edge(..args, "-", bend: bend, stroke: none, layer: 1, label: box(fill: white, inset: 0.5pt, text(size: uml-size, t)), label-pos: pos, label-side: right, label-sep: 3pt, label-fill: none)
    // (On a vertical edge a multiplicity sits beside the line, so its half height counts.)
    let off(t) = if r.at("vert", default: false) { 0.6 * uml-size + 3pt } else { t.clusters().len() * 0.3 * uml-size + 3pt }
    let mark-from = if r.kind in ("compose", "aggregate") { 13pt } else { 0pt }
    let mark-to = if r.kind in ("inherit", "realize") { 11pt } else if r.kind in ("nav", "dep") { 5pt } else { 0pt }
    if mfrom != none { out.push(mult(mfrom, 0% + off(mfrom) + mark-from)) }
    if mto != none { out.push(mult(mto, 100% - off(mto) - mark-to)) }
    out
  }).flatten()
  uml-frame("class", frame, sp => {
    set text(size: uml-size, fill: ink)
    diagram(
      spacing: sp.gap,
      node-stroke: none,
      node-inset: 0pt,
      // Fletcher treats a near-square node as a circle and crops edges at that circle; boxes are rectangles.
      node-shape: rect,
      edge-stroke: w-line + ink,
      mark-scale: 100%,
      ..boxes(sp.max),
      ..edges,
    )
  }, {
  let base = (
    // Starts below the two loosest spacings: they added height without legibility.
    (gap: (11mm, 9mm), max: 46mm),
    (gap: (11mm, 6mm), max: 46mm),
    (gap: (11mm, 6mm), max: 38mm),
    (gap: (11mm, 6mm), max: 32mm),
    (gap: (11mm, 4mm), max: member-max),
    (gap: (9mm, 6mm), max: 38mm),
    (gap: (9mm, 4mm), max: 32mm),
    (gap: (7mm, 6mm), max: 38mm),
    (gap: (7mm, 6mm), max: 32mm),
    (gap: (6mm, 4mm), max: 32mm),
    // Edge labels and multiplicities need 9 to 11 mm between columns to stay legible.
  ).filter(c => c.gap.at(0) >= 9mm or rels.all(r => r.label == none and r.at("mfrom", default: none) == none and r.at("mto", default: none) == none))
  // First try every spacing widened to what the labels between neighbours need (min-gap,
  // from typst.mjs); only a drawing that cannot fit that way falls back to the plain spacings,
  // where its labels still print on top of the boxes.
  // Rows are widened alone next, since height is usually the cheaper direction.
  let roomy = base.map(c => (gap: (calc.max(c.gap.at(0), min-gap.at(0)), calc.max(c.gap.at(1), min-gap.at(1))), max: c.max))
  let tall = base.map(c => (gap: (c.gap.at(0), calc.max(c.gap.at(1), min-gap.at(1))), max: c.max))
  roomy + tall + base
  })
}

// ---- State diagrams ----------------------------------------------------------

// states: array of (id, name, kind: "state" | "start" | "end" | "choice", pos, body)
// trans: array of (from, to, label, bend)
// A wide diagram closes up its gaps, then wraps the internal transitions at narrower widths.
#let state-diagram(states, trans, frame: none) = uml-frame("stm", frame, sp => {
  set text(size: uml-size, fill: ink)
  diagram(
    spacing: sp.gap,
    edge-stroke: w-line + ink,
    mark-scale: 80%,
    ..states.map(s => {
      if s.kind == "start" {
        node(s.pos, none, shape: circle, radius: 4.5pt, fill: ink, stroke: none, name: label(s.id))
      } else if s.kind == "end" {
        node(s.pos, circle(radius: 3pt, fill: ink), shape: circle, radius: 5.5pt, stroke: w-box + ink, fill: white, inset: 0pt, name: label(s.id))
      } else if s.kind == "choice" {
        node(s.pos, none, shape: fletcher.shapes.diamond, width: 12pt, height: 12pt, stroke: w-box + ink, fill: white, name: label(s.id))
      } else {
        let body = s.at("body", default: none)
        let lines = if body != none { body.split("\n") } else { () }
        node(s.pos, context {
          set par(leading: 0.3em, justify: false)
          let natural = calc.max(measure(text(size: uml-size, weight: "bold", s.name)).width, ..lines.map(l => measure(text(size: uml-size, l)).width))
          box(width: calc.min(natural, sp.max), {
            align(center, text(weight: "bold", breakable(s.name)))
            if body != none {
              v(-1pt)
              line(length: 100%, stroke: w-thin + ink)
              v(-2pt)
              align(left, { set par(hanging-indent: 0.8em, spacing: 0.3em); lines.join(parbreak()) })
            }
          })
        }, shape: fletcher.shapes.rect, corner-radius: 6pt, width: auto, stroke: w-box + ink, fill: role-fill(none), inset: 5pt, name: label(s.id))
      }
    }),
    ..trans.map(t => edge(label(t.from), label(t.to), "-|>", label: if t.label != none { wrap-label(t.label) }, label-fill: none, bend: t.at("bend", default: 0deg))),
  )
}, (
  (gap: (24mm, 20mm), max: 90mm),
  (gap: (24mm, 20mm), max: 62mm),
  (gap: (16mm, 20mm), max: 62mm),
  (gap: (16mm, 20mm), max: 50mm),
  (gap: (16mm, 14mm), max: 50mm),
  (gap: (10mm, 10mm), max: 50mm),
))

// ---- Sequence diagrams -------------------------------------------------------

// Message arrowheads: filled for a synchronous call, open for asynchronous and reply messages.
#let arrow-head(x, y, dir, open: false) = {
  // dir = 1 points right, -1 points left
  let l = 6pt
  let h = 3pt
  if open {
    place(top + left, line(start: (x, y), end: (x - dir * l, y - h), stroke: w-line + ink))
    place(top + left, line(start: (x, y), end: (x - dir * l, y + h), stroke: w-line + ink))
  } else {
    place(top + left, polygon(fill: ink, stroke: none, (x, y), (x - dir * l, y - h), (x - dir * l, y + h)))
  }
}

#let stick(x, y) = {
  let s = w-line + ink
  place(top + left, dx: x - 3.5pt, dy: y, circle(radius: 3.5pt, stroke: s, fill: white))
  place(top + left, line(start: (x, y + 7pt), end: (x, y + 15pt), stroke: s))
  place(top + left, line(start: (x - 6pt, y + 10pt), end: (x + 6pt, y + 10pt), stroke: s))
  place(top + left, line(start: (x, y + 15pt), end: (x - 5pt, y + 22pt), stroke: s))
  place(top + left, line(start: (x, y + 15pt), end: (x + 5pt, y + 22pt), stroke: s))
}

// A note: white, muted outline, folded top-right corner.
// (Called inside the diagram's layout callback, which supplies the context for measure.)
#let note-box(width, body) = {
  let c = box(width: width, inset: (x: 4pt, y: 3pt), body)
  let h = measure(c).height
  let f = 6pt
  box(width: width, height: h, {
    place(top + left, polygon(fill: white, stroke: w-thin + muted, (0pt, 0pt), (width - f, 0pt), (width, f), (width, h), (0pt, h)))
    place(top + left, polygon(fill: white, stroke: w-thin + muted, (width - f, 0pt), (width - f, f), (width, f)))
    place(top + left, c)
  })
}

// Combined-fragment operator in its pentagon tag.
#let frag-tag(t) = {
  let c = text(weight: "bold", t)
  let m = measure(c)
  let w = m.width + 12pt
  let h = m.height + 5pt
  let cut = 4pt
  box(width: w, height: h, {
    place(top + left, polygon(fill: white, stroke: w-thin + muted, (0pt, 0pt), (w, 0pt), (w, h - cut), (w - cut, h), (0pt, h)))
    place(top + left, dx: 4pt, dy: 2.5pt, c)
  })
}

// parts: array of (id, name, kind: "actor" | "participant" | stereotype string, created)
// steps: array of dictionaries:
//   (t: "msg", from, to, label, style: "sync" | "async" | "return",
//      pre: open an activation on the sender first, act: open one on the receiver,
//      deact: close the sender's innermost activation, create: the message creates the receiver,
//      destroy: none | id of the participant destroyed at this message)
//   (t: "act", id, here)   (t: "deact", id, implicit)
//   (t: "frag", kind: "alt" | "opt" | "loop" | "par" | "critical" | "break" | "rect", cond, lo, hi, depth)
//   (t: "else", cond)   (t: "end")   (t: "note", over: (ids), text)
// A long interaction closes up its vertical gaps (gap from 1 down to 0) before its text shrinks.
#let sequence-diagram(parts, steps, frame: none) = uml-frame("sd", frame, landscape: false, fill: true, gap => {
  set text(size: uml-size, fill: ink)
  set par(leading: 0.3em, justify: false)
  layout(size => {
  let W = size.width
  let n = parts.len()
  let cw = W / n
  let idx = (:)
  for (i, p) in parts.enumerate() { idx.insert(p.id, i) }
  let px(id) = cw * (idx.at(id) + 0.5)
  let head(p) = {
    let stereo = if p.kind != "participant" and p.kind != "actor" { "«" + p.kind + "»" } else { none }
    let nm = text(weight: "bold", breakable(p.name))
    align(center + horizon, { if stereo != none { text(stereo); linebreak() }; nm })
  }
  let head-w = calc.min(cw - 6pt, 40mm)
  let head-h = calc.max(28pt, ..parts.filter(p => p.kind != "actor").map(p => measure(box(width: head-w - 6pt, head(p))).height + 9pt))
  let actor-h = calc.max(..parts.filter(p => p.kind == "actor").map(p => 24pt + measure(box(width: cw, align(center, text(weight: "bold", breakable(p.name))))).height), 0pt)
  let top-h = calc.max(head-h, actor-h)
  // Activation bars: per participant, a stack of open start offsets; closed bars collect in bars.
  let bar-w = 7pt
  let nest = 3.5pt
  let open = (:)
  for p in parts { open.insert(p.id, ()) }
  let bars = ()
  let level-x(id, k) = px(id) + k * nest
  // x of the side facing dir (1 = right, -1 = left) of the innermost of k open bars, or the
  // lifeline when k is 0.
  let edge-x(id, k, side) = if k == 0 { px(id) } else { level-x(id, k - 1) + side * bar-w / 2 }
  let starts = (:)
  let ends = (:)
  let heads = ()
  let back = ()
  let mid = ()
  let front = ()
  let frames = ()
  let y = top-h + 6pt + 6pt * gap
  let last-y = y
  let last-act = false
  let label-box(c) = box(fill: white, inset: (x: 1.5pt, y: 1pt), c)
  // Widen the open fragments (outermost first) so a label from l to r stays inside each of
  // them, keeping 5 pt between nested outlines.
  let widen(frames, l, r) = frames.enumerate().map(((i, f)) => {
    let k = frames.len() - 1 - i
    f.x1 = calc.min(W - 1pt - i * 5pt, calc.max(f.x1, r + 4pt + k * 5pt))
    f.x0 = calc.max(1pt + i * 5pt, calc.min(f.x0, l - 4pt - k * 5pt))
    f
  })
  // A bar shorter than this is a stub; the row after a call that opens one starts below it,
  // so the next label never sits on the stub.
  let stub = 6pt
  // Place a guard between l and r where no open activation bar runs: the leftmost free stretch
  // wide enough for it on one line, else the widest free stretch (wrapping there), else the
  // whole width. open-now: the activation stacks at that moment. Returns (x, content).
  let place-guard(cond, l, r, open-now) = {
    let t = text(weight: "bold", "[" + cond + "]")
    let need = measure(t).width
    let blocked = parts.filter(p => open-now.at(p.id).len() > 0).map(p => {
      let k = open-now.at(p.id).len()
      (px(p.id) - bar-w / 2 - 2pt, level-x(p.id, k - 1) + bar-w / 2 + 2pt)
    }).sorted(key: b => b.at(0))
    let free = ()
    let at = l
    for (b0, b1) in blocked {
      if b0 > at { free.push((at, calc.min(b0, r))) }
      at = calc.max(at, b1)
    }
    if at < r { free.push((at, r)) }
    free = free.filter(f => f.at(1) > f.at(0))
    let fits = free.filter(f => f.at(1) - f.at(0) >= need + 4pt)
    if fits.len() > 0 { return (fits.first().at(0), box(width: need, t), true) }
    let widest = free.sorted(key: f => f.at(1) - f.at(0)).at(-1, default: none)
    if widest != none and widest.at(1) - widest.at(0) >= 60pt { return (widest.at(0), box(width: widest.at(1) - widest.at(0) - 3pt, t), false) }
    (l, box(width: calc.min(need, r - l), t), need <= r - l)
  }
  // A guard inside the fragment (l to r) when it fits there on one line, else up to rmax,
  // where the fragment then widens to hold it. Returns (x, content).
  let guard-in(cond, l, r, rmax, open-now) = {
    let (x, g, one) = place-guard(cond, l, r, open-now)
    if not one and rmax > r {
      let (x2, g2, one2) = place-guard(cond, l, rmax, open-now)
      if one2 { return (x2, g2) }
    }
    (x, g)
  }
  for s in steps {
    if s.t == "msg" {
      let from = s.from
      let to = s.to
      let lab = s.label
      let dashed = s.style == "return"
      let st = if dashed { (paint: ink, thickness: w-line, dash: (3pt, 2pt)) } else { w-line + ink }
      if from == to {
        let k = open.at(from).len()
        let x0 = edge-x(from, k, 1)
        let w = 16pt
        let drop = 10pt
        // The label goes right of the loop; when the frame's right edge leaves too little room,
        // it goes left of the lifeline instead, so it never runs out of the frame.
        let natural = measure(label-box(lab)).width
        let room = W - x0 - w - 4pt
        let xl = px(from) - bar-w / 2 - 3pt
        let on-right = natural <= room or room >= calc.min(80pt, xl)
        let lw = if on-right { calc.min(calc.max(cw * 1.9, 70pt), room) } else { calc.min(natural, calc.max(cw * 1.9, 70pt), xl) }
        let lb = box(width: lw, align(if on-right { left } else { right }, label-box(lab)))
        let lh = measure(lb).height
        let lx = if on-right { x0 + w + 3pt } else { xl - lw }
        y += calc.max(0pt, lh - drop - 2pt) + 2pt
        // Nested activation for the self call, at the next level.
        let xin = level-x(from, k) + bar-w / 2
        mid.push(place(top + left, line(start: (x0, y), end: (x0 + w, y), stroke: st)))
        mid.push(place(top + left, line(start: (x0 + w, y), end: (x0 + w, y + drop), stroke: st)))
        mid.push(place(top + left, line(start: (x0 + w, y + drop), end: (xin + 0.5pt, y + drop), stroke: st)))
        mid.push(arrow-head(xin, y + drop, -1, open: s.style != "sync"))
        bars.push((x: level-x(from, k), y0: y + drop - 3pt, y1: y + drop + 4pt))
        front.push(place(top + left, dx: lx, dy: y - 2pt, lb))
        frames = if on-right { widen(frames, x0 - 8pt, lx + calc.min(natural, lw)) } else { widen(frames, xl - calc.min(natural, lw), x0 + w + 2pt) }
        last-y = y + drop
        last-act = true
        y += calc.max(lh, drop) + 4pt + 5pt * gap
      } else {
        let dir = if px(to) > px(from) { 1 } else { -1 }
        if s.at("pre", default: false) { open.at(from).push(y) }
        let x1 = edge-x(from, open.at(from).len(), dir)
        let x2 = if s.at("create", default: false) { px(to) - dir * head-w / 2 } else if s.at("act", default: false) { edge-x(to, open.at(to).len() + 1, -dir) } else { edge-x(to, open.at(to).len(), -dir) }
        // The label is centred over the arrow when it fits between the two ends; otherwise it
        // starts just past the left end (clear of that activation bar) and runs right, wrapping
        // at 2.4 lifeline gaps.
        let span = calc.abs(x2 - x1) - 8pt
        let lx = calc.min(x1, x2) + 4pt
        let natural = measure(label-box(lab)).width
        let (a, b) = if natural <= span { (lx, lx + span) } else { (lx, calc.min(W, lx + calc.max(span, cw * 2.4))) }
        if b - a < natural and b == W { a = calc.max(0pt, W - calc.max(span, cw * 2.4)) }
        let lb = box(width: b - a, align(if natural <= span { center } else { left }, label-box(lab)))
        let lh = measure(lb).height
        // Open fragments widen to keep the label inside them.
        let lr = if natural <= span { b } else { calc.min(b, a + natural) }
        frames = widen(frames, a, lr)
        y += lh + 1pt
        if s.at("create", default: false) {
          // The created participant's head sits on the message, its lifeline starts below it.
          heads.push((p: s.to, y: y - (head-h - 4pt) / 2))
          starts.insert(s.to, y + (head-h - 4pt) / 2)
        }
        mid.push(place(top + left, line(start: (x1, y), end: (x2 - dir * 0.5pt, y), stroke: st)))
        mid.push(arrow-head(x2, y, dir, open: s.style != "sync"))
        front.push(place(top + left, dx: a, dy: y - lh - 1pt, lb))
        last-y = y
        if s.at("act", default: false) { open.at(to).push(y) }
        if s.at("deact", default: false) and open.at(from).len() > 0 {
          let y0 = open.at(from).pop()
          bars.push((x: level-x(from, open.at(from).len()), y0: y0, y1: calc.max(y, y0 + stub)))
        }
        if s.at("destroy", default: none) != none {
          let d = s.destroy
          while open.at(d).len() > 0 {
            let y0 = open.at(d).pop()
            bars.push((x: level-x(d, open.at(d).len()), y0: y0, y1: calc.max(y, y0 + stub)))
          }
          ends.insert(d, y)
          let c = 5pt
          front.push(place(top + left, line(start: (px(d) - c, y - c), end: (px(d) + c, y + c), stroke: 1.2pt + ink)))
          front.push(place(top + left, line(start: (px(d) - c, y + c), end: (px(d) + c, y - c), stroke: 1.2pt + ink)))
        }
        y += (if s.at("create", default: false) { (head-h - 4pt) / 2 } else { 0pt }) + 2.5pt + 3.5pt * gap
        // A call that opens an activation leaves room for its stub before the next label.
        last-act = s.at("act", default: false)
        if last-act { y = calc.max(y, last-y + stub + 2pt) }
      }
    } else if s.t == "act" {
      open.at(s.id).push(if s.at("here", default: false) { y - 4pt } else { last-y })
    } else if s.t == "deact" {
      if open.at(s.id).len() > 0 {
        let y0 = open.at(s.id).pop()
        let y1 = if s.at("implicit", default: false) { last-y + 4pt } else { last-y }
        bars.push((x: level-x(s.id, open.at(s.id).len()), y0: y0, y1: calc.max(y1, y0 + stub)))
      }
    } else if s.t == "frag" {
      let d = s.at("depth", default: 0) * 5pt
      let lo = s.at("lo", default: 0)
      let hi = s.at("hi", default: n - 1)
      let x0 = calc.max(cw * lo + 2pt, 1pt + d) + (if lo == 0 { d } else { 0pt })
      let x1 = calc.min(cw * (hi + 1) - 2pt, W - 1pt - d) - (if hi == n - 1 { d } else { 0pt })
      let tag = if s.kind == "rect" { none } else { frag-tag(s.kind) }
      let tw = if tag != none { measure(tag).width } else { 0pt }
      let th = if tag != none { measure(tag).height } else { 0pt }
      // The guard keeps clear of the activation bars running through the header row, and the
      // first message starts 4 pt below it.
      let (gx, g) = if s.cond != none and s.cond != "" { guard-in(s.cond, x0 + tw + 6pt, x1 - 4pt, W - 5pt - d, open) } else { (0pt, none) }
      let gh = if g != none { measure(g).height } else { 0pt }
      // A guard that needed more room widens this fragment and the ones around it.
      let gr = if g != none { gx + measure(g).width + 3pt } else { x1 }
      frames = widen(frames, gx, gr)
      frames.push((y: y, kind: s.kind, x0: x0, x1: calc.max(x1, gr + 4pt), tag: tag, g: g, gx: gx, tw: tw, elses: ()))
      y += calc.max(th, gh + 5pt) + 4pt + 3pt * gap
    } else if s.t == "else" {
      let f = frames.pop()
      let (gx, g) = if s.cond != none and s.cond != "" { guard-in(s.cond, f.x0 + 5pt, f.x1 - 4pt, W - 5pt - frames.len() * 5pt, open) } else { (0pt, none) }
      let gh = if g != none { measure(g).height } else { 0pt }
      if g != none {
        let gr = gx + measure(g).width + 3pt
        frames = widen(frames, gx, gr)
        f.x1 = calc.max(f.x1, gr + 4pt)
      }
      y += 2pt
      f.elses.push((y: y, g: g, gx: gx))
      frames.push(f)
      y += gh + 9pt + 3pt * gap
    } else if s.t == "end" {
      let f = frames.pop()
      // The bottom edge clears the stub of the last call inside.
      if last-act { y = calc.max(y, last-y + stub + 4pt) }
      let y1 = y - 2pt
      if f.kind == "rect" {
        back.insert(0, place(top + left, dx: f.x0, dy: f.y, rect(width: f.x1 - f.x0, height: y1 - f.y, stroke: none, fill: rgb("#F1F4FD"))))
      } else {
        back.push(place(top + left, dx: f.x0, dy: f.y, rect(width: f.x1 - f.x0, height: y1 - f.y, stroke: w-thin + muted)))
        front.push(place(top + left, dx: f.x0, dy: f.y, f.tag))
        if f.g != none { front.push(place(top + left, dx: f.gx, dy: f.y + 3pt, label-box(f.g))) }
      }
      for e in f.elses {
        back.push(place(top + left, line(start: (f.x0, e.y), end: (f.x1, e.y), stroke: (paint: muted, thickness: w-thin, dash: (4pt, 3pt)))))
        if e.g != none { front.push(place(top + left, dx: e.gx, dy: e.y + 3pt, label-box(e.g))) }
      }
      // Nested fragments that end together keep their bottoms 5 pt apart.
      y += 5pt + 3pt * gap
    } else if s.t == "note" {
      let xs = s.over.map(px)
      let a = calc.max(calc.min(..xs) - cw * 0.42, 0pt)
      let b = calc.min(calc.max(..xs) + cw * 0.42, W)
      let body = note-box(b - a, s.text)
      let h = measure(body).height
      front.push(place(top + left, dx: a, dy: y, body))
      y += h + 5pt + 5pt * gap
    }
  }
  // Close what is still open at the end of the interaction.
  for p in parts {
    while open.at(p.id).len() > 0 {
      let y0 = open.at(p.id).pop()
      bars.push((x: level-x(p.id, open.at(p.id).len()), y0: y0, y1: calc.max(last-y + 4pt, y0 + stub)))
    }
  }
  let H = y + 2pt
  box(width: W, height: H, {
    for b in back { b }
    for (i, p) in parts.enumerate() {
      let x = px(p.id)
      let y0 = starts.at(p.id, default: if p.kind == "actor" { actor-h + 2pt } else { head-h - 4pt })
      place(top + left, line(start: (x, y0), end: (x, ends.at(p.id, default: H)), stroke: (paint: muted, thickness: w-thin, dash: (4pt, 3pt))))
    }
    for b in bars {
      place(top + left, dx: b.x - bar-w / 2, dy: b.y0, rect(width: bar-w, height: b.y1 - b.y0, fill: white, stroke: w-line + ink))
    }
    for m in mid { m }
    for (i, p) in parts.enumerate() {
      let x = px(p.id)
      if p.kind == "actor" {
        stick(x, 0pt)
        place(top + left, dx: x - cw / 2, dy: 24pt, box(width: cw, align(center, text(weight: "bold", breakable(p.name)))))
      } else {
        let hy = heads.find(h => h.p == p.id)
        if p.at("created", default: false) and hy == none { continue }
        let b = box(width: head-w, height: head-h - 4pt, stroke: w-box + ink, fill: role-fill(if p.kind == "participant" { none } else { p.kind }), inset: 3pt, head(p))
        place(top + left, dx: x - head-w / 2, dy: if hy != none { hy.y } else { 0pt }, b)
      }
    }
    for f in front { f }
  })
  })
}, (0.0,)) // tightest row spacing only: labels stay 8.5 pt, the drawing is about 12 % shorter
