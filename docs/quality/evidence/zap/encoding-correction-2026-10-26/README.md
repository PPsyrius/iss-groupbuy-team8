# Issue-export encoding erratum — 2026-10-26

Refs #694 and the CodeRabbit data-integrity review of the pre-remediation evidence. Use [issues.json](issues.json) and [open-issues.json](open-issues.json) from this directory when quoting issue text in the report. The original `before-2026-10-26/` directory and its checksums remain unchanged.

## What was corrected

The original GitHub CLI output passed through a PowerShell text pipeline that decoded UTF-8 bytes as CP437 before saving the JSON. Consequently, characters such as `§`, `–` and `→` were corrupted before the first checksums were generated. The original JSON exports are not byte-for-byte copies of GitHub's UTF-8 output. This does not affect the separately downloaded ZAP report artifacts or the actual GitHub screenshot.

These files are corrected derivatives of the original capture, not a new historical snapshot or a fresh scan. The correction reverses the observed encoding transformation strictly and losslessly, then serializes JSON as UTF-8 without a BOM. Capture timestamps, issue states, ordering, record counts and all other data are preserved; the current verification time is recorded separately in [verification.json](verification.json).

| Original export | Corrected fields | Records retained |
|---|---|---:|
| `issues.json` | #606 body; #694 title and body | 15 |
| `open-issues.json` | Titles of #694, #681, #680, #679, #678, #677, #676, #675, #515, #465, #459 and #450 | 58 |

Every changed field was compared in full with its current GitHub source record using `gh issue view --json number,title,body,url,updatedAt`. All matched across 13 distinct issues. [verification.json](verification.json) preserves those source records, original-file hashes, the verification timestamp and the affected-field list. Current source records validate the text correction; they do not independently establish historical issue state.

## Reproduction and verification

For each original JSON export, the correction is equivalent to:

```python
original = original_path.read_text(encoding="utf-8-sig")
corrected = original.encode("cp437").decode("utf-8")
assert corrected.encode("utf-8").decode("cp437") == original
data = json.loads(corrected)
output_path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
```

GitHub verification used Python `subprocess.check_output` and explicit `.decode("utf-8")`, bypassing PowerShell text decoding. Future exports should likewise capture stdout as bytes and explicitly decode UTF-8, rather than pipe GitHub CLI output through an implicit console encoding.

Verification passed: strict encoding round-trip, full comparison of every changed field with GitHub, JSON parsing, unchanged issue identities/states/timestamps, retained counts of 15 detailed and 58 summary records, and all 24 original snapshot checksums. [SHA256SUMS.txt](SHA256SUMS.txt) fingerprints the other files in this correction directory; `.gitattributes` prevents checkout newline conversion. No post-remediation scan result is claimed.
