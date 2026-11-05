// SWE5001 final report — individual part of Liu Jin (#742), built on its own for review.
// The reconcile PR includes 03-2-physical.typ and 03-4-limitations.typ from
// sections/03-solution-overview.typ; this file is only the standalone preview.
// Build (from the repository root):
//   typst compile --root . docs/swe5001/report/parts/liu-jin/part.typ <output>.pdf

#import "../../lib.typ": *
#show: style

#page(footer: none)[
  #v(1fr)
  #image("../../../../nus-iss-logo.png", width: 5cm)
  #v(1.5cm)
  #text(22pt, weight: "bold", fill: accent)[Project Report for BlocBuy]
  #v(0.3cm)
  #text(13pt)[Individual part: Liu Jin]
  #v(0.8cm)
  #text(11pt)[
    3.2 Physical Architecture & Design \
    3.2.1 Key Architectural Decisions (AD-07 to AD-15) \
    3.2.2 Technology and Services \
    3.2.3 Persistence Design \
    3.4 Architectural Limitation (AISS-01 to AISS-15)
  ]
  #v(0.8cm)
  #text(10pt, fill: muted)[
    Practice Module for Graduate Certificate in Architecting Scalable Systems · Team 8 \
    Draft for the reconcile review — not the submitted report. Section numbers follow the template.
  ]
  #v(1fr)
]

#outline(title: [Contents of this part], depth: 3, indent: auto)

// Keep the template numbering: chapter 3, then 3.2 and 3.4.
#counter(heading).update(2)
= Solution Overview

#counter(heading).update((3, 1))
#include "03-2-physical.typ"

#counter(heading).update((3, 3))
#include "03-4-limitations.typ"
