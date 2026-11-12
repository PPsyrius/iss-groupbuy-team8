# V2 static verification checkpoint

The I/O-only v2 snapshot passed all eleven modules with `./mvnw -B -ntp clean verify -Pci` in Docker: 2,060 discovered tests, zero failures/errors, and one existing opt-in `PerformanceBaselineIT` skip. No skip flag or gate relaxation was used. [Sanitized summary](full-verification-summary.json) excludes test properties, system output and exception bodies; full logs/aggregate stay private with hashes.

The verified commerce JAR is byte-identical to diagnostic v2 (`f4c7372bef03a6627331a469113e3a341f5531485f36da90213ff9861d621dc1`), and both cache class-byte hashes match. The verified monolith JAR's OpenAPI is [byte-identical to the checkout](contract-summary.json); no contract or generated client changed.

**Not runtime acceptance or PR readiness.** The isolated v2 startup failed and its following control had no usable workload; see the [matrix](../MATRIX.md). Aggregate coverage still misses branches exercised by unit tests (the cache class is absent from infrastructure's collected execution data); collection/agent attribution is under investigation. Passing Maven alone does not establish a complete new-code coverage result, startup non-regression, target health, AWS capacity or linearity.
