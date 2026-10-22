import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import quote


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("sbom_report", ROOT / ".github/scripts/sbom-report.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
REVISION = "a" * 40
REF = "refs/pull/609/merge"
RUN = "https://github.com/example/project/actions/runs/123"


def fixtures(part):
    identities = ([
        ("org.example:dual", ["EPL-2.0", "LGPL-2.1"]), ("com.blocbuy:local", None),
    ] if part == "backend" else [
        ("mit-package", ["MIT"]), ("@fontsource/inter", ["OFL-1.1"]),
        ("@typescript/typescript-darwin-arm64", None), ("@blocbuy/local", []),
    ])
    source = "pom.xml" if part == "backend" else "pnpm-lock.yaml"
    packages, findings, libraries = [], [], []
    categories = {"MIT": "notice", "EPL-2.0": "reciprocal", "LGPL-2.1": "restricted", "OFL-1.1": "unknown"}
    for index, (name, licences) in enumerate(identities):
        purl = ("pkg:maven/" + name.replace(":", "/") if part == "backend" else "pkg:npm/" + quote(name, safe="/")) + "@1.0.0"
        package = {"ID": purl, "Name": name, "Version": "1.0.0", "Relationship": "direct",
                   "Identifier": {"PURL": purl, "UID": f"{index + 1:016x}"}}
        if licences is not None:
            package["Licenses"] = licences
        packages.append(package)
        for licence in licences or []:
            findings.append({"PkgName": name, "Name": licence, "Category": categories[licence], "Severity": "UNKNOWN"})
        libraries.append({"type": "library", "bom-ref": f"lib-{index}", "purl": purl, "name": name, "version": "1.0.0"})
    report = {"SchemaVersion": 2, "Trivy": {"Version": "0.74.0"}, "ArtifactName": part, "ArtifactType": "filesystem",
              "Results": [{"Target": source, "Class": "lang-pkgs", "Type": module.PARTS[part], "Packages": packages},
                          {"Target": source, "Class": "license", "Licenses": findings}]}
    bom = {"bomFormat": "CycloneDX", "specVersion": "1.7", "version": 1,
           "metadata": {"component": {"bom-ref": "root", "type": "application", "name": part},
                        "tools": {"components": [{"type": "application", "name": "trivy", "version": "0.74.0"}]}},
           "components": libraries + [{"bom-ref": "file", "type": "application", "name": source}],
           "dependencies": [{"ref": "root", "dependsOn": ["file"]},
                            {"ref": "file", "dependsOn": [component["bom-ref"] for component in libraries]},
                            *[{"ref": component["bom-ref"], "dependsOn": []} for component in libraries]]}
    return report, bom


def prepare(directory):
    directory.mkdir(parents=True)
    for part in module.PARTS:
        report, bom = fixtures(part)
        module.write_json(directory / f"{part}-licences.json", report)
        module.write_json(directory / f"{part}.cdx.json", bom)


class SbomReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "reports"
        prepare(self.output)

    def build(self, **kwargs):
        return module.build(self.output, kwargs.pop("revision", REVISION), kwargs.pop("ref", REF),
                            kwargs.pop("run_url", RUN), kwargs.pop("trivy_version", "v0.74.0"), **kwargs)

    def edit(self, filename, change):
        path = self.output / filename
        data = json.loads(path.read_text(encoding="utf-8"))
        change(data)
        module.write_json(path, data)

    def fresh(self):
        self.output = self.root / str(len(list(self.root.iterdir())))
        prepare(self.output)

    def test_missing_metadata_and_unknown_classification_are_distinct(self):
        inventory = self.build()
        frontend = inventory["parts"]["frontend"]
        self.assertEqual(frontend["package_occurrences"], 4)
        self.assertEqual(frontend["with_licence_metadata"], 2)
        self.assertEqual((frontend["missing_third_party"], frontend["missing_first_party"]), (1, 1))
        self.assertEqual(frontend["categories"]["unknown"], 1)
        self.assertEqual(frontend["third_party"]["packages"], 3)
        self.assertEqual(frontend["third_party"]["with_licence"], 2)
        self.assertEqual(frontend["third_party"]["without_licence"], ["@typescript/typescript-darwin-arm64"])
        self.assertEqual(frontend["unknown_classifications"][0]["licence"], "OFL-1.1")
        summary = (self.output / "summary.md").read_text(encoding="utf-8")
        for value in ("@typescript/typescript-darwin-arm64", "@blocbuy/local", "OFL-1.1", "Missing third-party", "Missing first-party"):
            self.assertIn(value, summary)
        self.assertIn("not proof that a package is unlicensed or shipped", summary)

    def test_dual_licences_are_entries_not_additive_package_counts(self):
        backend = self.build()["parts"]["backend"]
        self.assertEqual((backend["package_occurrences"], backend["licence_entries"]), (2, 2))
        self.assertEqual(backend["with_licence_metadata"], 1)
        self.assertEqual((backend["categories"]["reciprocal"], backend["categories"]["restricted"]), (1, 1))
        self.assertIn("not mutually exclusive package counts", (self.output / "summary.md").read_text())

    def test_manifest_binds_revision_ref_run_version_scope_and_every_other_artifact(self):
        original = {path.name: path.read_bytes() for path in self.output.iterdir()}
        step_summary = self.root / "step-summary.md"
        step_summary.write_text("Earlier step\n", encoding="utf-8")
        self.build(step_summary=step_summary)
        manifest = json.loads((self.output / "manifest.json").read_text())
        self.assertEqual(manifest["source"], {"revision": REVISION, "ref": REF})
        self.assertEqual(manifest["run_url"], RUN)
        self.assertEqual(manifest["scanner"], {"name": "Trivy", "version": "0.74.0"})
        self.assertFalse(manifest["attested"])
        self.assertIn("not a packaged-runtime", manifest["scope"]["kind"])
        self.assertEqual(set(manifest["files"]), {*original, "inventory.json", "summary.md"})
        for name, metadata in manifest["files"].items():
            content = (self.output / name).read_bytes()
            self.assertEqual(metadata, {"sha256": hashlib.sha256(content).hexdigest(), "size_bytes": len(content)})
        for name, content in original.items():
            self.assertEqual((self.output / name).read_bytes(), content)
        self.assertEqual(step_summary.read_text(), "Earlier step\n" + (self.output / "summary.md").read_text())

    def test_every_missing_or_empty_required_file_fails_without_publishing_success(self):
        for name in ("backend.cdx.json", "frontend.cdx.json", "backend-licences.json", "frontend-licences.json"):
            for missing in (True, False):
                with self.subTest(name=name, missing=missing):
                    self.fresh()
                    path = self.output / name
                    path.unlink() if missing else path.write_bytes(b"")
                    with self.assertRaises(module.ReportError):
                        self.build()
                    self.assertFalse((self.output / "manifest.json").exists())
                    self.assertFalse((self.output / "inventory.json").exists())

    def test_malformed_json_and_nonobject_reports_fail(self):
        for value in ("not json", "[]", "null"):
            with self.subTest(value=value):
                self.fresh()
                (self.output / "frontend-licences.json").write_text(value)
                with self.assertRaises(ValueError):
                    self.build()

    def test_ambiguous_duplicate_fields_and_nonfinite_json_numbers_fail(self):
        for value in ('{"SchemaVersion": 1, "SchemaVersion": 2}', '{"value": NaN}',
                      '{"value": Infinity}', '{"value": -Infinity}'):
            with self.subTest(value=value):
                self.fresh()
                (self.output / "frontend-licences.json").write_text(value, encoding="utf-8")
                with self.assertRaises(module.ReportError):
                    self.build()
                self.assertFalse((self.output / "manifest.json").exists())

    def test_oversized_reports_fail(self):
        with patch.object(module, "MAX_REPORT_BYTES", 1), self.assertRaisesRegex(module.ReportError, "oversized"):
            self.build()

    def test_empty_results_packages_components_and_graph_fail(self):
        cases = [("frontend-licences.json", lambda d: d.update(Results=[])),
                 ("frontend-licences.json", lambda d: d["Results"][0].update(Packages=[])),
                 ("frontend.cdx.json", lambda d: d.update(components=[])),
                 ("frontend.cdx.json", lambda d: d.update(dependencies=[]))]
        for name, change in cases:
            with self.subTest(name=name, change=change):
                self.fresh()
                self.edit(name, change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_wrong_schema_analyser_artifact_or_scanner_version_fails(self):
        changes = [lambda d: d.update(SchemaVersion=1), lambda d: d.update(SchemaVersion=True),
                   lambda d: d.update(ArtifactName="backend"), lambda d: d.update(ArtifactType="container_image"),
                   lambda d: d["Results"][0].update(Type="npm"), lambda d: d["Trivy"].update(Version="0.75.0")]
        for change in changes:
            with self.subTest(change=change):
                self.fresh()
                self.edit("frontend-licences.json", change)
                with self.assertRaises(module.ReportError):
                    self.build()
        for field, value in (("bomFormat", "SPDX"), ("specVersion", "1.6"), ("version", True), ("version", 0)):
            self.fresh()
            self.edit("frontend.cdx.json", lambda d: d.update({field: value}))
            with self.assertRaises(module.ReportError):
                self.build()
        self.fresh()
        self.edit("frontend.cdx.json", lambda d: d["metadata"]["tools"]["components"][0].update(version="0.75.0"))
        with self.assertRaises(module.ReportError):
            self.build()

    def test_wrong_shapes_missing_identity_and_invalid_licence_arrays_fail(self):
        changes = [lambda d: d.update(Results={}), lambda d: d["Results"][0].update(Packages={}),
                   lambda d: d["Results"][1].update(Licenses={}), lambda d: d["Results"][0]["Packages"][0].pop("Name"),
                   lambda d: d["Results"][0]["Packages"][0].pop("Version"),
                   lambda d: d["Results"][0]["Packages"][0]["Identifier"].pop("PURL"),
                   lambda d: d["Results"][0]["Packages"][0].update(Licenses="MIT"),
                   lambda d: d["Results"][0]["Packages"][0].update(Licenses=[None]),
                   lambda d: d["Results"][1]["Licenses"][0].update(PkgName="absent-package")]
        for change in changes:
            with self.subTest(change=change):
                self.fresh()
                self.edit("frontend-licences.json", change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_nested_metadata_objects_and_category_types_are_validated(self):
        cases = [("frontend-licences.json", lambda d: d.update(Trivy=None)),
                 ("frontend-licences.json", lambda d: d["Results"][0]["Packages"][0].update(Identifier=[])),
                 ("frontend-licences.json", lambda d: d["Results"][1]["Licenses"][0].update(Category=False)),
                 ("frontend-licences.json", lambda d: d["Results"][1]["Licenses"][0].update(Category=[])),
                 ("frontend.cdx.json", lambda d: d.update(metadata=[])),
                 ("frontend.cdx.json", lambda d: d["metadata"].update(component=None)),
                 ("frontend.cdx.json", lambda d: d["metadata"].update(tools=[]))]
        for filename, change in cases:
            with self.subTest(filename=filename, change=change):
                self.fresh()
                self.edit(filename, change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_licence_findings_match_source_package_and_detected_metadata(self):
        changes = [lambda d: d["Results"].pop(),
                   lambda d: d["Results"][1].update(Target="different-lockfile"),
                   lambda d: d["Results"][1]["Licenses"][0].update(Name="different-licence"),
                   lambda d: d["Results"][1].update(Class="wrong-analyser")]
        for change in changes:
            with self.subTest(change=change):
                self.fresh()
                self.edit("frontend-licences.json", change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_duplicate_or_unresolved_sbom_references_fail(self):
        changes = [lambda d: d["components"][1].update({"bom-ref": d["components"][0]["bom-ref"]}),
                   lambda d: d["components"][0].pop("bom-ref"),
                   lambda d: d["dependencies"][0].update(dependsOn=["missing"]),
                   lambda d: d["dependencies"][0].update(ref="missing"),
                   lambda d: d["dependencies"].append(copy.deepcopy(d["dependencies"][0]))]
        for change in changes:
            with self.subTest(change=change):
                self.fresh()
                self.edit("frontend.cdx.json", change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_same_count_but_different_sbom_packages_is_not_valid(self):
        for field, value in (("purl", "pkg:npm/something-else@1.0.0"), ("name", "different-package"),
                             ("version", "999.0.0"), ("group", "@different")):
            with self.subTest(field=field):
                self.fresh()
                self.edit("frontend.cdx.json", lambda d: d["components"][0].update({field: value}))
                with self.assertRaisesRegex(module.ReportError, "different package occurrences"):
                    self.build()

    def test_sbom_component_identity_fields_are_required(self):
        changes = [lambda d: d["components"][0].pop("name"),
                   lambda d: d["components"][0].pop("version"),
                   lambda d: d["components"][0].update(group=[]),
                   lambda d: d["components"][0].update(type="unexpected"),
                   lambda d: d["metadata"]["component"].update(type="library")]
        for change in changes:
            with self.subTest(change=change):
                self.fresh()
                self.edit("frontend.cdx.json", change)
                with self.assertRaises(module.ReportError):
                    self.build()

    def test_graph_cannot_omit_the_root_or_component_nodes(self):
        for index in (0, 1, 2):
            with self.subTest(index=index):
                self.fresh()
                self.edit("frontend.cdx.json", lambda d: d["dependencies"].pop(index))
                with self.assertRaisesRegex(module.ReportError, "omits"):
                    self.build()

    def test_present_but_disconnected_graph_nodes_fail(self):
        self.edit("frontend.cdx.json", lambda d: d["dependencies"][1].update(dependsOn=[]))
        with self.assertRaisesRegex(module.ReportError, "disconnected"):
            self.build()

    def test_forbidden_and_unknown_categories_remain_advisory(self):
        for category in ("forbidden", "unknown", "Future-Category", None):
            with self.subTest(category=category):
                self.fresh()
                self.edit("frontend-licences.json", lambda d: d["Results"][1]["Licenses"][0].update(Category=category))
                inventory = self.build()["parts"]["frontend"]
                expected = category if category in module.CATEGORIES else "unknown"
                self.assertGreaterEqual(inventory["categories"][expected], 1)
                self.assertTrue((self.output / "manifest.json").exists())

    def test_absent_empty_and_null_package_licences_are_counted_as_missing(self):
        for value in (None, []):
            self.fresh()
            self.edit("frontend-licences.json", lambda d: d["Results"][0]["Packages"][0].update(Licenses=value))
            self.edit("frontend-licences.json", lambda d: d["Results"][1]["Licenses"].pop(0))
            self.assertEqual(self.build()["parts"]["frontend"]["missing_third_party"], 2)

    def test_no_metadata_and_no_findings_remain_advisory(self):
        def remove_metadata(data):
            for package in data["Results"][0]["Packages"]:
                package.pop("Licenses", None)
            data["Results"].pop()
        for part in module.PARTS:
            self.edit(f"{part}-licences.json", remove_metadata)
        inventory = self.build()
        for data in inventory["parts"].values():
            self.assertEqual((data["with_licence_metadata"], data["licence_entries"]), (0, 0))
            self.assertEqual(len(data["missing_metadata"]), data["package_occurrences"])

    def test_first_party_classification_is_specific_and_does_not_exclude_packages(self):
        self.assertTrue(module.first_party("com.blocbuy:local"))
        self.assertTrue(module.first_party("@blocbuy/local"))
        self.assertFalse(module.first_party("org.com.blocbuy:external"))
        self.assertFalse(module.first_party("@blocbuyish/external"))
        data = self.build()["parts"]["backend"]
        self.assertEqual((data["first_party_occurrences"], data["missing_first_party"]), (1, 1))

    def test_first_party_only_inventory_still_publishes_coverage(self):
        for part in module.PARTS:
            report, bom = fixtures(part)
            report["Results"][0]["Packages"] = [package for package in report["Results"][0]["Packages"]
                                               if module.first_party(package["Name"])]
            report["Results"].pop()
            bom["components"] = [component for component in bom["components"]
                                 if component["type"] != "library" or module.first_party(component["name"])]
            refs = {component["bom-ref"] for component in bom["components"]} | {"root"}
            bom["dependencies"] = [{"ref": node["ref"], "dependsOn": [child for child in node.get("dependsOn", []) if child in refs]}
                                   for node in bom["dependencies"] if node["ref"] in refs]
            module.write_json(self.output / f"{part}-licences.json", report)
            module.write_json(self.output / f"{part}.cdx.json", bom)
        for data in self.build()["parts"].values():
            self.assertEqual((data["package_occurrences"], data["missing_first_party"]), (1, 1))
            self.assertEqual(data["third_party"]["packages"], 0)

    def test_repeated_module_occurrences_are_preserved_but_unique_purls_are_distinct(self):
        def repeat_report(data):
            data["Results"][0]["Packages"].append(copy.deepcopy(data["Results"][0]["Packages"][0]))
        def repeat_bom(data):
            component = {**data["components"][0], "bom-ref": "repeated"}
            data["components"].append(component)
            data["dependencies"].append({"ref": "repeated"})
            data["dependencies"][1]["dependsOn"].append("repeated")
        self.edit("backend-licences.json", repeat_report)
        self.edit("backend.cdx.json", repeat_bom)
        data = self.build()["parts"]["backend"]
        self.assertEqual((data["package_occurrences"], data["unique_package_urls"]), (3, 2))
        self.assertEqual(data["third_party"]["packages"], 1)
        self.assertEqual(data["third_party"]["by_category"]["reciprocal"], 1)

    def test_untrusted_package_text_cannot_inject_summary_markup(self):
        name = "bad|name\n<script>payload</script>`"
        self.edit("frontend-licences.json", lambda d: d["Results"][0]["Packages"][2].update(Name=name))
        self.edit("frontend.cdx.json", lambda d: d["components"][2].update(name=name))
        self.build()
        summary = (self.output / "summary.md").read_text()
        self.assertNotIn("<script>", summary)
        self.assertIn("bad\\|name &lt;script&gt;payload&lt;/script&gt;\\`", summary)

    def test_derived_artifacts_use_utf8_and_platform_independent_lf(self):
        self.build()
        for name in ("inventory.json", "summary.md", "manifest.json"):
            content = (self.output / name).read_bytes()
            self.assertNotIn(b"\r\n", content)
            self.assertTrue(content.endswith(b"\n"))
            content.decode("utf-8")

    def test_untrusted_text_cannot_embed_markdown_links_or_images(self):
        self.assertEqual(module.cell("![label](https://example.com/pixel)"), r"\!\[label\]\(https://example.com/pixel\)")
        self.assertEqual(module.cell("*bold*\x00\u2028next"), r"\*bold\*  next")

    def test_provenance_is_required_and_version_is_not_a_floating_tag(self):
        cases = [{"revision": "short"}, {"ref": ""}, {"ref": "refs/heads/dev\nsecret"},
                 {"run_url": "http://github.com/example/project/actions/runs/123"},
                 {"run_url": "https://token@github.com/example/project/actions/runs/123"},
                 {"run_url": RUN + "?token=secret"}, {"run_url": "https://github.com/example/project"},
                 {"run_url": RUN + "\n"},
                 {"trivy_version": "latest"}]
        for kwargs in cases:
            with self.subTest(kwargs=kwargs), self.assertRaises(module.ReportError):
                self.build(**kwargs)

    def test_stale_derived_evidence_is_not_overwritten(self):
        marker = self.output / "manifest.json"
        marker.write_text("previous evidence")
        with self.assertRaisesRegex(module.ReportError, "fresh output"):
            self.build()
        self.assertEqual(marker.read_text(), "previous evidence")

    def test_step_summary_must_not_corrupt_an_input_report(self):
        path = self.output / "frontend.cdx.json"
        content = path.read_bytes()
        with self.assertRaisesRegex(module.ReportError, "overwrite"):
            self.build(step_summary=path)
        self.assertEqual(path.read_bytes(), content)

    def test_cli_success_and_failure_use_process_exit_codes(self):
        argv = ["--directory", str(self.output), "--revision", REVISION, "--ref", REF,
                "--run-url", RUN, "--trivy-version", "v0.74.0"]
        with patch("sys.stdout"):
            self.assertEqual(module.main(argv), 0)
        self.fresh()
        (self.output / "frontend.cdx.json").unlink()
        argv[1] = str(self.output)
        with patch("sys.stderr") as error:
            self.assertEqual(module.main(argv), 1)
        self.assertIn("validation failed", error.write.call_args_list[0].args[0])


if __name__ == "__main__":
    unittest.main()
