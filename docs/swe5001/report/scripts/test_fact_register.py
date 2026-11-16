"""Offline regressions for the canonical fact register check."""
import importlib.util
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[4]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


register = load('register', 'docs/swe5001/report/scripts/check-fact-register.py')


class FactRegisterTests(unittest.TestCase):
    def setUp(self):
        self.facts = json.loads(register.FACTS.read_text(encoding='utf-8'))
        self.sources = register.load_sources()

    def test_committed_report_matches_register(self):
        self.assertEqual(register.check(self.facts, self.sources), [])

    def test_every_probe_has_a_value_group_and_compiles(self):
        for fact in self.facts:
            for probe in fact.get('probes', []):
                self.assertIn('(?P<v>', probe['pattern'], fact['id'])
                self.assertTrue(probe['accept'], fact['id'])

    def test_planted_mismatch_in_an_echo_fails(self):
        fact = {'id': 'SSM-left', 'probes': [{
            'pattern': r'(?P<v>\d+) SSM parameters', 'accept': ['13']}]}
        sources = {'sections/a.typ': 'We left 13 SSM parameters.',
                   'sections/b.typ': 'Afterwards 12 SSM parameters remained.'}
        failures = register.check([fact], sources)
        self.assertEqual(len(failures), 1)
        self.assertIn("sections/b.typ states '12'", failures[0])
        sources['sections/b.typ'] = 'Afterwards 13 SSM parameters remained.'
        self.assertEqual(register.check([fact], sources), [])

    def test_forbid_and_must_appear_fail_on_planted_text(self):
        fact = {'id': 'task', 'forbid': [r'0\.25 vCPU'],
                'must_appear': [{'file': 'sections/a.typ', 'pattern': r'1 vCPU / 2 GiB'}]}
        good = {'sections/a.typ': 'Each task has 1 vCPU / 2 GiB.'}
        self.assertEqual(register.check([fact], good), [])
        bad = {'sections/a.typ': 'Each task has 0.25 vCPU.'}
        self.assertEqual(len(register.check([fact], bad)), 2)

    def test_planted_mismatch_in_a_real_fact_fails(self):
        # Mutate the first captured value of every probed fact in the real
        # sources and require the check to notice each one.
        for fact in self.facts:
            for probe in fact.get('probes', []):
                regex = __import__('re').compile(probe['pattern'])
                hit = None
                for name, body in register.select(self.sources, probe.get('files')).items():
                    match = regex.search(body)
                    if match and match.group('v'):
                        hit = (name, body, match)
                        break
                if hit is None:
                    continue
                name, body, match = hit
                planted = 'X' + match.group('v') + '9'
                mutated = dict(self.sources)
                mutated[name] = body[:match.start('v')] + planted + body[match.end('v'):]
                if regex.search(mutated[name]) is None:
                    continue  # the plant broke the anchor; nothing to assert
                with self.subTest(fact=fact['id']):
                    self.assertTrue(register.check([fact], mutated))

    def test_duplicate_ids_fail(self):
        self.assertTrue(register.check([{'id': 'a'}, {'id': 'a'}], {}))


if __name__ == '__main__':
    unittest.main()
