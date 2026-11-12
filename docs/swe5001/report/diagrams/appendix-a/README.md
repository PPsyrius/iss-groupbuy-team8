# UML diagrams: one kit for the whole report

The report draws every UML diagram, in chapters 1–6 and in the detailed design source book (folder `appendix-a/`, `sections/07-appendix-a.typ`; not part of the submitted report), from a Mermaid or Mermaid-like source with one kit: the SWE5006 UML kit (`docs/swe5006/report/typst/uml.typ`, Visual Paradigm style). The kit draws seven kinds in one look (palette, role tints, strokes, 8.5 pt labels, frame with pentagon tab, scaling and landscape turn): class, sequence and state diagrams from Mermaid, and component, deployment, use-case and activity diagrams from the Mermaid-like grammar under [Component, deployment, use-case and activity diagrams](#component-deployment-use-case-and-activity-diagrams). The pilot part `sections/appendix-a/a06-uc4.typ` (UC4) shows every class and sequence convention below in use; `diagrams/kit-samples/*.mmd` holds one sample of each of the four newer kinds.

```
diagrams/appendix-a/
  src/<id>.mmd     editable Mermaid source, one diagram per file (commit)
  gen/<id>.typ     generated Typst drawing (commit with its source; never edit by hand)
sections/appendix-a/
  lib-a.typ        afig, atable, step and the source book numbering
  aNN-name.typ     one file per part (A.1 to A.14)
scripts/
  appendix-diagrams.mjs   src -> gen (wraps mermaid() from docs/swe5006/report/scripts/typst.mjs)
  preview-part.sh         render one part to PNGs and report each diagram's label size
```

## Commands

Run from the repository (worktree) root.

```bash
node docs/swe5001/report/scripts/appendix-diagrams.mjs              # all sources
node docs/swe5001/report/scripts/appendix-diagrams.mjs a-uc04-ac    # only these ids
node docs/swe5001/report/scripts/appendix-diagrams.mjs --check      # fail if gen/ is stale or orphaned
docs/swe5001/report/scripts/preview-part.sh a06                     # one part alone, about 35 s
FULL=1 docs/swe5001/report/scripts/preview-part.sh a06              # inside the whole report
typst compile --root . docs/swe5001/report/main.typ docs/swe5001/report/build/report.pdf
npm --prefix docs/swe5006/report run render:compare             # after changing the shared kit: SWE5006 pages unchanged
```

The generator prints one line per diagram (`wrote`, `same` or `FAIL` with the line and the reason) and exits 1 if any source fails.

`preview-part.sh` regenerates all diagrams, compiles, and writes the part's pages to `.build/swe5001-appendix/preview/<part>/page-NNN.png` in the main checkout. It prints the compiler's warnings and one line per figure:

```
a-uc04-dc-settle           p10   class    scale 0.96  labels 8.1 pt
a-uc04-dc-admit            p12   class    scale 0.89  labels 7.5 pt  landscape  SMALL: split the diagram or narrow its members
```

By default the part is set alone (`--input apart=<part>`): no chapters 1–6 and no other parts, so another writer's error cannot block you. The part keeps its number (A.6), its figures count from A.1, and a reference to a label outside the part prints as a red `?label`. `FULL=1` gives the real numbers and references; it falls back to the part alone if the report does not compile. Look at every PNG before you hand a part in.

## Naming

- Diagram id: `a-ucNN-<kind>[-<slice or flow>]`, lowercase kebab-case, starting `a-`. Kinds: `ac` analysis class, `as-basic` / `as-alt-…` analysis sequence per flow, `dc-<slice>` design class slice, `ds-<flow>` design sequence, `st-<object>` statechart. Shared parts: `a-str-…` (strategies), `a-pkg-…` (package-like views). Use only your own ids.
- Figure label: `<fig-ID>`, created by `afig`; refer to it as `@fig-a-uc04-ac`. Tables: `<tbl-a-ucNN-…>`. Sections: `<sec-a-ucNN-…>`; each part's level-2 heading already has `<sec-a-ucNN>` (or `<sec-a-reading>`, `<sec-a-strategies>`, `<sec-a-pay-upfront>`, `<sec-a-notifications>`, `<sec-a-trace>`).

## Source format

```
%% frame: UC4 design, slice 2: webhook settlement (commerce-svc)
classDiagram
    class StripeCheckoutService {
        <<application service, Ext, Conc>>
        -tx: TransactionTemplate
        +handleWebhook(rawBody: String, signature: String): void
    }
    …
%% at StripeCheckoutService 1,2
    StripeCheckoutService "1" --> "1" CheckoutGateway
```

- The first line is `%% frame: <name>`, the heading in the frame's tab (`class …`, `sd …`, `stm …`, `cmp …`, `deployment …`, `uc …`, `act …`). Required.
- `%% at Id col,row` pins a class or state to a grid cell (integers, column first, row down). Pin every box of a class diagram; the automatic layout is a fallback. Two pins may not share a cell. Pins do nothing in sequence diagrams (lifelines keep declaration order).
- `%% mside A B <side at A> <side at B>` (each `left` or `right`, walking from the relation's source) moves the multiplicities of the A–B relation to the other side of the line. A multiplicity sits on the right by default; at the end of a diagonal edge that reaches a box near its corner, that side can touch the box, so move that end to the left. Pinning boxes at fractional cells to straighten the edge makes the layout close up its gaps instead (UC4 Fig A.1 shows the hint in use).
- A «create» label wraps inside the gap before the created head. If the wrap makes a figure print below 8 pt, shorten the label (`«create» EXPIRED`, not `«create» status EXPIRED`). A one-word name wider than its head box (a created head is one column wide) takes a soft hyphen (U+00AD) at a syllable break: `Member­ship`.
- A one-branch `alt` whose branch ends the call (a reply such as `return false`, after which nothing else runs) is a `break`: draw it as one, or the bar of the call it ends stops inside the fragment.
- A message to a «static» operation goes to a lifeline for the class itself (`participant Cls as TeamPricePolicy (entity)`), never to a lifeline that a later «create» brings into being.
- Cross-references: `@fig-…`, `@tbl-…` or `@sec-…` inside a message, note or edge label becomes a real reference that follows renumbering (`Note over A,B: B7: @fig-a-uc04-ds-admit`). Never type a figure number such as "Figure 6.12" by hand. Not allowed in fragment guards or names.

### Supported Mermaid

Anything else fails loudly, because the translator itself silently drops lines it does not understand.

- `classDiagram`: `class X { … }` with members one per line (a line with `(` is an operation, otherwise an attribute or enumeration literal), `<<stereo>>` inside the body or as `<<stereo>> X`, `X : member`, `class X~T~` generics, `direction`. Relations `<|--` `..|>` `*--` `o--` `-->` `..>` `--` `..` in either direction, with `"mult"` on either end and `: label`. Every class must be declared with `class X` (a typo would otherwise create a box).
- `sequenceDiagram`: `actor X`, `participant X as Name`, `create participant X as Name` (the next message to it draws «create»), `destroy X`; messages `->>` sync, `-)` async, `-->>` return, each with a `: label`; `activate`/`deactivate` or none (activations are inferred, and the generator passes `continuousActivations`: an activation that an `alt` branch or a `break` ends and the flow after it resumes is one unbroken bar, as in UML); `alt`/`else`, `opt`, `loop`, `par`/`and`, `critical`/`option`, `break`, plain `rect`; `Note over A,B: text`. Every participant must be declared before use.
- `stateDiagram-v2`: `[*]`, `state "Name" as X`, `state X <<choice>>` (or fork, join), `A --> B : trigger [guard] / effect`, `X : entry / …` (body lines), a self-transition (drawn inside the state), `direction`.
- `componentDiagram`, `deploymentDiagram`, `usecaseDiagram`, `activityDiagram`: see the next section.
- Not supported: namespaces and packages in class diagrams, notes in class or state diagrams, composite or concurrent states, `ref` fragments, self-associations, coloured `rect`, static (underlined) members, `box`, flowcharts. Say these things in the caption or text instead.

## Component, deployment, use-case and activity diagrams

One grammar serves the four kinds. The translator is `structModel` in `docs/swe5006/report/scripts/typst.mjs`; the generator turns every fault it reports into an error.

```
%% frame: BlocBuy services by tier
componentDiagram
    actor Buyer
    package "Application tier" {
        interface IDeals["Group deal API"]
        component Commerce["commerce-svc"] <<subsystem>> {
            component Deals["Group buying"] <<bounded context>> :::business : core subdomain
            component Pay["Payments"] <<bounded context>> : supporting subdomain
        }
    }
    zone "Third party" {
        component Stripe["Stripe"] <<external system>>
    }
    Commerce -() IDeals
    Web -( IDeals : REST
    Deals -- Pay : Partnership
    Pay "D" ..> "U" Stripe : ACL
%% route ortho
%% at Deals 1,3
```

**Statements**, one per line:

- Element: `<keyword> Id["Display name"] <<stereotype>> :::tint : subtitle`. Everything after `Id` is optional. The name may break with `\n`; a name with spaces wraps only at spaces. `: subtitle` adds a second, regular line under the bold name ("core subdomain"). `:::tint` overrides the role tint the stereotype gives: `platform` (blue), `business` (red), `messaging` (amber) or `neutral`.
- Container: an element line ending in `{`, closed by `}` on its own line, holds other elements. Containers nest (tier › subsystem › component; region › VPC › subnet › task).
- Group: `package|rectangle|zone|lane "Title" [as Id] [<<stereotype>>] {` … `}`. `package` draws a UML package (title in a tab), `rectangle` a titled rectangle (tiers; the system boundary of a use-case diagram, title centred), `zone` a dashed titled rectangle (network zones), `lane` an activity partition. Give a group `as Id` to draw edges to it.
- Relation: `A ["end"] <op> ["end"] B [: label]`. The quoted end labels sit beside the line near each end, like class multiplicities ("U" and "D" on a context map, "1" and "*"). The label sits on the line, in the free band of the gap between the two ends.

| Operator | Meaning | Drawn as |
| --- | --- | --- |
| `--` | association, communication path, Partnership or Shared Kernel (undirected) | solid line |
| `-->` `<--` | navigable association, control flow | solid line, open arrowhead |
| `..>` `<..` | dependency (`: include`, `: extend` print as «include», «extend») | dashed line, open arrowhead |
| `..` | note anchor, undirected dashed link | dashed line |
| `--\|>` `<\|--` | generalisation (actors, use cases) | solid line, hollow triangle |
| `..\|>` `<\|..` | realisation | dashed line, hollow triangle |
| `-()` | `A -() I`: A provides interface I | line to the ball (lollipop) |
| `-(` | `A -( I`: A requires interface I | line ending in a socket round the ball |

**Keywords per kind.**

| Header (frame tab) | Elements | Containers | Groups |
| --- | --- | --- | --- |
| `componentDiagram` (`cmp`) | `component`, `database` (a component «database»), `interface`, `actor`, `artifact`, `node`, `device`, `environment`, `note` | `component`, `node`, `device`, `environment` | `package`, `rectangle`, `zone` |
| `deploymentDiagram` (`deployment`) | `node`, `device` («device»), `environment` («execution environment»), `artifact` («artifact», page icon), `component`, `database`, `interface`, `actor`, `note` | `node`, `device`, `environment`, `component` | `package`, `rectangle`, `zone` |
| `usecaseDiagram` (`uc`) | `actor`, `usecase`, `note` | none | `rectangle` (system boundary), `package` |
| `activityDiagram` (`act`) | `start`, `end` (activity final), `flowend` (flow final), `action`, `decision`, `merge`, `fork`, `join`, `object`, `note` | none | `lane` |

- A component is a box with the UML component icon; a node is a 3-D box; an artifact a box with the page icon; an actor a stick figure (a `<<stereotype>>` such as «system» prints above its name); a use case an ellipse; an action a rounded box; an object node a square box; a note a page with a folded corner.
- Extension points: `UseCase : point` adds one under an "extension points" line in the ellipse. For a condition, attach a note: `note N1["condition: …"]` and `N1 .. UseCase`.
- A decision's name, if given (`decision Gate["Scan clean?"]`), prints above and to the left of the diamond; put guards on its outgoing edges (`Gate --> Export : [no]`). Fork and join bars span the elements they connect.
- `direction TB` (default) or `LR`. In an activity diagram, `LR` lays the flow left to right and turns the lanes into rows.

**Layout.** Every element sits in a grid cell. Each column takes the width of its widest element and each row the height of its tallest; a gap widens for the insets and titles of the groups that start or end there and for the labels of the edges that cross it. A group takes the cells of what it holds.

- `%% at Id col,row` pins an element (whole cells, column first, row down). Groups are never pinned. Pin every element of a component, deployment or use-case diagram; the automatic layout (longest-path layers) is a fallback.
- An activity diagram with lanes lays itself out: one row per step along the flow, one or more columns per lane, lanes in declaration order. Pin only what a skip edge would otherwise run through.
- `%% route ortho` (the default in activity diagrams) draws edges between different rows and columns with right-angled bends; `%% route straight` (the default otherwise) draws them straight. `%% route A B straight|hv|vh|vhv|hvh` sets one edge: `hv` runs across then along, `vh` along then across, `vhv` and `hvh` bend twice with the middle run in the free band of the gap next to the source.
- The generator refuses: an unknown line, an undeclared name, a container or group with nothing inside, two elements in one cell, an element inside the area of a group it is not part of, overlapping groups or lanes, an element outside every lane (once there are lanes), and a straight edge along a row or column that runs through another element. Fix each with a pin or a route.
- A group that an edge enters from above widens so its title fits beside the line; a group title that an edge or label still crosses moves to the right end of its band.

## Placing figures and tables (lib-a.typ)

```typst
#import "lib-a.typ": *

#afig("a-uc04-ac", [UC4 analysis class diagram (UML class notation, BCE stereotypes). JoinTeamControl is the only object that talks to both boundaries and to the entities.])
#afig("a-uc04-dc-admit", orient: "landscape", [ … ])   // force a flipped page

#atable([UC4 flows of events. …], <tbl-a-uc04-flows>,
  columns: (auto, auto, 1fr),
  header: ([Step], [At], [What happens]),
  [*B1*], [], [The buyer asks to join an open team.],
)
```

- `afig(id, caption, lbl: auto, orient: auto, float: false)` puts the drawing in a numbered figure (Figure 3.n in a chapter, Figure 6.n in source book part 6) labelled `<fig-ID>`. It scales the drawing to the text width and the page height left under the caption. A class or state diagram that would print below 8 pt turns landscape on a flipped page of its own, caption included, when that helps. `orient: "landscape"` forces it (the kit only turns when landscape is at least 0.03 larger); `"portrait"` forbids it. Sequence diagrams never turn: split them.
- Call `afig` at the top level of the part file, never inside a list, block or table: a landscape figure needs a page break.
- A drawing never splits from its caption. When a sequence diagram does not fit in the space left on the page, `afig` offers it that space if its labels still print at 8 pt or more there (scale 0.945); otherwise it moves to the next page at full size. To avoid half-empty pages, put a section's paragraphs before its run of figures rather than one paragraph before each full-page figure.
- Code in any source-book table prints at 8 pt (set centrally in `lib-a.typ`); do not add part-local size rules.
- `atable(caption, lbl, columns:, header:, ..cells)` is the report's table style with a repeating header; long inline code in a cell wraps at `_ . / ( ,` and camelCase boundaries.
- Caption: notation + viewpoint + what to notice, two sentences at most.

## Layouts that stay legible (lessons from the UC4 pilot)

- Text is 8.5 pt and may shrink to 8 pt (scale 0.94). The preview's `SMALL` flag means fix it.
- Portrait text width holds three class columns at full size; four or five columns go landscape (25 cm wide, about 13.5 cm tall for the drawing). A design slice of 9 to 13 classes fits in 3 columns × 4–5 rows (portrait) or 4–5 columns × 3 rows (landscape).
- Put the class with the most links in the middle and its partners in the eight cells around it. Link only neighbouring cells. An edge that skips a cell diagonally clips the corner of the box in between and the router turns it into an arc that can cross other boxes.
- Short horizontal edges cannot carry two multiplicities and a label: use `*` instead of `0..*`, drop the label, or make the edge vertical or diagonal.
- Remove members that no diagram uses (private helpers no sequence calls). Every operation a sequence sends must stay on some design class diagram.
- Two edges between the same two boxes fan out into arcs; keep one (an attribute can stand for the association).
- Sequence diagrams: up to 8 lifelines read well; at 10 a «boundary» stereotype just overflows its head box. Split a long flow at a natural seam (before and after payment; receipt and transaction) and link the parts with a `@fig-…` note.
- A return message goes back to its caller, one lifeline at a time; do not jump to the actor.
- Lifelines are `:Class`. Name the interface the caller holds, unless the diagram shows what happens inside the implementation (then name the class, for example `:GroupDealPurchaseSupportImpl`). Two objects of one class: `txA : StripeCheckoutService`.
- Mark each transaction with `critical <name>, <isolation>, <timeout>`, row locks as explicit messages (`lockDeal(dealId): lock 1, the team row`), and step tags (`B4`) in a `Note over` at the start of each step group.

## Gotchas

- Compile time is about 1 s per diagram (each class diagram is laid out once to decide its orientation and once to draw). The whole report will take minutes once every part is in: use the default preview (part alone) while writing.
- Typst text: escape `#` as `\#`; `@word` is a reference and `<word>` a label, so put code such as `@Order(10)` or `Optional<UUID>` in backticks; `*` and `_` start strong and emphasis.
- `typst query` is deprecated in Typst 0.15; the preview uses `typst eval`.
- Floats are off by default: a floating figure escaped before the source book title and was lost at the end of the document in tests. A float never passes the next part or subsection heading: `07-appendix-a.typ` flushes pending floats before every level-2 and level-3 heading, so a figure always prints inside its own subsection.
- `ApplicationEventPublisher` is Spring's interface; it is the one framework type the UC4 diagrams draw, as the Observer's subject. Keep other framework and `java.util` types out of the boxes.
