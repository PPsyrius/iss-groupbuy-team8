"""Tests for the fact register's probe rules, planted drifts and the wording guards.

Run from the repository root: python3 -m unittest discover -s docs/swe5001/report/scripts
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
CHECKER = HERE / 'check-fact-register.py'


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


register = load('register', CHECKER)

# Realistic drifts: one per value, digit first, then a word number, then a month.
WORDS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten',
         'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen',
         'eighteen', 'nineteen', 'twenty']
MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']


def drifts(value):
    for i in reversed([i for i, c in enumerate(value) if c.isdigit()]):
        for d in ((int(value[i]) + 1) % 10, (int(value[i]) - 1) % 10):
            yield value[:i] + str(d) + value[i + 1:]
    for idx, word in enumerate(WORDS[:-1]):
        m = re.search(r'\b%s\b' % word, value, re.I)
        if m:
            yield value[:m.start()] + WORDS[idx + 1] + value[m.end():]
    for idx, month in enumerate(MONTHS):
        m = re.search(r'\b%s' % month, value)
        if m:
            yield value[:m.start()] + MONTHS[(idx + 1) % 12] + value[m.end():]
    yield value + 'x'


def strip_value_group(pattern):
    """Return the pattern with only the balanced (?P<v>...) group removed.

    A greedy r'\(\?P<v>.*\)' would also swallow every literal up to the last ')' in the pattern,
    hiding a value written after the group. Escapes and character classes are skipped so that
    '\)' or '[)]' do not close the group.
    """
    start = pattern.index('(?P<v>')
    i, depth, in_class = start, 0, False
    while i < len(pattern):
        c = pattern[i]
        if c == '\\':
            i += 2
            continue
        if in_class:
            in_class = c != ']'
        elif c == '[':
            in_class = True
            if pattern[i + 1:i + 2] == '^':
                i += 1
            if pattern[i + 1:i + 2] == ']':  # a leading ']' is a literal
                i += 1
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return pattern[:start] + pattern[i + 1:]
        i += 1
    raise ValueError(f'unbalanced value group in {pattern!r}')


def plant(regex, body, accept):
    """Return a body with the first echo drifted, or None if no drift keeps the anchor."""
    match = regex.search(body)
    if not match or match.group('v') is None:
        return None
    s, e = match.span('v')
    for d in drifts(match.group('v')):
        if d.strip() in accept:
            continue
        new = body[:s] + d + body[e:]
        if any(m.start('v') == s and m.group('v') == d for m in regex.finditer(new)):
            return new
    return None


class FactRegisterAdditionsTests(unittest.TestCase):
    def setUp(self):
        self.facts = json.loads(register.FACTS.read_text(encoding='utf-8'))
        self.sources = register.load_sources()

    def test_committed_report_matches_register(self):
        self.assertEqual(register.check(self.facts, self.sources), [])

    def test_every_probe_has_exactly_one_named_group_v(self):
        for fact in self.facts:
            for probe in fact.get('probes', []):
                with self.subTest(fact=fact['id'], pattern=probe['pattern']):
                    self.assertEqual(set(re.compile(probe['pattern']).groupindex), {'v'})

    def test_checker_rejects_a_probe_with_another_named_group(self):
        fact = {'id': 'w-group', 'probes': [{
            'pattern': r'(?P<v>\d+) of (?P<w>\d+)', 'accept': ['2']}]}
        failures = register.check([fact], {'sections/a.typ': '2 of 3'})
        self.assertTrue(failures and 'exactly one named group' in failures[0])

    def test_every_probe_matches_at_least_one_echo(self):
        for fact in self.facts:
            for probe in fact.get('probes', []):
                regex = re.compile(probe['pattern'])
                hits = sum(1 for body in register.select(self.sources, probe.get('files')).values()
                           for _ in regex.finditer(body))
                with self.subTest(fact=fact['id'], pattern=probe['pattern']):
                    self.assertGreater(hits, 0)

    def test_no_accept_value_is_a_literal_beside_the_group(self):
        # A value written into the pattern outside the group silences a drift of that value.
        # Link-label probes (#run(N)[N]) deliberately pin the label to the literal link target.
        for fact in self.facts:
            for probe in fact.get('probes', []):
                if re.match(r'#run\\\(', probe['pattern']):
                    continue
                outside = strip_value_group(probe['pattern'])
                for value in probe['accept']:
                    if len(value) < 3 or not re.search(r'\d', value):
                        continue
                    token = r'(?<![\w\\])%s(?![\w])'
                    with self.subTest(fact=fact['id'], value=value):
                        self.assertIsNone(re.search(token % re.escape(re.escape(value)), outside))
                        self.assertIsNone(re.search(token % re.escape(value), outside))

    def test_strip_value_group_keeps_literals_after_the_group(self):
        self.assertEqual(strip_value_group(r'apply (?P<v>\d+) s, 115 resources \(x\)'),
                         r'apply  s, 115 resources \(x\)')
        self.assertEqual(strip_value_group(r'\((?P<v>\d+ (?:Oct|Nov))\) and (a)'), r'\(\) and (a)')
        self.assertEqual(strip_value_group(r'at (?P<v>[)\]]+\)) now'), r'at  now')
        # The old greedy strip hid the 115 after the group; the balanced strip exposes it.
        pattern = r'initial apply (?P<v>\d+) s, 115 resources; smoke (\d+) s\)'
        self.assertNotIn('115', re.sub(r'\(\?P<v>.*\)', '', pattern))
        self.assertIn('115', strip_value_group(pattern))

    def test_run_link_guards_fire_on_a_label_target_mismatch(self):
        fact = next(f for f in self.facts if f['id'] == 'run-ids')
        guards = dict(fact, probes=[], must_appear=[])
        self.assertEqual(register.check([guards], self.sources), [])
        cases = {'sections/a.typ': ('see #run("39008208244")[39008208244].',
                                    'see #run("39008208244")[39008208245].'),
                 'registers.md': ('[run 39008208244](https://x/actions/runs/39008208244)',
                                  '[run 39008208244](https://x/actions/runs/39008208245)')}
        for name, (good, bad) in cases.items():
            with self.subTest(file=name):
                self.assertEqual(register.check([guards], {name: good}), [])
                failures = register.check([guards], {name: bad})
                self.assertTrue(failures and all('forbidden wording' in f for f in failures))

    def test_planted_drift_fails_for_every_probe(self):
        for fact in self.facts:
            for probe in fact.get('probes', []):
                regex = re.compile(probe['pattern'])
                accept = {a.strip() for a in probe['accept']}
                for name, body in register.select(self.sources, probe.get('files')).items():
                    mutated = plant(regex, body, accept)
                    if mutated is None:
                        continue
                    single = dict(fact, probes=[probe], forbid=[], must_appear=[])
                    with self.subTest(fact=fact['id'], pattern=probe['pattern'], file=name):
                        failures = register.check([single], dict(self.sources, **{name: mutated}))
                        self.assertTrue(failures)
                        self.assertTrue(failures[0].startswith(fact['id'] + ':'))
                    break

    def test_aiss14_window_forbid_and_probes(self):
        fact = next(f for f in self.facts if f['id'] == 'AISS14-scalabletarget-window')
        name = 'sections/03-4-limitations.typ'
        body = self.sources[name]
        self.assertIn('between 13:28 and 14:14 SGT', body)
        bad = dict(self.sources, **{name: body.replace('between 13:28 and 14:14 SGT',
                                                       'between 21:28 and 22:14 SGT')})
        failures = register.check([fact], bad)
        self.assertEqual(len(failures), 3)  # two probe values and the forbidden wording
        self.assertTrue(all(f.startswith('AISS14-scalabletarget-window:') for f in failures))
        self.assertTrue(any("forbidden wording 'between 21:28 and 22:14 SGT'" in f for f in failures))

    def test_analytics_cost_period_is_checked_with_the_amount(self):
        fact = next(f for f in self.facts if f['id'] == 'analytics-pipeline-cost')
        name = 'sections/06-5-analytics.typ'
        body = self.sources[name]
        self.assertIn('costs about US\\$0.20 a month', body)
        bad = dict(self.sources, **{name: body.replace('costs about US\\$0.20 a month',
                                                       'costs about US\\$0.20 a year')})
        failures = register.check([fact], bad)
        self.assertEqual(len(failures), 1)
        self.assertIn('0.20 a year', failures[0])

    def test_decimal_throughput_drift_is_reported_not_skipped(self):
        fact = next(f for f in self.facts if f['id'] == 'V3r4-peak-rps')
        name = 'sections/04-1-performance.typ'
        body = self.sources[name]
        self.assertIn('tested 600 req/s', body)
        bad = dict(self.sources, **{name: body.replace('tested 600 req/s', 'tested 6.00 req/s', 1)})
        failures = register.check([fact], bad)
        self.assertTrue(failures)
        self.assertTrue(any('6.00' in f for f in failures))

    def test_wording_guards_fail_on_the_old_text(self):
        old = {
            'DA-v3r4-session-only-label': ('sections/02-project-conduct.typ',
                                           'with the session settings in @tbl-perf-configs',
                                           'with session-only settings (@tbl-perf-configs)'),
            'DA-session-cost-estimate-kind': ('sections/03-2-physical.typ',
                                              'session was estimated from list prices at about',
                                              'session cost about'),
            'DA-19oct-alb-task-size-label': ('sections/04-1-performance.typ',
                                             'the 0.5 vCPU task size then committed',
                                             'the committed task size'),
            'DA-v3r4-never-ran-five-scope': ('sections/04-1-performance.typ',
                                             'never ran five tasks during the load.',
                                             'never ran five.'),
            'DA-identity-r1-overclaim': ('sections/04-2-availability.typ',
                                         "R1's probe ran before identity was down",
                                         'R1 never made identity unavailable'),
            'DA-v3r1-released-sizing-label': ('sections/04-1-performance.typ',
                                              '[Released sizing, as 10 Nov.',
                                              '[Committed sizing, as 10 Nov.'),
            'DA-v3r2-not-merged-label': ('sections/04-1-performance.typ',
                                         '[Session settings: commerce 1 vCPU, 2 GiB (merged to `dev` by #pr(807) on 14 Nov);',
                                         '[Session settings, not merged: commerce 1 vCPU, 2 GiB;'),
            'DA-openapi-count-xref': ('sections/03-1-logical.typ',
                                      '(31 on 8 November, §4.4)', '(31 on 8 November, §3.1.4.6)'),
            'DA-cost-explorer-13-15-partial': ('sections/06-3-cost.typ',
                                               'were only partly in Cost Explorer on 15 November (part of 13 November, nothing for 14 and 15 November)',
                                               'were not yet in Cost Explorer on 15 November'),
            'DA-appx-listing-caption-step': ('sections/07-appendix-a-walkthrough.typ',
                                             "team was open (UC3 B10), seen", "team was open, seen"),
            'DA-appx-names-blurred-or-cropped': ('sections/07-appendix-a-walkthrough.typ',
                                                 "the leader's name is blurred and the member's is cropped out.",
                                                 "the two test accounts' names are blurred."),
        }
        byid = {f['id']: f for f in self.facts}
        for fid, (name, new, stale) in old.items():
            with self.subTest(fact=fid):
                body = self.sources[name]
                self.assertEqual(body.count(new), 1)
                self.assertEqual(register.check([byid[fid]], self.sources), [])
                bad = dict(self.sources, **{name: body.replace(new, stale)})
                failures = register.check([byid[fid]], bad)
                self.assertTrue(failures)
                self.assertTrue(all(f.startswith(fid + ':') for f in failures))

    def test_drift_planted_in_a_file_fails_the_command_line_check(self):
        # End to end: copy the report sources, drift one echo on disk, run the checker.
        with tempfile.TemporaryDirectory() as tmp:
            copy = Path(tmp) / 'report'
            shutil.copytree(register.REPORT / 'sections', copy / 'sections')
            shutil.copy(register.REPORT / 'registers.md', copy / 'registers.md')
            path = copy / 'sections/03-4-limitations.typ'
            text = path.read_text(encoding='utf-8')
            self.assertIn('between 13:28 and 14:14 SGT', text)
            path.write_text(text.replace('between 13:28 and 14:14 SGT', 'between 13:28 and 14:41 SGT'),
                            encoding='utf-8')
            env = dict(os.environ, FACT_REGISTER_REPORT=str(copy))
            run = subprocess.run([sys.executable, str(CHECKER)], env=env, capture_output=True, text=True)
            self.assertEqual(run.returncode, 1)
            self.assertIn("AISS14-scalabletarget-window: sections/03-4-limitations.typ states '14:41'",
                          run.stdout)


if __name__ == '__main__':
    unittest.main()
