"""Verify V4/V7 public Git bytes, separately from immutable private receipts."""

import argparse
import hashlib
import json
import subprocess


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--rev", default="HEAD", help="Git revision, or index for staged bytes")
args = parser.parse_args()
root = "docs/swe5001/evidence/"
v4 = root + "20261114-v4-r5-live/"
v7 = root + "20261114-v7-r4-live/"
checks = 0


def read(path, rev=None):
    revision = args.rev if rev is None else rev
    spec = ":" + path if revision == "index" else revision + ":" + path
    return subprocess.check_output(["git", "show", spec])


def load(path, rev=None):
    return json.loads(read(path, rev))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(path, expected, length=None):
    global checks
    data = read(path)
    assert b"\r\n" not in data, f"Expected LF publication: {path}"
    assert digest(data) == expected, f"Hash mismatch: {path}"
    assert length is None or len(data) == length, f"Length mismatch: {path}"
    checks += 1


m4 = load(v4 + "publication-manifest.json")
m7 = load(v7 + "publication-manifest.json")
for name, expected in m4["filesSha256"].items():
    verify(v4 + name, expected)
for name, expected in m7["files"].items():
    verify(v7 + name, expected["sha256"], expected["bytes"])
packaging = load(v4 + "packaging-verification.json")
for name, expected in packaging["newDocumentSha256"].items():
    verify(v4 + name, expected)
verify(v4 + "observations.sanitized.jsonl", load(v4 + "v4-r5-pass.json")["sanitizedObservationsSha256"])
verify(v7 + "acceptance.json", m7["publishedFullAcceptanceSha256"])

# Historical maps must survive exactly; reconstruct their byte convention using
# the reviewed baseline, before the summary metadata correction in this commit.
baseline = "0b93d7d8"
for folder, current, old_key, key in (
    (v4, m4, "originalFilesSha256", "filesSha256"),
    (v7, m7, "originalFiles", "files"),
):
    original = load(folder + "publication-manifest.json", baseline)[key]
    assert current[old_key] == original, "Historical manifest changed"
    crlf_matches = 0
    for name, receipt in original.items():
        expected = receipt if isinstance(receipt, str) else receipt["sha256"]
        data = read(folder + name, baseline)
        crlf = data.replace(b"\n", b"\r\n")
        assert digest(data) == expected or digest(crlf) == expected, name
        if digest(crlf) == expected:
            crlf_matches += 1
            if isinstance(receipt, dict):
                assert len(crlf) == receipt["bytes"], name
    assert crlf_matches == (6 if folder == v4 else 4)
assert packaging["originalNewDocumentSha256"] == load(v4 + "packaging-verification.json", baseline)["newDocumentSha256"]
print(f"PASS {checks} current publication hash checks; historical maps intact; 6 V4 / 4 V7 CRLF receipts reproduced")
