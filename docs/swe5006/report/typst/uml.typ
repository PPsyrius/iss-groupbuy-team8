// UML drawing kit for the reports: class, state, sequence, component, deployment, use-case and
// activity diagrams, all drawn to look like the deck's Visual Paradigm-style UML
// (docs/presentation-kit/diagrams/STYLE.md and the deck's src/diagrams/*.svg). One kit draws
// every UML diagram of the SWE5006 report and the SWE5001 report. scripts/typst.mjs translates
// Mermaid (and the Mermaid-like componentDiagram, deploymentDiagram, usecaseDiagram and
// activityDiagram, whose grammar docs/swe5001/report/diagrams/appendix-a/README.md documents)
// into calls to the diagram functions at the end of each part below. Frame headings: "class",
// "sd", "stm", "cmp", "deployment", "uc", "act".
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
// "sd <name>", "class <name>", "stm <name>", "cmp <name>", "deployment <name>", "uc <name>" or
// "act <name>".

// Height of the frame for a diagram without a report-figure host; inside a report figure the
// frame takes the portrait height the figure allows it (host.height, set in template.typ), and
// this height only chooses its spacing (uml-frame).
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
// before its text shrinks; the portrait choice is made at max-figure and then scaled to the
// figure's own height (uml-frame). A drawing that would still have to be scaled below the 8 pt minimum
// turns a quarter (landscape) when that scales it less. fill: the drawing takes the full width
// (sequence diagrams); otherwise it is measured at its natural width.
// host: set by report-figure in template.typ (the translator passes it to the diagram call).
// Inside a report figure the frame fits the figure's portrait width, and its landscape room is
// the rotated page less the figure's caption: a frame that turns turns the whole figure, caption
// included. report-figure first lays the body out as a probe (probe: true), where the frame
// returns only its decision, a block 1 pt tall if it turns and 0 pt if not; a turned figure is
// then set on a landscape page (rotated: true), where the frame is drawn upright at its
// landscape scale. Without a host (a diagram outside a figure) only the drawing turns.
// Each frame records its kind, name and final scale as <uml-scale> metadata, so a build can
// list the figures whose labels print below 8 pt (typst query ... "<uml-scale>").
#let uml-frame(kind, name, build, candidates, landscape: true, fill: false, host: none) = layout(size => context {
  let fig = host
  let th = measure(frame-title(kind, name)).height + 7pt
  let px = 2 * frame-pad.x + 2 * w-line
  let py = th + frame-pad.top + frame-pad.bottom + 2 * w-line
  let W = (if fig != none { fig.width } else { size.width }) - px
  let H = (if fig != none { fig.height } else { max-figure }) - py
  // Landscape: the drawing's width runs along the page height, its height across the page.
  let LW = if fig != none { fig.land.width - px } else { H }
  let LH = if fig != none { fig.land.height - py } else { size.width - py }
  // cap: the candidate's scale under the fixed max-figure height. The portrait spacing is
  // chosen by it, so a frame keeps the spacing it has under max-figure; the figure's own
  // portrait height (H) then only sets the scale, which is never smaller than under max-figure.
  let Hc = calc.min(H, max-figure - py)
  let fits = ()
  for sp in candidates {
    let m = if fill { measure(build(sp), width: W) } else { measure(build(sp)) }
    fits.push((m: m, portrait: calc.min(1.0, W / m.width, H / m.height), land: calc.min(1.0, LW / m.width, LH / m.height), cap: calc.min(1.0, W / m.width, Hc / m.height)))
    if fits.last().cap >= 1.0 { break }
  }
  let pick(key) = {
    let target = calc.min(1.0, calc.max(..fits.map(f => f.at(key)))) - 0.02
    fits.position(f => f.at(key) >= target)
  }
  let i = pick("cap")
  let j = pick("land")
  let turn = landscape and fits.at(i).portrait < min-scale and fits.at(j).land > fits.at(i).portrait + 0.03
  let k = if turn { j } else { i }
  let s = if turn { fits.at(k).land } else { fits.at(k).portrait }
  if fig != none and fig.probe {
    block(width: 0pt, height: if turn { 1pt } else { 0pt })
  } else {
    let drawing = build(candidates.at(k))
    let shown = if s < 1.0 { scale(s * 100%, reflow: true, drawing) } else { drawing }
    [#metadata((kind: kind, name: name, scale: s, landscape: turn)) <uml-scale>]
    if turn and fig != none and fig.rotated {
      align(center, draw-frame(kind, name, fits.at(k).m.width * s + px, shown))
    } else if turn {
      align(center, rotate(-90deg, reflow: true, draw-frame(kind, name, fits.at(k).m.width * s + px, shown)))
    } else {
      draw-frame(kind, name, size.width, shown)
    }
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
#let class-diagram(classes, rels, frame: none, min-gap: (0pt, 0pt), host: none) = {
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
    // An end may be moved to the left (sfrom / sto) where a diagonal edge reaches a box corner.
    let mult(t, pos, side: right) = edge(..args, "-", bend: bend, stroke: none, layer: 1, label: box(fill: white, inset: 0.5pt, text(size: uml-size, t)), label-pos: pos, label-side: side, label-sep: 3pt, label-fill: none)
    // (On a vertical edge a multiplicity sits beside the line, so its half height counts.)
    let off(t) = if r.at("vert", default: false) { 0.6 * uml-size + 3pt } else { t.clusters().len() * 0.3 * uml-size + 3pt }
    let mark-from = if r.kind in ("compose", "aggregate") { 13pt } else { 0pt }
    let mark-to = if r.kind in ("inherit", "realize") { 11pt } else if r.kind in ("nav", "dep") { 5pt } else { 0pt }
    if mfrom != none { out.push(mult(mfrom, 0% + off(mfrom) + mark-from, side: r.at("sfrom", default: right))) }
    if mto != none { out.push(mult(mto, 100% - off(mto) - mark-to, side: r.at("sto", default: right))) }
    out
  }).flatten()
  uml-frame("class", frame, host: host, sp => {
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
#let state-diagram(states, trans, frame: none, host: none) = uml-frame("stm", frame, host: host, sp => {
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
    ..trans.map(t => edge(label(t.from), label(t.to), "-|>", bend: t.at("bend", default: 0deg))),
    // Transition labels sit on their line (white ground) on layer 1, above the states, so none
    // is hidden under a box or pushed off the curve onto a neighbour.
    ..trans.filter(t => t.label != none).map(t => edge(label(t.from), label(t.to), "-", stroke: none, layer: 1, label: wrap-label(t.label, max: 34mm), label-side: center, label-fill: none, bend: t.at("bend", default: 0deg),
      // The two arcs of a there-and-back pair carry their labels before their midpoints, so
      // the labels sit at different heights instead of side by side.
      label-pos: if t.at("bend", default: 0deg) != 0deg { 0.38 } else { 0.5 })),
  )
}, (
  // A choice gathers many labelled transitions around one point: give it more room first.
  ..if states.any(s => s.kind == "choice") { ((gap: (24mm, 32mm), max: 90mm),) } else { () },
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
#let sequence-diagram(parts, steps, frame: none, host: none) = uml-frame("sd", frame, landscape: false, fill: true, host: host, gap => {
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
  // whole width. open-now: the activation stacks at that moment. Returns (x, content, how):
  // how is "one" (on one line), "wrap" (wrapped in a free stretch) or "over" (wrapped across
  // the whole width, over any bars there).
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
    if fits.len() > 0 { return (fits.first().at(0), box(width: need, t), "one") }
    let widest = free.sorted(key: f => f.at(1) - f.at(0)).at(-1, default: none)
    if widest != none and widest.at(1) - widest.at(0) >= 60pt { return (widest.at(0), box(width: widest.at(1) - widest.at(0) - 3pt, t), "wrap") }
    (l, box(width: calc.min(need, r - l), t), if need <= r - l { "one" } else { "over" })
  }
  // A guard inside the fragment (l to r) when it fits there on one line, else up to rmax,
  // where the fragment then widens to hold it. A guard that has to wrap wraps in a free stretch
  // (inside the fragment if one is wide enough, else up to rmax), so a wrapped guard's white
  // ground never hides an activation bar; only when no stretch is wide enough does it run over
  // the bars.
  // Returns (x, content).
  let guard-in(cond, l, r, rmax, open-now) = {
    let (x, g, how) = place-guard(cond, l, r, open-now)
    if how != "one" and rmax > r {
      let (x2, g2, how2) = place-guard(cond, l, rmax, open-now)
      if how2 == "one" or (how == "over" and how2 == "wrap") { return (x2, g2) }
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
        // A «create» label stops at the created head, which sits on the arrow: it wraps inside
        // the span whenever the span can hold a word or two.
        let fit = natural <= span or (s.at("create", default: false) and span >= 60pt)
        let (a, b) = if fit { (lx, lx + span) } else { (lx, calc.min(W, lx + calc.max(span, cw * 2.4))) }
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

// ---- Component, deployment, use-case and activity diagrams -------------------
//
// One grid engine draws the four kinds, in the look of the class and state diagrams above: the
// same palette, role tints, strokes, label size, frame and scaling. scripts/typst.mjs places every
// element in a grid cell (col, row: pins or its automatic layout) and gives every group the cell
// range of what it holds; here each column takes the width of its widest element and each row the
// height of its tallest, and the gaps between them widen for the borders and titles of the groups
// that start or end there (nested groups stack their insets) and for the edge labels between
// neighbours. Edges are straight, or orthogonal with one or two bends (route).
//
// elems:  array of (id, k, name, stereo, sub, tint, col, row, lab, ext), sub: a second, regular
//         line under the name ("core subdomain"); k one of component, interface,
//         actor, artifact, node, usecase, note, start, end, flowend, action, decision, merge, fork,
//         join, object; tint: none (from the stereotype) or platform | business | messaging |
//         neutral; lab: where an interface's name goes (below, above, right); ext: a use case's
//         extension points.
// groups: array of (id, k, name, stereo, sub, tint, c0, r0, c1, r1, path), k one of package, rectangle,
//         zone, node (3-D), component; c0..r1: the cells the group holds; path: its ancestors.
// lanes:  array of (name, a0, a1): activity partitions over columns a0..a1 (dir TB) or rows (LR).
// edges:  array of (from, to, k, label, ma, mb, route), ma/mb: end labels (multiplicities, or
//         "U"/"D" on a context map); k one of assoc, nav, dep, dashed, inherit,
//         realize, provides (to an interface: lollipop), requires (to an interface: socket);
//         route: straight, hv, vh, vhv, hvh or auto.

#let s-depth = 6.0 // 3-D depth of a node, pt
#let s-pad = 6.0 // inset of a group around what it holds, pt
#let s-socket = 7.5 // radius of a required interface's socket, pt
#let s-ball = 4.5 // radius of a provided interface's ball, pt
#let s-tint-key(stereo, tint) = if tint != none { (platform: "boundary", business: "control", messaging: "messaging", neutral: "neutral").at(tint, default: "neutral") } else { role-of(stereo) }
#let s-fill(stereo, tint) = gradient.linear(..tints.at(s-tint-key(stereo, tint)), dir: ttb)
// A container (a node or component that holds others) takes a pale, flat tint, so the elements
// inside it keep their own gradient.
#let s-pale(stereo, tint) = tints.at(s-tint-key(stereo, tint)).at(1).lighten(45%)
#let pt-of(l) = l / 1pt

// Name block: optional «stereotype» line, then the bold name, wrapping at max (a name may also
// break at "\n"). Called inside a context.
#let s-text(name, stereo: none, sub: none, max: 36mm, bold: true, al: center) = {
  let w8 = if bold { "bold" } else { "regular" }
  if type(name) != str {
    return box(width: max, { set par(leading: 0.3em, justify: false); align(al, name) })
  }
  let parts = name.split("\n")
  // A display name with spaces breaks only there; one long identifier at its camel case.
  let brk(p) = if p.contains(" ") { p } else { breakable(p) }
  let st = if stereo != none { text(size: uml-size, "«" + stereo + "»") }
  let sb = if sub != none { text(size: uml-size, sub) }
  let natural = calc.max(0pt, ..parts.map(p => measure(text(size: uml-size, weight: w8, p)).width), if st != none { measure(st).width } else { 0pt }, if sb != none { measure(sb).width } else { 0pt })
  let pieces = parts.map(p => brk(p).split(regex("[\u{200B} ]"))).flatten()
  let unbreak = calc.max(0pt, ..pieces.map(p => measure(text(size: uml-size, weight: w8, p)).width))
  box(width: calc.max(calc.min(natural, max), unbreak), {
    set par(leading: 0.3em, justify: false)
    align(al, {
      if st != none { st; linebreak() }
      text(weight: w8, parts.map(brk).join(linebreak()))
      if sb != none { linebreak(); sb }
    })
  })
}

// The UML component icon (a box with two tabs) and the artifact icon (a page with a folded corner).
#let comp-icon = box(width: 11pt, height: 9pt, {
  place(top + left, dx: 2.5pt, rect(width: 8.5pt, height: 9pt, stroke: w-thin + ink, fill: white))
  place(top + left, dy: 1.6pt, rect(width: 5pt, height: 2.2pt, stroke: w-thin + ink, fill: white))
  place(top + left, dy: 5.2pt, rect(width: 5pt, height: 2.2pt, stroke: w-thin + ink, fill: white))
})
#let doc-icon = box(width: 8pt, height: 10pt, {
  place(top + left, polygon(fill: white, stroke: w-thin + ink, (0pt, 0pt), (5pt, 0pt), (8pt, 3pt), (8pt, 10pt), (0pt, 10pt)))
  place(top + left, polygon(fill: white, stroke: w-thin + ink, (5pt, 0pt), (5pt, 3pt), (8pt, 3pt)))
})

// A 3-D node: the front face w x h with its top and right faces d deep, in a box (w + d) x (h + d).
#let cube(w, h, fill, top-fill, d: s-depth * 1pt, stroke: w-box + ink) = box(width: w + d, height: h + d, {
  place(top + left, polygon(fill: top-fill, stroke: stroke, (0pt, d), (d, 0pt), (w + d, 0pt), (w, d)))
  place(top + left, polygon(fill: top-fill, stroke: stroke, (w, d), (w + d, 0pt), (w + d, h), (w, h + d)))
  place(top + left, dy: d, rect(width: w, height: h, fill: fill, stroke: stroke))
})

// One element: its drawing (body, a box w x h, in pt) and its anchor, the outline edges attach
// to: shape (rect, ellipse, circle, diamond, bar), centre offset (ax, ay) from the box centre and
// half sizes (aw, ah). Called inside a context.
#let s-elem(e, sp, lr) = {
  let k = e.k
  let max = sp.max
  let res(body, w, h, shape: "rect", ax: 0.0, ay: 0.0, aw: none, ah: none) = (body: body, w: w, h: h, shape: shape, ax: ax, ay: ay, aw: if aw == none { w / 2 } else { aw }, ah: if ah == none { h / 2 } else { ah })
  if k == "component" or k == "artifact" {
    let t = s-text(e.name, stereo: e.stereo, sub: e.at("sub", default: none), max: max)
    let m = measure(t)
    let icon = if k == "component" { comp-icon } else { doc-icon }
    let w = calc.max(pt-of(m.width) + 2 * 19, if k == "component" { 74.0 } else { 62.0 })
    let h = calc.max(pt-of(m.height) + 13, 30.0)
    res(box(width: w * 1pt, height: h * 1pt, {
      place(top + left, rect(width: w * 1pt, height: h * 1pt, stroke: w-box + ink, fill: s-fill(e.stereo, e.tint)))
      place(top + right, dx: -4.5pt, dy: 4.5pt, icon)
      place(center + horizon, t)
    }), w, h)
  } else if k == "node" {
    let t = s-text(e.name, stereo: e.stereo, sub: e.at("sub", default: none), max: max)
    let m = measure(t)
    let w = calc.max(pt-of(m.width) + 16, 68.0)
    let h = calc.max(pt-of(m.height) + 14, 32.0)
    let d = s-depth
    res(box(width: (w + d) * 1pt, height: (h + d) * 1pt, {
      place(top + left, cube(w * 1pt, h * 1pt, s-fill(e.stereo, e.tint), tints.at(s-tint-key(e.stereo, e.tint)).at(1)))
      place(top + left, dy: d * 1pt, box(width: w * 1pt, height: h * 1pt, align(center + horizon, t)))
    }), w + d, h + d, ax: -d / 2, ay: d / 2, aw: w / 2, ah: h / 2)
  } else if k == "actor" {
    let t = s-text(e.name, stereo: e.stereo, max: 28mm)
    let m = measure(t)
    let w = calc.max(pt-of(m.width), 16.0)
    let h = 25 + pt-of(m.height)
    res(box(width: w * 1pt, height: h * 1pt, {
      stick(w / 2 * 1pt, 0pt)
      place(top + left, dy: 25pt, box(width: w * 1pt, align(center, t)))
    }), w, h)
  } else if k == "interface" {
    let t = s-text(e.name, max: 30mm)
    let m = measure(t)
    let (tw, th) = (pt-of(m.width), pt-of(m.height))
    let r = s-ball
    let near = s-socket + 1.5
    let far = s-socket + 2.5
    let ball(x, y) = place(top + left, dx: (x - r) * 1pt, dy: (y - r) * 1pt, circle(radius: r * 1pt, fill: white, stroke: w-box + ink))
    if e.lab == "right" {
      let w = 2 * (far + tw)
      let h = calc.max(th, 2 * near)
      res(box(width: w * 1pt, height: h * 1pt, {
        ball(w / 2, h / 2)
        place(top + left, dx: (w / 2 + far) * 1pt, dy: (h - th) / 2 * 1pt, t)
      }), w, h, shape: "circle", aw: r, ah: r)
    } else {
      let w = calc.max(tw, 2 * near)
      let h = near + far + th
      let cy = if e.lab == "above" { far + th } else { near }
      res(box(width: w * 1pt, height: h * 1pt, {
        ball(w / 2, cy)
        place(top + left, dx: (w - tw) / 2 * 1pt, dy: (if e.lab == "above" { 0 } else { cy + far }) * 1pt, t)
      }), w, h, shape: "circle", ay: cy - h / 2, aw: r, ah: r)
    }
  } else if k == "usecase" {
    let t = s-text(e.name, stereo: e.stereo, sub: e.at("sub", default: none), max: calc.min(max, 34mm))
    let ext = e.at("ext", default: ())
    let et = if ext.len() > 0 {
      box({
        set par(leading: 0.3em, justify: false)
        align(left, { text(style: "italic", "extension points"); linebreak(); ext.join(linebreak()) })
      })
    }
    let mt = measure(t)
    let me = if et != none { measure(et) } else { (width: 0pt, height: 0pt) }
    let iw = pt-of(calc.max(mt.width, me.width))
    let ih = pt-of(mt.height) + if et != none { 5 + pt-of(me.height) } else { 0 }
    let a = iw / 2 * 1.2 + 6
    let b = calc.max((ih / 2) / calc.sqrt(1 - calc.pow(iw / 2 / a, 2)) + 5, 13.0)
    let (w, h) = (2 * a, 2 * b)
    let ty = (h - ih) / 2
    res(box(width: w * 1pt, height: h * 1pt, {
      place(top + left, ellipse(width: w * 1pt, height: h * 1pt, fill: s-fill(e.stereo, e.tint), stroke: w-box + ink))
      place(top + left, dx: (w - pt-of(mt.width)) / 2 * 1pt, dy: ty * 1pt, t)
      if et != none {
        let yd = ty + pt-of(mt.height) + 2.5
        let half = a * calc.sqrt(calc.max(0, 1 - calc.pow((yd - b) / b, 2)))
        place(top + left, line(start: ((a - half) * 1pt, yd * 1pt), end: ((a + half) * 1pt, yd * 1pt), stroke: w-thin + ink))
        place(top + left, dx: (w - pt-of(me.width)) / 2 * 1pt, dy: (yd + 2.5) * 1pt, et)
      }
    }), w, h, shape: "ellipse")
  } else if k == "note" {
    let body = if type(e.name) == str { e.name.split("\n").join(linebreak()) } else { e.name }
    let natural = measure(text(size: uml-size, body)).width
    let w = calc.min(natural + 14pt, 46mm)
    let n = note-box(w, { set par(leading: 0.3em, justify: false); body })
    let m = measure(n)
    res(n, pt-of(m.width), pt-of(m.height))
  } else if k == "start" {
    res(box(width: 10pt, height: 10pt, circle(radius: 5pt, fill: ink, stroke: none)), 10.0, 10.0, shape: "circle")
  } else if k == "end" {
    res(box(width: 12pt, height: 12pt, {
      place(top + left, circle(radius: 6pt, fill: white, stroke: w-box + ink))
      place(top + left, dx: 2.5pt, dy: 2.5pt, circle(radius: 3.5pt, fill: ink, stroke: none))
    }), 12.0, 12.0, shape: "circle")
  } else if k == "flowend" {
    let c = 6 * calc.sqrt(0.5)
    res(box(width: 12pt, height: 12pt, {
      place(top + left, circle(radius: 6pt, fill: white, stroke: w-box + ink))
      place(top + left, line(start: ((6 - c) * 1pt, (6 - c) * 1pt), end: ((6 + c) * 1pt, (6 + c) * 1pt), stroke: w-box + ink))
      place(top + left, line(start: ((6 - c) * 1pt, (6 + c) * 1pt), end: ((6 + c) * 1pt, (6 - c) * 1pt), stroke: w-box + ink))
    }), 12.0, 12.0, shape: "circle")
  } else if k == "decision" or k == "merge" {
    let r = 8.0
    let dia = polygon(fill: white, stroke: w-box + ink, (r * 1pt, 0pt), (2 * r * 1pt, r * 1pt), (r * 1pt, 2 * r * 1pt), (0pt, r * 1pt))
    let has = k == "decision" and e.name != none and e.name != "" and e.name != e.id
    if not has {
      res(box(width: 2 * r * 1pt, height: 2 * r * 1pt, place(top + left, dia)), 2 * r, 2 * r, shape: "diamond")
    } else {
      // The question sits above and to the left of the diamond, clear of the edges that leave
      // its top, left and right corners.
      let t = s-text(e.name, max: 30mm, al: right)
      let m = measure(t)
      let (tw, th) = (pt-of(m.width), pt-of(m.height))
      let w = 2 * calc.max(r, tw + 6)
      let h = 2 * calc.max(r, th + 6)
      res(box(width: w * 1pt, height: h * 1pt, {
        place(top + left, dx: (w / 2 - r) * 1pt, dy: (h / 2 - r) * 1pt, dia)
        place(top + left, dx: (w / 2 - 6 - tw) * 1pt, dy: (h / 2 - 6 - th) * 1pt, t)
      }), w, h, shape: "diamond", aw: r, ah: r)
    }
  } else if k == "fork" or k == "join" {
    // Drawn later, once the bar's span is known (s-diagram).
    let (w, h) = if lr { (4.0, 30.0) } else { (30.0, 4.0) }
    res(none, w, h, shape: "bar")
  } else {
    // action (rounded) or object (square corners); a «stereotype» shows above the name.
    let t = s-text(e.name, stereo: e.stereo, sub: e.at("sub", default: none), max: max)
    let m = measure(t)
    let w = calc.max(pt-of(m.width) + 14, if k == "action" { 56.0 } else { 48.0 })
    let h = calc.max(pt-of(m.height) + 10, 22.0)
    res(box(width: w * 1pt, height: h * 1pt, {
      place(top + left, rect(width: w * 1pt, height: h * 1pt, radius: if k == "action" { 6pt } else { 0pt }, stroke: w-box + ink, fill: s-fill(e.stereo, e.tint)))
      place(center + horizon, t)
    }), w, h)
  }
}

// A group's title: «stereotype» and name, top left (a use case's system boundary: centred).
#let s-group-title(g, kind) = {
  let t = s-text(g.name, stereo: g.stereo, sub: g.at("sub", default: none), max: 70mm, al: if kind == "usecase" and g.k == "rectangle" { center } else { left })
  if g.k == "component" { stack(dir: ltr, spacing: 6pt, t, comp-icon) } else { t }
}

// Distance from an anchor's centre to its outline along the unit vector (dx, dy).
#let s-reach(an, dx, dy) = {
  if an.shape == "ellipse" or an.shape == "circle" {
    1 / calc.sqrt(calc.pow(dx / an.aw, 2) + calc.pow(dy / an.ah, 2))
  } else if an.shape == "diamond" {
    1 / (calc.abs(dx) / an.aw + calc.abs(dy) / an.ah)
  } else {
    calc.min(if dx != 0 { an.aw / calc.abs(dx) } else { 1e9 }, if dy != 0 { an.ah / calc.abs(dy) } else { 1e9 })
  }
}

#let s-pt(p) = (p.at(0) * 1pt, p.at(1) * 1pt)
#let s-line(pts, stroke) = place(top + left, curve(stroke: stroke, fill: none, curve.move(s-pt(pts.at(0))), ..pts.slice(1).map(p => curve.line(s-pt(p)))))
#let s-unit(a, b) = {
  let (dx, dy) = (b.at(0) - a.at(0), b.at(1) - a.at(1))
  let l = calc.max(calc.sqrt(dx * dx + dy * dy), 1e-6)
  (dx / l, dy / l)
}

#let s-diagram(kind, heading, elems, groups, lanes, edges, frame: none, dir: "TB", ortho: false, host: none) = uml-frame(heading, frame, host: host, sp => {
  set text(size: uml-size, fill: ink)
  set par(leading: 0.3em, justify: false)
  // A nested context, so measure sees the text size set above.
  context {
  let lr = dir == "LR" or dir == "RL"
  let E = (:)
  for e in elems { E.insert(e.id, s-elem(e, sp, lr)) }
  let byid = (:)
  for e in elems { byid.insert(e.id, e) }
  let gbyid = (:)
  for g in groups { gbyid.insert(g.id, g) }
  let ncol = calc.max(1, ..elems.map(e => e.col + 1))
  let nrow = calc.max(1, ..elems.map(e => e.row + 1))
  let colW = range(ncol).map(c => calc.max(12.0, ..elems.filter(e => e.col == c).map(e => E.at(e.id).w)))
  let rowH = range(nrow).map(r => calc.max(12.0, ..elems.filter(e => e.row == r).map(e => E.at(e.id).h)))
  // Group titles and insets.
  let GT = (:)
  for g in groups {
    let t = s-group-title(g, kind)
    let m = measure(t)
    GT.insert(g.id, (t: t, w: pt-of(m.width), h: pt-of(m.height) + if g.k == "package" { 5 } else { 6 }))
  }
  let d3(g) = if g.k == "node" { s-depth } else { 0.0 }
  let padL(g) = s-pad
  let padR(g) = s-pad + d3(g)
  let padT(g) = GT.at(g.id).h + s-pad - 1 + d3(g)
  let padB(g) = s-pad
  let within(h, g) = h.id == g.id or g.id in h.path
  // A group is at least as wide as its title, and twice that when an edge enters it from above
  // (entered), so the title fits beside the line: its last column widens if not.
  for g in groups.sorted(key: g => -g.path.len()) {
    let span = range(g.c0, g.c1 + 1).map(c => colW.at(c)).sum() + (g.c1 - g.c0) * sp.gx
    let need = if g.at("entered", default: false) { 2 * (GT.at(g.id).w + 8) } else { GT.at(g.id).w + 12 }
    if span < need { colW.at(g.c1) += need - span }
  }
  // Lane headers.
  let LT = lanes.map(l => s-text(l.name, max: 60mm))
  let lane-head = if lanes.len() > 0 { calc.max(..LT.map(t => pt-of(if lr { measure(t).width } else { measure(t).height }))) + 8 } else { 0.0 }
  let lane-gap = if lanes.len() > 0 { 2 * s-pad } else { 0.0 }
  // The cell that stands for an end of an edge: an element's own, or, for a group, its cell
  // nearest the other end.
  let mid-cell(g) = (calc.floor((g.c0 + g.c1) / 2), calc.floor((g.r0 + g.r1) / 2))
  let cell-of(id, other) = {
    if id in byid { let e = byid.at(id); return (e.col, e.row) }
    let g = gbyid.at(id)
    let o = if other in byid { (byid.at(other).col, byid.at(other).row) } else { mid-cell(gbyid.at(other)) }
    (calc.min(calc.max(o.at(0), g.c0), g.c1), calc.min(calc.max(o.at(1), g.r0), g.r1))
  }
  let kind-of(id) = if id in byid { byid.at(id).k } else { "group" }
  // Routes, decided on the grid: straight, or orthogonal with one bend (hv: across, then
  // along) or two (vhv, hvh: the middle run in the free band of the gap after the source's row
  // or column).
  let route-of(ed) = {
    if ed.route != "auto" { return ed.route }
    if not ortho { return "straight" }
    let (ac, ar) = cell-of(ed.from, ed.to)
    let (bc, br) = cell-of(ed.to, ed.from)
    let (ak, bk) = (kind-of(ed.from), kind-of(ed.to))
    if ac == bc or ar == br or ak in ("fork", "join") or bk in ("fork", "join") { return "straight" }
    if bk == "interface" { return if lr { "vh" } else { "hv" } }
    if not lr {
      if ak == "decision" { "hv" } else if bk == "merge" { "vh" } else if br > ar { "vhv" } else { "hvh" }
    } else {
      if ak == "decision" { "vh" } else if bk == "merge" { "hv" } else if bc > ac { "hvh" } else { "vhv" }
    }
  }
  let R = edges.map(route-of)
  // The gaps edge labels need between neighbouring columns (rows).
  let needX = range(ncol + 1).map(_ => 0.0)
  let needY = range(nrow + 1).map(_ => 0.0)
  for (i, ed) in edges.enumerate() {
    let ends = (ed.at("ma", default: none), ed.at("mb", default: none)).filter(t => t != none).map(t => measure(text(size: uml-size, t)))
    if ed.label == none and ends.len() == 0 { continue }
    let (ac, ar) = cell-of(ed.from, ed.to)
    let (bc, br) = cell-of(ed.to, ed.from)
    let m = if ed.label != none { measure(wrap-label(ed.label)) } else { (width: 0pt, height: 0pt) }
    // End labels sit beside the line near each end, the centre label between them.
    let (lw, lh) = (pt-of(m.width) + ends.map(e => pt-of(e.width) + 12).sum(default: 0.0), pt-of(m.height) + ends.map(e => pt-of(e.height) + 8).sum(default: 0.0))
    let r = R.at(i)
    let (dc, dr) = (bc - ac, br - ar)
    let gx = if dc > 0 { ac + 1 } else { ac }
    let gy = if dr > 0 { ar + 1 } else { ar }
    if r == "straight" and dr == 0 and dc != 0 { needX.at(gx) = calc.max(needX.at(gx), lw + 14) }
    if r == "straight" and dc == 0 and dr != 0 { needY.at(gy) = calc.max(needY.at(gy), lh + 10) }
    if r == "vhv" and dr != 0 { needY.at(gy) = calc.max(needY.at(gy), lh + 10) }
    if r == "hvh" and dc != 0 { needX.at(gx) = calc.max(needX.at(gx), lw + 14) }
    if r == "hv" and calc.abs(dc) == 1 and ed.from in byid {
      needX.at(gx) = calc.max(needX.at(gx), lw + 16 - colW.at(bc) / 2 - (colW.at(ac) / 2 - E.at(ed.from).aw))
    }
    if r == "vh" and calc.abs(dr) == 1 and ed.from in byid {
      needY.at(gy) = calc.max(needY.at(gy), lh + 12 - rowH.at(br) / 2 - (rowH.at(ar) / 2 - E.at(ed.from).ah))
    }
  }
  // Each gap: the insets of the groups that end before it and start after it, around a free
  // band (base spacing or what the labels need) where labels and middle runs go.
  let gap-of(n, i, ends, starts, base-edge, base-mid, need) = {
    let e = ends.sum(default: 0.0)
    let s = starts.sum(default: 0.0)
    let free = calc.max(if i == 0 or i == n { base-edge } else { base-mid }, need)
    (total: e + s + free, lo: e, hi: e + free)
  }
  let gapX = range(ncol + 1).map(c => gap-of(ncol, c, groups.filter(g => g.c1 == c - 1).map(padR), groups.filter(g => g.c0 == c).map(padL),
    if not lr and lanes.len() > 0 { s-pad } else { 0.0 }, sp.gx + if not lr { lane-gap } else { 0.0 }, needX.at(c)))
  let gapY = range(nrow + 1).map(r => gap-of(nrow, r, groups.filter(g => g.r1 == r - 1).map(padB), groups.filter(g => g.r0 == r).map(padT),
    if lanes.len() > 0 { s-pad } else { 0.0 }, sp.gy + if lr { lane-gap } else { 0.0 }, needY.at(r)))
  let x0 = if lr { lane-head } else { 0.0 }
  let y0 = if lr { 0.0 } else { lane-head }
  let X = ()
  let BX = () // free band of each column gap: its middle
  let acc = x0
  for c in range(ncol + 1) {
    let g = gapX.at(c)
    BX.push(acc + (g.lo + g.hi) / 2)
    acc += g.total
    if c < ncol { X.push(acc); acc += colW.at(c) }
  }
  let W = acc
  let Y = ()
  let BY = ()
  acc = y0
  for r in range(nrow + 1) {
    let g = gapY.at(r)
    BY.push(acc + (g.lo + g.hi) / 2)
    acc += g.total
    if r < nrow { Y.push(acc); acc += rowH.at(r) }
  }
  let H = acc
  // Anchors (centre, half sizes, shape) of elements, then of groups.
  let A = (:)
  for e in elems {
    let el = E.at(e.id)
    let cx = X.at(e.col) + colW.at(e.col) / 2
    let cy = Y.at(e.row) + rowH.at(e.row) / 2
    A.insert(e.id, (x: cx + el.ax, y: cy + el.ay, bx: cx - el.w / 2, by: cy - el.h / 2, aw: el.aw, ah: el.ah, shape: el.shape))
  }
  let GB = (:)
  for g in groups {
    let inner = groups.filter(h => within(h, g))
    let gl = X.at(g.c0) - inner.filter(h => h.c0 == g.c0).map(padL).sum(default: 0.0)
    let gr = X.at(g.c1) + colW.at(g.c1) + inner.filter(h => h.c1 == g.c1).map(padR).sum(default: 0.0) - d3(g)
    let gt = Y.at(g.r0) - inner.filter(h => h.r0 == g.r0).map(padT).sum(default: 0.0) + d3(g)
    let gb = Y.at(g.r1) + rowH.at(g.r1) + inner.filter(h => h.r1 == g.r1).map(padB).sum(default: 0.0)
    GB.insert(g.id, (l: gl, r: gr, t: gt, b: gb))
    A.insert(g.id, (x: (gl + gr) / 2, y: (gt + gb) / 2, aw: (gr - gl) / 2, ah: (gb - gt) / 2, shape: "rect", group: true))
  }
  // Fork and join bars span the elements they connect.
  for e in elems.filter(e => e.k in ("fork", "join")) {
    let others = edges.filter(ed => ed.from == e.id or ed.to == e.id).map(ed => A.at(if ed.from == e.id { ed.to } else { ed.from }))
    let a = A.at(e.id)
    if lr {
      let ys = others.map(o => o.y) + (a.y,)
      let (t, b) = (calc.min(..ys) - 10, calc.max(..ys) + 10)
      A.at(e.id) = (x: a.x, y: (t + b) / 2, aw: 2.0, ah: calc.max((b - t) / 2, 15.0), shape: "rect")
    } else {
      let xs = others.map(o => o.x) + (a.x,)
      let (l, r) = (calc.min(..xs) - 10, calc.max(..xs) + 10)
      A.at(e.id) = (x: (l + r) / 2, y: a.y, aw: calc.max((r - l) / 2, 15.0), ah: 2.0, shape: "rect")
    }
  }
  let is-bar(id) = kind-of(id) in ("fork", "join")
  let ball(id) = kind-of(id) == "interface"
  let pull(id, k) = if ball(id) and k == "requires" { s-socket } else { 0.0 }
  // Polyline of an edge, from the source's outline to the target's.
  let path(ed, r) = {
    let a = A.at(ed.from)
    let b = A.at(ed.to)
    let out(an, id, px, py, k) = {
      // The point where a line from the centre of an towards (px, py) leaves its outline.
      let u = s-unit((an.x, an.y), (px, py))
      let d = s-reach(an, u.at(0), u.at(1)) + pull(id, k)
      (an.x + u.at(0) * d, an.y + u.at(1) * d)
    }
    if r == "straight" {
      if is-bar(ed.from) or is-bar(ed.to) or a.at("group", default: false) or b.at("group", default: false) {
        // A bar or a group meets the other end square on where their extents overlap: a bar at
        // the point facing the other end, a group straight above, below or beside it.
        let (lo-x, hi-x) = (calc.max(a.x - a.aw, b.x - b.aw), calc.min(a.x + a.aw, b.x + b.aw))
        let (lo-y, hi-y) = (calc.max(a.y - a.ah, b.y - b.ah), calc.min(a.y + a.ah, b.y + b.ah))
        let narrow = if a.aw <= b.aw { a } else { b }
        let flat = if a.ah <= b.ah { a } else { b }
        if hi-x - lo-x > 4 and (calc.abs(b.y - a.y) > a.ah + b.ah or is-bar(ed.from) or is-bar(ed.to)) {
          let x = calc.min(calc.max(narrow.x, lo-x + 2), hi-x - 2)
          let s = if b.y > a.y { 1 } else { -1 }
          return ((x, a.y + s * a.ah), (x, b.y - s * (b.ah + pull(ed.to, ed.k))))
        }
        if hi-y - lo-y > 4 {
          let y = calc.min(calc.max(flat.y, lo-y + 2), hi-y - 2)
          let s = if b.x > a.x { 1 } else { -1 }
          return ((a.x + s * a.aw, y), (b.x - s * (b.aw + pull(ed.to, ed.k)), y))
        }
      }
      return (out(a, ed.from, b.x, b.y, ""), out(b, ed.to, a.x, a.y, ed.k))
    }
    let (ac, ar) = cell-of(ed.from, ed.to)
    if r == "hv" {
      let c = (b.x, a.y)
      return (out(a, ed.from, c.at(0), c.at(1), ""), c, out(b, ed.to, c.at(0), c.at(1), ed.k))
    }
    if r == "vh" {
      let c = (a.x, b.y)
      return (out(a, ed.from, c.at(0), c.at(1), ""), c, out(b, ed.to, c.at(0), c.at(1), ed.k))
    }
    if r == "vhv" {
      let ym = if b.y > a.y { BY.at(ar + 1) } else { BY.at(ar) }
      return (out(a, ed.from, a.x, ym, ""), (a.x, ym), (b.x, ym), out(b, ed.to, b.x, ym, ed.k))
    }
    // hvh
    let xm = if b.x > a.x { BX.at(ac + 1) } else { BX.at(ac) }
    (out(a, ed.from, xm, a.y, ""), (xm, a.y), (xm, b.y), out(b, ed.to, xm, b.y, ed.k))
  }
  let P = edges.enumerate().map(((i, ed)) => path(ed, R.at(i)))
  // Where each label goes: on its run (r) at point (x, y), centred on the line, or beside a
  // vertical run. A straight run along a row or column puts it in the free band of the gap next
  // to the source; any other run, at its middle.
  let label-at(i) = {
    let ed = edges.at(i)
    let pts = P.at(i)
    let r = R.at(i)
    let seg = if r in ("vhv", "hvh") { 1 } else { 0 }
    let (p, q) = (pts.at(seg), pts.at(seg + 1))
    let vertical = calc.abs(p.at(0) - q.at(0)) < 0.5
    let horizontal = calc.abs(p.at(1) - q.at(1)) < 0.5
    let (ac, ar) = cell-of(ed.from, ed.to)
    let (bc, br) = cell-of(ed.to, ed.from)
    let mid = ((p.at(0) + q.at(0)) / 2, (p.at(1) + q.at(1)) / 2)
    let between(v, s, t) = v > calc.min(s, t) + 3 and v < calc.max(s, t) - 3
    if r == "straight" and vertical and ar != br {
      let y = if br > ar { BY.at(ar + 1) } else { BY.at(ar) }
      if between(y, p.at(1), q.at(1)) { mid = (mid.at(0), y) }
    }
    if r == "straight" and horizontal and ac != bc {
      let x = if bc > ac { BX.at(ac + 1) } else { BX.at(ac) }
      if between(x, p.at(0), q.at(0)) { mid = (x, mid.at(1)) }
    }
    (x: mid.at(0), y: mid.at(1), beside: vertical and r != "hvh")
  }
  // End labels («U», «D», multiplicities): beside the line near each end, on the right-hand side
  // walking from the source, clear of the end's mark (as in class diagrams).
  let M = edges.enumerate().map(((i, ed)) => {
    let pts = P.at(i)
    let out = ()
    for (t, p, q, mark) in ((ed.at("ma", default: none), pts.at(0), pts.at(1), 0.0), (ed.at("mb", default: none), pts.at(-1), pts.at(-2), if ed.k in ("inherit", "realize") { 10.0 } else if ed.k in ("nav", "dep") { 7.0 } else if ed.k == "requires" { 0.0 } else { 0.0 })) {
      if t == none { continue }
      let lb = box(fill: white, inset: 0.5pt, text(size: uml-size, t))
      let m = measure(lb)
      let (w, h) = (pt-of(m.width), pt-of(m.height))
      let u = s-unit(p, q) // into the line from this end
      let f = s-unit(pts.at(0), pts.at(1)) // walking direction at the source
      let dirn = if p == pts.at(0) { f } else { s-unit(pts.at(-2), pts.at(-1)) }
      let n = (-dirn.at(1), dirn.at(0)) // right-hand side, y down
      let along = 3 + calc.abs(u.at(0)) * w / 2 + calc.abs(u.at(1)) * h / 2 + mark
      let side = 3 + calc.abs(n.at(0)) * w / 2 + calc.abs(n.at(1)) * h / 2
      let c = (p.at(0) + u.at(0) * along + n.at(0) * side, p.at(1) + u.at(1) * along + n.at(1) * side)
      out.push((body: lb, x: c.at(0) - w / 2, y: c.at(1) - h / 2))
    }
    out
  }).flatten()
  let L = edges.enumerate().map(((i, ed)) => if ed.label == none { none } else {
    let lb = wrap-label(ed.label)
    let m = measure(lb)
    let (lw, lh) = (pt-of(m.width), pt-of(m.height))
    let at = label-at(i)
    let (lx, ly) = if at.beside { (at.x + 2, at.y - lh / 2) } else { (at.x - lw / 2, at.y - lh / 2) }
    (body: lb, x: lx, y: ly, w: lw, h: lh)
  })
  // A group title moves to the right end of its band when an edge or a label would cross it there.
  let crosses(l, t, r, b) = {
    let hit = false
    for (i, pts) in P.enumerate() {
      for j in range(pts.len() - 1) {
        let (p, q) = (pts.at(j), pts.at(j + 1))
        for s in range(21) {
          let (x, y) = (p.at(0) + (q.at(0) - p.at(0)) * s / 20, p.at(1) + (q.at(1) - p.at(1)) * s / 20)
          if x > l and x < r and y > t and y < b { hit = true }
        }
      }
      let lb = L.at(i)
      if lb != none and lb.x < r and lb.x + lb.w > l and lb.y < b and lb.y + lb.h > t { hit = true }
    }
    for lb in M {
      let m = measure(lb.body)
      if lb.x < r and lb.x + pt-of(m.width) > l and lb.y < b and lb.y + pt-of(m.height) > t { hit = true }
    }
    hit
  }
  box(width: W * 1pt, height: H * 1pt, {
    // Partitions: one frame split into lanes, each with its name in a header band.
    if lanes.len() > 0 {
      let bounds = lanes.map(l => if lr {
        (Y.at(l.a0) - gapY.at(l.a0).total / 2, Y.at(l.a1) + rowH.at(l.a1) + gapY.at(l.a1 + 1).total / 2)
      } else {
        (X.at(l.a0) - gapX.at(l.a0).total / 2, X.at(l.a1) + colW.at(l.a1) + gapX.at(l.a1 + 1).total / 2)
      })
      let head-fill = tints.neutral.at(1).lighten(40%)
      for (i, l) in lanes.enumerate() {
        let (p, q) = bounds.at(i)
        let t = LT.at(i)
        if lr {
          place(top + left, dx: 0pt, dy: p * 1pt, rect(width: W * 1pt, height: (q - p) * 1pt, stroke: w-line + ink, fill: none))
          place(top + left, dx: 0pt, dy: p * 1pt, rect(width: lane-head * 1pt, height: (q - p) * 1pt, stroke: w-line + ink, fill: head-fill))
          place(top + left, dx: 0pt, dy: p * 1pt, box(width: lane-head * 1pt, height: (q - p) * 1pt, align(center + horizon, rotate(-90deg, reflow: true, t))))
        } else {
          place(top + left, dx: p * 1pt, dy: 0pt, rect(width: (q - p) * 1pt, height: H * 1pt, stroke: w-line + ink, fill: none))
          place(top + left, dx: p * 1pt, dy: 0pt, rect(width: (q - p) * 1pt, height: lane-head * 1pt, stroke: w-line + ink, fill: head-fill))
          place(top + left, dx: p * 1pt, dy: 0pt, box(width: (q - p) * 1pt, height: lane-head * 1pt, align(center + horizon, t)))
        }
      }
    }
    // Groups, outermost first.
    for g in groups.sorted(key: g => g.path.len()) {
      let b = GB.at(g.id)
      let (w, h) = (b.r - b.l, b.b - b.t)
      let t = GT.at(g.id).t
      let (tw, th) = (GT.at(g.id).w, GT.at(g.id).h)
      let centred = kind == "usecase" and g.k == "rectangle"
      let tx = if centred { b.l + (w - tw) / 2 } else if crosses(b.l + 3, b.t, b.l + tw + 8, b.t + th) and not crosses(b.r - tw - 8, b.t, b.r - 3, b.t + th) { b.r - tw - 5 - if g.k == "component" { 0 } else { 0 } } else { b.l + 5 }
      if g.k == "package" {
        let tab = calc.min(tw + 10, w)
        let tl = if tx > b.l + 5 { b.r - tab } else { b.l }
        place(top + left, dx: tl * 1pt, dy: b.t * 1pt, rect(width: tab * 1pt, height: th * 1pt, stroke: w-line + ink, fill: white))
        place(top + left, dx: b.l * 1pt, dy: (b.t + th) * 1pt, rect(width: w * 1pt, height: (h - th) * 1pt, stroke: w-line + ink, fill: none))
        place(top + left, dx: (tl + 5) * 1pt, dy: (b.t + 2.5) * 1pt, t)
      } else if g.k == "node" {
        place(top + left, dx: b.l * 1pt, dy: (b.t - s-depth) * 1pt, cube(w * 1pt, h * 1pt, s-pale(g.stereo, g.tint), tints.at(s-tint-key(g.stereo, g.tint)).at(1), stroke: w-line + ink))
        place(top + left, dx: tx * 1pt, dy: (b.t + 3) * 1pt, t)
      } else {
        let st = if g.k == "zone" { (paint: muted, thickness: w-line, dash: "dashed") } else { w-line + ink }
        let fill = if g.k == "component" { s-pale(g.stereo, g.tint) } else { none }
        place(top + left, dx: b.l * 1pt, dy: b.t * 1pt, rect(width: w * 1pt, height: h * 1pt, stroke: st, fill: fill))
        place(top + left, dx: tx * 1pt, dy: (b.t + 3) * 1pt, t)
      }
    }
    // Edges: line, then the end mark.
    for (i, ed) in edges.enumerate() {
      let pts = P.at(i)
      let dashed = ed.k in ("dep", "dashed", "realize")
      let st = if dashed { (paint: ink, thickness: w-line, dash: "dashed") } else { w-line + ink }
      let tip = pts.last()
      let u = s-unit(pts.at(-2), tip)
      let n = (-u.at(1), u.at(0))
      let line-pts = if ed.k in ("inherit", "realize") { pts.slice(0, -1) + ((tip.at(0) - u.at(0) * 10, tip.at(1) - u.at(1) * 10),) } else { pts }
      s-line(line-pts, st)
      if ed.k in ("nav", "dep") {
        let back(s) = (tip.at(0) - u.at(0) * 7 + n.at(0) * s, tip.at(1) - u.at(1) * 7 + n.at(1) * s)
        s-line((back(3.2), tip, back(-3.2)), w-line + ink)
      } else if ed.k in ("inherit", "realize") {
        let base(s) = (tip.at(0) - u.at(0) * 10 + n.at(0) * s, tip.at(1) - u.at(1) * 10 + n.at(1) * s)
        place(top + left, polygon(fill: white, stroke: w-line + ink, s-pt(tip), s-pt(base(5)), s-pt(base(-5))))
      } else if ed.k == "requires" and ball(ed.to) {
        // The socket: a half circle round the ball, open towards it.
        let c = A.at(ed.to)
        let th0 = calc.atan2(-u.at(0), -u.at(1))
        let arc = range(-6, 7).map(j => {
          let th = th0 + j * 15deg
          (c.x + s-socket * calc.cos(th), c.y + s-socket * calc.sin(th))
        })
        s-line(arc, w-line + ink)
      }
    }
    // Elements above the edges.
    for e in elems {
      let el = E.at(e.id)
      let a = A.at(e.id)
      if el.shape == "bar" {
        place(top + left, dx: (a.x - a.aw) * 1pt, dy: (a.y - a.ah) * 1pt, rect(width: 2 * a.aw * 1pt, height: 2 * a.ah * 1pt, fill: ink, stroke: none))
      } else {
        place(top + left, dx: a.bx * 1pt, dy: a.by * 1pt, el.body)
      }
    }
    // Edge labels last, on a white ground.
    for lb in L {
      if lb != none { place(top + left, dx: lb.x * 1pt, dy: lb.y * 1pt, lb.body) }
    }
    for lb in M { place(top + left, dx: lb.x * 1pt, dy: lb.y * 1pt, lb.body) }
  })
  }
}, (
  (gx: 34.0, gy: 26.0, max: 38mm),
  (gx: 28.0, gy: 22.0, max: 38mm),
  (gx: 22.0, gy: 18.0, max: 34mm),
  (gx: 18.0, gy: 15.0, max: 32mm),
  (gx: 14.0, gy: 12.0, max: 30mm),
  (gx: 11.0, gy: 10.0, max: 28mm),
))

// The four kinds, each with its frame heading.
#let component-diagram(elems, groups, lanes, edges, frame: none, dir: "TB", ortho: false, host: none) = s-diagram("component", "cmp", elems, groups, lanes, edges, frame: frame, dir: dir, ortho: ortho, host: host)
#let deployment-diagram(elems, groups, lanes, edges, frame: none, dir: "TB", ortho: false, host: none) = s-diagram("deployment", "deployment", elems, groups, lanes, edges, frame: frame, dir: dir, ortho: ortho, host: host)
#let usecase-diagram(elems, groups, lanes, edges, frame: none, dir: "TB", ortho: false, host: none) = s-diagram("usecase", "uc", elems, groups, lanes, edges, frame: frame, dir: dir, ortho: ortho, host: host)
#let activity-diagram(elems, groups, lanes, edges, frame: none, dir: "TB", ortho: true, host: none) = s-diagram("activity", "act", elems, groups, lanes, edges, frame: frame, dir: dir, ortho: ortho, host: host)
