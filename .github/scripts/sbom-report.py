"""Validate source inventories, report licence coverage, and bind evidence to a run."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import html
import importlib.util
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit


summary_spec = importlib.util.spec_from_file_location("sbom_summary", Path(__file__).with_name("sbom-summary.py"))
summary_module = importlib.util.module_from_spec(summary_spec)
summary_spec.loader.exec_module(summary_module)


CATEGORIES = summary_module.CATEGORIES
PARTS = {"backend": "pom", "frontend": "pnpm"}
MAX_REPORT_BYTES = 32 * 1024 * 1024
SCOPE = {
    "kind": "source-dependency inventory, not a packaged-runtime SBOM",
    "roots": ["backend/", "frontend/"],
    "packages": "All package occurrences reported by the pinned Trivy source analysers; repeated module graphs are retained.",
    "analyser_selection": "Maven compile/runtime and pnpm production-closure selection, not every installed or packaged dependency.",
    "third_party_summary": "Distinct package names; first-party names are excluded only from this labelled view, not the full inventory.",
    "optional_platforms": "Retained when reported, including packages with no local licence metadata; installation/shipping status is not inferred.",
    "first_party": "Names beginning com.blocbuy: or @blocbuy/ are labelled, not dropped or exempted from coverage counts.",
    "licence_policy": "Report only; missing metadata and licence categories do not fail the job.",
}


class ReportError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ReportError(message)


def text(value):
    return isinstance(value, str) and bool(value.strip())


def object_value(value, name):
    require(isinstance(value, dict), f"{name} must be an object")
    return value


def array(value, name):
    require(isinstance(value, list), f"{name} must be an array")
    require(all(isinstance(item, dict) for item in value), f"{name} must contain objects")
    return value


def json_object(pairs):
    data = {}
    for key, value in pairs:
        require(key not in data, f"Duplicate JSON field: {key}")
        data[key] = value
    return data


def invalid_constant(value):
    raise ReportError(f"Non-finite JSON number: {value}")


def load_report(path):
    require(path.is_file() and not path.is_symlink(), f"Missing report or unsupported symlink: {path.name}")
    require(0 < path.stat().st_size <= MAX_REPORT_BYTES, f"Empty or oversized report: {path.name}")
    content = path.read_bytes()
    data = json.loads(content.decode("utf-8"), object_pairs_hook=json_object, parse_constant=invalid_constant)
    require(isinstance(data, dict), f"{path.name} must contain a JSON object")
    return data, {"sha256": hashlib.sha256(content).hexdigest(), "size_bytes": len(content)}


def first_party(name):
    return name.startswith(("com.blocbuy:", "@blocbuy/"))


def licence_inventory(part, report, version):
    require(type(report.get("SchemaVersion")) is int and report["SchemaVersion"] == 2,
            f"{part}: unsupported Trivy report schema")
    require(report.get("ArtifactType") == "filesystem" and report.get("ArtifactName") in (part, part + "/"),
            f"{part}: report must describe the expected source directory")
    scanner = object_value(report.get("Trivy"), f"{part} Trivy metadata")
    require(scanner.get("Version") == version, f"{part}: Trivy version does not match the pinned version")
    results = array(report.get("Results"), f"{part} Results")
    packages, findings = [], []
    for result in results:
        source = result.get("Target")
        require(text(source), f"{part}: result has no source target")
        if "Packages" in result:
            require(result.get("Class") == "lang-pkgs" and result.get("Type") == PARTS[part],
                    f"{part}: unexpected package analyser/scope")
            for package in array(result["Packages"], f"{part} Packages"):
                require(text(package.get("Name")) and text(package.get("Version")), f"{part}: package identity is missing")
                purl = object_value(package.get("Identifier"), f"{part} package identifier").get("PURL")
                ecosystem = "pkg:maven/" if part == "backend" else "pkg:npm/"
                require(text(purl) and purl.startswith(ecosystem), f"{part}: package {package['Name']} has no expected package URL")
                licences = package.get("Licenses")
                if licences is None:
                    licences = []
                require(isinstance(licences, list) and all(text(value) for value in licences),
                        f"{part}: malformed package licence metadata")
                packages.append({"name": package["Name"], "version": package["Version"], "purl": purl,
                                 "source": source, "licences": licences, "first_party": first_party(package["Name"])})
        if "Licenses" in result:
            require(result.get("Class") == "license", f"{part}: unexpected licence analyser/scope")
            for finding in array(result["Licenses"], f"{part} licence findings"):
                require(text(finding.get("PkgName")) and text(finding.get("Name")), f"{part}: licence finding identity is missing")
                category = finding.get("Category")
                if category is None or category == "":
                    category = "unknown"
                require(isinstance(category, str), f"{part}: licence category must be text")
                findings.append({"package": finding["PkgName"], "licence": finding["Name"], "source": source,
                                 "category": summary_module.category(category),
                                 "reported_category": category})
    require(packages, f"{part}: no dependency packages were reported")
    detected = {(package["source"], package["name"], licence) for package in packages for licence in package["licences"]}
    classified = {(finding["source"], finding["package"], finding["licence"]) for finding in findings}
    require(detected == classified, f"{part}: licence findings and package metadata do not agree")
    categories = Counter(finding["category"] for finding in findings)
    missing = [package for package in packages if not package["licences"]]
    return {
        "package_occurrences": len(packages), "unique_package_urls": len({package["purl"] for package in packages}),
        "first_party_occurrences": sum(package["first_party"] for package in packages),
        "with_licence_metadata": len(packages) - len(missing),
        "missing_third_party": sum(not package["first_party"] for package in missing),
        "missing_first_party": sum(package["first_party"] for package in missing),
        "missing_metadata": missing, "licence_entries": len(findings),
        "categories": {category: categories[category] for category in CATEGORIES},
        "unknown_classifications": [finding for finding in findings if finding["category"] == "unknown"],
    }, packages


def validate_sbom(part, bom, packages, version):
    require(bom.get("bomFormat") == "CycloneDX" and bom.get("specVersion") == "1.7", f"{part}: unsupported CycloneDX format/version")
    require(type(bom.get("version")) is int and bom["version"] >= 1, f"{part}: invalid CycloneDX document version")
    components = array(bom.get("components"), f"{part} SBOM components")
    require(components, f"{part}: SBOM contains no components")
    require(all(component.get("type") in ("application", "library") and text(component.get("name")) for component in components),
            f"{part}: invalid source SBOM component identity/type")
    metadata = object_value(bom.get("metadata"), f"{part} SBOM metadata")
    root = object_value(metadata.get("component"), f"{part} SBOM root component")
    require(root.get("type") == "application" and text(root.get("bom-ref")) and root.get("name") == part,
            f"{part}: SBOM root identity is missing or incorrect")
    tools = array(object_value(metadata.get("tools"), f"{part} SBOM tools").get("components"), f"{part} SBOM tools components")
    require(any(tool.get("name") == "trivy" and tool.get("version") == version for tool in tools),
            f"{part}: SBOM scanner version does not match the pinned version")
    refs = [component.get("bom-ref") for component in components] + [root["bom-ref"]]
    require(all(text(ref) for ref in refs) and len(refs) == len(set(refs)), f"{part}: missing or duplicate SBOM references")
    refs = set(refs)
    libraries = [component for component in components if component.get("type") == "library"]
    require(libraries and all(text(component.get("purl")) and text(component.get("version"))
            and (component.get("group") is None or text(component["group"])) for component in libraries),
            f"{part}: SBOM library identity is missing or malformed")
    require(Counter((summary_module.package_name(component), component["version"], component["purl"]) for component in libraries)
            == Counter((package["name"], package["version"], package["purl"]) for package in packages),
            f"{part}: SBOM and licence report describe different package occurrences")
    dependencies = array(bom.get("dependencies"), f"{part} SBOM dependencies")
    require(dependencies, f"{part}: SBOM dependency graph is empty")
    graph = {}
    for node in dependencies:
        children = node.get("dependsOn", [])
        require(isinstance(children, list) and all(text(child) for child in children), f"{part}: malformed dependency edges")
        require(text(node.get("ref")) and node["ref"] in refs and all(child in refs for child in children),
                f"{part}: unresolved SBOM dependency reference")
        require(node["ref"] not in graph, f"{part}: duplicate dependency graph nodes")
        graph[node["ref"]] = children
    require(set(graph) == refs, f"{part}: SBOM dependency graph omits components or the root")
    visited, pending = set(), [root["bom-ref"]]
    while pending:
        ref = pending.pop()
        if ref not in visited:
            visited.add(ref)
            pending.extend(graph[ref])
    require(visited == refs, f"{part}: SBOM components are disconnected from the source root")
    return len(components)


def cell(value):
    # Dependency metadata is untrusted text, not Markdown/HTML instructions.
    value = html.escape(str(value), quote=False)
    value = re.sub(r"[\x00-\x1f\x7f-\x9f\u2028\u2029]", " ", value)
    return re.sub(r"([\\|`\[\]()*_!~])", r"\\\1", value)


def markdown(inventory):
    lines = ["## SBOM and licence inventory", "", "**Source dependency inventory; licence findings are advisory.**", "",
             "| Part | SBOM components | Package occurrences | Unique package URLs | Metadata present | Missing third-party | Missing first-party |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for part, data in inventory["parts"].items():
        values = [part, data["components"], data["package_occurrences"], data["unique_package_urls"],
                  data["with_licence_metadata"], data["missing_third_party"], data["missing_first_party"]]
        lines.append("| " + " | ".join(map(cell, values)) + " |")
    lines += ["", "### Distinct third-party package names", "",
              "This labelled view deduplicates package names across modules and versions and excludes first-party names only here; all source package occurrences and their metadata gaps remain above. A dual-licensed package counts under each alternative. These are not mutually exclusive package counts.", "",
              "| Part | Package names | With a licence finding | Missing finding | Forbidden | Restricted | Reciprocal | Notice | Permissive | Unencumbered | Unknown classification |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for part, data in inventory["parts"].items():
        view = data["third_party"]
        values = [part, view["packages"], view["with_licence"], len(view["without_licence"]),
                  *(view["by_category"][category] for category in CATEGORIES)]
        lines.append("| " + " | ".join(map(cell, values)) + " |")
    lines += ["", "### Licence entries by Trivy category", "",
              "These are licence entries, not mutually exclusive package counts. Dual licences and repeated module graphs may contribute multiple entries. Unknown classification is separate from missing licence metadata.", "",
              "| Part | Forbidden | Restricted | Reciprocal | Notice | Permissive | Unencumbered | Unknown classification |",
              "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for part, data in inventory["parts"].items():
        lines.append("| " + " | ".join(map(cell, [part, *(data["categories"][category] for category in CATEGORIES)])) + " |")
    lines += ["", "### Packages without detected licence metadata", "",
              "Missing metadata is not proof that a package is unlicensed or shipped. First-party entries are labelled, not silently excluded. Optional/uninstalled platforms remain in scope when Trivy reports them.", "",
              "| Part | Package | Version | Ownership | Occurrences |", "|---|---|---|---|---:|"]
    missing = False
    for part, data in inventory["parts"].items():
        grouped = Counter((package["name"], package["version"], package["first_party"]) for package in data["missing_metadata"])
        for (name, version, own), count in sorted(grouped.items()):
            missing = True
            lines.append("| " + " | ".join(map(cell, [part, name, version, "first-party" if own else "third-party", count])) + " |")
    if not missing:
        lines.append("| All | None detected | — | — | 0 |")
    lines += ["", "### Detected licences with unknown classification", "", "| Part | Package | Licence | Reported category | Occurrences |", "|---|---|---|---|---:|"]
    unknown = False
    for part, data in inventory["parts"].items():
        grouped = Counter((finding["package"], finding["licence"], finding["reported_category"]) for finding in data["unknown_classifications"])
        for (name, licence, category), count in sorted(grouped.items()):
            unknown = True
            lines.append("| " + " | ".join(map(cell, [part, name, licence, category, count])) + " |")
    if not unknown:
        lines.append("| All | None detected | — | — | 0 |")
    lines += ["", "Category names follow Trivy's classification, not an agreed project compliance policy. See `inventory.json` for source paths and occurrence details, and `manifest.json` for revision/run metadata and file hashes. No packaged-runtime coverage, legal approval or signing/attestation is claimed.", ""]
    return "\n".join(lines)


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")


def build(directory, revision, ref, run_url, trivy_version, step_summary=None):
    require(re.fullmatch(r"[a-f0-9]{40}|[a-f0-9]{64}", revision), "Source revision must be a full Git SHA")
    require(text(ref) and not any(ord(char) < 32 for char in ref), "Source ref is required and must not contain control characters")
    parsed = urlsplit(run_url)
    require(not any(ord(char) < 32 for char in run_url) and parsed.scheme == "https" and parsed.hostname and parsed.username is None and parsed.password is None
            and not parsed.query and not parsed.fragment and re.search(r"/actions/runs/\d+$", parsed.path), "A credential-free HTTPS GitHub run URL is required")
    version = trivy_version.removeprefix("v")
    require(re.fullmatch(r"\d+\.\d+\.\d+", version), "Trivy version must be explicitly pinned")
    derived = ("inventory.json", "summary.md", "manifest.json")
    require(not any((directory / name).exists() or (directory / name).is_symlink() for name in derived),
            "Derived evidence already exists; use a fresh output directory")
    if step_summary:
        require(step_summary.resolve() not in {(directory / name).resolve() for name in (*derived,
                *(f"{part}{suffix}" for part in PARTS for suffix in (".cdx.json", "-licences.json")))}, "Step summary must not overwrite an artifact file")
    inventory, files = {"schema_version": 1, "scope": SCOPE, "parts": {}}, {}
    for part in PARTS:
        licence_name, sbom_name = f"{part}-licences.json", f"{part}.cdx.json"
        report, files[licence_name] = load_report(directory / licence_name)
        bom, files[sbom_name] = load_report(directory / sbom_name)
        data, packages = licence_inventory(part, report, version)
        data["components"] = validate_sbom(part, bom, packages, version)
        prefix = "com.blocbuy:" if part == "backend" else "@blocbuy/"
        data["third_party"] = summary_module.summarise(bom, report, own_prefix=prefix)
        inventory["parts"][part] = data
    # Publish derived evidence only after both report pairs have passed validation.
    summary = markdown(inventory)
    write_json(directory / "inventory.json", inventory)
    (directory / "summary.md").write_text(summary, encoding="utf-8", newline="\n")
    for name in ("inventory.json", "summary.md"):
        content = (directory / name).read_bytes()
        files[name] = {"sha256": hashlib.sha256(content).hexdigest(), "size_bytes": len(content)}
    write_json(directory / "manifest.json", {
        "schema_version": 1, "created_at": datetime.now(timezone.utc).isoformat(),
        "source": {"revision": revision, "ref": ref}, "run_url": run_url,
        "scanner": {"name": "Trivy", "version": version}, "scope": SCOPE, "files": files,
        "validation": "Required structures, package occurrence agreement and dependency references checked; not full-schema or legal validation.",
        "attested": False,
    })
    if step_summary:
        with step_summary.open("a", encoding="utf-8", newline="\n") as output:
            output.write(summary)
    return inventory


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--trivy-version", required=True)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args(argv)
    try:
        inventory = build(args.directory, args.revision, args.ref, args.run_url, args.trivy_version, args.summary)
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        print(f"SBOM evidence validation failed: {error}", file=sys.stderr)
        return 1
    for part, data in inventory["parts"].items():
        print(f"{part}: validated {data['components']} components; licence metadata {data['with_licence_metadata']}/{data['package_occurrences']}; missing third-party {data['missing_third_party']}, first-party {data['missing_first_party']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
