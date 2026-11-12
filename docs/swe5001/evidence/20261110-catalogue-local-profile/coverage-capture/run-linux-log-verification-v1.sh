#!/usr/bin/env bash
set -euo pipefail
cmp pom.xml /results/cache-channel-original-pom.xml
test ! -e /results/cache-linux-unit-exit.txt
test ! -e /results/cache-linux-full-verify-exit.txt
mkdir -p .local-verification-logs
# Keep Maven/Jansi's frequent writes on the named Linux volume. Copy only after JVMs exit.
set +e
bash ./mvnw -B -ntp -pl blocbuy-infrastructure -am clean test -Pci > .local-verification-logs/cache-linux-unit.log 2>&1
unit_code=$?
set -e
cp .local-verification-logs/cache-linux-unit.log /results/cache-linux-unit.log
echo "$unit_code" > /results/cache-linux-unit-exit.txt
java -cp /root/.m2/repository/org/jacoco/org.jacoco.core/0.8.16/org.jacoco.core-0.8.16.jar /results/ExecProbeReview.java > /results/cache-linux-unit-probes.txt
cat /results/cache-linux-unit-probes.txt
if test "$unit_code" -ne 0 || grep -q 'Surefire is going to kill self fork JVM' .local-verification-logs/cache-linux-unit.log; then
  echo 'Linux-log unit comparison failed or still lost a test JVM; remaining verification refused.'
  exit 1
fi
if ! grep -q 'blocbuy-infrastructure/target/jacoco.exec.*coveredProbes=84 totalProbes=84' /results/cache-linux-unit-probes.txt; then
  echo 'Cache unit coverage was not fully recorded; do not manufacture a coverage pass.'
  exit 1
fi
set +e
bash ./mvnw -B -ntp -T 2 clean verify -Pci > .local-verification-logs/cache-linux-full-verify.log 2>&1
code=$?
set -e
cp .local-verification-logs/cache-linux-full-verify.log /results/cache-linux-full-verify.log
echo "$code" > /results/cache-linux-full-verify-exit.txt
if test -f blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml; then
  cp blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml /results/cache-linux-jacoco-aggregate.xml
fi
tail -n 28 .local-verification-logs/cache-linux-full-verify.log
if test "$code" -ne 0; then exit "$code"; fi
if grep -q 'Surefire is going to kill self fork JVM' .local-verification-logs/cache-linux-full-verify.log; then
  echo 'Maven returned zero but a unit JVM was forcibly terminated; coverage collection is not accepted.'
  exit 1
fi
java -jar blocbuy-bootstrap/target/blocbuy-backend-exec.jar --spring.profiles.active=openapi-export > .local-verification-logs/cache-linux-openapi-app.log 2>&1 &
app_pid=$!
trap 'kill "$app_pid" 2>/dev/null || true' EXIT
fetched=0
for attempt in $(seq 1 30); do
  if curl -sf http://localhost:18080/v3/api-docs -o /results/cache-linux-openapi.json; then
    fetched=1
    break
  fi
  sleep 2
done
echo "$fetched" > /results/cache-linux-openapi-fetched.txt
kill "$app_pid" 2>/dev/null || true
wait "$app_pid" 2>/dev/null || true
trap - EXIT
cp .local-verification-logs/cache-linux-openapi-app.log /results/cache-linux-openapi-app.log
if test "$fetched" -ne 1; then exit 1; fi
echo 'Original POM/gates preserved; Linux capture completed with no lost test JVMs. Runtime acceptance remains separate.'
