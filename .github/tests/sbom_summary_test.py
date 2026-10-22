import importlib.util
import io
import json
from contextlib import redirect_stdout
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("sbom_summary", ROOT / ".github/scripts/sbom-summary.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def maven(group, name, version="1.0"):
    return {"type": "library", "group": group, "name": name, "version": version,
            "purl": f"pkg:maven/{group}/{name}@{version}"}


def npm(name, version="1.0", scope=None):
    component = {"type": "library", "name": name, "version": version,
                 "purl": f"pkg:npm/{scope.replace('@', '%40') + '/' if scope else ''}{name}@{version}"}
    if scope:
        component["group"] = scope
    return component


def licence_report(*results):
    return {"Results": [{"Target": target, "Class": "license", "Licenses": [
        {"PkgName": name, "Name": licence, "Category": category} for name, licence, category in findings
    ]} for target, findings in results]}


class SbomSummaryTest(unittest.TestCase):
    def test_a_library_used_by_several_modules_counts_once(self):
        sbom = {"components": [maven("org.slf4j", "slf4j-api"), maven("org.slf4j", "slf4j-api"),
                               {"type": "application", "name": "backend"}]}
        licences = licence_report(("a/pom.xml", [("org.slf4j:slf4j-api", "MIT", "notice")]),
                                  ("b/pom.xml", [("org.slf4j:slf4j-api", "MIT", "notice")]))
        summary = module.summarise(sbom, licences)
        self.assertEqual(summary["packages"], 1)
        self.assertEqual(summary["by_category"]["notice"], 1)

    def test_own_modules_are_not_third_party(self):
        # Trivy labels our modules Apache-2.0, inherited from the Spring Boot parent POM.
        sbom = {"components": [maven("com.blocbuy", "blocbuy-domain"), maven("org.slf4j", "slf4j-api")]}
        licences = licence_report(("pom.xml", [("com.blocbuy:blocbuy-domain", "Apache-2.0", "notice"),
                                               ("org.slf4j:slf4j-api", "MIT", "notice")]))
        summary = module.summarise(sbom, licences, own_prefix="com.blocbuy:")
        self.assertEqual((summary["packages"], summary["with_licence"]), (1, 1))

    def test_a_dual_licensed_package_counts_under_each_alternative(self):
        sbom = {"components": [maven("ch.qos.logback", "logback-core")]}
        licences = licence_report(("pom.xml", [("ch.qos.logback:logback-core", "EPL-2.0", "reciprocal"),
                                               ("ch.qos.logback:logback-core", "LGPL-2.1-only", "restricted")]))
        by_category = module.summarise(sbom, licences)["by_category"]
        self.assertEqual((by_category["reciprocal"], by_category["restricted"], by_category["notice"]), (1, 1, 0))

    def test_npm_names_match_trivy_and_packages_without_a_finding_are_listed(self):
        sbom = {"components": [npm("react"), npm("typescript-aix-ppc64", scope="@typescript"),
                               npm("inter", scope="@fontsource")]}
        licences = licence_report(("pnpm-lock.yaml", [("react", "MIT", "notice"),
                                                      ("@fontsource/inter", "OFL-1.1", None)]))
        summary = module.summarise(sbom, licences)
        self.assertEqual(summary["without_licence"], ["@typescript/typescript-aix-ppc64"])
        self.assertEqual(summary["by_category"]["unknown"], 1)

    def test_an_empty_sbom_or_a_licence_report_without_results_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "no third-party library"):
            module.summarise({"components": [maven("com.blocbuy", "blocbuy-domain")]},
                             licence_report(("pom.xml", [])), own_prefix="com.blocbuy:")
        with self.assertRaisesRegex(ValueError, "no licence results"):
            module.summarise({"components": [npm("react")]}, {"Results": []})

    def test_main_appends_the_summary_and_fails_on_a_missing_report(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / "frontend.cdx.json").write_text(json.dumps({"components": [npm("react")]}), encoding="utf-8")
            (folder / "frontend-licences.json").write_text(
                json.dumps(licence_report(("pnpm-lock.yaml", [("react", "MIT", "notice")]))), encoding="utf-8")
            summary = folder / "summary.md"
            with redirect_stdout(io.StringIO()):
                self.assertEqual(module.main(["--dir", temp, "--part", "frontend", "--summary", str(summary)]), 0)
            self.assertIn("| frontend | 1 | 1 | 0 | 0 | 0 | 1 |", summary.read_text(encoding="utf-8"))
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(module.main(["--dir", temp, "--part", "backend=com.blocbuy:"]), 1)
            self.assertIn("::error::Incomplete SBOM inventory", output.getvalue())


class SbomWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/sbom.yml").read_text(encoding="utf-8")

    def test_every_scan_step_is_bounded_and_the_inventory_is_checked(self):
        steps = self.workflow.split("      - name: ")[1:]
        for name in ("Backend SBOM", "Frontend SBOM", "Backend licence", "Frontend licence",
                     "Populate local Maven", "Install frontend", "Summarise and check"):
            step = next(step for step in steps if step.startswith(name))
            self.assertIn("timeout-minutes:", step, name)
        self.assertIn("python3 .github/scripts/sbom-summary.py --dir sbom", self.workflow)
        self.assertIn("--part backend=com.blocbuy: --part frontend", self.workflow)

    def test_licences_stay_report_only(self):
        self.assertEqual(self.workflow.count("exit-code: '0' # report only"), 2)


if __name__ == "__main__":
    unittest.main()
