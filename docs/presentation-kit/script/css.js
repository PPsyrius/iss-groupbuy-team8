// A4 speaker-script styles: the same BlocBuy tokens as the deck (deck/css.js), print sizes in pt.
module.exports = `
/* BlocBuy design tokens (design-screens.md §2): Inter, light-only, 8px surfaces / 12px overlays, full-radius chips. */
:root{--background:#F6F2EC;--surface:#FFFFFF;--surface-secondary:#EFE9DF;--surface-tertiary:#E7E0D4;--foreground:#1B1A18;--muted:#6B655C;--border:#DDD5C8;--default:#ECE6DC;--accent:#E8402A;--accent-soft:#FBDFD9;--accent-soft-foreground:#B02E1B;--success-soft:#E3E8FB;--success-soft-foreground:#3E5CD9;--warning-soft:#FFEDC7;--warning-soft-foreground:#7A5C20;--danger-soft:#EFE9DF;--danger-soft-foreground:#8C2414}
@page{size:A4;margin:18mm 17mm 18mm 17mm}
body{font:10.4pt/1.5 Inter,"Helvetica Neue",Helvetica,Arial,sans-serif;color:var(--foreground);margin:0;font-variant-numeric:tabular-nums}
h1{font:700 21pt/1.2 Inter;letter-spacing:-.02em;color:var(--foreground);margin:0 0 8pt;padding-top:6pt;position:relative}
h1:before{content:'';display:block;width:34pt;height:4pt;border-radius:99px;background:var(--accent);margin-bottom:8pt}
h2{font:600 13.5pt Inter;letter-spacing:-.01em;color:var(--foreground);margin:16pt 0 6pt;break-after:avoid;page-break-after:avoid}
h3{font:600 11pt Inter;color:var(--foreground);margin:12pt 0 5pt;break-after:avoid;page-break-after:avoid}
.chap{page-break-before:always;break-before:page}
table{border-collapse:separate;border-spacing:0;width:100%;margin:4pt 0 8pt;font-size:8.9pt;line-height:1.38;border:1px solid var(--border);border-radius:8px;overflow:hidden}
th{background:var(--surface-secondary);text-align:left;padding:4.5pt 6pt;border-bottom:1px solid var(--border);font-weight:600}
td{padding:4.5pt 6pt;border-top:1px solid var(--border);vertical-align:top}
tr:nth-child(2) td{border-top:none}
td.n,th.n{white-space:nowrap}
table.meta{border:none;border-radius:0}.meta td{border:none;padding:2pt 8pt 2pt 0}.meta td:first-child{font-weight:600;white-space:nowrap;width:90pt}
.box{border:1px solid var(--border);background:var(--surface-secondary);padding:7pt 10pt;margin:8pt 0;border-radius:8px}
.box.req{background:var(--surface-secondary)}.box.tip{background:var(--success-soft);border-color:#C5CFF4}.box.warn{background:var(--danger-soft);border-color:#CDBFB0}
.box b.h{display:block;margin-bottom:2pt;font-weight:600}
.box.tip b.h{color:var(--success-soft-foreground)}.box.warn b.h{color:var(--danger-soft-foreground)}.box.req b.h{color:var(--foreground)}
.seghead{background:var(--surface-secondary);border-radius:8px;padding:6pt 10pt;font-size:9.6pt}
.card{border:1px solid var(--border);border-radius:8px;margin:9pt 0;break-inside:avoid;page-break-inside:avoid;background:var(--surface);overflow:hidden}
.card .hd{background:var(--foreground);color:#fff;font-weight:600;padding:5pt 9pt;display:flex;justify-content:space-between}
.card.demo .hd{background:var(--accent-soft);color:var(--accent-soft-foreground)}
.card .hd small{font-weight:400}
.card .bd{padding:6pt 9pt}
.row{display:grid;grid-template-columns:62pt 1fr;gap:0 8pt;margin:2pt 0}
.lab{font-weight:600;color:var(--foreground)}.lab.say{color:var(--success-soft-foreground)}.lab.cue{color:var(--foreground)}.lab.ho{color:var(--accent-soft-foreground)}.lab.fb{color:var(--danger-soft-foreground)}
.say{font-style:italic}
.meter{font-size:8.3pt;color:var(--muted)}.over{color:var(--danger-soft-foreground);font-weight:600}
ul{margin:2pt 0 2pt 0;padding-left:14pt}li{margin:1pt 0}
code,.mono{font:8.6pt 'JetBrains Mono',monospace}
pre{font:8pt/1.45 'JetBrains Mono',monospace;background:var(--surface-secondary);border:1px solid var(--border);border-radius:8px;padding:7pt 9pt;white-space:pre-wrap;word-break:break-all;margin:4pt 0 8pt}
.mk{display:inline-block;font:600 7.8pt/1.3 'JetBrains Mono',monospace;border-radius:99px;padding:0 4pt}
.mk-fill{background:var(--danger-soft);color:var(--danger-soft-foreground);border:1px dashed var(--danger-soft-foreground)}.mk-verify{background:var(--warning-soft);color:var(--warning-soft-foreground);border:1px dashed var(--warning-soft-foreground)}.mk-confirm{background:var(--default);color:var(--foreground);border:1px dashed var(--muted)}
.clk{color:var(--accent-soft-foreground);font-style:normal}
.small{font-size:8.6pt;color:var(--muted)}
`;
