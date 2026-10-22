"""Aggregate the distinct third-party view of inputs validated by sbom-report.py."""

from collections import defaultdict

CATEGORIES = ("forbidden", "restricted", "reciprocal", "notice", "permissive", "unencumbered", "unknown")


def category(value):
    normalized = (value or "unknown").strip().lower()
    return normalized if normalized in CATEGORIES else "unknown"


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

    libraries = [c for c in sbom.get("components", []) if c.get("type") == "library"]
    if not libraries:
        raise ValueError("SBOM has no library components")
    packages = {package_name(c) for c in libraries}
    packages = {name for name in packages if third_party(name)}
    results = [r for r in licences.get("Results", []) if r.get("Class") == "license"]
    # A valid package inventory may have no metadata/findings at all. That is an
    # advisory coverage gap, unlike an empty report with no dependency inventory.
    if not results and not any(r.get("Packages") for r in licences.get("Results", [])):
        raise ValueError("licence report has no licence results")
    categories = defaultdict(set)
    for result in results:
        for finding in result.get("Licenses") or []:
            if finding["PkgName"] in packages:
                categories[finding["PkgName"]].add(category(finding.get("Category")))
    return {
        "packages": len(packages),
        "with_licence": len(packages & categories.keys()),
        # A dual-licensed package (A OR B) is counted under the category of each alternative.
        "by_category": {key: sum(key in found for found in categories.values()) for key in CATEGORIES},
        "without_licence": sorted(packages - categories.keys()),
    }
