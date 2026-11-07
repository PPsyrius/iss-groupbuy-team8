#!/usr/bin/env python3
"""Reconstruct a sprint burndown from the daily board snapshots.

Method (the one Appendix A of the SWE5006 report used for Sprints 3 and 4): each
backlog item keeps its planned hours as remaining while any of its issues is open
in that day's snapshot (`docs/agile/metrics/YYYY-MM-DD.csv`), and burns in full on
a snapshot where all of its issues are closed (a reopened issue adds the hours back;
`burned_items` names each item on its first burn). Items without an issue are
listed in the items file but left out of the total. The board Estimate field is
reported beside the result, because it is what a burndown would have used had
owners kept it current.

Usage:
  burndown.py --items docs/agile/sprint-04/burndown-items.csv \
    --metrics docs/agile/metrics \
    --days 2026-10-12,2026-10-13,... \
    --csv docs/agile/sprint-04/burndown.csv \
    [--svg docs/agile/sprint-04/burndown.svg --title "Sprint 4 burndown"]

Standard library only; prints a summary to stdout.
"""

import argparse
import csv
import os
import sys
from decimal import ROUND_HALF_UP, Decimal


def read_items(path):
    """Read the backlog items file; `issues` is a |-separated list of issue numbers."""
    items = []
    with open(path, newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            issues = [int(n) for n in row["issues"].split("|") if n.strip()]
            items.append({**row, "hours": float(row["hours"]), "issues": issues})
    return items


def read_snapshot(metrics, day):
    """Read one daily board snapshot, keyed by issue number."""
    path = os.path.join(metrics, f"{day}.csv")
    with open(path, newline="", encoding="utf-8") as fh:
        return {int(r["number"]): r for r in csv.DictReader(fh)}


def fmt(value):
    """Format hours without a trailing .0, otherwise to one decimal place."""
    return f"{value:g}" if value == int(value) else f"{value:.1f}"


def compute(items, metrics, days):
    """Return the tracked total and one row per day (plus the start row)."""
    tracked = [i for i in items if i["issues"]]
    total = sum(i["hours"] for i in tracked)
    rows = [{"date": "start", "ideal_h": total, "remaining_h": total, "open_items": len(tracked), "board_estimate_h": "", "burned": ""}]
    burned_before = set()
    for k, day in enumerate(days, start=1):
        snap = read_snapshot(metrics, day)
        missing = sorted({n for i in tracked for n in i["issues"] if n not in snap})
        if missing:
            sys.exit(f"{day}: issues missing from snapshot: {missing}")
        open_items = [i for i in tracked if any(snap[n]["state"] != "CLOSED" for n in i["issues"])]
        open_ids = {i["id"] for i in open_items}
        burned_today = [i["id"] for i in tracked if i["id"] not in open_ids and i["id"] not in burned_before]
        burned_before |= set(burned_today)
        estimate = sum(float(snap[n]["estimate"] or 0) for n in sorted({n for i in tracked for n in i["issues"]}))
        rows.append(
            {
                "date": day,
                "ideal_h": float((Decimal(str(total)) * (len(days) - k) / len(days)).quantize(Decimal("0.1"), ROUND_HALF_UP)),
                "remaining_h": sum(i["hours"] for i in open_items),
                "open_items": len(open_items),
                "board_estimate_h": estimate,
                "burned": "|".join(burned_today),
            }
        )
    return total, rows


def write_csv(path, rows):
    """Write the burndown rows as CSV."""
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["date", "ideal_h", "remaining_h", "open_items", "board_estimate_h", "burned_items"])
        for r in rows:
            est = "" if r["board_estimate_h"] == "" else fmt(r["board_estimate_h"])
            w.writerow([r["date"], fmt(r["ideal_h"]), fmt(r["remaining_h"]), r["open_items"], est, r["burned"]])


def label(day):
    """Turn a YYYY-MM-DD date into a short axis label such as "12 Oct"."""
    if day == "start":
        return "Start"
    months = {"01": "Jan", "02": "Feb", "03": "Mar", "04": "Apr", "05": "May", "06": "Jun", "07": "Jul", "08": "Aug", "09": "Sep", "10": "Oct", "11": "Nov", "12": "Dec"}
    return f"{int(day[8:10])} {months[day[5:7]]}"


def write_svg(path, rows, title, subtitle):
    """Draw the ideal and actual remaining lines as an SVG chart; needs at least two rows."""
    width, height = 720, 420
    left, right, top, bottom = 64, 24, 92, 56
    pw, ph = width - left - right, height - top - bottom
    ymax = max(r["ideal_h"] for r in rows)
    step = 50 if ymax > 150 else 20
    ytop = (int(ymax // step) + 1) * step
    n = len(rows)

    def x(i):
        return left + pw * i / (n - 1)

    def y(v):
        return top + ph - ph * v / ytop

    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" font-family="Arial, Helvetica, sans-serif" role="img" aria-label="{title}">',
        f'<rect width="{width}" height="{height}" fill="#ffffff"/>',
        f'<text x="{left}" y="26" font-size="16" font-weight="bold" fill="#1f2933">{title}</text>',
        f'<text x="{left}" y="46" font-size="12" fill="#5f6b78">{subtitle}</text>',
    ]
    lx = left
    out += [
        f'<line x1="{lx}" y1="66" x2="{lx + 28}" y2="66" stroke="#7d8793" stroke-width="2" stroke-dasharray="6 4"/>',
        f'<text x="{lx + 34}" y="70" font-size="12" fill="#1f2933">Ideal remaining</text>',
        f'<line x1="{lx + 150}" y1="66" x2="{lx + 178}" y2="66" stroke="#0a72b0" stroke-width="3"/>',
        f'<text x="{lx + 184}" y="70" font-size="12" fill="#1f2933">Actual remaining</text>',
    ]
    for t in range(0, ytop + 1, step):
        out.append(f'<line x1="{left}" y1="{y(t):.1f}" x2="{left + pw}" y2="{y(t):.1f}" stroke="#d5dde5" stroke-width="1"/>')
        out.append(f'<text x="{left - 8}" y="{y(t) + 4:.1f}" font-size="11" text-anchor="end" fill="#1f2933">{t}</text>')
    out.append(f'<text x="18" y="{top + ph / 2:.1f}" font-size="12" fill="#1f2933" transform="rotate(-90 18 {top + ph / 2:.1f})" text-anchor="middle">Remaining hours</text>')
    for i, r in enumerate(rows):
        out.append(f'<text x="{x(i):.1f}" y="{top + ph + 20}" font-size="11" text-anchor="middle" fill="#1f2933">{label(r["date"])}</text>')
    ideal = " ".join(f"{x(i):.1f},{y(r['ideal_h']):.1f}" for i, r in enumerate(rows))
    actual = " ".join(f"{x(i):.1f},{y(r['remaining_h']):.1f}" for i, r in enumerate(rows))
    out.append(f'<polyline points="{ideal}" fill="none" stroke="#7d8793" stroke-width="2" stroke-dasharray="6 4"/>')
    out.append(f'<polyline points="{actual}" fill="none" stroke="#0a72b0" stroke-width="3" stroke-linejoin="round"/>')
    for i, r in enumerate(rows):
        out.append(f'<circle cx="{x(i):.1f}" cy="{y(r["remaining_h"]):.1f}" r="3.5" fill="#0a72b0"/>')
    last = rows[-1]
    out.append(f'<text x="{x(n - 1) - 4:.1f}" y="{y(last["remaining_h"]) - 10:.1f}" font-size="12" font-weight="bold" text-anchor="end" fill="#0a72b0">{fmt(last["remaining_h"])} h</text>')
    out.append(f'<text x="{left}" y="{height - 12}" font-size="11" fill="#5f6b78">Planned hours burn in full on the daily board snapshot where all of an item\'s issues are closed; no hours were logged.</text>')
    out.append("</svg>")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


def main():
    """Parse the arguments, compute the burndown and write the outputs."""
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--items", required=True)
    p.add_argument("--metrics", required=True)
    p.add_argument("--days", required=True, help="comma-separated working days, YYYY-MM-DD")
    p.add_argument("--csv", required=True)
    p.add_argument("--svg")
    p.add_argument("--title", default="Sprint burndown")
    p.add_argument("--subtitle", default="Remaining planned hours of issue-backed items")
    a = p.parse_args()
    items = read_items(a.items)
    days = [d.strip() for d in a.days.split(",") if d.strip()]
    if not days:
        sys.exit("--days must list at least one date")
    total, rows = compute(items, a.metrics, days)
    write_csv(a.csv, rows)
    if a.svg:
        write_svg(a.svg, rows, a.title, a.subtitle)
    excluded = [f"{i['id']} ({fmt(i['hours'])} h)" for i in items if not i["issues"]]
    print(f"total {fmt(total)} h over {len(days)} days; excluded without issue: {', '.join(excluded) or 'none'}")
    for r in rows:
        print(r["date"], fmt(r["ideal_h"]), fmt(r["remaining_h"]), r["open_items"], r["board_estimate_h"], r["burned"])


if __name__ == "__main__":
    main()
