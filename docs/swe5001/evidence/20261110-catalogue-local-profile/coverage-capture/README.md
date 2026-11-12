# Local coverage capture repair

The [sanitized comparison](cache-coverage-capture-review.json) records missing cache unit data and a forced JVM termination with both default pipes and TCP, despite Maven exit zero. Parent stacks show the output consumer blocked in Windows-mounted file writes. Capture on the named Linux build volume recovered all 84/84 v2 probes without a forced JVM or changes to plugins, POM, timeout or coverage policy.

The first Linux-log full run failed because the disposable container lacked `../../contracts/openapi.json`; [failure summary](cache-linux-verification-summary.json) preserves it. The corrected full run is [retained separately](../verification-v2-linux/README.md). Logs, stacks, test properties and exception bodies remain private; hashes and safe frame classifications are retained. This is a local verification-harness diagnosis, not application shutdown or AWS root-cause evidence.
