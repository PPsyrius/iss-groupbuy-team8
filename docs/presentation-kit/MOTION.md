# Motion

Entrance choreography for the HTML deck, ported from the SWE5001 "v3" deck (a 1920 × 1080 site built outside the repository). The vocabulary is the v3 markup, unchanged; every length is scaled by 2/3 for the kit's 1280 × 720 canvas, and durations, delays and easing are kept as they were. Styles: [`deck/motion-css.js`](deck/motion-css.js); runtime: [`deck/runtime.js`](deck/runtime.js).

## Engine

### Opt-in

`motion: true` in a deck's `deck.config.js` (default `false`) does two things in `deck/build.js`:

- appends `deck/motion-css.js` to the page CSS;
- adds the chrome choreography to every main and backup slide (dark slides have no chrome).

Without it the vocabulary below has no effect: `.a` elements are simply visible and slides switch without a crossfade. The presenter keys, the click navigation, the key hint and the `.` blank screen work in every deck.

With motion on, slides crossfade (opacity 0.45 s, `visibility` instead of `display`, so the scaled, absolutely positioned slides keep their layout). Every entrance plays when a slide becomes `.active` and replays on each visit: leaving a slide returns its elements to their start state.

### Easing and delays

- `--ease: cubic-bezier(.2,.75,.2,1)` on `:root`, used by every transition.
- `--d` is an inline custom property on the element, in milliseconds after the slide becomes active: `style="--d:320ms"`. It defaults to `0ms`. Delays are not generated: each element carries its own.

### Entrances: `class="a" data-a="…"`

All use opacity 0 → 1 plus the transform below, over 0.65 s (`rise`: 0.9 s), starting `--d` after the slide activates.

| `data-a` | Start state (kit px) | Use |
|---|---|---|
| `fade` | opacity only | chrome, tables, quiet elements |
| `up` | `translateY(14.67px)` (v3: 22px) | titles, cards, text rows |
| `drop` | `translateY(-18.67px)` (v3: -28px) | items falling into place |
| `left` | `translateX(-20px)` (v3: -30px) | element arriving from the left |
| `right` | `translateX(20px)` (v3: 30px) | element arriving from the right |
| `pop` | `scale(.88)` | dots, icons, hubs |
| `grow` | `scaleX(0)`, origin left centre | bars, rules, progress |
| `rise` | `scaleY(0)`, origin bottom centre, 0.9 s | columns of a bar chart |

```html
<div class="card a" data-a="up" style="position:absolute;left:0;top:40px;width:360px;--d:200ms">…</div>
```

### Other classes

| Class | Markup (kit coordinates) | Behaviour |
|---|---|---|
| `cn` | `<path class="cn" pathLength="1" d="M400,200 L460,200" style="stroke:#1B1A18;stroke-width:3px;--d:700ms" marker-end="url(#ah)"/>` | connector draws on: `stroke-dasharray:1 1`, offset 1 → 0 over 0.9 s and opacity over 0.3 s, after `--d`. `pathLength="1"` is required. |
| `cn dashed` | `<path class="cn dashed" style="stroke:#C9C1B4;stroke-width:1.33px;stroke-dasharray:6.67 6;--d:500ms" d="…"/>` | keeps its own inline dash pattern and only fades in. Leave `pathLength` off: with `pathLength="1"` the dash lengths are measured in path units and the line renders solid (v3 had this bug on its dashed lifelines) |
| `cline` | `<path class="cline" pathLength="1" style="--d:500ms" d="M630,233 L691,288 …" stroke="#A39D94" stroke-width="3.33" fill="none"/>` | chart line draws on over 1.4 s |
| `cdot` | `<circle class="cdot" style="--d:600ms" cx="630" cy="233" r="4.67"/>` | chart dot pops from `scale(0)` over 0.4 s; stagger the dots after the line |
| `trow` | `<tr class="trow" style="--d:300ms">…</tr>` | table row: `translateY(5.33px)` (v3: 8px) and opacity over 0.5 s; works in any table (v3 needed a `.tbl` parent) |
| `pulse` | `<div class="dot a pulse" data-a="pop" style="--d:420ms">` | endless 3 s ring: `box-shadow` spread 0 → 12px (v3: 18px), `rgba(27,26,24,.25)` |
| `recdot` | `<div class="a recdot" data-a="fade" style="width:14.67px;height:14.67px;border-radius:50%;background:#B8321F;--d:1100ms">` | endless 1.6 s recording ring: spread 0 → 9.33px (v3: 14px) |
| `cover` | `<div class="cover" data-frag="1" style="left:211px;top:393px;width:559px;height:39px"></div>` | opaque panel (`var(--surface)`, absolute, z-index 3) over content; fades out over 0.5 s when fragment step 1 is reached (gains `.on`). Override `background` inline to match a tinted panel. |

### Fragments

Fragments stay the kit model: `<div class="frag" data-frag="1">`, revealed by → in `data-frag` order (`[click]` in the `say` text marks each step). With motion on, an unrevealed fragment is `opacity:0; translateY(10.67px)` (v3: 16px) and hides without delay; a plain `.frag` reveals over 0.25 s; a `.frag.a` element reveals with the entrance duration and its own `--d` (measured from the click), but always from the fragment's translate, whatever its `data-a`. Children of a fragment cannot stagger after the click: their entrances already played while it was hidden. A `.cover[data-frag]` counts as a fragment step too. When the presenter arrives on a slide, its fragment state is set before the slide activates, so unrevealed fragments never flash.

### Chrome timings

`deck/build.js` adds, on every main and backup slide when motion is on:

| Element | Markup | Timing |
|---|---|---|
| header kicker `.hdr` | `class="hdr a" data-a="fade" style="--d:0ms"` | fade, 0 ms |
| speaker chip `.who` | `class="who a" data-a="fade" style="--d:0ms"` | fade, 0 ms |
| title `.ttl` | `class="ttl a" data-a="up"`, `--d:60ms` in its style | up, 60 ms |
| footer `.foot` | none | static, as in v3 |

Body choreography therefore usually starts at about 120–200 ms.

### Overview, print, PDF and screenshots

Every hidden start state and every loop is scoped to `body.present` inside `@media screen`. The runtime adds `body.present` only in presenter mode, so:

- overview (O, `body.overview`) shows every slide in its final state;
- `render.js` sets `window.__PRINT__`, which skips presenter mode, and prints (PDF) or emulates print media (screenshots): all content is visible, covers are hidden, nothing loops;
- printing the live deck from the browser uses print media and is complete too.

The key hint and the blank overlay are hidden in print.

## Authoring rule

Choreography is authored per element, by a human or an AI agent. The engine does not stagger anything automatically beyond the chrome: an element without `class="a"` appears with the slide. Reuse a pattern from the list below by role (for example "card row: `up`, 120 ms apart from 200 ms"). Whenever a slide gets a new or unique pattern, add an entry here in the same commit.

## Hand-composed patterns

Each entry names the pattern, the slides using it, the element roles, the `data-a` types and the delay rhythm (first `--d` and step). The entries are filled from the v3 deck. Slide references are `<deck> <slide>`, e.g. `5001 12` or `5001 B13`. "d" is the element's own first delay; "+n" is relative to it. `5006` references are the current 39-slide deck of the rubric re-plan (#600); a row whose slides read — was used only by the retired 22-slide #566 deck that #597 choreographed, and stays as a recipe.

### Overall rhythm

- Chrome at 0 / 60 ms; body choreography starts at about 150–300 ms.
- Reading order drives the delays: top-left to bottom-right, column by column where a slide is built in columns.
- Most slides settle by 700–1000 ms; busy slides run to about 1600 ms. A step of 30–60 ms reads as one gesture, 80–170 ms as a sequence.
- A late footnote or caption closes the slide with `up` after everything else.
- Connectors draw after both elements they join have arrived.

### Reusable patterns

| Pattern | Slides | Roles → `data-a` | Delay rhythm |
|---|---|---|---|
| Icon + text row | 5001 2, 5, 8, 18, 19, 5006 2 | dot or icon → `pop`; its text → `up` | text at the pop's d +6…+40; rows step 70–120 |
| Icon card | 5001 8, 12, 15, 17, 21, 23, B10, B11, 5006 4 | card → `up`; icon or number → `pop`; title → `up`; body → `up`; tag chip → `pop`; optional fix panel → `fade` + text `up` | icon +23…+60, title +34…+90, body +91…+125, tag +110…+203; cards step 74–160 from about 200 |
| Card column with header band | 5001 4 | card → `up`; band (+ square patch) → `fade`; band icon → `pop`; name → `up`; list → `up` | card d at 200, +120 per column; band d, icon d+60, name d+80, list d+160 |
| Side columns | 5001 2, 5, 11, 18, 22, 5006 2, 3, 4 | left column → `left`; right column → `right`; middle column → `up`; heading → `up`; list → `up` | columns 200–300 (right column may come much later, e.g. 600); heading +12…+50; list +59…+65; tick rows inside: pop from 300–500, +80 per row, text +30 |
| Box then text | 5001 3, 10, 11, 14, B5, B9, B11, B13, B14 | panel background → `fade`, `pop` or `left`; its text → `up` | text at the box's d +2…+12; sibling boxes step 30–90 |
| Table with rows | 5001 11, B1a, B1b, B2, B5, B7, B8, B9, B12, 5006 3, 5 | heading → `up`; table → `fade`; body rows → `trow` | table 246, rows 368 then +38.4 each (406, 445, 483, …); on slide 11 heading 449, table 485, rows 608 +38 |
| Trailing callout | 5001 B5, B9, 19, 23, 5006 5 | dark panel → `fade` or `up`; bar or `recdot` → `fade`; text → `up` | at the end of what it comments on (after a table: 560–607; on a full slide: 1100); text +4…+50 |
| Footnote or caption | 5001 3, 4, 6, 9, B6, B12, BX | closing line → `up` | last on the slide: 649–1500 |
| Stat strip | 5001 14, 19, 20, 24 | box → `up`; value and caption → `up` | content +40…+50 (caption +46); boxes step 42–120, from 600–1000 |
| Connector between elements | 5001 2, 3, 7, 10, 18, 20, 22, B10, B13, B14, 5006 2 | arrow → `cn` | after both ends have arrived; chains step +56…+90; pipeline stage arrows at stage +120 |
| Process chain | 5001 7, 20, B11 | panel → `fade`; heading → `up`; steps → `pop` (stage) or `fade` + text `up`; connectors → `cn` | panel about 280–430, heading +4…+23; steps +29…+170 apart; each connector after its pair |
| Bars and timeline | 5001 6 | rail → `cn`; milestone dot → `pop` + date `up`; label → `up`; bar → `grow`; value → `up` | rail 150; milestones 300 +110, label +40; bar rows 700 +110: name d, bar d+60, description d+100, value d+200 |
| Band list | 5001 12, 17 | dark band → `up`; check or icon → `pop`; text → `up` | bands 800–900, step 50–150; icon +60, text +80 |
| Diagram slide | 5001 9, 13, 16, B3, B4, B6 | diagram wrapper → `fade`; caption or side text → `up`; side box background → `fade` + text `up` | diagram 200; side list about 250–350; box 594–670; caption last (672–900) |
| Index grid | 5001 BX | per row: card background → `up`, tag → `pop`, title → `up` | 100, +30 per row, all three at the same delay |
| Use case band | — | three band cards (use case, analysis → design, design problem) → `up` | 150, +80 each; the slide's cards follow from about 400 |
| Line chart | 5001 14 | chart → `fade`; solid series → `cline`; dots → `cdot`; dashed (ideal) series → `fade`; legend → `up` | chart d; dashed series +40…+250; solid line +100…+350; dots after the line, +50…+60 each; two charts side by side start 150 apart |

### One-off patterns

| Pattern | Slide | Recipe |
|---|---|---|
| Title lockup | 5001 1, 5006 1 | bag `pop` and wordmark `up` 200, kicker `up` 213, tagline 320, headline 420, subline 520, names `fade` 650, programme `fade` 700, caption `up` 1500; 5006 1 adds its section roadmap `fade` 600 and runs names 680, programme 740 |
| Bar and avatar chart | 5001 1 | bars `rise` 500 +160; each avatar `drop` 300 after its bar (800 +160) |
| Hub with satellites | 5001 3 | note boxes `fade` (text +3…+4); side cards `left`/`right`; hub ring `pop` 380, disc `pop` + `pulse` 420, label 500, text 560; connectors `cn` 745 / 826 / 900; pill row `pop` 1000 +90; footnote 1300 |
| Service panels with event mapping | 5001 10 | panels `fade` 254 / 535 / 605, header +9, inner card `fade` +42…+51, card text +2; mapping rows: domain pill `pop` 450 +64, integration pill `pop` 690 +64, arrow `cn` 728 +63 |
| Pill ribbon | 5001 11 | dark band `up` 645, label +4, pills `pop` 1009 then about +90 |
| Two-series line chart | 5001 14 | chart `fade` 300; line 1 `cline` 500, dots `cdot` 600 +60; line 2 800, dots 900 +60 |
| Inline state diagram | — | SVG with its nodes `fade` 440; each transition `<line>` `cn` 540, then +60…+80; transition labels `fade` at their line +60 |
| Sequence draw-on | — | SVG with lifeline heads `fade` 480; solid messages `cn` 600 +100; dashed messages, crash mark and labels `fade` at their message +60; final note last (1100) |
| Twin burndown charts | — | per column (150 / 300): heading `up`, subtitle +50, chart `fade` +150, dashed ideal +250, actual `cline` +350, dots `cdot` +450 then +50, legend +550, comment card +1000, source +1150 |
| Stacked cylinders | 5001 15 | cylinder `up` 244 / 318 / 393, name +53, contents +98 |
| Branch diagram | 5001 18 | main line `cn` 120, release line 300; feature arrows `cn` 200 +80 ×6; labels `up` 248–519 top to bottom; promotion arrows 700 / 950, each with a label +100; pills `pop` 564 / 634 |
| Nested boundary boxes | 5001 22 | left cards `left` (200–400) with dot `pop` +18…+20; nested boxes `fade` 150 / 300 / 450 / 600, outer to inner, heading +20, tags `pop` +29…+125; inner tiles `fade` 700 / 760 / 850; arrows `cn` 587 / 718 |
| Closing slide (dark) | 5001 24, 5006 39 | headline `up` 150, sub 300, kicker 420; takeaway pills `pop` 600 +110 ×4; stat strip from 1000; footer icon + text 1400; 5006 39, shortened: headline 150, sub 300, footer `up` with its icon `pop` 520 |
| Click-revealed diagram | 5001 16 | the kit SVG's own `.frag` groups, renumbered when the slide is built (the kit file is unchanged): fragment 1 loses its `frag` class and fades in with the drawing, fragments 2 and 3 become click 1 and 2 to match the two `[click]` markers (v3 faded white covers out) |
| Sequence lanes | 5001 B15 | lane headers `pop` 150 +70, text +20; lifelines `fade` 500; step arrows `grow` 60; labels 120; final line `up` 300 (v3 timings, arrows before headers) |

### Authoring techniques

- **Split a box from its content** when the background and the text enter differently. Use an absolute background layer plus a content layer that keeps the original border width as a transparent border, so the settled frame does not move. Nested `.a` children work too: transforms then combine during the entrance, but the settled frame is the same.
- **Arrows that draw on** must be SVG. Turn CSS-border arrows into a `cn` path with a marker of the same geometry, e.g. `arrCn()` and `s18Line()` in `docs/swe5001/presentation/src/slides-main.js`. Set `stroke-linecap:butt` (or square) inline, because `.cn` sets round caps. The marker head shows while the shaft draws; v3 does the same.
- **Filled icons** (Font Awesome arrows, chevrons) cannot draw on: `.cn` sets `fill:none`. Give the wrapper `grow` or `fade` at the connector delay instead (5001 B10, B11, 20).
- **Placing elements from a 1920 × 1080 source:** `site(l, t, w, h)` in `docs/swe5001/presentation/src/site-geometry.js` converts a v3 box (× 2/3, minus the `.body` offset 58, 128). `fill(svg(name), viewBox)` in the same file makes a kit diagram fill such a box, showing the part of the drawing given by `viewBox`.
- **Shared helpers** take motion as an optional trailing parameter whose default keeps the old output (`row`, `tick`, `box`, `ln`); `tbl()` in `slides-backup.js` adds the standard table rhythm.
- **Animating a helper you should not change:** inject the entrance into the first tag of its output, or into the tag containing a needle string, instead of adding parameters (`gA` and `hAt`/`hIn` in `docs/swe5006/presentation/src/slides-main.js`).
- **Dashed lines** (ideal series, "creates" dependencies, async messages) fade at the delay their solid neighbours draw on; see `cn dashed`.
- **Check a port** with a settled-frame pixel diff (`npm run screenshots` before and after) plus screenshots at 300 and 900 ms after arriving on the slide.
