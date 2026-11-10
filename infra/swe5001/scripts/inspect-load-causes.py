#!/usr/bin/env python3
"""Read-only follow-up for retained V3 20261110-v3-194543; never sends app load.

Upload beside cap-pools.py after reauthenticating normally. Uses the existing
preflight for resource identities; writes only a new private evidence archive.
"""

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import runpy
import tarfile
import tempfile

CAP = runpy.run_path(str(Path(__file__).with_name("cap-pools.py")))
ALLOWED = {("sts", "get-caller-identity"), ("cloudwatch", "describe-alarms"),
           ("cloudwatch", "describe-alarm-history"), ("cloudwatch", "get-metric-data"),
           ("logs", "filter-log-events"), ("ecs", "describe-services"),
           ("elbv2", "describe-target-health"),
           ("application-autoscaling", "describe-scalable-targets")}
START = "2026-11-09T19:44:00Z"
END = "2026-11-09T20:17:00Z"
PATTERNS = {"instance_connection_limit": ("too many clients", "remaining connection slots"),
            "pool_connection_wait": ("connection is not available",)}


def aws(*args):
    CAP["require"](args[:2] in ALLOWED, "Read-only investigation refused an unapproved operation")
    return CAP["aws"](*args)


def classify(events):
    # Application message bodies remain in memory and are never emitted or archived.
    return [{"timestamp": item["timestamp"], "eventId": item["eventId"],
             "logStreamName": item["logStreamName"],
             "categories": [name for name, patterns in PATTERNS.items()
                            if any(pattern in item.get("message", "").lower() for pattern in patterns)]}
            for item in events]


def metric_queries(alb, group):
    lb = [{"Name": "LoadBalancer", "Value": alb}]
    tg = lb + [{"Name": "TargetGroup", "Value": group}]
    db = [{"Name": "DBInstanceIdentifier", "Value": CAP["CLUSTER"]}]
    specs = [("AWS/ApplicationELB", name, "Sum", lb) for name in (
        "HTTPCode_ELB_5XX_Count", "HTTPCode_ELB_503_Count", "HTTPCode_ELB_504_Count",
        "RejectedConnectionCount", "ActiveConnectionCount")]
    specs += [("AWS/ApplicationELB", name, stat, tg) for name, stat in (
        ("HealthyHostCount", "Minimum"), ("HealthyHostCount", "Maximum"),
        ("UnHealthyHostCount", "Maximum"), ("TargetConnectionErrorCount", "Sum"))]
    specs += [("AWS/RDS", name, stat, db) for name, stat in (
        ("CPUCreditBalance", "Minimum"), ("CPUSurplusCreditBalance", "Maximum"),
        ("SwapUsage", "Maximum"), ("ReadLatency", "Average"), ("WriteLatency", "Average"),
        ("DiskQueueDepth", "Maximum"))]
    specs += [("ECS/ContainerInsights", "RunningTaskCount", "Average", [
        {"Name": "ClusterName", "Value": CAP["CLUSTER"]},
        {"Name": "ServiceName", "Value": "commerce-svc"}])]
    return [{"Id": f"m{index}", "Label": f"{namespace}/{name}/{stat}", "MetricStat": {
        "Metric": {"Namespace": namespace, "MetricName": name, "Dimensions": dimensions},
        "Period": 60, "Stat": stat}} for index, (namespace, name, stat, dimensions) in enumerate(specs)]


def tracking_alarms(policies):
    """Select the exact commerce request policy independently of AWS response order."""
    matches = [policy for policy in policies
               if policy.get("PolicyName") == "blocbuy-swe5001-commerce-requests-per-target"
               and policy.get("PolicyType") == "TargetTrackingScaling"
               and policy.get("ResourceId") == "service/blocbuy-swe5001/commerce-svc"]
    CAP["require"](len(matches) == 1, "Expected exactly one commerce request-tracking policy")
    alarms = matches[0].get("Alarms", [])
    CAP["require"](len(alarms) == 2 and len({alarm["AlarmName"] for alarm in alarms}) == 2
                   and all(alarm["AlarmName"].startswith("TargetTracking-service/blocbuy-swe5001/commerce-svc-") for alarm in alarms),
                   "Unexpected alarm scope")
    return alarms


def main():
    CAP["require"](aws("sts", "get-caller-identity")["Account"] == CAP["ACCOUNT"], "Wrong AWS account")
    original = Path.home() / "swe5001-v3-b84uhoj0"
    preflight = json.loads((original / "preflight.json").read_text())
    CAP["require"](preflight["account"] == CAP["ACCOUNT"] and preflight["region"] == CAP["REGION"], "Unexpected source preflight")
    group = preflight["routing"]["targetGroups"][0]
    prefix = f"arn:aws:elasticloadbalancing:{CAP['REGION']}:{CAP['ACCOUNT']}:"
    CAP["require"](group["arn"].startswith(prefix + "targetgroup/blocbuy-swe5001-commerce-svc/")
                   and len(group["loadBalancerArns"]) == 1
                   and group["loadBalancerArns"][0].startswith(prefix + "loadbalancer/app/blocbuy-swe5001/"), "Unexpected load-balancer scope")
    alb = group["loadBalancerArns"][0].split(":loadbalancer/")[1]
    tg = group["arn"].split(":")[-1]
    alarms = tracking_alarms(preflight["policies"]["ScalingPolicies"])
    directory = Path(tempfile.mkdtemp(prefix="swe5001-v3-causes-", dir=Path.home()))
    directory.chmod(0o700)
    problems = []

    def save(name, value):
        with (directory / name).open("x", encoding="utf-8") as stream:
            os.chmod(stream.name, 0o600)
            json.dump(value, stream, indent=2)

    def collect(name, operation):
        try:
            save(name, operation())
        except (ValueError, KeyError) as error:
            problems.append({"file": name, "error": str(error)})

    collect("alarm-config.json", lambda: aws("cloudwatch", "describe-alarms", "--alarm-names", *[a["AlarmName"] for a in alarms],
        "--query", "MetricAlarms[].{name:AlarmName,namespace:Namespace,metric:MetricName,statistic:Statistic,period:Period,evaluationPeriods:EvaluationPeriods,datapointsToAlarm:DatapointsToAlarm,threshold:Threshold,comparison:ComparisonOperator,treatMissingData:TreatMissingData,dimensions:Dimensions}"))
    for index, alarm in enumerate(alarms):
        collect(f"alarm-history-{index}.json", lambda alarm=alarm: aws("cloudwatch", "describe-alarm-history", "--alarm-name", alarm["AlarmName"],
            "--start-date", START, "--end-date", END, "--history-item-type", "StateUpdate"))
    queries = metric_queries(alb, tg)
    save("metric-queries.json", queries)
    collect("additional-metrics.json", lambda: aws("cloudwatch", "get-metric-data", "--metric-data-queries", f"file://{directory / 'metric-queries.json'}",
        "--start-time", START, "--end-time", END, "--scan-by", "TimestampAscending"))
    start_ms = str(int(datetime.fromisoformat(START.replace("Z", "+00:00")).timestamp() * 1000))
    end_ms = str(int(datetime.fromisoformat(END.replace("Z", "+00:00")).timestamp() * 1000))
    collect("classified-connection-events.json", lambda: {"events": classify(aws("logs", "filter-log-events", "--log-group-name", "/blocbuy/swe5001/commerce-svc",
        "--start-time", start_ms, "--end-time", end_ms,
        "--filter-pattern", '?"too many clients" ?"remaining connection slots" ?"Connection is not available"')["events"]),
        "limit": "Categories are matches per log event, not distinct exception/request counts. No message bodies retained."})
    collect("current-commerce.json", lambda: aws("ecs", "describe-services", "--cluster", CAP["CLUSTER"], "--services", "commerce-svc",
        "--query", "{services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,revision:taskDefinition,deployments:deployments[].{state:rolloutState},events:events[:10]},failures:failures}"))
    collect("current-target-health.json", lambda: aws("elbv2", "describe-target-health", "--target-group-arn", group["arn"]))
    collect("current-floor.json", lambda: aws("application-autoscaling", "describe-scalable-targets", "--service-namespace", "ecs", "--resource-ids", "service/blocbuy-swe5001/commerce-svc"))
    save("context.json", {"runLabel": "20261110-v3-194543", "collectedAt": datetime.now(timezone.utc).isoformat(),
        "historicalWindow": {"start": START, "end": END}, "problems": problems,
        "limits": "Read-only follow-up, no replacement load or missing ECS samples; sparse/missing metrics are not zero. Current configuration is not proof of its historical value."})
    archive = directory.with_suffix(".tar.gz")
    with tarfile.open(archive, "x:gz") as output:
        output.add(directory, arcname=directory.name)
    print(f"Download: {archive}")
    print(f"Collection warnings: {len(problems)}. No load, runtime changes or teardown.")


if __name__ == "__main__":
    main()
