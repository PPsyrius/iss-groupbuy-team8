import json, runpy, tarfile
from datetime import datetime
from pathlib import Path
h = Path.home()
m = runpy.run_path(str(h / "load-capture.py"))
a = m["aws"]
assert a("sts", "get-caller-identity")["Account"] == "402178232802", "Wrong account"
d = h / "swe5001-v3-b84uhoj0"
start = datetime.fromisoformat(json.loads((d / "observer.json").read_text())["startedAt"].replace("Z", "+00:00"))
m["set_floor"](2)
if m["observation"]()["services"][0]["desired"] < 2:
    a("ecs", "update-service", "--cluster", "blocbuy-swe5001", "--service", "commerce-svc", "--desired-count", "2")
print("Floor set to two; collecting retained AWS metrics/history. No load or teardown.", flush=True)
problems = []
try:
    m["finish"](d, start)
except Exception as e:
    problems.append(str(e))
targets = m["scaling"]()
assert len(targets["ScalableTargets"]) == 1 and targets["ScalableTargets"][0]["MinCapacity"] == 2 and targets["ScalableTargets"][0]["MaxCapacity"] == 6, "Floor readback failed"
m["write"](d, "recovery.json", {"at": m["now"](), "targets": targets, "observation": m["observation"](), "problems": problems, "missingObservationsAfter": "2026-11-09T20:03:47.522113Z", "collection": "Recovery of stored metrics/history only; no replacement observation timeline or load"})
archive = d.with_suffix(".tar.gz")
with tarfile.open(archive, "x:gz") as output:
    output.add(d, arcname=d.name)
print("Download:", archive)
print("Collection warnings:", problems)
