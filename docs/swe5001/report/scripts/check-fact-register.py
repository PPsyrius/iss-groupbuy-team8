"""Check every report echo of a load-bearing fact against its register entry.

Offline and fast: reads facts.json next to this script and the report sources
(sections/*.typ and registers.md); no git, cloud or evidence reads. Each fact
holds probes (a regex whose named group ``v`` captures the stated value in any
echo; the capture must be one of ``accept``), ``forbid`` patterns (known stale
or wrong wordings) and ``must_appear`` patterns (the canonical statement).
Run from the repository root; exit status 1 lists every mismatch.
"""
import json
import os
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[4]
REPORT = Path(os.environ.get('FACT_REGISTER_REPORT', ROOT / 'docs/swe5001/report'))
FACTS = Path(__file__).resolve().with_name('facts.json')
DEFAULT_FILES = ('sections/*.typ', 'registers.md')


def load_sources(report=REPORT):
    """Map report-relative path to text with whitespace runs collapsed."""
    sources = {}
    for pattern in DEFAULT_FILES:
        for path in sorted(report.glob(pattern)):
            raw = path.read_text(encoding='utf-8')
            sources[path.relative_to(report).as_posix()] = re.sub(r'\s+', ' ', raw)
    return sources


def select(sources, glob):
    if not glob:
        return sources
    globs = glob if isinstance(glob, list) else [glob]
    return {name: body for name, body in sources.items()
            if any(Path(name).match(g) for g in globs)}


def check(facts, sources):
    """Return a list of failure strings; empty means the report agrees."""
    failures = []
    seen = set()
    for fact in facts:
        fid = fact['id']
        if fid in seen:
            failures.append(f'{fid}: duplicate fact id')
        seen.add(fid)
        for probe in fact.get('probes', []):
            accept = {a.strip() for a in probe['accept']}
            regex = re.compile(probe['pattern'])
            if set(regex.groupindex) != {'v'}:
                failures.append(f"{fid}: probe {probe['pattern']!r} must have exactly one "
                                f"named group, v (has {sorted(regex.groupindex)})")
                continue
            for name, body in select(sources, probe.get('files')).items():
                for match in regex.finditer(body):
                    value = (match.group('v') or '').strip()
                    if value not in accept:
                        context = body[max(0, match.start() - 40):match.end() + 40]
                        failures.append(f'{fid}: {name} states {value!r}, register accepts '
                                        f'{sorted(accept)} ... {context} ...')
        for pattern in fact.get('forbid', []):
            regex = re.compile(pattern)
            for name, body in sources.items():
                for match in regex.finditer(body):
                    failures.append(f'{fid}: {name} has forbidden wording {match.group(0)!r}')
        for need in fact.get('must_appear', []):
            body = sources.get(need['file'])
            if body is None:
                failures.append(f'{fid}: canonical home {need["file"]} is missing')
            elif not re.search(need['pattern'], body):
                failures.append(f'{fid}: canonical statement not found in {need["file"]}')
    return failures


def count_occurrences(facts, sources):
    total = 0
    for fact in facts:
        for probe in fact.get('probes', []):
            regex = re.compile(probe['pattern'])
            for body in select(sources, probe.get('files')).values():
                total += sum(1 for _ in regex.finditer(body))
        total += len(fact.get('must_appear', []))
    return total


def main():
    facts = json.loads(FACTS.read_text(encoding='utf-8'))
    sources = load_sources()
    failures = check(facts, sources)
    for failure in failures:
        print(failure)
    if failures:
        print(f'{len(failures)} fact-register mismatch(es)')
        return 1
    print(f'fact register OK: {len(facts)} facts, '
          f'{count_occurrences(facts, sources)} occurrences checked')
    return 0


if __name__ == '__main__':
    sys.exit(main())
