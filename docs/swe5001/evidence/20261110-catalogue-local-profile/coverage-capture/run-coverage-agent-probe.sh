#!/usr/bin/env bash
set -euo pipefail
cd /build
probe=blocbuy-infrastructure/src/test/java/com/blocbuy/groupbuy/gateway/CoverageAgentDiagnosticTest.java
test ! -e "$probe"
cp /results/CoverageAgentDiagnosticTest.java "$probe"
# Remove this exact disposable local fixture on every exit; never change checkout sources.
trap 'rm -f "$probe"' EXIT
if test -f blocbuy-infrastructure/target/jacoco.exec; then
  cp blocbuy-infrastructure/target/jacoco.exec /results/cache-coverage-before-probe-infra.exec
fi
bash ./mvnw -B -ntp -pl services/commerce-svc -am spotless:apply > /results/cache-coverage-probe-format.log 2>&1
set +e
bash ./mvnw -B -ntp -pl services/commerce-svc -am test -Pci \
  -Dtest=CoverageAgentDiagnosticTest,RedisOpenDealOfferCacheTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcoverage.diagnostic.out=/results/cache-coverage-agent-ci.txt > /results/cache-coverage-probe-ci.log 2>&1
ci=$?
bash ./mvnw -B -ntp -pl services/commerce-svc -am test \
  -Dtest=CoverageAgentDiagnosticTest,RedisOpenDealOfferCacheTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dcoverage.diagnostic.out=/results/cache-coverage-agent-plain.txt > /results/cache-coverage-probe-plain.log 2>&1
plain=$?
set -e
printf '{"ciExit":%s,"plainExit":%s,"kind":"disposable-local-coverage-agent-probe"}\n' "$ci" "$plain" > /results/cache-coverage-agent-probe.json
cat /results/cache-coverage-agent-probe.json
for name in ci plain; do
  if test -f "/results/cache-coverage-agent-$name.txt"; then
    echo "$name JaCoCo argument:"
    cat "/results/cache-coverage-agent-$name.txt"
  else
    echo "$name probe did not execute; no agent-presence inference"
  fi
done
