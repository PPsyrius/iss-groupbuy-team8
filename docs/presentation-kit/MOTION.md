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
| `cn dashed` | `<path class="cn dashed" pathLength="1" style="stroke:#C9C1B4;stroke-width:1.33px;stroke-dasharray:6.67 6;--d:500ms" d="…"/>` | keeps its own inline dash pattern and only fades in |
| `cline` | `<path class="cline" pathLength="1" style="--d:500ms" d="M630,233 L691,288 …" stroke="#A39D94" stroke-width="3.33" fill="none"/>` | chart line draws on over 1.4 s |
| `cdot` | `<circle class="cdot" style="--d:600ms" cx="630" cy="233" r="4.67"/>` | chart dot pops from `scale(0)` over 0.4 s; stagger the dots after the line |
| `trow` | `<tr class="trow" style="--d:300ms">…</tr>` | table row: `translateY(5.33px)` (v3: 8px) and opacity over 0.5 s; works in any table (v3 needed a `.tbl` parent) |
| `pulse` | `<div class="dot a pulse" data-a="pop" style="--d:420ms">` | endless 3 s ring: `box-shadow` spread 0 → 12px (v3: 18px), `rgba(27,26,24,.25)` |
| `recdot` | `<div class="a recdot" data-a="fade" style="width:14.67px;height:14.67px;border-radius:50%;background:#B8321F;--d:1100ms">` | endless 1.6 s recording ring: spread 0 → 9.33px (v3: 14px) |
| `cover` | `<div class="cover" data-frag="1" style="left:211px;top:393px;width:559px;height:39px"></div>` | opaque panel (`var(--surface)`, absolute, z-index 3) over content; fades out over 0.5 s when fragment step 1 is reached (gains `.on`). Override `background` inline to match a tinted panel. |

### Fragments

Fragments stay the kit model: `<div class="frag" data-frag="1">`, revealed by → in `data-frag` order (`[click]` in the `say` text marks each step). With motion on, an unrevealed fragment is `opacity:0; translateY(10.67px)` (v3: 16px) and hides without delay; a plain `.frag` reveals over 0.25 s; a `.frag.a` element reveals with its own `data-a` entrance and `--d` (measured from the click). A `.cover[data-frag]` counts as a fragment step too. When the presenter arrives on a slide, its fragment state is set before the slide activates, so unrevealed fragments never flash.

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

Each entry names the pattern, the slides using it, the element roles, the `data-a` types and the delay rhythm (first `--d` and step). The entries are filled from the v3 deck.
