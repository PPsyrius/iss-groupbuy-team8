#!/usr/bin/env python3
"""Foreground read-only ECS observer for an explicitly authorized future V3 session.

Refuses CloudShell and unknown/short credential lifetime. Shares heartbeat/evidence
with load-capture.py prepare. Never starts load or changes/restores runtime settings.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import runpy
import signal
import tarfile
import time
import uuid

LOAD = runpy.run_path(str(Path(__file__).with_name("load-capture.py")))
READS = {("sts", "get-caller-identity"), ("configure", "export-credentials"),
         ("ecs", "describe-services"), ("application-autoscaling", "describe-scaling-activities"),
         ("cloudwatch", "get-metric-data"), ("logs", "filter-log-events")}


def read_aws(*args):
    LOAD["require"](args[:2] in READS, "Read-only observer refused a mutation/unsupported operation")
    return LOAD["aws"](*args)


def guard_host(confirmed):
    shell = "cloudshell" in os.environ.get("AWS_EXECUTION_ENV", "").lower()
    shell = shell or os.environ.get("USER") == "cloudshell-user" or bool(os.environ.get("CLOUDSHELL_ENVIRONMENT_ID"))
    LOAD["require"](confirmed and not shell, "Use a confirmed persistent host outside CloudShell; background processes do not prevent its idle shutdown")


def credential_expiry(seconds=2400):
    credentials = read_aws("configure", "export-credentials", "--format", "process")
    # Never write or print access keys, session tokens or the credential response.
    expiry = credentials.get("Expiration")
    del credentials
    LOAD["require"](isinstance(expiry, str), "Credential expiration is unknown; use a reviewed temporary profile with observed expiry")
    at = datetime.fromisoformat(expiry.replace("Z", "+00:00"))
    required = seconds + 900
    LOAD["require"](at.tzinfo is not None and (at - datetime.now(timezone.utc)).total_seconds() >= required,
                    f"Need at least {required} seconds of observed credential validity for capture plus the 15-minute margin")
    return expiry


def heartbeat(directory, session, samples, state):
    temporary = directory / "observer-heartbeat.tmp"
    with temporary.open("w", encoding="utf-8") as stream:
        os.chmod(stream.name, 0o600)
        json.dump({"session": session, "at": LOAD["now"](), "samples": samples, "state": state}, stream)
    os.replace(temporary, directory / "observer-heartbeat.json")


def capture(directory, seconds=2400):
    session = str(uuid.uuid4())
    start = datetime.now(timezone.utc)
    deadline = time.monotonic() + seconds
    samples, last, maximum_gap = 0, None, 0
    errors = []
    completed = False
    LOAD["write"](directory, "observer-context.json", {
        "session": session, "account": LOAD["CAP"]["ACCOUNT"], "region": LOAD["CAP"]["REGION"],
        "cluster": LOAD["CLUSTER"], "persistentHostConfirmed": True, "startedAt": start.isoformat(), "seconds": seconds,
        "sourceFileSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()})
    try:
        with (directory / "observations.jsonl").open("x", encoding="utf-8") as stream:
            os.chmod(stream.name, 0o600)
            next_sample = time.monotonic()
            while True:
                try:
                    row = read_aws("ecs", "describe-services", "--cluster", LOAD["CLUSTER"], "--services", "commerce-svc",
                        "--cli-connect-timeout", "5", "--cli-read-timeout", "10", "--query",
                        "{services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},events:events[:5]},failures:failures}")
                    LOAD["require"](row.get("failures") == [] and len(row.get("services", [])) == 1, "Missing/failed observation")
                    at = datetime.now(timezone.utc)
                    if last is not None:
                        gap = (at - last).total_seconds()
                        maximum_gap = max(maximum_gap, gap)
                        if gap <= 0:
                            errors.append({"at": LOAD["now"](), "error": "Observation clock did not advance"})
                    last = at
                    row["observedAt"] = at.isoformat().replace("+00:00", "Z")
                    stream.write(json.dumps(row) + "\n")
                    stream.flush()
                    os.fsync(stream.fileno())
                    samples += 1
                    heartbeat(directory, session, samples, "recording")
                    if samples == 1:
                        print(f"Observer recording: {directory}; run prepare in another terminal using this same directory.", flush=True)
                except (ValueError, KeyError) as error:
                    errors.append({"at": LOAD["now"](), "error": str(error)})
                    LOAD["require"](samples > 0, "First observation failed; preparation must not start")
                if time.monotonic() >= deadline:
                    completed = True
                    break
                next_sample += 15
                time.sleep(max(0, min(next_sample, deadline) - time.monotonic()))
        if (directory / "preflight.json").exists():
            LOAD["finish"](directory, start, read=read_aws)
    except (Exception, KeyboardInterrupt) as error:
        errors.append({"at": LOAD["now"](), "error": type(error).__name__})
    finally:
        heartbeat(directory, session, samples, "finished")
        result = {"at": LOAD["now"](), "samples": samples, "timerCompleted": completed,
                  "maximumSampleGapSeconds": maximum_gap, "errors": errors,
                  "collectionComplete": completed and samples >= 2 and maximum_gap <= 45 and not errors,
                  "restorationPerformed": False, "restoration": "Explicit short load-capture.py restore command required; no observer mutation or teardown"}
        LOAD["write"](directory, "observer-result.json", result)
        archive = Path(str(directory) + ".tar.gz")
        with tarfile.open(archive, "x:gz") as output:
            output.add(directory, arcname=directory.name)
        print(f"Observer archive: {archive}; collectionComplete={result['collectionComplete']}. Restore/verify the floor explicitly.", flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="New private results directory shared with prepare/runner")
    parser.add_argument("--persistent-host", action="store_true")
    parser.add_argument("--seconds", type=int, default=2400, help="Observation window, 2400–7200 seconds; credentials need 15 minutes more")
    args = parser.parse_args()
    LOAD["require"](2400 <= args.seconds <= 7200, "Observation window must be 2400–7200 seconds")
    guard_host(args.persistent_host)
    LOAD["require"](read_aws("sts", "get-caller-identity")["Account"] == LOAD["CAP"]["ACCOUNT"], "Wrong AWS account")
    expiry = credential_expiry(args.seconds)
    directory = args.directory.resolve()
    repo = Path(__file__).resolve().parents[3]
    LOAD["require"](not directory.exists() and directory != repo and repo not in directory.parents, "Use a new results directory outside the checkout; no overwrite/repeat")
    directory.mkdir(parents=True, mode=0o700)
    LOAD["write"](directory, "credential-window.json", {"observedExpiration": expiry, "requiredSeconds": args.seconds + 900})
    os.environ["AWS_MAX_ATTEMPTS"] = "1"
    def stop(_signal, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, stop)
    result = capture(directory, seconds=args.seconds)
    if not result["collectionComplete"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
