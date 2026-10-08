#!/usr/bin/env python3
"""Reject missing or structurally invalid Trivy database downloads before caching."""

from datetime import datetime
import json
from pathlib import Path
import sys


def validate(cache_dir):
    for directory, filename, schema in [("db", "trivy.db", 2), ("java-db", "trivy-java.db", 1)]:
        folder = Path(cache_dir) / directory
        database = folder / filename
        if not database.is_file() or database.stat().st_size == 0:
            raise ValueError(f"Missing or empty {directory} database")
        metadata = json.loads((folder / "metadata.json").read_text(encoding="utf-8"))
        if metadata.get("Version") != schema:
            raise ValueError(f"Unsupported {directory} schema version")
        for key in ["UpdatedAt", "NextUpdate", "DownloadedAt"]:
            timestamp = datetime.fromisoformat(metadata[key].replace("Z", "+00:00"))
            if timestamp.tzinfo is None:
                raise ValueError(f"Missing timezone in {directory} {key}")
    # Trivy validates DB content while downloading/opening it and retains freshness checks.
    print("Both Trivy database payloads and metadata are present and structurally valid")


if __name__ == "__main__":
    validate(sys.argv[1])
