"""Read-only merge guard against the imported dev snapshot; no network or credentials."""

import hashlib
import json
import pathlib
import re
import sys


SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")


def digest(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


def baseline_format_errors(baseline):
    if not isinstance(baseline, dict):
        return ["Invalid baseline: expected an object"]

    errors = []
    if not isinstance(baseline.get("base_commit"), str) or not COMMIT_RE.fullmatch(
        baseline["base_commit"]
    ):
        errors.append("Invalid baseline metadata: base_commit must be a 40-character hash")
    for section in ("files", "openapi_paths", "openapi_schemas"):
        entries = baseline.get(section)
        if not isinstance(entries, dict):
            errors.append("Invalid baseline section: " + section + " must be an object")
            continue
        for name, expected in entries.items():
            if not isinstance(name, str) or not name:
                errors.append("Invalid baseline entry name: " + section)
                continue
            if not isinstance(expected, str) or not SHA256_RE.fullmatch(expected):
                errors.append("Invalid SHA-256 baseline value: " + section + "/" + name)
    return errors


def read_json(path, label):
    try:
        return json.loads(path.read_text(encoding="utf-8")), []
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        return None, [f"Invalid {label} JSON: {failure}"]


def retired_source_errors(root):
    bootstrap = root / "backend/blocbuy-bootstrap/src/main"
    errors = []
    for retired in (
        bootstrap / "java/com/blocbuy/bootstrap/local",
        bootstrap / "resources/db/stripe-local",
    ):
        if retired.exists() and any(path.is_file() for path in retired.rglob("*")):
            errors.append(
                "Retired local runtime files must not ship: "
                + str(retired.relative_to(root))
            )
    return errors


def file_digest_errors(root, baseline):
    errors = []
    for name, expected in baseline["files"].items():
        path = root / name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            errors.append("Original dev file changed or missing: " + name)
    return errors


def contract_digest_errors(root, baseline):
    contract, errors = read_json(root / "contracts/openapi.json", "OpenAPI contract")
    if errors:
        return errors
    if not isinstance(contract, dict):
        return ["Invalid OpenAPI contract: expected an object"]

    paths = contract.get("paths")
    components = contract.get("components")
    schemas = components.get("schemas") if isinstance(components, dict) else None
    sections = (("openapi_paths", paths), ("openapi_schemas", schemas))
    for label, actual in sections:
        if not isinstance(actual, dict):
            errors.append("Invalid OpenAPI contract section: " + label)
    if errors:
        return errors

    for label, actual in (
        ("openapi_paths", paths),
        ("openapi_schemas", schemas),
    ):
        for name, expected in baseline[label].items():
            if name not in actual or digest(actual[name]) != expected:
                errors.append("Original dev contract changed or missing: " + name)
    return errors


def main():
    root = pathlib.Path(__file__).resolve().parents[1]
    baseline, read_errors = read_json(
        root / "scripts/stripe-dev-contract-baseline.json", "baseline"
    )
    if read_errors:
        print("\n".join(read_errors), file=sys.stderr)
        return 1
    format_errors = baseline_format_errors(baseline)
    errors = format_errors + retired_source_errors(root)
    if not format_errors:
        errors += file_digest_errors(root, baseline)
        errors += contract_digest_errors(root, baseline)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(
        f"PASS: {len(baseline['files'])} original source/config/DDL files, "
        f"{len(baseline['openapi_paths'])} original HTTP paths and "
        f"{len(baseline['openapi_schemas'])} original schemas unchanged "
        f"from dev {baseline['base_commit'][:7]}."
    )
    print("New files and endpoints are allowed. Rebase requires a fresh baseline comparison.")
    print("PASS: retired local controllers/adapters and fixture migrations are absent from main sources.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
