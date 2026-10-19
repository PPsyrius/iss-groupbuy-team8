// Deck styles: BlocBuy token structure (design-screens.md §2, light-only) with values taken from the
// BlocBuy logo (blocbuy-logo.svg): ink #1B1A18, cream #F6F2EC, red #E8402A ("BUY"), cobalt #3E5CD9,
// amber #FFB01F, gold-brown #8A6A2A (tagline). Roles: platform services = cobalt, business application = red,
// events/messaging = amber, externals = warm neutral. Text-on-tint colours darkened where needed for 4.5:1.
module.exports = `
:root{
  /* BlocBuy tokens, logo palette */
  --background:#F6F2EC; --surface:#FFFFFF; --surface-secondary:#EFE9DF; --surface-tertiary:#E7E0D4;
  --foreground:#1B1A18; --muted-t:#6B655C; --border:#DDD5C8; --default:#ECE6DC;
  --accent:#E8402A; --accent-soft:rgba(232,64,42,.15); --accent-soft-foreground:#B02E1B;
  --success-soft-foreground:#3E5CD9; --warning-soft-foreground:#7A5C20; --danger-soft-foreground:#8C2414;
  --focus:#E8402A; --inverse:#1B1A18;
  /* derived tints */
  --accent-soft-solid:#FBDFD9; --accent-softer:#FDEFEC;
  --success-soft:#E3E8FB; --warning-soft:#FFEDC7; --danger-soft:#EFE9DF;
  --radius:8px; --radius-overlay:12px;
  --shadow-surface:0 1px 2px rgba(27,26,24,.06),0 1px 1px rgba(27,26,24,.04);

  /* deck roles -> tokens (legacy names kept so slide markup stays stable)
     platform services = success family · business application = accent family
     events/messaging = warning family · externals = neutral */
  --teal:var(--success-soft-foreground); --teal-d:var(--foreground); --teal-dd:var(--foreground);
  --teal-l:var(--success-soft); --teal-ll:var(--surface-secondary);
  --orange:var(--accent); --orange-d:var(--accent-soft-foreground); --orange-l:var(--accent-soft-solid); --orange-ll:var(--accent-softer);
  --yellow:var(--warning-soft); --yellow-l:var(--warning-soft); --peach:var(--accent-softer); --cream:var(--warning-soft);
  --ink:var(--foreground); --ink2:var(--foreground); --ink3:var(--muted-t); --muted:var(--muted-t); --line:var(--border);
  --green:var(--success-soft-foreground); --green-l:var(--success-soft); --red:var(--danger-soft-foreground); --red-l:var(--danger-soft);
  --slate:var(--muted-t); --card:var(--surface);
}
/* design-screens.md §7 "Muted contrast": muted only on white surfaces; on the canvas and on tinted
   panels secondary text uses foreground (or the panel's own soft foreground, set inline). */
.slide{--ink3:var(--foreground); --muted:var(--foreground)}
.card, table.t, .on-surface{--ink3:var(--muted-t); --muted:var(--muted-t)}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:#11100F}
body{font-family:Inter,"Helvetica Neue",Helvetica,Arial,sans-serif;color:var(--foreground);-webkit-font-smoothing:antialiased;font-variant-numeric:tabular-nums}
.slide{width:1280px;height:720px;position:relative;overflow:hidden;background:var(--background)}
.slide.dark{color:#fff;background:var(--inverse)}
.slide.dark:before{content:'';position:absolute;left:0;right:0;bottom:0;height:8px;background:linear-gradient(90deg,#E8402A 0 33.3%,#FFB01F 33.3% 66.6%,#3E5CD9 66.6%)}
#s1.dark:before{}
.hdr{position:absolute;left:56px;top:26px;display:flex;align-items:center;gap:10px;
  font:600 14px/1 Inter;letter-spacing:.08em;text-transform:uppercase;color:var(--foreground)}
.hdr .it{font:600 12px/1 Inter;letter-spacing:0;text-transform:none;background:var(--default);color:var(--foreground);border-radius:999px;padding:5px 9px}
.who{position:absolute;right:56px;top:20px;display:flex;align-items:center;gap:8px;font:600 15px Inter;color:var(--foreground)}
.who .av{width:30px;height:30px;border-radius:50%;background:var(--foreground);color:#fff;font:600 11px/30px Inter;text-align:center;letter-spacing:.02em}
.ttl{position:absolute;left:56px;right:56px;top:56px;font:700 34px/1.18 Inter;color:var(--foreground);letter-spacing:-.02em}
.body{position:absolute;left:58px;right:58px;top:128px;bottom:44px}
.foot{position:absolute;left:56px;right:56px;bottom:16px;height:20px;display:flex;align-items:center;gap:16px;font:500 12px Inter;color:var(--foreground)}
.foot .brand{white-space:nowrap;display:flex;gap:8px;align-items:center}
.foot .brand b{font-weight:700;letter-spacing:-.01em}
.foot .seg{white-space:nowrap;width:120px;color:var(--accent-soft-foreground);font-weight:600}
.foot .bar{flex:1;display:flex;gap:4px;height:6px}
.foot .bar span{display:block;border-radius:999px;background:var(--surface-tertiary)}
.foot .bar span.done{background:var(--accent-soft-solid)}
.foot .bar span.on{background:var(--accent)}
.foot .pg{width:60px;text-align:right;font:600 14px Inter;color:var(--foreground)}
.foot .pg small{font-weight:400;color:var(--foreground);opacity:.75;font-size:13px}
.ref{position:absolute;right:58px;bottom:42px;font:500 13px Inter;color:var(--foreground);letter-spacing:.02em}

/* generic pieces */
.card{background:var(--surface);border-radius:var(--radius);box-shadow:var(--shadow-surface);border:1px solid var(--border);position:relative}
.card.flat{box-shadow:none}
.serif{font-family:Inter,"Helvetica Neue",Helvetica,Arial,sans-serif;font-weight:700;letter-spacing:-.015em}
.ic{width:1em;height:1em;display:inline-block;vertical-align:-.125em}
.dot{flex:none;display:flex;align-items:center;justify-content:center;border-radius:50%;color:#fff}
.dot .ic{width:46%;height:46%}
.pill{display:inline-flex;align-items:center;justify-content:center;border-radius:999px;font:600 19px Inter;color:#fff;background:var(--success-soft-foreground);padding:0 22px}
.pill.biz{background:var(--accent-soft-solid);color:var(--accent-soft-foreground)}
.pill.neutral{background:var(--default);color:var(--foreground)}
.mono{font-family:'JetBrains Mono',ui-monospace,monospace}
ul.b{list-style:none}
ul.b li{position:relative;padding-left:22px;margin:0 0 10px}
ul.b li:before{content:'';position:absolute;left:4px;top:.55em;width:7px;height:7px;border-radius:50%;background:var(--foreground)}
.mk{display:inline-block;font:600 12.5px/1.25 'JetBrains Mono',monospace;border-radius:999px;padding:2px 8px;vertical-align:.12em;letter-spacing:-.01em;white-space:normal}
.mk-fill{background:var(--danger-soft);color:var(--danger-soft-foreground);border:1.5px dashed var(--danger-soft-foreground)}
.mk-verify{background:var(--warning-soft);color:var(--warning-soft-foreground);border:1.5px dashed var(--warning-soft-foreground)}
.mk-confirm{background:var(--default);color:var(--foreground);border:1.5px dashed var(--muted-t)}
.cap{font:500 17px Inter;color:var(--foreground);text-align:center}
.tag{display:inline-block;font:600 12px/1 Inter;letter-spacing:.02em;color:var(--foreground);background:var(--default);border-radius:999px;padding:5px 9px}
.tag.o{color:var(--accent-soft-foreground);background:var(--accent-soft-solid)}
table.t{border-collapse:separate;border-spacing:0;width:100%;font:400 16px/1.3 Inter;background:var(--surface);border-radius:var(--radius);overflow:hidden;box-shadow:var(--shadow-surface);border:1px solid var(--border)}
table.t th{background:var(--surface-secondary);color:var(--foreground);font:600 14px Inter;text-align:left;padding:9px 12px;border-bottom:1px solid var(--border)}
table.t td{padding:8px 12px;border-top:1px solid var(--border);vertical-align:top}
table.t tr:nth-child(2) td{border-top:none}
table.t td.id{font:600 15px 'JetBrains Mono',monospace;color:var(--foreground);white-space:nowrap}
.dia{display:flex;justify-content:center}
.dia svg{display:block}

/* presentation runtime */
@media screen{
  body.present{overflow:hidden;height:100vh}
  body.present .slide{display:none;position:absolute;left:50%;top:50%;transform-origin:center center}
  body.present .slide.active{display:block}
  body.present .frag.hidden{opacity:0}
  body.present .frag{transition:opacity .25s}
  body.overview{overflow:auto;background:#11100F}
  body.overview .deck{display:flex;flex-wrap:wrap;gap:18px;padding:18px;justify-content:center}
  body.overview .slide{display:block!important;position:relative!important;left:auto!important;top:auto!important;transform:scale(.24)!important;transform-origin:top left!important;margin:0 -973px -547px 0;cursor:pointer;outline:3px solid transparent}
  body.overview .slide.active{outline-color:#E8402A}
}
@media print{
  @page{size:1280px 720px;margin:0}
  html,body{background:#fff}
  .slide{page-break-after:always;break-after:page}
  .frag{opacity:1!important}
}
`;
