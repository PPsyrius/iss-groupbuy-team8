// Detailed design source book (folder `appendix-a/`): the analysis-to-design transition for every
// use case. It is the team's design reference, not part of the submitted SWE5001 report: main.typ
// does not include it, but it is kept in step with the report and the code, and it compiles on its
// own (scripts/preview-part.sh).
// One file per part under appendix-a/; diagrams in diagrams/appendix-a (README there).
// Numbering: the title is unnumbered; parts 1 … 14, subsections 6.1 …, figures and tables 6.1 …
// counted per part (the `appendix-a` show rule in appendix-a/lib-a.typ).
// Each part is preceded by an <a-part> marker that scripts/preview-part.sh uses to find its pages;
// <a-pages> holds the document's page count for the same script.
// `typst compile --input apart=a06-uc4 …` sets one part alone (the preview's fallback when another
// part breaks the build): the part keeps its own number and its figure numbers, and a
// reference to a label outside it prints as a red "?label" instead of failing.
#import "appendix-a/lib-a.typ": appendix-a

#show: appendix-a
// A floating figure never drifts past the next subsection's heading: pending floats print first.
#show heading: it => if it.level in (2, 3) { place.flush(); it } else { it }

#let only = sys.inputs.at("apart", default: none)
#show ref: it => if only == none { it } else if query(it.target).len() == 0 { text(fill: red)[?#str(it.target)] } else { it }

// Unnumbered: the source book is no appendix of the report, so its title carries no letter.
#heading(level: 1, numbering: none)[Detailed design source book] <sec-appendix-a>

This source book is Team 8's design reference; it is not part of the submitted SWE5001 report. It carries every use case from its flows of events to the classes, messages and tables of the code. We keep it in step with the report and the code: the same names, aggregate roots, lock order and evidence. A part that refers to the report names the section in plain text (for example report §3.2.4), so the source book also compiles on its own.

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
