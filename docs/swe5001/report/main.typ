// SWE5001 Team 8 — Final Project Report
// Structure follows "02 Project Report Template for Practice Project" exactly.
// Build (from the repository root):
//   mkdir -p docs/swe5001/report/build
//   typst compile --root . docs/swe5001/report/main.typ docs/swe5001/report/build/report.pdf

#import "lib.typ": *
#show: style

// Cover page (template: title, module, team, members)
#page(footer: none)[
  #v(1fr)
  #image("../../nus-iss-logo.png", width: 5cm)
  #v(1.5cm)
  #text(24pt, weight: "bold", fill: accent)[Project Report for BlocBuy]
  #v(0.3cm)
  #text(13pt)[A Scalable Community Group-Buying Platform]
  #v(1cm)
  #text(12pt)[Practice Module for Graduate Certificate in Architecting Scalable Systems]
  #v(0.4cm)
  #text(12pt)[Team 8]
  #v(0.6cm)
  #text(11pt)[
    *Members:* \
    Lim Jiechao \
    Liu Jin \
    Panpakorn Siripanich \
    Wang Ziang \
    Yu Qianshuo
  ]
  #v(1fr)
  #text(9pt, fill: muted)[Scaffold draft — not for submission]
]

#outline(title: [Contents], depth: 3, indent: auto)

#include "sections/01-introduction.typ"
#include "sections/02-project-conduct.typ"
#include "sections/03-solution-overview.typ"
#include "sections/04-quality-attributes.typ"
#include "sections/05-devops.typ"
#include "sections/06-highlights.typ"
#include "sections/source-book.typ"
