# Diagram style

The UML diagrams are hand-written SVG drawn to look like Visual Paradigm output, recoloured to the BlocBuy palette. This guide records the conventions the six existing diagrams already follow; a new diagram should be indistinguishable from them.

## Files

| Diagram | Kind | Canvas |
|---|---|---|
| `logical-view.svg` | UML logical deployment view | 1160 wide |
| `physical-deployment.svg` | UML deployment diagram | 1160 wide |
| `expiry-refund-sequence.svg` | UML sequence diagram | 1160 wide |
| `uc4-join-sequence.svg` | UML sequence diagram | 1160 wide |
| `context-map.svg` | DDD context map | 600 × 680 |
| `aggregates.svg` | DDD aggregates | 540 × 680 |

A deck inlines a diagram with `svg('<name>')`. A deck-local `src/diagrams/<name>.svg` overrides the shared file for that deck only: copy a shared diagram there before changing it for one course, and change the shared file only when both decks should get the change.

## Canvas and type

- Root: `<svg xmlns="http://www.w3.org/2000/svg" width="W" height="H" viewBox="0 0 W H" font-family="Inter, Helvetica, Arial, sans-serif">`, no `<?xml ?>` prolog (it is stripped anyway), then a full-size white `<rect>` background.
- Full-width slide diagrams are 1160 wide so they fit the 1164 px slide body; tall diagrams are scaled on the slide with `.replace('<svg ', '<svg style="width:470px;height:auto" ')`.
- Text: Inter, `fill="#1B1A18"`. Sizes: 15 for body labels, 14.5 for «stereotypes», 16–18 bold for element names; nothing below 14.5 so it stays readable on a shared Zoom screen.
- Stereotypes are written with guillemets: «service», «component», «node», «queue», «topic», «database», «external», «scheduler», «platform services», «business application».

## Colour by role

| Role | Stroke | Fill (gradient top → bottom) | Name text |
|---|---|---|---|
| Platform service (identity, notification) | `#3E5CD9` | `#FFFFFF` → `#E3E8FB`; boundary tint `#F1F4FD` | `#3E5CD9` |
| Business application (commerce) | `#E8402A` | `#FFFFFF` → `#FBDFD9`; boundary tint `#FDEFEC` | `#B02E1B` |
| Messaging (SNS, SQS, event bus) | `#7A5C20` | `#FFF6E2` → `#FFEDC7` | `#1B1A18` |
| External system, node, infrastructure | `#6B655C` | `#FFFFFF` → `#ECE6DC` (or `#EFE9DF`) | `#1B1A18` |
| Lines, arrows, ordinary text | `#1B1A18` / `#6B655C` | — | `#1B1A18` |

Red (`#E8402A`) also marks the one thing a diagram wants the eye on, such as the only synchronous call; use it for at most one path per diagram.

## Shapes and lines

- Strokes 1.2–1.6 px (1.4 most common); 2–2.2 px only for an emphasised path.
- Service and deployment boundaries are dashed rectangles, `stroke-dasharray="6 4"`, with the stereotype and name top-left.
- Gradients are vertical (`x1="0" y1="0" x2="0" y2="1"`); 3D node boxes and database cylinders follow `physical-deployment.svg` and `logical-view.svg`.
- Arrowheads are `<marker>` elements: filled triangle for calls and messages, open chevron for dependencies; `orient="auto-start-reverse"`.

## Ids must be unique per diagram

Every inlined SVG shares one HTML document with all the others, so a repeated `id` (gradient, marker, clipPath) silently picks up another diagram's definition. Prefix every id with a short diagram tag: `lv*` logical view, `pd*` physical deployment, `sq*` expiry sequence, `u4g*` UC4 sequence, `cm*` context map, `ag*` aggregates. The title slide's logo already uses `bagclip`.
