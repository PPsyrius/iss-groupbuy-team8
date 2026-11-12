# V2 complete Linux-log verification

Fresh eleven-module `./mvnw -B -ntp -T 2 clean verify -Pci` passed in 7:17: **2,060 discovered tests, zero failures/errors, one existing opt-in performance skip**, no skip flags or forced test-JVM terminations. The [aggregate summary](full-verification-summary.json) has zero missed cache lines/branches. The verified commerce JAR equals diagnostic v2; [verified-JAR OpenAPI](contract-summary.json) is byte-identical to the checkout.

Linux-volume capture and corrected parent fixture layout fix the local reporting defect; original incomplete/failed runs remain retained. This verifies **v2 only**, not the later v3 source. V2's runtime startup comparison failed, so this is not deployment readiness, a hosted Sonar gate or AWS acceptance.
