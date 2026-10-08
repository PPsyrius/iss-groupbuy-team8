#!/usr/bin/env python3
"""Benchmark local Docker Trivy scans without modifying the repository or live caches."""

import argparse
import json
from pathlib import Path
import statistics
import subprocess
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True, help="Existing local backend image (tag or ID)")
    parser.add_argument("--output-dir", type=Path, required=True, help="Private benchmark output directory")
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--archive-cache", action="store_true", help="Also time zstd archive creation/extraction locally")
    parser.add_argument("--trivy-image", default="aquasec/trivy@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969")
    args = parser.parse_args()
    if args.iterations < 1:
        parser.error("--iterations must be positive")
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=False)
    image = subprocess.check_output(
        ["docker", "image", "inspect", args.image, "--format", "{{.Id}}"], text=True
    ).strip()
    prefix = f"blocbuy-trivy-bench-{uuid.uuid4().hex[:12]}"
    volumes = []
    containers = []
    results = {"image_id": image, "trivy_image": args.trivy_image, "iterations": args.iterations,
               "freshness_checks": True, "phases": [], "limitations": [
                   "Local Docker Desktop, not GitHub runners or GitHub cache transport.",
                   "Scanner/image already installed; image build and download are excluded.",
                   "DB-only copies model fresh runners; full-cache scans model same-runner reuse.",
               ]}

    def save():
        (output / "summary.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")

    def run(name, command, allow_findings=False):
        start = time.perf_counter()
        completed = subprocess.run(command, capture_output=True, text=True, timeout=660)
        seconds = round(time.perf_counter() - start, 3)
        (output / f"{name}.log").write_text(completed.stdout + completed.stderr, encoding="utf-8")
        phase = {"name": name, "seconds": seconds, "exit_code": completed.returncode}
        results["phases"].append(phase)
        save()
        report = output / f"{name}.json"
        if allow_findings:
            if completed.returncode not in [0, 1] or not report.exists():
                raise RuntimeError(f"{name} did not produce a complete scan report; inspect {output / f'{name}.log'}")
            data = json.loads(report.read_text(encoding="utf-8"))
            phase["findings"] = sum(len(item.get("Vulnerabilities") or []) for item in data.get("Results", []))
            phase["report_created"] = True
            save()
        elif completed.returncode != 0:
            raise RuntimeError(f"{name} failed with exit {completed.returncode}; inspect {output / f'{name}.log'}")
        print(json.dumps(phase), flush=True)
        return completed.stdout

    def new_volume(suffix):
        name = f"{prefix}-{suffix}"
        subprocess.run(["docker", "volume", "create", name], check=True, capture_output=True)
        volumes.append(name)
        return name

    def scan(name, volume):
        run(name, ["docker", "run", "--rm", "--volume", f"{volume}:/cache",
                   "--volume", f"{output}:/reports", "--volume", "/var/run/docker.sock:/var/run/docker.sock",
                   args.trivy_image, "image", "--image-src", "docker", "--cache-dir", "/cache",
                   "--timeout", "10m", "--scanners", "vuln", "--severity", "HIGH,CRITICAL",
                   "--ignore-unfixed", "--exit-code", "1", "--no-progress", "--format", "json",
                   "--output", f"/reports/{name}.json", image], allow_findings=True)

    try:
        cold = new_volume("cold")
        scan("cold", cold)
        sizes = run("db-sizes", ["docker", "run", "--rm", "--entrypoint", "sh",
                    "--volume", f"{cold}:/cache:ro", args.trivy_image, "-c",
                    "du -sk /cache/db /cache/java-db; cat /cache/db/metadata.json; cat /cache/java-db/metadata.json"])
        results["db_metadata_and_sizes"] = sizes.strip()
        for iteration in range(1, args.iterations + 1):
            warm = new_volume(f"warm-{iteration}")
            run(f"copy-db-{iteration}", ["docker", "run", "--rm", "--entrypoint", "sh",
                "--volume", f"{cold}:/source:ro", "--volume", f"{warm}:/target", args.trivy_image, "-c",
                "mkdir -p /target/db /target/java-db; cp -a /source/db/. /target/db/; cp -a /source/java-db/. /target/java-db/"])
            scan(f"warm-db-{iteration}", warm)
            scan(f"warm-full-{iteration}", warm)
        if args.archive_cache:
            restored = new_volume("archive-restore")
            toolbox = f"{prefix}-archive-tools"
            containers.append(toolbox)
            subprocess.run(["docker", "run", "--detach", "--name", toolbox, "--entrypoint", "sh",
                            "--volume", f"{cold}:/source:ro", "--volume", f"{restored}:/target",
                            "--volume", f"{output}:/reports", args.trivy_image, "-c", "sleep 660"],
                           check=True, capture_output=True)
            run("archive-tool-setup", ["docker", "exec", toolbox, "apk", "add", "--no-cache", "zstd"])
            run("archive-pack", ["docker", "exec", toolbox, "sh", "-c",
                "set -o pipefail; tar -cf - -C /source db java-db | zstd -T0 -3 -o /reports/db.tar.zst"])
            results["archive_bytes"] = (output / "db.tar.zst").stat().st_size
            run("archive-unpack", ["docker", "exec", toolbox, "sh", "-c",
                "set -o pipefail; zstd -d -c /reports/db.tar.zst | tar -xf - -C /target"])
            scan("warm-archive", restored)
            results["limitations"].append("Zstd level 3 archive timing excludes network transfer and GitHub cache service overhead.")
        phases = results["phases"]
        cold_seconds = next(p["seconds"] for p in phases if p["name"] == "cold")
        db_median = statistics.median(p["seconds"] for p in phases if p["name"].startswith("warm-db-"))
        full_median = statistics.median(p["seconds"] for p in phases if p["name"].startswith("warm-full-"))
        results["comparison"] = {"cold_seconds": cold_seconds, "warm_db_median_seconds": db_median,
                                 "warm_full_median_seconds": full_median,
                                 "warm_db_reduction_percent": round(100 * (cold_seconds - db_median) / cold_seconds, 2)}
        save()
        print(json.dumps(results["comparison"]), flush=True)
    except Exception as error:
        results["error"] = str(error)
        save()
        raise
    finally:
        # Only remove uniquely named volumes created by this invocation.
        for container in containers:
            subprocess.run(["docker", "rm", "--force", container], capture_output=True)
        for volume in volumes:
            subprocess.run(["docker", "volume", "rm", volume], capture_output=True)


if __name__ == "__main__":
    main()
