// D10a/b: separate UML activity views for a called image job and its deployment caller.
// A final node in D10a returns the job result; it does not terminate the caller in D10b.
#import "style.typ": *
#import fletcher.shapes: circle, diamond

#let activity-style(body) = {
  set text(font: "Arial", size: 8.5pt, fill: ink)
  set par(justify: false)
  body
}
#let main(body) = block(width: 180pt, align(center, body))
#let side(body) = block(width: 105pt, align(center, body))

#let d10-validation = activity-style(diagram(
  spacing: (9mm, 5mm),
  node-stroke: 0.6pt + grey,
  node-corner-radius: 3pt,
  edge-stroke: 0.6pt + ink,
  node((0, 0), [], shape: circle, radius: 2pt, fill: ink, name: <start>),
  node((0, 1), main[Package → build ARM64 image], fill: platform-fill, name: <build>),
  node((0, 2), main[Trivy scans that image \ Fixable HIGH / CRITICAL fail], fill: business-fill, name: <scan>),
  node((0, 3), [Scan passes?], shape: diamond, name: <gate>),
  node((1, 3), side[Image job fails \ No export], fill: business-fill, name: <fail>),
  node((0, 4), [Export requested?], shape: diamond, name: <mode>),
  node((0, 5), main[Save scanned image → upload archive], fill: platform-fill, name: <export>),
  node((0, 6), [], shape: diamond, width: 7pt, height: 7pt, inset: 0pt, name: <merge>),
  node((0, 7), main[Image job succeeds], fill: infra-fill, name: <validated>),
  node((0, 8), [●], shape: circle, radius: 4pt, inset: 0pt, fill: white, name: <end>),
  node((1, 4), [●], shape: circle, radius: 4pt, inset: 0pt, fill: white, name: <fail-end>),
  edge(<start>, <build>, "->"),
  edge(<build>, <scan>, "->"),
  edge(<scan>, <gate>, "->"),
  edge(<gate>, <fail>, "->", [[no]], label-side: left),
  edge(<fail>, <fail-end>, "->"),
  edge(<gate>, <mode>, "->", [[yes]], label-side: left),
  edge(<mode>, <export>, "->", [[yes]], label-side: left),
  edge(<mode>, (-1, 4), (-1, 6), <merge>, "->", [[no]], label-side: right),
  edge(<export>, <merge>, "->"),
  edge(<merge>, <validated>, "->"),
  edge(<validated>, <end>, "->"),
))

#let d10 = activity-style(diagram(
  spacing: (9mm, 4mm),
  node-stroke: 0.6pt + grey,
  node-corner-radius: 3pt,
  edge-stroke: 0.6pt + ink,
  node((0, 0), [], shape: circle, radius: 2pt, fill: ink, name: <start>),
  node((0, 1), main[Call image workflow \ Export requested (D10a)], fill: platform-fill, name: <images>),
  node((0, 2), [Images succeed?], shape: diamond, name: <image-gate>),
  node((0, 3), main[Publish archives to ECR \ Record candidate digests], fill: platform-fill, name: <publish>),
  node((0, 4), [Publish succeeds?], shape: diamond, name: <publish-gate>),
  node((0, 5), main[Create runtime → await readiness \ Smoke suite; attempt log capture], fill: infra-fill, name: <up>),
  node((0, 6), [Up succeeds?], shape: diamond, name: <up-gate>),
  node((0, 7), main[Publish SPA → record release digests], fill: platform-fill, name: <release>),
  node((1, 6), [], shape: diamond, width: 7pt, height: 7pt, inset: 0pt, name: <failure-merge>),
  node((1, 7), side[Skip blocked \ downstream jobs], fill: business-fill, name: <skip>),
  node((0, 8), [], shape: diamond, width: 7pt, height: 7pt, inset: 0pt, name: <cleanup-merge>),
  node((0, 9), main[Attempt down (always) \ Destroy runtime → check status], fill: infra-fill, name: <down>),
  node((0, 10), main[Attempt evidence upload (always) \ Retain stage outcomes], fill: infra-fill, name: <evidence>),
  node((0, 11), [●], shape: circle, radius: 4pt, inset: 0pt, fill: white, name: <end>),
  edge(<start>, <images>, "->"),
  edge(<images>, <image-gate>, "->"),
  edge(<image-gate>, <publish>, "->", [[yes]], label-side: left),
  edge(<image-gate>, (1, 2), <failure-merge>, "->", [[no]], label-side: left),
  edge(<publish>, <publish-gate>, "->"),
  edge(<publish-gate>, <up>, "->", [[yes]], label-side: left),
  edge(<publish-gate>, (1, 4), <failure-merge>, "->", [[no]], label-side: left),
  edge(<up>, <up-gate>, "->"),
  edge(<up-gate>, <release>, "->", [[yes]], label-side: left),
  edge(<up-gate>, <failure-merge>, "->", [[no]], label-side: left),
  edge(<failure-merge>, <skip>, "->"),
  edge(<release>, <cleanup-merge>, "->", [[either outcome]], label-side: left),
  edge(<skip>, (1, 8), <cleanup-merge>, "->"),
  edge(<cleanup-merge>, <down>, "->"),
  edge(<down>, <evidence>, "->", [[either outcome]], label-side: left),
  edge(<evidence>, <end>, "->"),
))
