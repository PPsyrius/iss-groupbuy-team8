"""Focused offline regressions; run with unittest discovery in this directory."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[4]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reviewed = load('reviewed', 'docs/swe5001/report/scripts/check-reviewed-sections.py')
closure = load('closure', 'docs/swe5001/evidence/20261112-issue-789/check_closure.py')


class ReviewedCheckerTests(unittest.TestCase):
    def run_report(self, transform=lambda name, content: content):
        original_text = reviewed.text

        def scoped_git(*args):
            if args[0] == 'diff':
                self.assertEqual(args[:4], ('diff', '--name-only', reviewed.BASE, '--'))
                self.assertTrue(all(path.startswith('docs/swe5001/evidence/20261112-issue-789/') for path in args[4:]))
                return ''
            return ''

        with patch.object(reviewed, 'git', side_effect=scoped_git), patch.object(
            reviewed, 'text', side_effect=lambda name: transform(name, original_text(name))
        ), contextlib.redirect_stdout(io.StringIO()):
            reviewed.main()

    def test_review_scopes_diff_to_frozen_receipts_not_unrelated_code(self):
        self.run_report()

    def test_changed_calibration_table_is_rejected(self):
        # Change the first completed-count cell independent of table formatting.
        def changed_row(name, content):
            if name.endswith('04-1-performance.typ'):
                return reviewed.re.sub(r'(\[\d+\], \[)[\d,]+', r'\g<1>1', content, count=1)
            return content

        with self.assertRaises(AssertionError):
            self.run_report(changed_row)

    def test_cost_estimate_need_not_use_one_sample_count_or_sentence(self):
        def change(name, content):
            if name.endswith('05-devops.typ'):
                return reviewed.re.sub(r'\d', '7', content)
            return content

        # Numeric arithmetic is outside this check: a dated estimate verifier
        # must compare its evidence JSON, not freeze report prose or counts.
        self.run_report(change)

    def test_governance_requires_missing_output_boundary(self):
        def change(name, content):
            if name.endswith('04-3-security.typ'):
                return '\n'.join(line for line in content.splitlines() if not reviewed.re.search(
                    r'not retained|unverified|unavailable|not independently', line, reviewed.re.I))
            return content

        with self.assertRaises(AssertionError):
            self.run_report(change)


class FrozenMetadataTests(unittest.TestCase):
    def test_original_bytes_match_recorded_hashes(self):
        directory = ROOT / 'docs/swe5001/evidence/20261111-v3-r4'
        self.assertTrue(all(closure.frozen_metadata_checks(directory).values()))

    def test_changed_or_missing_frozen_copy_is_rejected(self):
        source = ROOT / 'docs/swe5001/evidence/20261111-v3-r4'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in closure.FROZEN_METADATA_SHA256:
                (directory / name).write_bytes((source / name).read_bytes() + b'changed')
            self.assertFalse(any(closure.frozen_metadata_checks(directory).values()))
            for name in closure.FROZEN_METADATA_SHA256:
                (directory / name).unlink()
            self.assertFalse(any(closure.frozen_metadata_checks(directory).values()))

    def test_expected_hashes_agree_with_retained_original_manifests(self):
        evidence = ROOT / 'docs/swe5001/evidence'
        scan = closure.json.loads((evidence / '20261111-v3-r4/secret-scan.json').read_text())
        receipt = closure.json.loads((evidence / '20261112-issue-789/verification.json').read_text())
        prefix = 'docs/swe5001/evidence/20261111-v3-r4/'
        self.assertEqual(closure.FROZEN_METADATA_SHA256['README.original-20261111.md'], scan['files'][prefix + 'README.md'])
        for name, expected in closure.FROZEN_METADATA_SHA256.items():
            self.assertEqual(expected, receipt['secretScan']['sourceHashes'][prefix + name])


if __name__ == '__main__':
    unittest.main()
