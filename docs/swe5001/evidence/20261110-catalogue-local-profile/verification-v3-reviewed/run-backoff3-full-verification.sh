#!/usr/bin/env bash
set -euo pipefail
cmp pom.xml /results/cache-channel-original-pom.xml
test ! -e /results/cache-backoff3-full-verify-exit.txt
test -s /repo/contracts/openapi.json
cmp blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache.java /results/formatted-cache-backoff3-main.java
cmp blocbuy-infrastructure/src/test/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCacheTest.java /results/formatted-cache-backoff3-test.java
cmp blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/BestOpenDealCacheIT.java /results/formatted-cache-backoff3-it.java
for name in scripts perf infra docs; do
  test -L "../$name" || ln -s "/repo/$name" "../$name"
done
mkdir -p ../contracts .local-verification-logs
cp /repo/contracts/openapi.json ../contracts/
bash ./mvnw -B -ntp -T 2 clean > .local-verification-logs/cache-backoff3-preclean.log 2>&1
set +e
bash ./mvnw -B -ntp -T 2 clean verify -Pci > .local-verification-logs/cache-backoff3-full-verify.log 2>&1
code=$?
set -e
cp .local-verification-logs/cache-backoff3-full-verify.log /results/cache-backoff3-full-verify.log
echo "$code" > /results/cache-backoff3-full-verify-exit.txt
if test -f blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml; then
  cp blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml /results/cache-backoff3-jacoco-aggregate.xml
fi
tail -n 28 .local-verification-logs/cache-backoff3-full-verify.log
if test "$code" -ne 0; then exit "$code"; fi
if grep -q 'Surefire is going to kill self fork JVM' .local-verification-logs/cache-backoff3-full-verify.log; then exit 1; fi
java -jar blocbuy-bootstrap/target/blocbuy-backend-exec.jar --spring.profiles.active=openapi-export > .local-verification-logs/cache-backoff3-openapi-app.log 2>&1 &
app_pid=$!
trap 'kill "$app_pid" 2>/dev/null || true' EXIT
fetched=0
for attempt in $(seq 1 30); do
  if curl -sf http://localhost:18080/v3/api-docs -o /results/cache-backoff3-openapi.json; then fetched=1; break; fi
  sleep 2
done
echo "$fetched" > /results/cache-backoff3-openapi-fetched.txt
kill "$app_pid" 2>/dev/null || true
wait "$app_pid" 2>/dev/null || true
trap - EXIT
cp .local-verification-logs/cache-backoff3-openapi-app.log /results/cache-backoff3-openapi-app.log
test "$fetched" -eq 1
echo 'Reviewed v3 full verification/export completed; original runtime snapshot remains separately attributed.'
