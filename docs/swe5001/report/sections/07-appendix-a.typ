// Appendix A: detailed design specifications, the analysis-to-design transition for every use
// case. One file per part under appendix-a/; diagrams in diagrams/appendix-a (README there).
// Numbering: parts A.1 …, figures and tables A.1 … (appendix-a in appendix-a/lib-a.typ).
// Each part is preceded by an <a-part> marker that scripts/preview-part.sh uses to find its pages;
// <a-pages> holds the document's page count for the same script.
// `typst compile --input apart=a06-uc4 …` sets one part alone (the preview's fallback when another
// part breaks the build): the part keeps its own number, its figures count from A.1, and a
// reference to a label outside it prints as a red "?label" instead of failing.
#import "appendix-a/lib-a.typ": appendix-a

#show: appendix-a
// A floating figure never drifts past the next subsection's heading: pending floats print first.
#show heading: it => if it.level in (2, 3) { place.flush(); it } else { it }

#let only = sys.inputs.at("apart", default: none)
#show ref: it => if only == none { it } else if query(it.target).len() == 0 { text(fill: red)[?#str(it.target)] } else { it }

= Detailed Design Specifications <sec-appendix-a>

#let parts = (
  "a01-reading",
  "a02-strategies",
  "a03-uc1",
  "a04-uc2",
  "a05-uc3",
  "a06-uc4",
  "a07-uc5",
  "a08-uc6",
  "a09-uc7",
  "a10-uc8",
  "a11-uc9",
  "a12-pay-upfront",
  "a13-notifications",
  "a14-traceability",
)
#let marker(name) = context [#metadata((part: name, page: here().page())) <a-part>]

#for (i, p) in parts.enumerate() {
  if only == none or only == p {
    if only != none { counter(heading).update((1, i)) }
    marker(p)
    include "appendix-a/" + p + ".typ"
  }
}
#marker("end")
#context [#metadata(counter(page).final().first()) <a-pages>]
