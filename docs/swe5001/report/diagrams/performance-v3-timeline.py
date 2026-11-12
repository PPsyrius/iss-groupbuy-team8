"""Render only V3 R4 retained projections; no network or new measurements.

Python standard library only. --check compares the generated SVG without writing.
Client completion bins and UTC CPU minutes keep distinct time origins. Task dots
are observations, not interpolated transitions. Shading marks supplemental-only
service coverage after the canonical observer ends, not a repaired analyzer pass.
"""
import argparse
import csv
from datetime import datetime
from html import escape
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
EVIDENCE = HERE.parent.parent / 'evidence' / '20261111-v3-r4'
context = json.loads((EVIDENCE / 'verification.json').read_text(encoding='utf-8'))
start = datetime.fromisoformat(context['generatorResources']['coverage']['start'].replace('Z', '+00:00'))
end = datetime.fromisoformat(context['generatorResources']['coverage']['end'].replace('Z', '+00:00'))

def elapsed(value):
    return (datetime.fromisoformat(value.replace('Z', '+00:00')) - start).total_seconds() / 60

with (EVIDENCE / 'browse-minutes.csv').open(encoding='utf-8', newline='') as stream:
    data = [(row, int(row['completionMinuteSinceContainerStart']) + 0.5) for row in csv.DictReader(stream)]
assert len(data) == 30
assert sum(int(row['browseSamples']) for row, _ in data) == 725999
assert all(int(row['browseErrors']) == 0 for row, _ in data)
with (EVIDENCE / 'server-minutes.csv').open(encoding='utf-8', newline='') as stream:
    cpu = [(row, elapsed(row['minuteUtc']) + 0.5) for row in csv.DictReader(stream)
           if row['partialRunEdgeMinute'] == 'False' and row['commerceCpuAveragePercent'] != '']
observations = [json.loads(line) for line in (EVIDENCE / 'service-observations.jsonl').read_text(encoding='utf-8').splitlines()]
samples = [row for row in observations if 0 <= elapsed(row['observedAt']) <= (end - start).total_seconds() / 60]
assert len(cpu) == 29 and len(samples) == 200

def arrival(t):
    if t <= 300:
        return 40 + 160 * max(0, t) / 300
    if t <= 900:
        return 200 + 400 * (t - 300) / 600
    if t <= 1500:
        return 600
    return max(0, 600 * (1800 - t) / 300)

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
    return LEFT + PW * t / 30.05

gap = elapsed(context['scaling']['observationCoverage']['canonical']['lastObservedAt'])
events = context['scaling']['activities']
assert [event['desiredTarget'] for event in events] == [2, 3, 4, 6]
text(LEFT, 12, '11 November 2026 • V3 R4 • zero browse errors / drops', font_weight='bold')
text(LEFT, 26, 'Verticals: request-policy starts; light band: tail covered by supplemental samples')

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
        xx = x(elapsed(event['startTime']))
        line(xx, top, xx, top + height, '#6B655C', '3 3')
    for name, color, values, dash in series:
        if dash == 'dots':
            for t, value in values:
                fill = color if name == 'running' else 'white'
                parts.append(f'<circle cx="{x(t):.2f}" cy="{y(value):.2f}" r="1.5" fill="{fill}" stroke="{color}" stroke-width="0.7"/>')
        else:
            points = ' '.join(f'{x(t):.2f},{y(v):.2f}' for t, v in values)
            extra = f' stroke-dasharray="{dash}"' if dash else ''
            parts.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="1.5"{extra}/>')
    for minute in (0, 5, 10, 15, 20, 25, 30):
        xx = x(minute)
        line(xx, top + height, xx, top + height + 3)
        text(xx, top + height + 13, str(minute), text_anchor='middle')

def values(column, divisor=1):
    return [(minute, float(row[column]) / divisor) for row, minute in data]

panel(0, 'Throughput (requests/s): blue = completed; grey dashed = offered reference', 650,
      (0, 200, 400, 600), [
          ('completed', '#3E5CD9', values('browseSamples', 60), None),
          ('offered', '#6B655C', [(m, arrival(m * 60)) for m in (0, 5, 15, 25, 30)], '4 3')])
panel(1, 'Client p95 per completion minute (ms; not whole-run percentile)', 24,
      (0, 10, 20), [('p95', '#3E5CD9', values('browseLatencyP95Ms'), None)])
panel(2, 'Actual task samples: blue filled = running; grey hollow = desired', 6.7,
      (0, 2, 4, 6), [
          ('desired', '#6B655C', [(elapsed(row['observedAt']), row['desired']) for row in samples], 'dots'),
          ('running', '#3E5CD9', [(elapsed(row['observedAt']), row['running']) for row in samples], 'dots')])
panel(3, 'Commerce CPU (%) at complete UTC-minute centres; final partial minute unknown', 65,
      (0, 25, 50), [('CPU', '#28744d', [(minute, float(row['commerceCpuAveragePercent'])) for row, minute in cpu], None)])
text(LEFT, 449, 'Elapsed minutes from container start 22:22:40.555 SGT; schedule is a reference only.')
parts.extend(['</g>', '</svg>'])
output = HERE / 'performance-v3-timeline.svg'
generated = ('\n'.join(parts) + '\n').encode('utf-8')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
if args.check:
    assert output.read_bytes() == generated, 'R4 timeline export differs; regenerate from retained R4 data'
else:
    output.write_bytes(generated)
print(f'{output.name}: R4 only, {len(data)} browse bins, {len(samples)} task samples, {len(cpu)} CPU minutes, {len(events)} scale-out starts')
