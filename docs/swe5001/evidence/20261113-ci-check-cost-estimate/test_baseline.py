"""Offline arithmetic and completeness checks for the recorded runner-time baseline."""
from collections import Counter
from datetime import datetime
import json
from math import ceil
from pathlib import Path
import statistics
import unittest


class BaselineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt = json.loads(Path(__file__).with_name('development-baseline.json').read_text())
        cls.runs = cls.receipt['runs']
        cls.eligible = [r for r in cls.runs if r['classification']]
        cls.summary = cls.receipt['summary']

    def test_distinct_runs_and_classifications(self):
        self.assertEqual(len(self.runs), len({r['id'] for r in self.runs}))
        self.assertEqual(len(self.runs), self.summary['inspected_runs'])
        self.assertEqual(len(self.eligible), self.summary['eligible_runs'])
        self.assertEqual(dict(Counter(r['classification'] for r in self.eligible)),
                         self.summary['classifications'])

    def test_job_times_and_rounding(self):
        for run in self.runs:
            for job in run['jobs']:
                elapsed = (datetime.fromisoformat(job['completed_at'].replace('Z', '+00:00')) -
                           datetime.fromisoformat(job['started_at'].replace('Z', '+00:00'))).total_seconds()
                self.assertGreaterEqual(elapsed, 0)
                self.assertEqual(elapsed, job['seconds'])
                self.assertEqual(ceil(elapsed / 60), job['rounded_minutes'])
                self.assertNotEqual(job['conclusion'], 'skipped')

    def test_summed_parallel_runner_time(self):
        totals = [sum(j['seconds'] for j in r['jobs']) for r in self.eligible]
        self.assertEqual(sum(totals), self.summary['total_runner_seconds'])
        self.assertAlmostEqual(sum(totals) / 60, self.summary['total_runner_minutes'])
        self.assertAlmostEqual(statistics.mean(totals) / 60,
                               self.summary['mean_runner_minutes_per_run'])
        self.assertAlmostEqual(statistics.median(totals) / 60,
                               self.summary['median_runner_minutes_per_run'])

    def test_cost_and_breakdown_totals(self):
        minutes = sum(j['rounded_minutes'] for r in self.eligible for j in r['jobs'])
        self.assertEqual(minutes, self.summary['rounded_job_minutes'])
        self.assertAlmostEqual(minutes * self.summary['linux_usd_per_minute'],
                               self.summary['rounded_minutes_cost_equivalent_usd'])
        for group in ('by_month', 'by_job'):
            self.assertEqual(sum(r['seconds'] for r in self.summary[group].values()),
                             self.summary['total_runner_seconds'])
            self.assertEqual(sum(r['rounded_minutes'] for r in self.summary[group].values()), minutes)

    def test_image_checks_are_separate_estimate(self):
        mean_seconds = statistics.mean((60, 54, 56))
        minutes = len(self.eligible) * mean_seconds / 60
        self.assertAlmostEqual(minutes, 138.83333333333334)
        self.assertAlmostEqual(minutes * self.summary['linux_usd_per_minute'], 0.833)
        self.assertGreater(self.summary['total_runner_minutes'], minutes)


if __name__ == '__main__':
    unittest.main()
