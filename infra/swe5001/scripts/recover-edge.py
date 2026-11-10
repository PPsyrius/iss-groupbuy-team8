#!/usr/bin/env python3
"""One-off CloudShell recovery for promotion #757's operator-owned edge policy.

Plans by default. --apply asks for one explicit confirmation after checking the
private plan. Never changes IAM or starts runtime. Uses the exact merged source,
not this local working tree. Plans/logs persist in private home storage; bulky
Terraform tools/provider working data use a separate temporary directory.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shlex
import shutil
import stat
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen
import zipfile

ACCOUNT = "402178232802"
REGION = "ap-southeast-1"
SHA = "e177a3fbb21bccaa9bd232f30ea7dbed6775d48c"
REPO = "PPsyrius/iss-groupbuy-team8"
VERSION = "1.16.4"
POLICY_ID = "03faf3c6-02b3-4a63-b3af-4154bc434e74"
DISTRIBUTION_ID = "E3N39GHVFV6CT3"
IDLE_ORIGIN = "runtime-absent.blocbuy-swe5001.invalid"
POLICY = "aws_cloudfront_response_headers_policy.security"
DISTRIBUTION = "aws_cloudfront_distribution.this"
BUCKET = f"blocbuy-swe5001-spa-{ACCOUNT}"
BUCKET_POLICY = "aws_s3_bucket_policy.spa"
SOURCE_PREFIX = "SWE5001_SOURCE_"
SOURCE_KEYS = tuple("AWS_" + suffix for suffix in (
    "PROFILE", "DEFAULT_PROFILE", "CONFIG_FILE", "SHARED_CREDENTIALS_FILE", "SDK_LOAD_CONFIG",
    "ACCESS_KEY_ID", "SECRET_ACCESS_KEY", "SESSION_TOKEN", "SECURITY_TOKEN", "CREDENTIAL_EXPIRATION",
    "CONTAINER_CREDENTIALS_FULL_URI", "CONTAINER_CREDENTIALS_RELATIVE_URI",
    "CONTAINER_AUTHORIZATION_TOKEN", "CONTAINER_AUTHORIZATION_TOKEN_FILE",
    "ROLE_ARN", "ROLE_SESSION_NAME", "WEB_IDENTITY_TOKEN_FILE", "EC2_METADATA_DISABLED",
))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def changed_keys(before, after):
    return {key for key in before.keys() | after.keys() if before.get(key) != after.get(key)}


def validate_credentials(credentials, now=None):
    """Accept valid short-lived credentials: SDKs refresh using Expiration."""
    require(credentials.get("Version") == 1, "Unexpected AWS CLI credential-process protocol version")
    require(all(isinstance(credentials.get(key), str) and credentials[key] for key in
                ("AccessKeyId", "SecretAccessKey", "SessionToken", "Expiration")),
            "AWS CLI did not resolve temporary credentials; refresh the CloudShell sign-in")
    expiration = datetime.fromisoformat(credentials["Expiration"].replace("Z", "+00:00"))
    now = now or datetime.now(timezone.utc)
    require(expiration.tzinfo is not None and expiration > now,
            "Temporary credentials are expired; refresh the CloudShell sign-in")


def credential_environment(base, config, shared):
    """Select a refreshable SDK profile, retaining original sources only in memory."""
    env = dict(base)
    for key in SOURCE_KEYS:
        env.pop(SOURCE_PREFIX + key, None)
        if key in env:
            env[SOURCE_PREFIX + key] = env.pop(key)
    env.update({"AWS_PROFILE": "swe5001-edge-recovery", "AWS_CONFIG_FILE": str(config),
                "AWS_SHARED_CREDENTIALS_FILE": str(shared), "AWS_SDK_LOAD_CONFIG": "1",
                "AWS_EC2_METADATA_DISABLED": "true"})
    return env


def original_credential_environment(env):
    """Avoid credential_process recursion by restoring the original CLI chain."""
    original = dict(env)
    for key in SOURCE_KEYS:
        original.pop(key, None)
        source = original.pop(SOURCE_PREFIX + key, None)
        if source is not None:
            original[key] = source
    return original


def export_credential_process():
    """Private SDK protocol: stdout is consumed by the SDK, never the terminal."""
    try:
        result = subprocess.run(["aws", "configure", "export-credentials", "--format", "process", "--no-cli-pager"],
                                env=original_credential_environment(os.environ), capture_output=True,
                                text=True, encoding="utf-8", check=False)
        if result.returncode != 0:
            raise ValueError("CLI credential resolution failed")
        credentials = json.loads(result.stdout)
        validate_credentials(credentials)
        sys.stdout.write(json.dumps(credentials) + "\n")
        return 0
    except (ValueError, KeyError, OSError):
        print("Temporary credential refresh failed; refresh the CloudShell sign-in", file=sys.stderr)
        return 1


def validate_resume(work):
    info = work.lstat()
    require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid() and not info.st_mode & 0o077,
            "Resume directory must be a private directory owned by the current operator, not a symlink")
    allowed_parents = {Path(tempfile.gettempdir()).resolve(), (Path.home() / ".swe5001-edge-recovery").resolve()}
    require(work.name.startswith("swe5001-edge-recovery-") and work.parent.resolve() in allowed_parents,
            "Unexpected recovery directory")
    require((work / "repo/.git").is_dir(), "Resume directory lacks the original checkout")


def persistent_work():
    root = Path.home() / ".swe5001-edge-recovery"
    work = root / ("swe5001-edge-recovery-" + SHA[:12])
    for directory in (root, work):
        directory.mkdir(mode=0o700, exist_ok=True)
        info = directory.lstat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid() and not info.st_mode & 0o077,
                "Recovery storage must be private, operator-owned directories, not symlinks")
    return work


def approved_changes(plan, targeted=False):
    """Fail closed on deletes, replacements, unknown resources or origin secrets."""
    require(plan.get("errored") is not True, "Terraform reports an errored plan")
    require(plan.get("complete", True) is True or targeted, "Terraform plan is incomplete")
    approved = []
    for resource in plan.get("resource_changes", []):
        change = resource["change"]
        actions = change["actions"]
        if actions == ["no-op"]:
            continue
        if resource.get("mode") == "data" and actions == ["read"]:
            continue
        address = resource["address"]
        require(resource.get("mode") == "managed" and actions == ["update"],
                "Plan contains a create/delete/replacement or unexpected action; stop for review")
        require(address in (POLICY, DISTRIBUTION),
                f"Plan changes an unreviewed resource: {address}; stop for review")
        before, after = change["before"], change["after"]
        require(isinstance(before, dict) and isinstance(after, dict), "Missing plan values")
        if address == POLICY:
            require(before.get("id") == after.get("id") == POLICY_ID, "Unexpected header-policy ID")
            require(before.get("name") == after.get("name") == "blocbuy-swe5001-security-headers",
                    "Unexpected header-policy name")
            require(changed_keys(before, after) <= {
                "security_headers_config", "custom_headers_config", "remove_headers_config", "comment", "etag"
            }, "Header policy has changes outside the expected configuration")
        else:
            require(before.get("id") == after.get("id") == DISTRIBUTION_ID, "Unexpected distribution ID")
            require(changed_keys(before, after) <= {
                "origin", "etag", "last_modified_time", "status", "in_progress_validation_batches"
            }, "Distribution has changes beyond the idle origin; stop for review")
            old = {origin["origin_id"]: dict(origin) for origin in before.get("origin", [])}
            new = {origin["origin_id"]: dict(origin) for origin in after.get("origin", [])}
            require(set(old) == set(new) == {"s3-spa", "alb-runtime"}, "Unexpected origin inventory")
            require(new["alb-runtime"].get("domain_name") == IDLE_ORIGIN, "API origin is not the idle placeholder")
            old["alb-runtime"].pop("domain_name", None)
            new["alb-runtime"].pop("domain_name", None)
            require(old == new, "Origin configuration/secret changed beyond the idle hostname; stop for review")
        require(address not in approved, "Duplicate resource change")
        approved.append(address)
    require(not plan.get("deferred_changes"), "Plan contains deferred changes")
    return approved


def expected_bucket_policy():
    return {"Version": "2012-10-17", "Statement": [{
        "Sid": "AllowCloudFrontOac", "Effect": "Allow", "Action": "s3:GetObject",
        "Resource": f"arn:aws:s3:::{BUCKET}/*", "Principal": {"Service": "cloudfront.amazonaws.com"},
        "Condition": {"StringEquals": {"AWS:SourceArn": f"arn:aws:cloudfront::{ACCOUNT}:distribution/{DISTRIBUTION_ID}"}},
    }]}


def canonical_policy(value):
    """IAM treats singleton/list actions, resources and principals equivalently."""
    if isinstance(value, dict):
        return {key: canonical_policy(item) for key, item in value.items()}
    if isinstance(value, list):
        items = sorted((canonical_policy(item) for item in value), key=lambda item: json.dumps(item, sort_keys=True))
        return items[0] if len(items) == 1 else items
    return value


def verify_bucket_policy(value):
    require(isinstance(value, str), "Bucket policy is unavailable for review")
    require(canonical_policy(json.loads(value)) == canonical_policy(expected_bucket_policy()),
            "Bucket policy differs from the exact expected OAC-only grant; no S3 policy write approved")


def bucket_dependency_only(plan):
    """Prove that S3 policy may be left untouched during scoped CF recovery."""
    matches = [resource for resource in plan.get("resource_changes", [])
               if resource["address"] == BUCKET_POLICY and resource["change"]["actions"] != ["no-op"]]
    if not matches:
        return False
    require(len(matches) == 1, "Duplicate bucket-policy changes")
    resource = matches[0]
    change = resource["change"]
    require(resource.get("mode") == "managed" and change["actions"] == ["update"],
            "Bucket-policy create/delete/replacement is not approved")
    before, after = change["before"], change["after"]
    require(before.get("id") == after.get("id") == BUCKET and before.get("bucket") == after.get("bucket") == BUCKET,
            "Unexpected SPA bucket identity")
    require(changed_keys(before, after) <= {"policy"}, "Bucket-policy change is not limited to its document")
    verify_bucket_policy(before.get("policy"))
    if change.get("after_unknown", {}).get("policy") is True:
        require(after.get("policy") is None, "Inconsistent unknown bucket policy")
        require(any(item["address"] == "data.aws_iam_policy_document.spa_read" and
                    item.get("mode") == "data" and item["change"]["actions"] == ["read"] and
                    item.get("action_reason") in ("read_because_dependency_pending", "read_because_config_unknown")
                    for item in plan.get("resource_changes", [])),
                "Unknown bucket policy is not explained by the pending distribution dependency")
    else:
        verify_bucket_policy(after.get("policy"))
    # Review every other managed change before even planning a targeted recovery.
    remainder = dict(plan, resource_changes=[item for item in plan["resource_changes"] if item is not resource])
    approved_changes(remainder)
    return True


def describe_plan_scope(plan):
    """Report addresses/actions only, including changes a scope guard will reject."""
    return [", ".join(resource["change"]["actions"]) + ": " + resource["address"]
            for resource in plan.get("resource_changes", [])
            if resource.get("mode") == "managed" and resource["change"]["actions"] != ["no-op"]]


def plan_scope_records(plan):
    records = []
    for resource in plan.get("resource_changes", []):
        change = resource["change"]
        if resource.get("mode") != "managed" or change["actions"] == ["no-op"]:
            continue
        before, after = change.get("before") or {}, change.get("after") or {}
        records.append({"address": resource["address"], "actions": change["actions"],
                        "changed_fields": sorted(changed_keys(before, after))})
    return records


def inspect_private_plan(runner, tf, plan_path, work, label):
    result = runner.run("Checking private plan scope", tf + ["show", "-json", str(plan_path)], True)
    plan = json.loads(result.stdout)
    scope = {"source": SHA, "account": ACCOUNT, "region": REGION, "kind": label,
             "resources": plan_scope_records(plan)}
    scope_path = work / "latest-plan-scope.json"
    summary = json.dumps(scope, indent=2) + "\n"
    scope_path.write_text(summary, encoding="utf-8")
    (runner.logs / (label + "-plan-scope.json")).write_text(summary, encoding="utf-8")
    print(f"{label} plan scope (addresses/actions only; not yet approved):", flush=True)
    for line in describe_plan_scope(plan):
        print("  " + line, flush=True)
    print(f"Safe summary saved: {scope_path}", flush=True)
    return plan


def read_url(url):
    request = Request(url, headers={"User-Agent": "BlocBuy-SWE5001-edge-recovery"})
    with urlopen(request, timeout=90) as response:
        return response.read()


def check_workflows():
    base = f"https://api.github.com/repos/{REPO}"
    ref = json.loads(read_url(base + "/git/ref/heads/prod"))
    require(ref["object"]["sha"] == SHA, "prod moved beyond #757; review the new revision before recovery")
    for workflow in ("swe5001-deploy.yml", "swe5001-env.yml"):
        data = json.loads(read_url(base + f"/actions/workflows/{workflow}/runs?per_page=100"))
        require(all(run["status"] == "completed" for run in data["workflow_runs"]),
                "A deployment/environment workflow is active; do not overlap it")


def install_terraform(work):
    architecture = {"x86_64": "amd64", "aarch64": "arm64"}.get(platform.machine())
    require(sys.platform.startswith("linux") and architecture, "Run this recovery in Linux CloudShell")
    filename = f"terraform_{VERSION}_linux_{architecture}.zip"
    base = f"https://releases.hashicorp.com/terraform/{VERSION}/"
    checksums = read_url(base + f"terraform_{VERSION}_SHA256SUMS").decode("ascii")
    expected = [line.split()[0] for line in checksums.splitlines() if line.split()[-1] == filename]
    require(len(expected) == 1, "Missing Terraform archive checksum")
    archive = read_url(base + filename)
    require(hashlib.sha256(archive).hexdigest() == expected[0], "Terraform checksum mismatch")
    archive_path = work / filename
    archive_path.write_bytes(archive)
    binary = work / "terraform"
    with zipfile.ZipFile(archive_path) as bundle:
        binary.write_bytes(bundle.read("terraform"))
    binary.chmod(0o700)
    archive_path.unlink()
    return binary


class Runner:
    def __init__(self, work):
        self.work = work
        self.index = 0
        self.logs = Path(tempfile.mkdtemp(prefix="attempt-", dir=work))
        self._tools = tempfile.TemporaryDirectory(prefix="swe5001-edge-tools-")
        self.cache = Path(self._tools.name)
        self.env = dict(os.environ)
        # Do not inherit ambient Terraform injection/targeting settings.
        for key in list(self.env):
            if key.startswith(("TF_VAR_", "TF_CLI_ARGS", "TF_WORKSPACE", "TF_DATA_DIR")):
                self.env.pop(key)
        self.env.update({
            "AWS_REGION": REGION, "AWS_DEFAULT_REGION": REGION, "AWS_PAGER": "",
            "SWE5001_TF_STATE_BUCKET": f"blocbuy-tfstate-{ACCOUNT}",
            "TF_DATA_DIR": str(self.cache / "tfdata"), "TF_IN_AUTOMATION": "1", "GIT_TERMINAL_PROMPT": "0",
            "PATH": str(self.cache) + os.pathsep + self.env.get("PATH", ""),
        })
        config = self.logs / "terraform.rc"
        config.write_text("", encoding="utf-8")
        self.env["TF_CLI_CONFIG_FILE"] = str(config)
        self.env.pop("TF_REATTACH_PROVIDERS", None)
        self.credential_source_env = dict(self.env)

    def close(self):
        self._tools.cleanup()

    def run(self, phase, command, capture=False, codes=(0,), env=None):
        self.index += 1
        path = self.logs / f"{self.index:02d}.log"
        print(phase, flush=True)
        with path.open("w", encoding="utf-8") as log:
            result = subprocess.run(command, env=self.env if env is None else env,
                                    stdout=subprocess.PIPE if capture else log,
                                    stderr=log, text=True, encoding="utf-8", check=False)
        require(result.returncode in codes,
                f"Phase failed (exit {result.returncode}). Private diagnostic: {path}; do not paste the full log")
        return result

    def configure_credentials(self):
        result = self.run("Checking refreshable AWS CLI credentials (not printed or saved)",
                          ["aws", "configure", "export-credentials", "--format", "process", "--no-cli-pager"],
                          True, env=self.credential_source_env)
        validate_credentials(json.loads(result.stdout))
        helper = self.logs / "credential-process.py"
        if not helper.exists():
            # Only source code/paths are written. Source credential values remain
            # in process memory; the SDK receives refreshed JSON through a pipe.
            helper.write_bytes(Path(__file__).read_bytes())
            config = self.logs / "aws-config"
            shared = self.logs / "empty-credentials"
            command = shlex.join([sys.executable, str(helper), "--credential-process"])
            config.write_text("[profile swe5001-edge-recovery]\n" +
                              f"region = {REGION}\ncredential_process = {command}\n", encoding="utf-8")
            shared.write_text("", encoding="utf-8")
            self.env = credential_environment(self.credential_source_env, config, shared)

    def aws(self, phase, *args):
        result = self.run(phase, ["aws", *args, "--region", REGION, "--output", "json", "--no-cli-pager"], True)
        return json.loads(result.stdout)


def verify_headers(actual, canonical):
    for key in ("SecurityHeadersConfig", "CustomHeadersConfig", "RemoveHeadersConfig"):
        expected = canonical[key]
        received = actual.get(key)
        # CloudFront can serialise an omitted optional XSS block as {}. Provider
        # 5.100.0 likewise drops it when its flattened map is empty. Accept only
        # that exact representation, never a populated/different configuration.
        if (key == "SecurityHeadersConfig" and isinstance(received, dict)
                and "XSSProtection" not in expected and received.get("XSSProtection") == {}):
            received = {name: value for name, value in received.items() if name != "XSSProtection"}
        if key in ("CustomHeadersConfig", "RemoveHeadersConfig") and isinstance(received, dict):
            expected = dict(expected, Items=sorted(expected["Items"], key=lambda item: item["Header"]))
            received = dict(received, Items=sorted(received.get("Items", []), key=lambda item: item["Header"]))
        require(received == expected, "Live security headers do not match the promoted source")


def recover(work, apply, resume=False):
    for tool in ("aws", "git", "bash", "jq", "curl"):
        require(shutil.which(tool), f"CloudShell is missing {tool}")
    runner = Runner(work)
    try:
        return recover_with_runner(work, apply, resume, runner)
    finally:
        runner.close()


def recover_with_runner(work, apply, resume, runner):
    runner.configure_credentials()
    identity = runner.aws("Checking AWS identity", "sts", "get-caller-identity")
    require(identity["Account"] == ACCOUNT, "Wrong AWS account; no resource reads or writes performed")
    print(f"Target: {ACCOUNT}, {REGION}; operator: {identity['Arn']}", flush=True)
    print("Keep the agreed no-promotion window active until this script finishes.", flush=True)
    check_workflows()
    checkout = work / "repo"
    terraform = install_terraform(runner.cache)
    if not resume:
        runner.run("Fetching the merged repository (sparse checkout)", ["git", "clone", "--filter=blob:none", "--sparse",
                                                    "--branch", "prod", "--single-branch",
                                                    f"https://github.com/{REPO}.git", str(checkout)])
        runner.run("Selecting only recovery source directories", ["git", "-C", str(checkout), "sparse-checkout", "set",
                                                                "infra/swe5001", "deployment/frontend/cloudfront"])
        runner.run("Selecting exact promotion source", ["git", "-C", str(checkout), "checkout", "--detach", SHA])
    actual_sha = runner.run("Checking source SHA", ["git", "-C", str(checkout), "rev-parse", "HEAD"], True).stdout.strip()
    require(actual_sha == SHA, "Unexpected checkout SHA")
    source_status = runner.run("Checking checkout is unchanged", ["git", "-C", str(checkout), "status",
                              "--porcelain", "--untracked-files=all"], True)
    require(not source_status.stdout.strip(), "Recovery checkout was edited; preserve it and stop for review")
    status = checkout / "infra/swe5001/scripts/env-status.sh"
    runner.run("Verifying runtime is absent (read-only)", ["bash", str(status)])
    edge = checkout / "infra/swe5001/edge"
    tf = [str(terraform), f"-chdir={edge}"]
    runner.run("Initialising only the edge backend", tf + ["init", "-input=false", "-lockfile=readonly",
               f"-backend-config=bucket=blocbuy-tfstate-{ACCOUNT}", f"-backend-config=region={REGION}"])
    plan_path = work / ("edge-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f") + ".tfplan")
    plan_args = ["plan", "-input=false", "-no-color", "-lock-timeout=60s", "-var-file=../envs/demo.tfvars",
                 f"-var=runtime_alb_dns_name={IDLE_ORIGIN}"]
    runner.run("Preparing private edge plan", tf + plan_args + [f"-out={plan_path}"])
    plan = inspect_private_plan(runner, tf, plan_path, work, "full")
    targeted = bucket_dependency_only(plan)
    if targeted:
        print("Existing S3 policy matches the exact OAC grant; S3 policy writes remain disallowed.", flush=True)
        print("Preparing exceptional CloudFront-only recovery; full convergence is checked afterwards.", flush=True)
        plan_path = plan_path.with_name("cloudfront-" + plan_path.name)
        runner.run("Preparing private CloudFront-only recovery plan", tf + plan_args +
                   [f"-target={POLICY}", f"-target={DISTRIBUTION}", f"-out={plan_path}"])
        plan = inspect_private_plan(runner, tf, plan_path, work, "cloudfront-only")
    changes = approved_changes(plan, targeted=targeted)
    print("Plan scope validated" if changes else "No managed-resource changes", flush=True)
    if not apply:
        print("PLAN ONLY: nothing applied. Run again with --apply for a fresh reviewed plan.")
        return
    if changes:
        print("Only the SWE5001 header policy and idle API origin may change; no IAM/runtime changes.")
        require(input("Type APPLY SWE5001 EDGE to apply this saved plan: ").strip() == "APPLY SWE5001 EDGE",
                "Apply not confirmed; nothing applied")
        check_workflows()
        runner.configure_credentials()
        identity = runner.aws("Rechecking AWS identity before apply", "sts", "get-caller-identity")
        require(identity["Account"] == ACCOUNT, "AWS account changed; saved plan not applied")
        runner.run("Rechecking runtime absence before apply", ["bash", str(status)])
        runner.run("Applying only the approved saved edge plan", tf + ["apply", "-input=false", "-no-color", str(plan_path)])
    # Wait commands have no JSON response; invoke directly rather than decoding it.
    runner.run("Waiting for CloudFront deployment", ["aws", "cloudfront", "wait", "distribution-deployed",
               "--id", DISTRIBUTION_ID, "--region", REGION, "--no-cli-pager"])
    policy = runner.aws("Verifying live header policy", "cloudfront", "get-response-headers-policy", "--id", POLICY_ID)
    canonical = json.loads((checkout / "deployment/frontend/cloudfront/response-headers-policy.json").read_text(encoding="utf-8"))
    verify_headers(policy["ResponseHeadersPolicy"]["ResponseHeadersPolicyConfig"], canonical)
    if targeted:
        bucket_policy = runner.aws("Verifying SPA bucket policy remained unchanged", "s3api", "get-bucket-policy", "--bucket", BUCKET)
        verify_bucket_policy(bucket_policy["Policy"])
    runner.run("Verifying edge convergence (must exit 0)", tf + plan_args + ["-detailed-exitcode"])
    runner.run("Verifying runtime remains absent", ["bash", str(status)])
    receipt = {"verified_at": datetime.now(timezone.utc).isoformat(),
               "source": SHA, "account": ACCOUNT, "region": REGION, "policy": POLICY_ID,
                "distribution": DISTRIBUTION_ID, "updates": changes, "headers": "match promoted source",
                "cloudfront_only_recovery": targeted, "bucket_policy": "unchanged OAC grant verified" if targeted else "not separately read",
               "edge_plan_exit": 0, "runtime": "absent", "promotion_retry": "not started"}
    (work / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print("RECOVERY VERIFIED: headers match source; edge plan exit 0; runtime absent.")
    print("Promotion has NOT been retried. Share only this status and receipt.json, not plans/logs.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Offer one confirmation after private plan validation")
    parser.add_argument("--resume", type=Path, help="Use a specific private recovery directory; always make a fresh plan")
    parser.add_argument("--credential-process", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.credential_process:
        return export_credential_process()
    os.umask(0o077)
    work = None
    try:
        work = args.resume if args.resume else persistent_work()
        print(f"Private recovery directory: {work}", flush=True)
        resume = bool(args.resume) or (work / "repo/.git").is_dir()
        if resume:
            validate_resume(work)
        recover(work, args.apply, resume=resume)
    except (ValueError, KeyError, OSError, EOFError, KeyboardInterrupt, zipfile.BadZipFile):
        # Provider/HTTP/subprocess exceptions can contain raw responses. Print only
        # controlled guard messages; preserve private evidence on every failure.
        error = sys.exc_info()[1]
        message = str(error) if type(error) is ValueError else type(error).__name__
        print(f"RECOVERY STOPPED: {message}. Private files retained at {work}.", file=sys.stderr)
        print("Do not rerun concurrently or share full plans/logs. AWS changes may have occurred if apply started.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
