# V2 evidence review — 11 November 2026

The new released monolith passed the 10–200 requests/s browse baseline. All ten plateaus passed, with 57,600 browse requests, zero failures and zero dropped iterations. All-run client p95 was 30 ms; the 200-RPS plateau p95 was 18 ms. Raw samples match the summary.

The transferred observations match the operator's SHA-256. All 47 samples show one stable running monolith at revision 7; the largest observation gap is 17.0 seconds. They bracket the full workload and extend 77.7 seconds beyond its last request. The post-run snapshot retains the same task, digest, route and scaling state.

The 15-minute observer ended early with exit 255 after a container-role metadata HTTP 500. Preserve this failure: the captured measurement window is usable, but the planned full collector duration did not complete. The exact credential-failure cause is not established.

200 requests/s is a tested lower bound, not saturation. The 12,000 requests/target/minute candidate is calculated and was not written to AWS/GitHub. No distributed capacity or scaling-efficiency result follows from this monolith run. AWS server-metric corroboration is not in the downloaded archive.

The runtime is not automatically torn down. Authorise cleanup separately; V3 requires a distributed session and reliable persistent observation before load.
