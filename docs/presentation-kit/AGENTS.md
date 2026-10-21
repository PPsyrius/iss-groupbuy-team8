# Presentation kit

Shared engine for the BlocBuy course presentations. Each deck folder (`docs/swe5001/presentation/`, `docs/swe5006/presentation/`) holds only its content, slide layouts, speaker-script chapters and committed `dist/`; this kit turns them into the same three outputs with the same look:

| Output | Use |
|---|---|
| `dist/<deck>.html` | live presentation in Chrome or Edge (presenter notes, timer, fragments, backup index) |
| `dist/<deck>.pdf` | slide deck for submission, one 1280 × 720 page per slide, backups last |
| `dist/<deck>-script.pdf` | A4 speaker script and demo run book with running header and page numbers; built in memory and printed straight to PDF, so there is no script HTML file |

All committed text is English. Read the deck's own `AGENTS.md` before changing a deck.

## Setup and build

Run every command from the repository root; each is a single command, so it works in shells that do not keep the working directory between calls.

```sh
npm --prefix docs/presentation-kit ci   # only when docs/presentation-kit/node_modules is missing: fonts, Font Awesome, Playwright 1.64.0
npm --prefix docs/presentation-kit exec -- playwright install chromium   # only if a render step reports a missing browser
npm --prefix docs/<course>/presentation run all           # HTML deck, deck PDF, script PDF
npm --prefix docs/<course>/presentation run build:deck    # HTML deck only (fast loop while editing slides)
npm --prefix docs/<course>/presentation run render:deck   # deck PDF from the built HTML deck
npm --prefix docs/<course>/presentation run script        # script PDF only (also prints the marker counts)
npm --prefix docs/<course>/presentation run screenshots   # png/<slide id>.png (git-ignored) + overflow report
npm --prefix docs/<course>/presentation run screenshots -- '^s1[0-9]$'   # only the matching slide ids
```

Under the hood every script is `node ../../presentation-kit/bin/build.js . <steps>` with steps `deck`, `deck-pdf`, `script`, `screenshots [regex]` or `all`. A successful build prints `slides: N characters: M`, `pdf ok`, then `markers { FILL, VERIFY, CONFIRM } over: <slide:estimate/slot …>` and `script pdf ok`. A clean `screenshots` run prints nothing after the npm header; every printed line is a slide with overflow.

Commit the regenerated `dist/` files with the source change. Chromium writes a timestamp into every PDF and its output size can vary slightly between runs, so judge by the HTML. Deck: when `git diff` shows no change to the deck `.html`, the deck PDF carries no content change either. Script: set `SCRIPT_HTML` to an absolute path outside `dist/` (the repository ignores `out/`) to also write the script HTML, and compare two builds of it, e.g. `SCRIPT_HTML=$PWD/out/script.html npm --prefix docs/<course>/presentation run script`. Leave a PDF without a content change out of the commit.

## Layout

| Path | Role |
|---|---|
| `bin/build.js` | CLI; selects the deck (`lib/context.js`) before any deck module loads |
| `lib/index.js` | what slide modules import: `icon`, `md`, `mk`, `esc`, `words`, `svg`, `lockup`, `fontCSS` |
| `lib/assets.js` | Inter and JetBrains Mono inlined as base64; Font Awesome Free icons by name |
| `lib/text.js` | HTML escaping, draft markers, word counts |
| `lib/diagrams.js` | `svg(name)`: deck `src/diagrams/` first, then `diagrams/` here |
| `lib/brand.js` | `lockup()`: BlocBuy bag mark, wordmark and tagline for dark title slides |
| `deck/css.js` | slide styles and BlocBuy tokens |
| `deck/build.js`, `deck/runtime.js` | slide chrome (header, speaker chip, footer progress bar), notes data, presenter runtime |
| `script/css.js`, `script/build.js` | A4 script styles, the helpers a deck's `src/script.js` uses, and the in-memory script document |
| `render.js` | Playwright printing and screenshots |
| `diagrams/` | shared UML SVGs; conventions in [`diagrams/STYLE.md`](diagrams/STYLE.md) |

## What a deck folder provides

```
deck.config.js     title, brand, darkSlides, fillLabel, guidelineLabel, demoSegment, outputs, scriptTitle, scriptHeader
package.json       npm scripts only (no dependencies)
src/content.js     exports { SPEAKERS, SEGMENTS, SLIDES, QA, BACKUPS, OPEN_ITEMS }
src/slides-main.js exports { <slide n>: () => html } for every SLIDES entry
src/slides-backup.js (optional) exports { list() } → [{ id, title, speaker?, body }]; id 'BX' is the index the B key opens
src/script.js      exports the script body HTML (see below)
src/diagrams/      optional per-deck diagram overrides
```

`deck.config.js` fields: `title` (HTML title), `brand` (footer text after "BlocBuy"), `darkSlides` (slide numbers drawn full-bleed dark with no header or footer, e.g. title and thank-you), `fillLabel` (text of the FILL chip, e.g. `FILL 19 Oct`), `guidelineLabel` (heading of each segment's requirement box), `demoSegment` (segment number whose Q&A the deck's own demo chapter renders, or `null`), `outputs` (`deck`, `deckPdf`, `scriptPdf` file names), `scriptTitle` (PDF document title), `scriptHeader` (`left`, `right` text of the PDF running header).

`src/content.js` shapes:
- `SEGMENTS[i] = { n, name, head, short, talk, qa, start: 'HH:MM:SS', owner, items: { '1a': guideline text } }`; minutes in `talk`/`qa`; `short` labels the footer.
- `SLIDES[i] = { n, seg, item, speaker, slot, title, say, cues?, handover?, trim? }`; `slot` in seconds (`null` = Q&A); `speaker` is a `SPEAKERS` key `{ full, init }`. Titles over 58 characters render smaller.
- `say` is the sample wording; `[click]` marks a fragment step. Speaking time is estimated at 130 words a minute, and the script flags slides that run over their slot.
- `QA[segmentNumber] = { window, rows: [[question, answer, owner]] }`; `BACKUPS` rows `[id, title, answers]`; `OPEN_ITEMS` rows `[#, item, owner, blocks slide]`.
- The kit reads only the fields above, so a deck may add its own to `SLIDES` without changing the built HTML; the SWE5006 deck adds `rubric` (the rubric rows a slide covers).

### Adding, removing or moving a slide

- Main slides are numbered 1…N without gaps, in presentation order. `SLIDES[].n` is also the key of the slide's function in the `slides-main.js` export, so renumbering means renaming those keys (and any `#n` links or slide numbers quoted in cues, Q&A, backups and the script).
- Update `darkSlides` in `deck.config.js` whenever the title or closing slide number changes; otherwise a content slide renders full-bleed dark and the closing slide renders as a normal slide.
- Clock times are not derived: each segment has an absolute `start`. When a segment's slots grow beyond `talk` minutes, raise `talk` and shift the `start` of every later segment. A `slot: null` slide (Q&A) is clocked at its segment's `start + talk`.
- Then rebuild the script and read the run sheet: it compares each segment's talk budget, slot total and estimated speaking time.

`src/script.js` starts with `require('../../../presentation-kit/script/build').helpers()`, which returns `{ C, cfg, md, esc, box, tbl, pre, reqBox, slideCard, qaTable, segment, cover, runSheet, clock, est, hms, toS, short, markers, countM, overs }`, and exports `cover(...) + '\n' + runSheet(after) + <chapters>`. `segment(seg, { tip, warn })` renders a whole segment chapter (clock, owner, requirement box, slide cards, Q&A). Markers inside slide cards and Q&A tables are counted automatically; call `countM(text)` for markers you write directly in a chapter, before rendering `markers`. `docs/swe5001/presentation/src/script.js` is the complete worked example.

## Draft markers

`{{FILL:text}}`, `{{VERIFY:text}}` and `{{CONFIRM:text}}` in any content string (or `mk('FILL', 'text')` in slide HTML) render as visible dashed chips on slides, in notes and in the script. FILL = a value that does not exist yet (never present a guess); VERIFY = a claim to check against the repo, report or run logs; CONFIRM = needs team agreement. They stay visible on purpose and must all be gone before the final rehearsal; the script build prints the remaining counts.

## Design rules

- Canvas 1280 × 720, light theme only; `.body` is the content area, 1164 × 548 px (58 px side margins, from 128 px to 44 px above the bottom). A full-width diagram is 1160 wide and at most about 548 high; SWE5001 pulls a 530-high diagram up with `<div class="dia" style="margin-top:-12px">` to clear the footer. Slides are positioned with inline styles; reuse the patterns in `docs/swe5001/presentation/src/slides-main.js` (cards `.card`, icon dots `.dot`, chips `.tag`/`.pill`, tables `table.t`, diagrams `.dia`).
- BlocBuy tokens from `deck/css.js`: ink `#1B1A18`, cream `#F6F2EC`, red `#E8402A` (accent), cobalt `#3E5CD9` (platform), amber `#FFB01F` (events), muted `#6B655C`. Accent fills never carry text: use the soft fill with the soft-foreground colour instead.
- Type: Inter only (400–700, 900 for the wordmark); JetBrains Mono for code identifiers and marker chips. Fonts are inlined, so the HTML works offline.
- Icons: Font Awesome Free names, `icon('users')`; `icon` throws on an unknown name.
- Diagrams follow [`diagrams/STYLE.md`](diagrams/STYLE.md).
- After a layout change, run `npm run screenshots`: each printed line is a slide with an element reaching into the bottom or right margin. Look at the PNGs; fix or justify every line.

## Presenter runtime (HTML deck)

Arrows, Space, Enter, PageUp/PageDown step through fragments (`<div class="frag" data-frag="1">`) and slides; Home/End; S opens the notes window (say, cues, hand-over, slot, timer); T starts the timer; O toggles the overview grid; F fullscreen; B jumps to the backup index; `data-go="B6"` on any element links to that slide; `#23` or `#B6` in the URL opens a slide directly.

## Changing the kit

A kit change affects every deck. After any change here, rebuild every deck and compare its HTML before and after: the deck HTML against the committed `dist/` (`npm --prefix docs/swe5001/presentation run build:deck`, then `git diff --stat -- docs/swe5001/presentation/dist`, and the same for each other deck), and the script HTML by writing it with `SCRIPT_HTML` before and after the change. An HTML change in a deck you did not mean to change is a regression. The SWE5001 extraction was accepted only with byte-identical HTML, so keep that bar for pure refactors.

Chromium is pinned through `playwright` 1.64.0. A different Chromium version changes PDF anti-aliasing and file size, not content; compare HTML, page count and text rather than PDF bytes.

## Working from the Claude macOS app

The app may not be able to run `npm` or a browser. In that case edit the sources only, never hand-edit `dist/`, and ask the person to run `npm run all` in the deck folder (or open a Claude Code session in the repository) before committing.
