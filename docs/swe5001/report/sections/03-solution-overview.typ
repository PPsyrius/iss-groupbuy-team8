#import "../lib.typ": *
#import "../diagrams/d5-physical-deployment.typ": d5
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d9-data-models.typ": d9-identity, d9-notification, d9-commerce-team, d9-commerce-payment

// AD and AISS numbers come from ../registers.md. Replace each short description with
// context, options, choice, trade-off and consequence (AGENTS.md writing rule 3).
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  [Identifier], [Description],
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

= Solution Overview

#include "03-1-logical.typ"
#include "03-2-physical.typ"
#include "03-2-4-detailed-design.typ"
#include "03-3-other-decisions.typ"
#include "03-4-limitations.typ"
