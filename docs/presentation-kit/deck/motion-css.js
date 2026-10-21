// Motion styles, appended to the deck CSS only when deck.config.js sets motion: true (see MOTION.md).
// Ported from the SWE5001 "v3" deck (1920 x 1080 canvas) with every length scaled by 2/3 for the 1280 x 720 canvas;
// durations, delays and easing are unchanged. Every hidden start state is scoped to body.present inside
// @media screen, so overview mode (body.overview), print/PDF and screenshots (window.__PRINT__, print media)
// always show the final, fully visible state.
module.exports = `
:root{--ease:cubic-bezier(.2,.75,.2,1)}
.cn{fill:none;stroke-linecap:round;stroke-linejoin:round}
.cdot{transform-box:fill-box;transform-origin:center}
.cover{position:absolute;background:var(--surface);z-index:3;display:none}
@media screen{
  /* slide crossfade: visibility/opacity instead of display, so entrance transitions can run */
  body.present .slide{display:block;visibility:hidden;opacity:0;transition:opacity .45s var(--ease),visibility 0s linear .45s;pointer-events:none}
  body.present .slide.active{visibility:visible;opacity:1;transition:opacity .45s var(--ease);pointer-events:auto}
  /* entrances: class a + data-a + inline --d */
  body.present .a{opacity:0;transition:opacity .65s var(--ease),transform .65s var(--ease);transition-delay:var(--d,0ms)}
  body.present .a[data-a=up]{transform:translateY(14.67px)}
  body.present .a[data-a=left]{transform:translateX(-20px)}
  body.present .a[data-a=right]{transform:translateX(20px)}
  body.present .a[data-a=pop]{transform:scale(.88)}
  body.present .a[data-a=grow]{transform:scaleX(0);transform-origin:left center}
  body.present .a[data-a=fade]{transform:none}
  body.present .a[data-a=rise]{transform:scaleY(0);transform-origin:bottom center;transition-duration:.9s}
  body.present .a[data-a=drop]{transform:translateY(-18.67px)}
  body.present .slide.active .a{opacity:1;transform:none}
  /* fragments: unrevealed ones drop back without delay; plain fragments fade in .25s, .frag.a ones use their entrance */
  body.present .frag:not(.a){transition:none}
  body.present .slide.active .frag:not(.a){transition:opacity .25s var(--ease),transform .25s var(--ease)}
  body.present .frag.hidden{opacity:0!important;transform:translateY(10.67px)!important;transition-delay:0ms!important}
  /* covers: an opaque panel over content that fades away when its fragment is reached */
  body.present .cover{display:block;opacity:1;transition:none}
  body.present .slide.active .cover{transition:opacity .5s var(--ease)}
  body.present .cover.on{opacity:0}
  /* SVG connectors (pathLength="1") draw on; .dashed keeps its own dasharray and only fades in */
  body.present .cn{stroke-dasharray:1 1;stroke-dashoffset:1;opacity:0;transition:stroke-dashoffset .9s var(--ease),opacity .3s;transition-delay:var(--d,0ms)}
  body.present .cn.dashed{stroke-dashoffset:0}
  body.present .slide.active .cn{stroke-dashoffset:0;opacity:1}
  /* chart line and dots */
  body.present .cline{stroke-dasharray:1 1;stroke-dashoffset:1;transition:stroke-dashoffset 1.4s var(--ease) var(--d,0ms)}
  body.present .cdot{opacity:0;transform:scale(0);transition:all .4s var(--ease) var(--d,0ms)}
  body.present .slide.active .cline{stroke-dashoffset:0}
  body.present .slide.active .cdot{opacity:1;transform:none}
  /* table rows */
  body.present .trow{opacity:0;transform:translateY(5.33px);transition:all .5s var(--ease) var(--d,0ms)}
  body.present .slide.active .trow{opacity:1;transform:none}
  /* looping emphasis */
  body.present .pulse{animation:pulse 3s var(--ease) infinite}
  body.present .recdot{animation:rec 1.6s infinite}
}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(27,26,24,.25)}50%{box-shadow:0 0 0 12px rgba(27,26,24,0)}}
@keyframes rec{0%{box-shadow:0 0 0 0 rgba(184,50,31,.55)}100%{box-shadow:0 0 0 9.33px rgba(184,50,31,0)}}
`;
