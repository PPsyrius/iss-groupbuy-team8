# Rejected codec experiment — source preservation

The eager-reader/immutable-profile candidate failed both healthy controls (p95 **3,713/2,717 ms**, **110/69 errors**) and both cold runs. Its fault phases were refused after the failed A-arm control. This does not establish healthy non-regression or a cold-start fix, so the candidate was rejected.

[Restoration receipt](restoration.json) proves all three current source/test files exactly match the independently [verified reviewed-v3 snapshot](../verification-v3-reviewed/README.md): **2,073 tests**, complete cache coverage and unchanged OpenAPI. The rejected implementation and its policy-specific test remain here as `.java.txt` evidence; original/reviewed 31 tests are restored exactly, not weakened to pass the rejected candidate. Its own later static result, if successful, cannot supersede the failed runtime controls.
