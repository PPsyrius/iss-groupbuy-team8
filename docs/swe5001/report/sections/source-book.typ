// Appendix A: detailed design specifications, the analysis-to-design transition for every use
// case. One file per part under source-book/; diagrams in diagrams/uml (README there).
// Numbering: parts A.1 …, figures and tables A.1 … (source-book in ../figures.typ).
// Each part is preceded by an <a-part> marker that scripts/preview-source-book.sh uses to find its pages;
// <a-pages> holds the document's page count for the same script.
// `typst compile --input apart=06-uc4 …` sets one part alone (the preview's fallback when another
// part breaks the build): the part keeps its own number, its figures count from A.1, and a
// reference to a label outside it prints as a red "?label" instead of failing.
#import "../figures.typ": source-book

#show: source-book
// A floating figure never drifts past the next subsection's heading: pending floats print first.
#show heading: it => if it.level in (2, 3) { place.flush(); it } else { it }

#let only = sys.inputs.at("apart", default: none)
#show ref: it => if only == none { it } else if query(it.target).len() == 0 { text(fill: red)[?#str(it.target)] } else { it }

= Detailed Design Specifications <sec-appendix-a>

#let parts = (
  "01-reading",
  "02-strategies",
  "03-uc1",
  "04-uc2",
  "05-uc3",
  "06-uc4",
  "07-uc5",
  "08-uc6",
  "09-uc7",
  "10-uc8",
  "11-uc9",
  "12-pay-upfront",
  "13-notifications",
  "14-traceability",
)
#let marker(name) = context [#metadata((part: name, page: here().page())) <a-part>]

#for (i, p) in parts.enumerate() {
  if only == none or only == p {
    if only != none { counter(heading).update((1, i)) }
    marker(p)
    include "source-book/" + p + ".typ"
  }
}
#marker("end")
#context [#metadata(counter(page).final().first()) <a-pages>]
