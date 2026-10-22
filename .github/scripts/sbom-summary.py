#!/usr/bin/env python3
"""Summarise the CycloneDX SBOMs and Trivy licence reports; reject a missing or empty inventory."""

import argparse
from collections import defaultdict
import json
from pathlib import Path
import sys

CATEGORIES = ("forbidden", "restricted", "reciprocal", "notice", "permissive", "unencumbered", "unknown")


def package_name(component):
    """The key Trivy uses as PkgName: Maven `group:artifact`, npm `@scope/name` or `name`."""
    group, name = component.get("group"), component["name"]
    if not group:
        return name
    return f"{group}:{name}" if component.get("purl", "").startswith("pkg:maven/") else f"{group}/{name}"


def summarise(sbom, licences, own_prefix=None):
    """Count distinct third-party packages; Trivy lists a Maven library once per module that uses it."""
    def third_party(name):
        return not (own_prefix and name.startswith(own_prefix))

    packages = {package_name(c) for c in sbom.get("components", []) if c.get("type") == "library"}
    packages = {name for name in packages if third_party(name)}
    if not packages:
        raise ValueError("SBOM has no third-party library components")
    results = [r for r in licences.get("Results", []) if r.get("Class") == "license"]
    if not results:
        raise ValueError("licence report has no licence results")
    categories = defaultdict(set)
    for result in results:
        for finding in result.get("Licenses") or []:
            if third_party(finding["PkgName"]):
                categories[finding["PkgName"]].add((finding.get("Category") or "unknown").lower())
    return {
        "packages": len(packages),
        "with_licence": len(packages & categories.keys()),
        # A dual-licensed package (A OR B) is counted under the category of each alternative.
        "by_category": {key: sum(key in found for found in categories.values()) for key in CATEGORIES},
        "without_licence": sorted(packages - categories.keys()),
    }


def render(summaries):
    lines = [
        "## SBOM and licence inventory",
        "",
        "| Part | Third-party packages | With a licence finding | " + " | ".join(CATEGORIES) + " |",
        "|---" * (3 + len(CATEGORIES)) + "|",
    ]
    for part, s in summaries.items():
        counts = " | ".join(str(s["by_category"][key]) for key in CATEGORIES)
        lines.append(f"| {part} | {s['packages']} | {s['with_licence']} | {counts} |")
    lines += ["", "Categories follow Trivy's licence classification; a dual-licensed package counts under each alternative."]
    for part, s in summaries.items():
        if s["without_licence"]:
            lines.append(f"{part}: no licence finding for {len(s['without_licence'])} packages: "
                         + ", ".join(f"`{name}`" for name in s["without_licence"]) + ".")
    lines += ["", "Full CycloneDX SBOMs and per-package licences are in the `sbom` artifact.", ""]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", type=Path, required=True)
    parser.add_argument("--part", action="append", required=True,
                        help="NAME or NAME=OWN_PREFIX; reads NAME.cdx.json and NAME-licences.json")
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args(argv)
    summaries = {}
    try:
        for part in args.part:
            name, _, own_prefix = part.partition("=")
            sbom = json.loads((args.dir / f"{name}.cdx.json").read_text(encoding="utf-8"))
            licences = json.loads((args.dir / f"{name}-licences.json").read_text(encoding="utf-8"))
            try:
                summaries[name] = summarise(sbom, licences, own_prefix or None)
            except ValueError as error:
                raise ValueError(f"{name}: {error}") from error
    except (OSError, ValueError, KeyError) as error:
        print(f"::error::Incomplete SBOM inventory: {error}")
        return 1
    text = render(summaries)
    if args.summary:
        with args.summary.open("a", encoding="utf-8") as summary:
            summary.write(text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
