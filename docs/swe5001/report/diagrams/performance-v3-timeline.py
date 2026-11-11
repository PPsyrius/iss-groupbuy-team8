"""Render retained November V3 observations; no network or new measurements.

Run with Python 3 from any directory. Output is next to this editable source.
Uses only complete UTC minute buckets and integrates the nominal arrival schedule.
The original observer gap is shaded; no ECS task samples are reconstructed.
"""
import csv
from datetime import datetime
from html import escape
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
EVIDENCE = HERE.parent.parent / 'evidence' / '20261110-v3-autoscaling'
rows = list(csv.DictReader((EVIDENCE / 'minute-correlation.csv').open()))
context = json.loads((EVIDENCE / 'aws-correlation.json').read_text())
start = datetime.fromisoformat(context['interval']['start'].replace('Z', '+00:00'))
end = datetime.fromisoformat(context['interval']['end'].replace('Z', '+00:00'))
origin = start.replace(second=0, microsecond=0)

def elapsed(value):
    return (datetime.fromisoformat(value.replace('Z', '+00:00')) - origin).total_seconds() / 60

data = []
for row in rows:
    at = datetime.fromisoformat(row['at'].replace('Z', '+00:00'))
    if at >= start and at.timestamp() + 60 <= end.timestamp():
        data.append((row, (at - origin).total_seconds() / 60 + 0.5))
assert len(data) == 29
assert all(float(row['completed']) >= float(row['errors']) for row, _ in data)

def arrival(t):
    if t <= 300:
        return 40 + 160 * max(0, t) / 300
    if t <= 900:
        return 200 + 400 * (t - 300) / 600
    if t <= 1500:
        return 600
    return max(0, 600 * (1800 - t) / 300)

def offered(minute):
    # Integrate each linear segment exactly across its schedule boundaries.
    a = minute * 60 - (start - origin).total_seconds()
    b = a + 60
    cuts = sorted({a, b, *(t for t in (300, 900, 1500) if a < t < b)})
    return sum((arrival(x) + arrival(y)) * (y - x) / 2
               for x, y in zip(cuts, cuts[1:])) / 60

W, H = 470, 458
LEFT, RIGHT = 48, 458
PW = RIGHT - LEFT
parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}pt" height="{H}pt" viewBox="0 0 {W} {H}">',
         '<rect width="100%" height="100%" fill="white"/>',
         '<g font-family="Arial, sans-serif" font-size="8.5" fill="#1B1A18">']

def text(x, y, value, **attributes):
    attrs = ' '.join(f'{key.replace("_", "-")}="{value}"' for key, value in attributes.items())
    parts.append(f'<text x="{x:.2f}" y="{y:.2f}" {attrs}>{escape(str(value))}</text>')

def line(x1, y1, x2, y2, color='#b8bec5', dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ''
    parts.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" stroke="{color}" stroke-width="0.7"{extra}/>')

def x(t):
    return LEFT + PW * t / 30

gap = elapsed(context['ecs']['incompleteAfter'])
events = context['automaticScaleOutActivities']
text(LEFT, 12, '10 November 2026 SGT • failed AWS campaign', font_weight='bold')
text(LEFT, 26, 'Dashed verticals: scale-out activity starts; shading: original ECS observer missing')

def panel(index, title, maximum, ticks, series):
    top = 51 + index * 96
    height = 66
    text(LEFT, top - 7, title, font_weight='bold')
    parts.append(f'<rect x="{x(gap):.2f}" y="{top}" width="{RIGHT-x(gap):.2f}" height="{height}" fill="#f0ece5"/>')
    def y(value):
        return top + height * (1 - value / maximum)
    for tick in ticks:
        line(LEFT, y(tick), RIGHT, y(tick))
        text(LEFT - 5, y(tick) + 3, f'{tick:,}', text_anchor='end')
    for event in events:
        xx = x(elapsed(event['start']))
        line(xx, top, xx, top + height, '#6B655C', '3 3')
    for name, color, values, dash in series:
        points = ' '.join(f'{x(t):.2f},{y(v):.2f}' for t, v in values)
        extra = f' stroke-dasharray="{dash}"' if dash else ''
        parts.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="1.5"{extra}/>')
    for minute in (0, 5, 10, 15, 20, 25, 30):
        xx = x(minute)
        line(xx, top + height, xx, top + height + 3)
        label = f'{3 + (45 + minute)//60:02}:{(45 + minute)%60:02}'
        text(xx, top + height + 13, label, text_anchor='middle')

def values(column, divisor=1):
    return [(minute, float(row[column]) / divisor) for row, minute in data]

panel(0, 'Throughput (requests/s): blue = completed; grey dashed = offered reference', 650,
      (0, 200, 400, 600), [
          ('completed', '#3E5CD9', values('completed', 60), None),
          ('offered', '#6B655C', [(m, offered(m-0.5)) for _, m in data], '4 3')])
panel(1, 'Client p95 per minute (ms; not an average of percentiles)', 11000,
      (0, 5000, 10000), [('p95', '#3E5CD9', values('p95Ms'), None)])
panel(2, 'Commerce CPU per minute (%)', 110,
      (0, 50, 100), [('CPU', '#28744d', values('commerce_avg_cpu_pct'), None)])
panel(3, 'Counts per minute: red = browse failures; grey dashed = dropped arrivals', 18000,
      (0, 8000, 16000), [
          ('errors', '#bf3022', values('errors'), None),
          ('dropped', '#6B655C', values('dropped'), '4 3')])
text(LEFT, 449, 'Complete buckets 03:46–04:14; observer ended 04:03:47. No task-count interpolation.')
parts.extend(['</g>', '</svg>'])
output = HERE / 'performance-v3-timeline.svg'
output.write_text('\n'.join(parts) + '\n', encoding='utf-8')
print(f'{output.name}: {len(data)} complete minute buckets, {len(events)} scale-out starts')
