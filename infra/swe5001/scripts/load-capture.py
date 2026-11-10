#!/usr/bin/env python3
"""Short V3 prepare/restore operations paired with observe-load.py on a persistent host.

Never starts a detached CloudShell observer. prepare requires a fresh shared observer
heartbeat before changing counts and before publishing a preflight. No load or teardown.
"""

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess

CAP = runpy.run_path(str(Path(__file__).with_name("cap-pools.py")))
aws, require = CAP["aws"], CAP["require"]
CLUSTER = CAP["CLUSTER"]
RESOURCE = f"service/{CLUSTER}/commerce-svc"
SERVICES = tuple(CAP["COUNTS"])


def now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def driver_sha():
    sha = os.environ.get("TEST_SHA", "")
    require(len(sha) == 40 and all(char in "0123456789abcdef" for char in sha),
            "Set TEST_SHA to the reviewed committed driver source; do not attribute changed tooling to the old release")
    return sha


def write(directory, filename, value):
    with (directory / filename).open("x", encoding="utf-8") as stream:
        os.chmod(stream.name, 0o600)
        json.dump(value, stream, indent=2)


def scaling():
    return aws("application-autoscaling", "describe-scalable-targets", "--service-namespace", "ecs", "--resource-ids", RESOURCE)


def set_floor(value):
    aws("application-autoscaling", "register-scalable-target", "--service-namespace", "ecs",
        "--resource-id", RESOURCE, "--scalable-dimension", "ecs:service:DesiredCount", "--min-capacity", str(value), "--max-capacity", "6")


def observation():
    result = aws("ecs", "describe-services", "--cluster", CLUSTER, "--services", "commerce-svc",
                 "--query", "{services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},events:events[:5]},failures:failures}")
    require(result.get("failures") == [] and len(result["services"]) == 1, "Incomplete observation")
    return {**result, "observedAt": now()}


def require_observer(directory):
    require(directory is not None, "Start observe-load.py on a persistent host, then pass its shared evidence directory")
    directory = Path(directory)
    require((directory / "observer-context.json").is_file() and (directory / "observer-heartbeat.json").is_file(),
            "Persistent observer context/heartbeat is missing; no runtime change permitted")
    context = json.loads((directory / "observer-context.json").read_text(encoding="utf-8"))
    heartbeat = json.loads((directory / "observer-heartbeat.json").read_text(encoding="utf-8"))
    require(context.get("account") == CAP["ACCOUNT"] and context.get("region") == CAP["REGION"]
            and context.get("cluster") == CLUSTER and context.get("persistentHostConfirmed") is True,
            "Observer identity/host confirmation mismatch")
    require(heartbeat.get("session") == context.get("session") and heartbeat.get("state") == "recording",
            "Observer is not recording this session")
    at = datetime.fromisoformat(heartbeat["at"].replace("Z", "+00:00"))
    age = (datetime.now(timezone.utc) - at).total_seconds()
    require(0 <= age <= 30 and heartbeat.get("samples", 0) >= 1, "Observer heartbeat is stale or has no samples")
    started = datetime.fromisoformat(context["startedAt"].replace("Z", "+00:00"))
    seconds = context["seconds"]
    require(isinstance(seconds, int) and 2400 <= seconds <= 7200
            and (started + timedelta(seconds=seconds) - datetime.now(timezone.utc)).total_seconds() >= 2160,
            "Observer needs at least 36 minutes remaining for preflight, the unchanged workload and post-run coverage")
    return directory


def restore_floor():
    require(aws("sts", "get-caller-identity")["Account"] == CAP["ACCOUNT"], "Wrong account")
    targets = scaling()["ScalableTargets"]
    require(len(targets) == 1 and targets[0]["MaxCapacity"] == 6, "Scaling maximum changed; inspect before restoration")
    set_floor(2)
    current = observation()
    if current["services"][0]["desired"] < 2:
        aws("ecs", "update-service", "--cluster", CLUSTER, "--service", "commerce-svc", "--desired-count", "2")
    actual = scaling()
    require(len(actual["ScalableTargets"]) == 1 and actual["ScalableTargets"][0]["MinCapacity"] == 2
            and actual["ScalableTargets"][0]["MaxCapacity"] == 6, "Restoration readback failed")
    return {"at": now(), "targets": actual, "observation": observation(), "settledPlacementVerified": False}


def sanitize_rules(raw, edge):
    origins = [item for item in edge["origins"] if item["id"] == "alb-runtime"]
    require(len(origins) == 1, "Expected runtime origin")
    secrets = [header["HeaderValue"] for header in origins[0].get("headers") or []
               if header["HeaderName"].lower() == "x-origin-secret"]
    sanitized = []
    for item in raw:
        conditions = item.get("conditions") or []
        headers = [condition["header"] for condition in conditions if condition["field"] == "http-header"]
        sanitized.append({
            "priority": item["priority"], "isDefault": item["isDefault"], "actions": item["actions"],
            "hasTransforms": bool(item.get("transforms")),
            "conditionFields": [condition["field"] for condition in conditions],
            "pathPatterns": [path for condition in conditions if condition["field"] == "path-pattern" for path in condition.get("pathPatterns") or []],
            "hasPathRegex": any(condition.get("pathRegex") for condition in conditions),
            "originHeaderMatches": len(secrets) == 1 and bool(secrets[0]) and len(headers) == 1
                and headers[0].get("HttpHeaderName", "").lower() == "x-origin-secret"
                and headers[0].get("Values") == secrets,
        })
    # Header values exist only in process memory, never in the saved snapshot.
    for origin in edge["origins"]:
        origin.pop("headers", None)
    return sanitized


def connection_events(result):
    patterns = {"instance_connection_limit": ("too many clients", "remaining connection slots"),
                "pool_connection_wait": ("connection is not available",)}
    return {"matches": [{"timestamp": item["timestamp"], "eventId": item["eventId"],
        "logStreamName": item["logStreamName"],
        "categories": [name for name, terms in patterns.items() if any(term in item.get("message", "").lower() for term in terms)]}
        for item in result["events"]],
        "limit": "Matches per log event, not distinct requests/exceptions; message bodies are never retained"}


def preflight():
    sha = driver_sha()
    collected = now()
    edge = aws("cloudfront", "get-distribution", "--id", "E3N39GHVFV6CT3", "--query",
        "Distribution.{id:Id,domainName:DomainName,status:Status,enabled:DistributionConfig.Enabled,behaviors:DistributionConfig.CacheBehaviors.Items[].{pathPattern:PathPattern,targetOriginId:TargetOriginId,functions:FunctionAssociations.Quantity,lambdas:LambdaFunctionAssociations.Quantity,cachePolicyId:CachePolicyId},origins:DistributionConfig.Origins.Items[].{id:Id,domainName:DomainName,path:OriginPath,protocolPolicy:CustomOriginConfig.OriginProtocolPolicy,httpPort:CustomOriginConfig.HTTPPort,httpsPort:CustomOriginConfig.HTTPSPort,headers:CustomHeaders.Items}}")
    services = aws("ecs", "describe-services", "--cluster", CLUSTER, "--services", "commerce-svc", "--query",
        "{services:services[].{serviceName:serviceName,status:status,desiredCount:desiredCount,runningCount:runningCount,pendingCount:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},loadBalancers:loadBalancers[].{targetGroupArn:targetGroupArn,containerName:containerName,containerPort:containerPort}},failures:failures}")
    require(services.get("failures") == [] and len(services["services"]) == 1, "Missing service")
    service = services["services"][0]
    require(service["desiredCount"] == service["runningCount"] == 1 and service["pendingCount"] == 0, "Not stable at one commerce task")
    require(len(service["loadBalancers"]) == 1, "Unexpected target groups")
    group = service["loadBalancers"][0]["targetGroupArn"]
    groups = aws("elbv2", "describe-target-groups", "--target-group-arns", group, "--query", "TargetGroups[].{arn:TargetGroupArn,loadBalancerArns:LoadBalancerArns,port:Port,protocol:Protocol}")
    require(len(groups) == 1 and len(groups[0]["loadBalancerArns"]) == 1, "Unexpected ALB")
    alb = groups[0]["loadBalancerArns"][0]
    balancers = aws("elbv2", "describe-load-balancers", "--load-balancer-arns", alb, "--query", "LoadBalancers[].{arn:LoadBalancerArn,dnsName:DNSName,type:Type,state:State.Code}")
    listeners = aws("elbv2", "describe-listeners", "--load-balancer-arn", alb, "--query", "Listeners[].{arn:ListenerArn,loadBalancerArn:LoadBalancerArn,port:Port,protocol:Protocol}")
    origins = [item for item in edge["origins"] if item["id"] == "alb-runtime"]
    require(len(origins) == 1, "Missing runtime origin")
    origin = origins[0]
    policy = origin["protocolPolicy"]
    require(policy in ("http-only", "https-only", "match-viewer"), "Unexpected origin protocol")
    protocol, port = ("HTTP", origin["httpPort"]) if policy == "http-only" else ("HTTPS", origin["httpsPort"])
    selected = [item for item in listeners if item["protocol"] == protocol and item["port"] == port]
    require(len(selected) == 1, "Unexpected listener")
    listener = selected[0]["arn"]
    raw = aws("elbv2", "describe-rules", "--listener-arn", listener, "--query", "Rules[].{priority:Priority,isDefault:IsDefault,transforms:Transforms[].Type,actions:Actions[].{type:Type,targetGroupArn:TargetGroupArn,forwardGroups:ForwardConfig.TargetGroups[].{arn:TargetGroupArn,weight:Weight}},conditions:Conditions[].{field:Field,pathPatterns:PathPatternConfig.Values,pathRegex:PathPatternConfig.RegexValues,header:HttpHeaderConfig}}")
    rules = sanitize_rules(raw, edge)
    arns = aws("ecs", "list-tasks", "--cluster", CLUSTER, "--service-name", "commerce-svc")["taskArns"]
    require(len(arns) == 1, "Expected one running task")
    tasks = aws("ecs", "describe-tasks", "--cluster", CLUSTER, "--tasks", *arns, "--query", "{tasks:tasks[].{taskArn:taskArn,taskDefinitionArn:taskDefinitionArn,lastStatus:lastStatus,desiredStatus:desiredStatus,availabilityZone:availabilityZone,containers:containers[].{name:name,imageDigest:imageDigest}},failures:failures}")
    policies = aws("application-autoscaling", "describe-scaling-policies", "--service-namespace", "ecs", "--resource-id", RESOURCE, "--scalable-dimension", "ecs:service:DesiredCount")
    cpu = None
    if any(p.get("PolicyName") == "blocbuy-swe5001-commerce-cpu-headroom" for p in policies["ScalingPolicies"]):
        cpu = {"alarms": aws("cloudwatch", "describe-alarms", "--alarm-names", "blocbuy-swe5001-commerce-cpu-headroom", "--query",
            "MetricAlarms[].{name:AlarmName,namespace:Namespace,metric:MetricName,statistic:Statistic,period:Period,evaluationPeriods:EvaluationPeriods,datapointsToAlarm:DatapointsToAlarm,threshold:Threshold,comparison:ComparisonOperator,missingData:TreatMissingData,actionsEnabled:ActionsEnabled,actions:AlarmActions,dimensions:Dimensions}")}
    return {"schema": 1, "account": CAP["ACCOUNT"], "region": CAP["REGION"], "cluster": CLUSTER,
        "collectedAt": collected, "toolingSha": sha, "mode": "v3", "baseUrl": "https://" + edge["domainName"],
        "services": services, "tasks": tasks, "targets": scaling(), "policies": policies,
        "routing": {"edge": edge, "targetGroups": groups, "loadBalancers": balancers, "listeners": listeners, "rulesListenerArn": listener, "rules": rules},
        "cpuHeadroom": cpu,
        "collector": {"state": "operator-helper-by-content-hash", "sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), "driverSourceSha": sha}}


def prepare(directory=None):
    directory = require_observer(directory)
    driver_sha()
    require(not (directory / "before.json").exists() and not (directory / "preflight.json").exists(),
            "Preparation receipt already exists; inspect/restore rather than repeating")
    require(aws("sts", "get-caller-identity")["Account"] == CAP["ACCOUNT"], "Wrong account")
    before = CAP["services"]()
    for name, service in before.items():
        definition = aws("ecs", "describe-task-definition", "--task-definition", service["taskDefinition"])["taskDefinition"]
        containers = [item for item in definition["containerDefinitions"] if item["name"] == name]
        require(len(containers) == 1, "Unexpected container")
        require(containers[0]["image"].endswith("@" + CAP["DIGESTS"][name]), "Unexpected release image")
        require([item["value"] for item in containers[0]["environment"] if item["name"] == CAP["KEY"]] == ["5"], "Pool cap not present")
    targets = scaling()["ScalableTargets"]
    require(len(targets) == 1 and targets[0]["MinCapacity"] == 2 and targets[0]["MaxCapacity"] == 6
            and targets[0]["SuspendedState"] == {"DynamicScalingInSuspended": False, "DynamicScalingOutSuspended": False, "ScheduledScalingSuspended": False}, "Unexpected scaling bounds/state")
    write(directory, "before.json", {"at": now(), "services": before, "targets": targets})
    print(f"Evidence: {directory}", flush=True)
    try:
        set_floor(1)
        aws("ecs", "update-service", "--cluster", CLUSTER, "--service", "commerce-svc", "--desired-count", "1")
        aws("ecs", "wait", "services-stable", "--cluster", CLUSTER, "--services", "commerce-svc")
        require_observer(directory)
        snapshot = preflight()
        require_observer(directory)
        write(directory, "preflight.json", snapshot)
    except (Exception, KeyboardInterrupt):
        try:
            write(directory, "prepare-failure-restoration.json", restore_floor())
        except Exception:
            print("Restoration not verified; retain receipts and use the explicit restore command.", flush=True)
        raise
    print(f"READY: {directory / 'preflight.json'}; persistent observer heartbeat verified. Start k6 within five minutes.", flush=True)
    print("No load started. Restore the floor explicitly after measurement; observer never changes runtime.", flush=True)


def finish(directory, start, read=None):
    read = aws if read is None else read
    end = datetime.now(timezone.utc)
    activities = read("application-autoscaling", "describe-scaling-activities", "--service-namespace", "ecs", "--resource-id", RESOURCE)
    write(directory, "scaling-activities.json", activities)
    query_start = start - timedelta(minutes=2)
    group = json.loads((directory / "preflight.json").read_text(encoding="utf-8"))["routing"]["targetGroups"][0]
    alb_suffix = group["loadBalancerArns"][0].split(":loadbalancer/")[1]
    group_suffix = group["arn"].split(":")[-1]
    dimensions = [{"Name": "LoadBalancer", "Value": alb_suffix}, {"Name": "TargetGroup", "Value": group_suffix}]
    specs = [("AWS/ApplicationELB", metric, stat, dimensions) for metric, stat in (
        ("RequestCount", "Sum"), ("RequestCountPerTarget", "Sum"), ("TargetResponseTime", "p50"),
        ("TargetResponseTime", "p95"), ("TargetResponseTime", "p99"), ("HTTPCode_Target_5XX_Count", "Sum"))]
    specs += [("AWS/RDS", metric, stat, [{"Name": "DBInstanceIdentifier", "Value": CLUSTER}])
              for metric, stat in (("DatabaseConnections", "Maximum"), ("CPUUtilization", "Average"), ("FreeableMemory", "Minimum"))]
    specs += [("AWS/ECS", metric, "Average", [{"Name": "ClusterName", "Value": CLUSTER}, {"Name": "ServiceName", "Value": name}])
              for name in SERVICES for metric in ("CPUUtilization", "MemoryUtilization")]
    queries = [{"Id": f"m{index}", "Label": f"{namespace}/{metric}/{stat}/{dims[-1]['Value']}", "MetricStat": {
        "Metric": {"Namespace": namespace, "MetricName": metric, "Dimensions": dims}, "Period": 60, "Stat": stat}}
        for index, (namespace, metric, stat, dims) in enumerate(specs)]
    write(directory, "metric-queries.json", queries)
    write(directory, "metrics.json", read("cloudwatch", "get-metric-data", "--metric-data-queries", f"file://{directory / 'metric-queries.json'}",
        "--start-time", query_start.isoformat(), "--end-time", end.isoformat(), "--scan-by", "TimestampAscending"))
    # Project connection-related errors only, never application log bodies.
    for name in SERVICES:
        result = read("logs", "filter-log-events", "--log-group-name", f"/blocbuy/swe5001/{name}",
            "--start-time", str(int(start.timestamp() * 1000)), "--end-time", str(int(end.timestamp() * 1000)),
            "--filter-pattern", '?"too many clients" ?"remaining connection slots" ?"Connection is not available"')
        write(directory, f"{name}-connection-errors.json", connection_events(result))


def observe(directory):
    raise ValueError("Detached observation was removed; use observe-load.py --persistent-host outside CloudShell")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "restore", "observe"))
    parser.add_argument("directory", nargs="?")
    args = parser.parse_args()
    try:
        if args.mode == "prepare":
            prepare(Path(args.directory) if args.directory else None)
        elif args.mode == "restore":
            require(args.directory is not None and Path(args.directory).is_dir(), "Missing existing evidence directory")
            stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
            write(Path(args.directory), f"restoration-{stamp}.json", restore_floor())
            import tarfile
            archive = Path(str(Path(args.directory)) + f".restored-{stamp}.tar.gz")
            with tarfile.open(archive, "x:gz") as output:
                output.add(args.directory, arcname=Path(args.directory).name)
            print(f"Restoration receipt and evidence archive: {archive}; no settled-placement claim or teardown.")
        else:
            require(args.directory is not None, "Missing evidence directory")
            observe(Path(args.directory))
    except (ValueError, KeyError, subprocess.TimeoutExpired) as error:
        raise SystemExit(f"STOP: {error}. Preserve evidence; no automatic retry or teardown.") from None
