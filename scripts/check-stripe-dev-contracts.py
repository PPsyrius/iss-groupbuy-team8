"""Read-only merge guard against the imported dev snapshot; no network or credentials."""

import hashlib
import json
import pathlib
import re
import sys


SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def digest(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


def baseline_format_errors(baseline):
    errors = []
    for section in ("files", "openapi_paths", "openapi_schemas"):
        for name, expected in baseline[section].items():
            if not isinstance(expected, str) or not SHA256_RE.fullmatch(expected):
                errors.append("Invalid SHA-256 baseline value: " + section + "/" + name)
    return errors


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
    contract = json.loads((root / "contracts/openapi.json").read_text())
    errors = []
    for label, actual in (
        ("openapi_paths", contract["paths"]),
        ("openapi_schemas", contract.get("components", {}).get("schemas", {})),
    ):
        for name, expected in baseline[label].items():
            if name not in actual or digest(actual[name]) != expected:
                errors.append("Original dev contract changed or missing: " + name)
    return errors


def main():
    root = pathlib.Path(__file__).resolve().parents[1]
    baseline = json.loads(
        (root / "scripts/stripe-dev-contract-baseline.json").read_text()
    )
    errors = (
        baseline_format_errors(baseline)
        + retired_source_errors(root)
        + file_digest_errors(root, baseline)
        + contract_digest_errors(root, baseline)
    )
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
