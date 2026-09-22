# Repository rules for coding agents

BlocBuy — SWE5006 Team 8. This file holds the rules that apply to the **whole repository**,
whatever language or module you are working in. Area-specific guidance lives elsewhere and still
applies on top of this:

| Area | Where to look |
|---|---|
| Frontend | [`frontend/AGENTS.md`](frontend/AGENTS.md) |
| Backend | `.claude/skills/backend-constraints-skill/` (Claude Code loads it automatically) |
| Database DDL | `.claude/skills/pgsql-ddl/` |
| Agile evidence (sprint log, NFR register, ADRs) | `.claude/skills/agile-evidence-capture/`, [`docs/agile/README.md`](docs/agile/README.md) |

## English is the language of the repository

**Every file committed to this repository is written in English.** No exceptions, no mixed-language
documents.

This covers:

- documentation of every kind — design docs, implementation plans, ADRs, runbooks, sprint logs,
  the NFR register, READMEs
- code comments and Javadoc, identifier names, log messages, test names
- SQL migration comments and `COMMENT ON` text
- commit messages and pull-request descriptions
- GitHub issue titles and bodies, and review comments

It does **not** cover conversation. Team members work in the language they are most fluent in, and
an agent should keep replying in the language the user wrote in. The rule is about what lands in
git, not about how the work is discussed while it is being done.

### Why

This is a five-person team whose members do not share one first language, and the deliverable is
assessed by readers outside the team. A Chinese-only implementation plan is invisible to the
teammates who have to act on it, to the reviewer of the PR it belongs to, and to the marker
reading the progress report that cites it. It also silently makes the author the only person who
can maintain that document.

The cost of writing English at the moment the document is created is near zero. The cost of
translating it later — after it has been cited, linked and partly superseded — is not, and the
translation is usually done by whoever needs it least.

### For agents specifically

- When the user asks for a document in another language, write it in English and say why in one
  sentence. If they repeat the request after that, it is their call — write what they asked for.
- Never produce a document that is English in structure and another language in its prose, or a
  table whose headers are English and whose cells are not.
- When you touch an existing non-English document for any other reason, do not silently translate
  it as a side effect. Translating a document is its own change, in its own commit.
- Terms that are already English stay verbatim in any language: identifiers, API paths, error
  codes, CLI commands, commit-type keywords (`feat`, `fix`, …) and exact error strings.

## Evidence travels with the change

Every PR that changes code, config or infrastructure carries its own sprint-log entry; every PR
that touches a non-functional concern carries its NFR register row; every real choice between
alternatives is written down where that area keeps decisions. The full rule, and the table of
which artefact lives where, is in [`docs/agile/README.md`](docs/agile/README.md).
