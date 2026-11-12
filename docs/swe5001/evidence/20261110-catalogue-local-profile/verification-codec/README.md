# Rejected codec candidate — static verification only

The codec candidate passed fresh eleven-module `./mvnw -B -ntp -T 2 clean verify -Pci` in **8:13**, with **2,074 tests, zero failures/errors and one existing opt-in performance skip**. [Aggregate summary](full-verification-summary.json) has complete touched-cache lines/branches and no forced JVM termination; [verified-JAR OpenAPI](contract-summary.json) is byte-identical to the checkout. All three cache class byte hashes match the measured codec package.

**Still rejected.** Both healthy runtime controls failed, and neither cold run passed. Static success does not supersede those results. The [active source restoration](../codec-rejected-source/README.md) matches the different, independently verified **2,073-test reviewed-v3 snapshot**; this 2,074-test result is not the active source's verification or a performance acceptance claim.
