#!/usr/bin/env python3
"""One approved SWE5001 rolling pool-cap update. No count/scaling/infra changes."""

import argparse
import copy
import json
import os
import re
from pathlib import Path
import subprocess
import tempfile
import time
from datetime import datetime, timezone

ACCOUNT = "402178232802"
REGION = "ap-southeast-1"
CLUSTER = "blocbuy-swe5001"
KEY = "SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE"
DIGESTS = {
    "identity-svc": "sha256:91894f6b4f1fe9c537f48dcec1c993084beb5c392fa7f90ea744fec8a4c78cef",
    "commerce-svc": "sha256:c8ba2a7a09115a0d459f648d3ea57faf13709975a0c1e960d7f385372cd351d9",
    "notification-svc": "sha256:24bbc3ff1b42f0f1f6c9e891ba92010bba197aab184ef907172f4ba1585737c7",
}
COUNTS = {"identity-svc": 1, "commerce-svc": 2, "notification-svc": 1}
READ_ONLY = {"taskDefinitionArn", "revision", "status", "requiresAttributes",
             "compatibilities", "registeredAt", "registeredBy", "deregisteredAt"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def aws(*args):
    # Never echo commands/responses: task environments can contain private values.
    result = subprocess.run(
        ["aws", "--region", REGION, "--output", "json", "--no-cli-pager", *args],
        capture_output=True, text=True, timeout=900,
        env={**os.environ, "AWS_PAGER": ""},
    )
    code = re.search(r"An error occurred \(([A-Za-z0-9_.:-]{1,80})\)", result.stderr or "")
    reason = f" ({code.group(1)})" if code else ""
    require(result.returncode == 0, f"AWS {args[0]} {args[1]} failed{reason}; inspect privately; do not rerun blindly")
    return json.loads(result.stdout) if result.stdout.strip() else {}


def replacement(definition, name):
    new = copy.deepcopy(definition)
    require(new["family"] == f"{CLUSTER}-{name}", "Unexpected task family")
    require(len(new["containerDefinitions"]) == 1, "Unexpected containers")
    container = new["containerDefinitions"][0]
    require(container["name"] == name, "Unexpected container name")
    require(container["image"] == f"{ACCOUNT}.dkr.ecr.{REGION}.amazonaws.com/swe5001/{name}@{DIGESTS[name]}", "Image differs from approved release")
    env = container.get("environment", [])
    require(len({item["name"] for item in env}) == len(env), "Duplicate environment key")
    require(not any(item["name"] == KEY for item in container.get("secrets", [])), "Pool setting supplied as secret")
    direct = [item["value"] for item in env if item["name"] == KEY]
    require(not direct, "Pool cap already present: inspect existing receipt, do not repeat rollout")
    alternatives = {item["name"]: item["value"] for item in env if item["name"] in {
        "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "SPRING_APPLICATION_JSON",
        "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE",
    }}
    require(alternatives == {"JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=75"}, "Unexpected JVM/Spring override")
    container["environment"] = sorted([*env, {"name": KEY, "value": "5"}], key=lambda item: item["name"])
    return {key: value for key, value in new.items() if key not in READ_ONLY}


def services():
    result = aws("ecs", "describe-services", "--cluster", CLUSTER, "--services", *COUNTS)
    require(not result.get("failures"), "Service lookup failed")
    found = {item["serviceName"]: item for item in result["services"]}
    require(set(found) == set(COUNTS), "Missing service")
    for name, service in found.items():
        require(service["status"] == "ACTIVE" and service["desiredCount"] == COUNTS[name]
                and service["runningCount"] == COUNTS[name] and service["pendingCount"] == 0,
                "Counts changed or service not stable; stop before rollout")
        require(len(service["deployments"]) == 1 and service["deployments"][0].get("rolloutState") == "COMPLETED", "Deployment not completed")
        config = service["deploymentConfiguration"]
        require(config["minimumHealthyPercent"] == 100 and config["maximumPercent"] == 200
                and config.get("strategy", "ROLLING") == "ROLLING"
                and all(config.get("deploymentCircuitBreaker", {}).get(key) is True for key in ("enable", "rollback")), "Unexpected rollout policy")
    return found


def wait_completed(name, revision):
    # ECS's services-stable waiter can succeed while rolloutState is IN_PROGRESS.
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        result = aws("ecs", "describe-services", "--cluster", CLUSTER, "--services", name)
        require(not result.get("failures") and len(result.get("services", [])) == 1, "Missing rollout service")
        service = result["services"][0]
        require(service["taskDefinition"] == revision, "Rollout reverted or concurrent revision detected")
        require(service["desiredCount"] == COUNTS[name], "Desired count changed during rollout")
        deployments = service["deployments"]
        require(not any(item.get("rolloutState") == "FAILED" for item in deployments), "Deployment failed")
        if (len(deployments) == 1 and deployments[0].get("rolloutState") == "COMPLETED"
                and service["runningCount"] == COUNTS[name] and service["pendingCount"] == 0):
            return
        time.sleep(10)
    raise ValueError(f"{name}: rollout did not complete within 20 minutes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Apply the approved cap, sequentially; default is read-only")
    parser.add_argument("--resume", type=Path, help="Resume this private receipt directory without repeating completed updates")
    args = parser.parse_args()
    require(aws("sts", "get-caller-identity")["Account"] == ACCOUNT, "Wrong AWS account")
    if args.resume:
        directory = args.resume.resolve()
        original = json.loads((directory / "before-private.json").read_text())
        before, definitions = original["services"], original["definitions"]
        require(set(before) == set(COUNTS) and set(definitions) == set(COUNTS), "Incomplete original snapshot")
    else:
        before = services()
        definitions = {name: aws("ecs", "describe-task-definition", "--task-definition", service["taskDefinition"], "--include", "TAGS") for name, service in before.items()}
    prepared = {name: replacement(value["taskDefinition"], name) for name, value in definitions.items()}
    # Sequential identity -> commerce -> notification: peak runtime pools 45.
    # Allow 3 owner, 1 operator, 15 headroom: 45+3+1+15=64 <= 74.
    # Steady maximum: 55+3+1+15=74. Never roll deployments at maximum scale.
    print("Ready: only pool 10 -> 5; counts 1/2/1, images and scaling unchanged.", flush=True)
    if not args.apply and not args.resume:
        return
    if not args.resume:
        directory = Path(tempfile.mkdtemp(prefix="swe5001-pool-cap-", dir=Path.home()))
    directory.chmod(0o700)
    print(f"Private recovery/receipt directory: {directory}", flush=True)
    def save(filename, value):
        with (directory / filename).open("x", encoding="utf-8") as stream:
            os.chmod(stream.name, 0o600)
            json.dump(value, stream, indent=2)
    if not args.resume:
        save("before-private.json", {"services": before, "definitions": definitions})
    expected = {name: value["taskDefinition"] for name, value in before.items()}
    recorded = {}
    if args.resume:
        for name in COUNTS:
            receipt = directory / f"{name}-registered.json"
            if not receipt.exists():
                continue
            registered = json.loads(receipt.read_text())
            require(registered["old"] == expected[name] and registered["new"].startswith(
                f"arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/{CLUSTER}-{name}:"), "Unexpected resume receipt")
            actual = aws("ecs", "describe-task-definition", "--task-definition", registered["new"])["taskDefinition"]
            actual = {key: value for key, value in actual.items() if key not in READ_ONLY}
            for container in actual["containerDefinitions"]:
                container["environment"] = sorted(container.get("environment", []), key=lambda item: item["name"])
            require(actual == prepared[name], "Recorded task revision differs from approved pool-only change")
            wait_completed(name, registered["new"])
            expected[name] = recorded[name] = registered["new"]
    for name in COUNTS:
        current = services()
        require(all(current[key]["taskDefinition"] == expected[key] for key in COUNTS), "Concurrent deployment detected")
        if name in recorded:
            new_arn = recorded[name]
            print(f"{name}: retaining completed {new_arn}; no repeat rollout", flush=True)
        else:
            payload = {**prepared[name], "tags": definitions[name].get("tags", [])}
            save(f"{name}-register-private.json", payload)
            registered = aws("ecs", "register-task-definition", "--cli-input-json", f"file://{directory / (name + '-register-private.json')}")
            new_arn = registered["taskDefinition"]["taskDefinitionArn"]
            save(f"{name}-registered.json", {"old": expected[name], "new": new_arn})
            aws("ecs", "update-service", "--cluster", CLUSTER, "--service", name, "--task-definition", new_arn)
            print(f"{name}: rolling to {new_arn}; waiting for health and COMPLETED", flush=True)
            wait_completed(name, new_arn)
        expected[name] = new_arn
        after = services()
        require(all(after[key]["taskDefinition"] == expected[key] for key in COUNTS), "Rollout reverted or concurrent revision detected")
        for group in after[name].get("loadBalancers", []):
            health = aws("elbv2", "describe-target-health", "--target-group-arn", group["targetGroupArn"])["TargetHealthDescriptions"]
            healthy = [item for item in health if item["TargetHealth"]["State"] == "healthy"]
            require(len(healthy) == COUNTS[name], "Healthy target count mismatch")
        tasks = aws("ecs", "list-tasks", "--cluster", CLUSTER, "--service-name", name)["taskArns"]
        require(len(tasks) == COUNTS[name], "Running task count mismatch")
        actual = aws("ecs", "describe-tasks", "--cluster", CLUSTER, "--tasks", *tasks)
        require(not actual.get("failures") and all(
            task["taskDefinitionArn"] == new_arn and any(
                container["name"] == name and container.get("imageDigest") == DIGESTS[name]
                for container in task["containers"])
            for task in actual["tasks"]), "Task revision/digest mismatch")
        if not (directory / f"{name}-verified.json").exists():
            save(f"{name}-verified.json", {"at": datetime.now(timezone.utc).isoformat(), "service": name, "taskDefinition": new_arn, "pool": 5, "digest": DIGESTS[name], "running": len(tasks), "healthy": True})
        print(f"{name}: healthy; released digest unchanged; pool override 5", flush=True)
    print("Completed. No scaling/count changes or teardown. Retain receipts; reconcile Terraform state separately.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, subprocess.TimeoutExpired) as error:
        raise SystemExit(f"STOP: {error}. Retain private receipts; no automatic retry or teardown.") from None
