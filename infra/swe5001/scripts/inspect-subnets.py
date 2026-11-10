#!/usr/bin/env python3
"""Collect projected, read-only AWS metadata for SWE5001 subnet verification.

This does not deploy, read Terraform state/SSM values, or test packet connectivity.
Only a new local evidence directory is written. Review it against the checklist in
docs/swe5001/subnet-verification.md before recording an acceptance result.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys

ACCOUNT = "402178232802"
REGION = "ap-southeast-1"
NAME = "blocbuy-swe5001"
SERVICES = ("identity-svc", "commerce-svc", "notification-svc")
READ_CALLS = {
    ("sts", "get-caller-identity"),
    ("ec2", "describe-vpcs"),
    ("ec2", "describe-subnets"),
    ("ec2", "describe-route-tables"),
    ("ec2", "describe-security-groups"),
    ("ec2", "describe-network-interfaces"),
    ("ec2", "describe-vpc-endpoints"),
    ("ecs", "describe-services"),
    ("ecs", "list-tasks"),
    ("ecs", "describe-tasks"),
    ("rds", "describe-db-instances"),
    ("elasticache", "describe-replication-groups"),
    ("elasticache", "describe-cache-clusters"),
    ("elasticache", "describe-cache-subnet-groups"),
}


def aws(*args):
    if tuple(args[:2]) not in READ_CALLS:
        raise ValueError("Only allowlisted read-only AWS calls are permitted")
    command = ["aws", *args, "--region", REGION, "--output", "json", "--no-cli-pager",
               "--cli-connect-timeout", "10", "--cli-read-timeout", "60"]
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=90)
    return json.loads(result.stdout)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def collect(directory, call=aws):
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)

    def save(filename, value):
        path = directory / filename
        # Exclusive creation also refuses accidental overwrite of an existing file.
        with path.open("x", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2)
            stream.write("\n")
        path.chmod(0o600)
        return value

    def capture(filename, *args):
        return save(filename, call(*args))

    try:
        account = call("sts", "get-caller-identity", "--query", "Account")
        require(account == ACCOUNT, f"Expected account {ACCOUNT}; no resource reads performed")
        save("metadata.json", {"account": account, "region": REGION,
                               "started_at": datetime.now(timezone.utc).isoformat(),
                               "purpose": "Read-only subnet metadata; acceptance not yet assessed"})
        vpcs = capture("vpc.json", "ec2", "describe-vpcs", "--filters",
                       "Name=tag:Course,Values=swe5001", "Name=tag:Stack,Values=runtime",
                       "--query", "Vpcs[].{id:VpcId,cidr:CidrBlock,state:State}")
        require(len(vpcs) == 1 and vpcs[0]["state"] == "available",
                "Expected exactly one available SWE5001 runtime VPC; check session state")
        vpc = vpcs[0]["id"]
        capture("subnets.json", "ec2", "describe-subnets", "--filters", f"Name=vpc-id,Values={vpc}",
                "--query", "Subnets[].{id:SubnetId,cidr:CidrBlock,az:AvailabilityZone,public_ips:MapPublicIpOnLaunch}")
        capture("routes.json", "ec2", "describe-route-tables", "--filters", f"Name=vpc-id,Values={vpc}",
                "--query", "RouteTables[].{id:RouteTableId,associations:Associations,routes:Routes}")
        capture("endpoints.json", "ec2", "describe-vpc-endpoints", "--filters", f"Name=vpc-id,Values={vpc}",
                "--query", "VpcEndpoints[].{id:VpcEndpointId,type:VpcEndpointType,service:ServiceName,routes:RouteTableIds,subnets:SubnetIds}")
        groups = capture("security-groups.json", "ec2", "describe-security-groups", "--filters",
                         f"Name=vpc-id,Values={vpc}",
                         "Name=group-name,Values=" + ",".join(f"{NAME}-{x}" for x in ("svc", "rds", "cache", "dbinit")),
                         "--query", "SecurityGroups[].{id:GroupId,name:GroupName,ingress:IpPermissions,egress:IpPermissionsEgress}")
        by_name = {g["name"]: g["id"] for g in groups}
        expected_groups = {f"{NAME}-{x}" for x in ("svc", "rds", "cache", "dbinit")}
        require(len(groups) == 4 and set(by_name) == expected_groups,
                "Expected the four runtime service/data/db-init security groups; "
                f"missing={sorted(expected_groups - set(by_name))}; "
                f"unexpected={sorted(set(by_name) - expected_groups)}; "
                f"returned={len(groups)}; inspect security-groups.json and session state")
        services = capture("services.json", "ecs", "describe-services", "--cluster", NAME,
                           "--services", *SERVICES, "--query",
                           "{failures:failures,services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,network:networkConfiguration}}")
        require(not services["failures"] and len(services["services"]) == len(SERVICES)
                and {s["name"] for s in services["services"]} == set(SERVICES),
                "Missing distributed service or ECS returned a failure")
        require(all(s["status"] == "ACTIVE" and s["desired"] > 0 and s["running"] == s["desired"]
                    and s["pending"] == 0 for s in services["services"]),
                "Services are not ready; wait for startup before collecting acceptance evidence")
        tasks = []
        running = {s["name"]: s["running"] for s in services["services"]}
        for service in SERVICES:
            arns = capture(f"tasks-{service}.json", "ecs", "list-tasks", "--cluster", NAME,
                           "--service-name", service, "--desired-status", "RUNNING", "--query", "taskArns")
            require(arns and all(a.startswith(f"arn:aws:ecs:{REGION}:{ACCOUNT}:task/{NAME}/") for a in arns),
                    f"No running tasks or unexpected task ARN for {service}")
            require(len(arns) == running[service], f"Task count changed for {service}; repeat collection")
            tasks.extend(arns)
        require(len(set(tasks)) == len(tasks) <= 100, "Unexpected task count; no truncated batch is accepted")
        task_data = capture("task-details.json", "ecs", "describe-tasks", "--cluster", NAME, "--tasks", *tasks,
                            "--query", "{failures:failures,tasks:tasks[].{arn:taskArn,definition:taskDefinitionArn,status:lastStatus,attachments:attachments,containers:containers[].{name:name,image:image,digest:imageDigest}}}")
        require(not task_data["failures"] and len(task_data["tasks"]) == len(tasks)
                and all(t["status"] == "RUNNING" for t in task_data["tasks"]),
                "Tasks changed or lookup failed; collection is inconclusive")
        enis = [d["value"] for t in task_data["tasks"] for a in t["attachments"]
                if a["type"] == "ElasticNetworkInterface" for d in a["details"] if d["name"] == "networkInterfaceId"]
        require(len(set(enis)) == len(tasks) and all(re.fullmatch(r"eni-[0-9a-f]+", e) for e in enis),
                "Expected one task ENI per running task")
        projection = "NetworkInterfaces[].{id:NetworkInterfaceId,vpc:VpcId,subnet:SubnetId,az:AvailabilityZone,status:Status,description:Description,managed:RequesterManaged,groups:Groups,public_ip:Association.PublicIp}"
        task_enis = capture("task-enis.json", "ec2", "describe-network-interfaces",
                            "--network-interface-ids", *enis, "--query", projection)
        require(len(task_enis) == len(enis) and all(e["vpc"] == vpc for e in task_enis), "Task ENI inventory is incomplete or in another VPC")
        rds = capture("rds.json", "rds", "describe-db-instances", "--db-instance-identifier", NAME, "--query",
                      "DBInstances[].{id:DBInstanceIdentifier,status:DBInstanceStatus,az:AvailabilityZone,public:PubliclyAccessible,subnet_group:DBSubnetGroup,groups:VpcSecurityGroups}")
        require(len(rds) == 1 and rds[0]["id"] == NAME, "Expected the runtime RDS instance")
        cache = capture("cache-group.json", "elasticache", "describe-replication-groups", "--replication-group-id", NAME,
                        "--query", "ReplicationGroups[].{id:ReplicationGroupId,status:Status,members:MemberClusters}")
        require(len(cache) == 1 and len(cache[0]["members"]) == 1, "Expected the configured single-node Valkey group")
        cluster = capture("cache-node.json", "elasticache", "describe-cache-clusters", "--cache-cluster-id", cache[0]["members"][0],
                          "--query", "CacheClusters[].{id:CacheClusterId,status:CacheClusterStatus,az:PreferredAvailabilityZone,subnet_group:CacheSubnetGroupName,groups:SecurityGroups}")
        require(len(cluster) == 1 and cluster[0]["subnet_group"] == NAME, "Unexpected cache subnet group")
        capture("cache-subnets.json", "elasticache", "describe-cache-subnet-groups", "--cache-subnet-group-name", NAME,
                "--query", "CacheSubnetGroups[].{name:CacheSubnetGroupName,vpc:VpcId,subnets:Subnets}")
        data_enis = capture("data-enis.json", "ec2", "describe-network-interfaces", "--filters",
                            f"Name=vpc-id,Values={vpc}",
                            f"Name=group-id,Values={by_name[NAME + '-rds']},{by_name[NAME + '-cache']}", "--query", projection)
        require(all(any(g["GroupId"] == by_name[NAME + '-' + kind] for e in data_enis for g in e["groups"])
                    for kind in ("rds", "cache")), "RDS/cache ENIs were not both observed; do not infer placement from subnet groups alone")
        save("collection.json", {"completed_at": datetime.now(timezone.utc).isoformat(),
                                 "metadata_collection": "complete", "acceptance": "not assessed",
                                 "connectivity": "not tested"})
    except Exception:
        save("incomplete.json", {"metadata_collection": "incomplete", "acceptance": "not verified"})
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", help="New private output directory; existing directories are refused")
    args = parser.parse_args()
    try:
        collect(args.directory)
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        # Do not echo raw provider responses or subprocess stdout/stderr.
        reason = str(error) if type(error) is ValueError else f"{type(error).__name__}; check session/account/read permissions"
        print(f"Collection failed: {reason}. Partial evidence is not acceptance.", file=sys.stderr)
        return 1
    print(f"Metadata saved to {args.directory}. Review the subnet checklist; connectivity is NOT tested.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
