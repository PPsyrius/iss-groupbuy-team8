// Diagram palette from docs/presentation-kit/diagrams/STYLE.md, so report and deck match.
#import "@preview/fletcher:0.5.8" as fletcher: diagram, node, edge

#let ink = rgb("#1B1A18")
#let grey = rgb("#6B655C")
#let platform = rgb("#3E5CD9")
#let platform-fill = rgb("#E3E8FB")
#let business = rgb("#E8402A")
#let business-fill = rgb("#FBDFD9")
#let messaging = rgb("#7A5C20")
#let messaging-fill = rgb("#FFEDC7")
#let infra-fill = rgb("#ECE6DC")
#let zone-fill = rgb("#F6F3EE")

#let stereo(s) = text(size: 7pt, fill: grey)[«#s»]
#let box-label(s, name, extra: none) = align(center)[
  #stereo(s) \ #text(weight: "bold", size: 8pt, name)
  #if extra != none [ \ #text(size: 6.5pt, fill: grey, extra)]
]
