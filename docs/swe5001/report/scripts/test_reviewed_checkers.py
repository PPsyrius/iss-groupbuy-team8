"""Focused offline regressions; run with unittest discovery in this directory."""
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
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
    def test_snapshot_matches_section_wrapper_contracts(self):
        # Assert the actual small helpers, rather than parsing arbitrary Typst.
        # Removing a shadowing wrapper must not leave Python silently routing
        # to the inspected revision while Typst falls back to the release.
        security = reviewed.text('sections/04-3-security.typ')
        ops = reviewed.text('sections/05-devops.typ')
        self.assertIn('#import "../lib.typ": src as report-src', security)
        self.assertIn('#import "../lib.typ": src as report-src', ops)
        self.assertIn('#let src(path, body) = report-src(path, body, rev: if path.starts-with("infra/") or path.starts-with("backend/") { "' + reviewed.INSPECTED + '" } else { none })', security)
        self.assertIn('#let src(path, body, rev: none) = report-src(path, body, rev: if rev != none { rev } else if path.starts-with("infra/") or path.starts-with(".github/") or path == "CONTRIBUTING.md" or path == "docs/agile/README.md" { "' + reviewed.INSPECTED + '" } else { none })', ops)
        cases = (
            ('infra/swe5001/runtime/ecs.tf', '04-3-security.typ', reviewed.INSPECTED),
            ('backend/blocbuy-platform/pom.xml', '04-3-security.typ', reviewed.INSPECTED),
            ('.github/CODEOWNERS', '04-3-security.typ', reviewed.APP),
            ('infra/swe5001/runtime/ecs.tf', '05-devops.typ', reviewed.INSPECTED),
            ('.github/workflows/ci-backend.yml', '05-devops.typ', reviewed.INSPECTED),
            ('CONTRIBUTING.md', '05-devops.typ', reviewed.INSPECTED),
            ('docs/agile/README.md', '05-devops.typ', reviewed.INSPECTED),
            ('backend/blocbuy-platform/pom.xml', '05-devops.typ', reviewed.APP),
            ('infra/swe5001/runtime/ecs.tf', '03-2-physical.typ', reviewed.APP),
            ('docs/agile/README.md#policy', '05-devops.typ', reviewed.APP),
        )
        for path, section, expected in cases:
            with self.subTest(path=path, section=section):
                self.assertEqual(reviewed.snapshot(path, section, None), expected)

    def test_snapshot_shared_defaults_and_explicit_revision_precedence(self):
        lib = reviewed.text('lib.typ')
        for name, revision in (('freeze', reviewed.APP), ('evidence-freeze', reviewed.EVIDENCE), ('report-base', reviewed.BASE)):
            self.assertIn('#let ' + name + ' = "' + revision + '"', lib)
        self.assertIn('let snapshot = if rev != none { rev }', lib)
        paths = (
            ('docs/swe5001/evidence/20261111-cost-explorer/README.md', reviewed.BASE),
            ('docs/swe5001/evidence/20261113-ci-check-cost-estimate/development-baseline.json', reviewed.EVIDENCE),
            ('docs/swe5001/report/README.md', reviewed.BASE),
            ('frontend/package.json', reviewed.APP),
        )
        ops = reviewed.text('sections/05-devops.typ')
        baseline_call = reviewed.re.search(
            r'#src\("' + reviewed.re.escape(paths[1][0]) + r'", rev: "([0-9a-f]{40})"\)', ops)
        self.assertIsNotNone(baseline_call, 'Baseline JSON must retain an explicit immutable revision')
        baseline_rev = baseline_call[1]
        # The receipt postdates the evidence merge, so the explicit pin must
        # override that default. It must also be reachable from dev or prod:
        # an ancestor of HEAD is not enough, because on a PR branch HEAD
        # includes the PR's own commits, which squash merging discards.
        self.assertNotEqual(baseline_rev, reviewed.EVIDENCE)
        protected = [ref for ref in ('origin/dev', 'origin/prod')
                     if subprocess.run(['git', '-C', str(reviewed.ROOT), 'rev-parse', '--verify', '-q', ref],
                                       capture_output=True).returncode == 0]
        if not protected:
            self.skipTest('origin/dev and origin/prod are not fetched')
        self.assertTrue(any(
            subprocess.run(['git', '-C', str(reviewed.ROOT), 'merge-base', '--is-ancestor', baseline_rev, ref],
                           capture_output=True).returncode == 0 for ref in protected),
            baseline_rev + ' is not reachable from ' + ' or '.join(protected))
        for section in ('04-3-security.typ', '05-devops.typ', '01-introduction.typ'):
            for path, expected in paths:
                with self.subTest(path=path, section=section):
                    self.assertEqual(reviewed.snapshot(path, section, None), expected)
                    self.assertEqual(reviewed.snapshot(path, section, 'freeze'), reviewed.APP)
                    self.assertEqual(reviewed.snapshot(path, section, '"' + baseline_rev + '"'), baseline_rev)
        # Chapter 5 forwards an explicit freeze even for its inspected paths.
        self.assertEqual(reviewed.snapshot('.github/CODEOWNERS', '05-devops.typ', 'freeze'), reviewed.APP)

    def run_report(self, transform=lambda name, content: content):
        original_text = reviewed.text
        original_git = reviewed.git

        def scoped_git(*args):
            if args[0] == 'diff':
                self.assertEqual(args[:4], ('diff', '--name-only', reviewed.BASE, '--'))
                self.assertTrue(all(path.startswith('docs/swe5001/evidence/20261112-issue-789/') for path in args[4:]))
                return ''
            if args[0] == 'show':
                return original_git(*args)
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

    def test_cost_estimate_allows_rephrasing(self):
        def change(name, content):
            if name.endswith('05-devops.typ'):
                return content.replace("Estimated from GitHub's job timings, the", 'Calculated from recorded job timings, the')
            return content

        self.run_report(change)

    def test_changed_report_baseline_numbers_are_rejected(self):
        for old, new in (('147 qualifying', '148 qualifying'), ('1,733', '999,999'), ('10.40', '9999.99'), ('0.006/min', '0.060/min')):
            with self.subTest(value=old), self.assertRaises(AssertionError):
                self.run_report(lambda name, content: content.replace(old, new)
                                if name.endswith('05-devops.typ') else content)

    def test_ci_billing_allows_concise_qualification_and_rejects_reversal(self):
        revision = reviewed.re.search(r'development-baseline.json", rev: "([0-9a-f]{40})"',
                                      reviewed.text('sections/05-devops.typ'))[1]
        paragraph = ('Estimated: 147 qualifying backend delivery runs used 1,733 runner-minutes '
                     '(#src("docs/swe5001/evidence/20261113-ci-check-cost-estimate/development-baseline.json", rev: "' + revision + '")[receipt]). '
                     'At US\\$0.006/min, US\\$10.40 is a list-price equivalent, not observed billing. '
                     'Standard GitHub-hosted runners are free for public repositories.')
        reviewed.check_ci_baseline(paragraph)
        for old, new in (('list-price equivalent', 'billed charge'),
                         ('are free', 'are not free'),
                         ('are free', 'incur a charge'),
                         ('public repositories', 'private repositories')):
            with self.subTest(change=new), self.assertRaises(AssertionError):
                reviewed.check_ci_baseline(paragraph.replace(old, new))

    def test_matching_local_receipt_and_report_edits_cannot_override_cited_jobs(self):
        ops = reviewed.text('sections/05-devops.typ')
        path = 'docs/swe5001/evidence/20261113-ci-check-cost-estimate/development-baseline.json'
        revision = reviewed.re.search(r'development-baseline.json", rev: "([0-9a-f]{40})"', ops)[1]
        cited = reviewed.git('show', revision + ':' + path)
        local = reviewed.json.loads(cited)
        eligible = next(r for r in local['runs'] if r['classification'])
        eligible['jobs'][0]['seconds'] += 600
        local['summary']['rounded_job_minutes'] += 10
        local['summary']['rounded_minutes_cost_equivalent_usd'] = 10.458
        # In-memory substitutes only: no report or receipt file is rewritten.
        changed_ops = ops.replace('1,733', '1,743').replace('10.40', '10.46')
        with patch.object(Path, 'read_text', return_value=reviewed.json.dumps(local)), patch.object(
            reviewed, 'git', return_value=cited
        ) as read_revision:
            reviewed.check_ci_baseline(ops)
            with self.assertRaises(AssertionError):
                reviewed.check_ci_baseline(changed_ops)
            read_revision.assert_called_with('show', revision + ':' + path)

    def test_report_ci_billing_qualification_cannot_be_removed_or_reversed(self):
        for old, new in (('list-price equivalent', 'billed charge'),
                         ('free in this public repository', 'not free in this public repository'),
                         ('free in this public repository', 'free in this private repository')):
            with self.subTest(change=new), self.assertRaises(AssertionError):
                self.run_report(lambda name, content: content.replace(old, new)
                                if name.endswith('05-devops.typ') else content)

    def test_missing_baseline_citation_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.run_report(lambda name, content: content.replace(
                '20261113-ci-check-cost-estimate/development-baseline.json', 'missing-baseline/development-baseline.json')
                if name.endswith('05-devops.typ') else content)

    def test_successful_delivery_overstatement_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.run_report(lambda name, content: content.replace(
                'qualifying backend delivery runs', 'successful backend deliveries')
                if name.endswith('05-devops.typ') else content)

    def test_reversed_account_read_boundary_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.run_report(lambda name, content: content.replace(
                'No output is retained to verify that observation.',
                'AWS output independently verifies that observation.')
                if name.endswith('04-3-security.typ') else content)

    def test_missing_account_read_boundary_is_rejected_despite_other_qualifiers(self):
        with self.assertRaises(AssertionError):
            self.run_report(lambda name, content: content.replace(
                'No output is retained to verify that observation.', '')
                if name.endswith('04-3-security.typ') else content)

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
