# ADR template (Nygard, under sixty lines)

Copy into `frontend/docs/adr/NNNN-kebab-title.md`. Replace every angle-bracket slot; delete none of the headings.

```markdown
# NNNN. <Decision title, imperative or noun phrase>

Date: YYYY-MM-DD

## Status

Accepted

## Context

<Two to six lines: the forces, the constraint that made this a decision, the plan task or issue
(#N) that raised it, and the ADRs it builds on (ADR 000X).>

## Decision

<What was chosen, naming the files, endpoints or settings that implement it. One paragraph.>

Alternatives rejected: <option A> (<reason>); <option B> (<reason>).

## Consequences

<What it costs, what it enables, what stays open, and which plan tasks or issues carry the
follow-ups. Dated addenda go here later, newest last.>
```

README index row, appended to the table in `frontend/docs/adr/README.md`:

```markdown
| NNNN | [<Decision title>](NNNN-kebab-title.md) | Accepted (YYYY-MM-DD, <task or #issue>) |
```

Addendum shape (inside `## Consequences` of an existing ADR, and the README status text updated in place):

```markdown
YYYY-MM-DD addendum (<task or #issue>): <one to three lines with the measured result and the
recommendation>.
```

Worked example on `dev`: `0014-stripe-hosted-checkout-server-owned-intent.md` (new ADR recording a
shipped slice) and `0010-react-compiler-babel-now-native-spike.md` (addendum from a spike).
