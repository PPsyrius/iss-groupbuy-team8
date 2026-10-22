import importlib.util
from pathlib import Path
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

    def test_only_first_party_libraries_are_a_valid_zero_third_party_view(self):
        sbom = {"components": [maven("com.blocbuy", "blocbuy-domain")]}
        licences = licence_report(("pom.xml", [("com.blocbuy:blocbuy-domain", "Apache-2.0", "notice")]))
        summary = module.summarise(sbom, licences, own_prefix="com.blocbuy:")
        self.assertEqual((summary["packages"], summary["with_licence"]), (0, 0))
        self.assertFalse(summary["without_licence"])
        self.assertEqual(sum(summary["by_category"].values()), 0)

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
        licences["Results"][0]["Licenses"][1]["Category"] = "future-category"
        self.assertEqual(module.summarise(sbom, licences)["by_category"]["unknown"], 1)

    def test_package_names_are_deduplicated_across_versions_but_empty_findings_are_advisory(self):
        sbom = {"components": [npm("react", version="1.0"), npm("react", version="2.0")]}
        licences = {"Results": [{"Class": "lang-pkgs", "Packages": [{"Name": "react"}]}]}
        summary = module.summarise(sbom, licences)
        self.assertEqual((summary["packages"], summary["with_licence"]), (1, 0))
        self.assertEqual(summary["without_licence"], ["react"])

    def test_categories_count_only_packages_in_the_selected_view(self):
        summary = module.summarise({"components": [npm("react")]},
                                  licence_report(("pnpm-lock.yaml", [("absent", "MIT", "notice")])))
        self.assertEqual(summary["by_category"]["notice"], 0)
        self.assertEqual(summary["without_licence"], ["react"])

    def test_an_empty_sbom_or_a_licence_report_without_results_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "no library components"):
            module.summarise({"components": []}, licence_report(("pom.xml", [])))
        with self.assertRaisesRegex(ValueError, "no licence results"):
            module.summarise({"components": [npm("react")]}, {"Results": []})


if __name__ == "__main__":
    unittest.main()
