import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("validate_trivy_db", Path(__file__).parents[1] / "scripts/validate-trivy-db.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ValidateTrivyDbTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory, filename, schema in [("db", "trivy.db", 2), ("java-db", "trivy-java.db", 1)]:
            folder = self.root / directory
            folder.mkdir()
            (folder / filename).write_bytes(b"database-placeholder")
            (folder / "metadata.json").write_text(json.dumps({
                "Version": schema, "UpdatedAt": "2026-10-08T00:00:00Z",
                "NextUpdate": "2026-10-08T06:00:00Z", "DownloadedAt": "2026-10-08T01:00:00Z",
            }), encoding="utf-8")

    def edit_metadata(self, directory, change):
        path = self.root / directory / "metadata.json"
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data), encoding="utf-8")

    def test_valid_payloads(self):
        module.validate(self.root)

    def test_missing_payload(self):
        (self.root / "java-db/trivy-java.db").unlink()
        with self.assertRaisesRegex(ValueError, "Missing or empty"):
            module.validate(self.root)

    def test_empty_payload(self):
        (self.root / "db/trivy.db").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "Missing or empty"):
            module.validate(self.root)

    def test_wrong_schema(self):
        self.edit_metadata("db", lambda data: data.update(Version=1))
        with self.assertRaisesRegex(ValueError, "schema"):
            module.validate(self.root)

    def test_missing_metadata(self):
        (self.root / "db/metadata.json").unlink()
        with self.assertRaises(FileNotFoundError):
            module.validate(self.root)

    def test_malformed_metadata(self):
        (self.root / "db/metadata.json").write_text("not JSON")
        with self.assertRaises(json.JSONDecodeError):
            module.validate(self.root)

    def test_missing_timestamp(self):
        self.edit_metadata("java-db", lambda data: data.pop("DownloadedAt"))
        with self.assertRaises(KeyError):
            module.validate(self.root)

    def test_invalid_timestamp(self):
        self.edit_metadata("db", lambda data: data.update(NextUpdate="invalid"))
        with self.assertRaises(ValueError):
            module.validate(self.root)

    def test_timezone_required(self):
        self.edit_metadata("db", lambda data: data.update(NextUpdate="2026-10-08T06:00:00"))
        with self.assertRaisesRegex(ValueError, "timezone"):
            module.validate(self.root)

    def test_expired_metadata_does_not_disable_scan_freshness_checks(self):
        # Stale restored DBs are allowed: Trivy, not this structural check, refreshes them.
        self.edit_metadata("db", lambda data: data.update(NextUpdate="2020-01-01T00:00:00Z"))
        module.validate(self.root)


if __name__ == "__main__":
    unittest.main()
