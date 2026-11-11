#!/usr/bin/env bash
set -euo pipefail
cd /build
test ! -e /results/cache-codec-red-exit.txt
test ! -e /results/cache-codec-green-exit.txt
main=blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache.java
testfile=blocbuy-infrastructure/src/test/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCacheTest.java
it=blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/BestOpenDealCacheIT.java
mkdir -p .local-verification-logs
cp /results/formatted-cache-backoff3-main.java "$main"
cp "/repo/backend/$testfile" "$testfile"
sed -i 's/\r$//' "$testfile"
bash ./mvnw -B -ntp -pl services/commerce-svc -am spotless:apply > .local-verification-logs/cache-codec-red-format.log 2>&1
set +e
bash ./mvnw -B -ntp -pl services/commerce-svc -am test '-Dtest=RedisOpenDealOfferCacheTest#startupProfileAndEntryReaderAreResolvedOnceForRepeatedOperations' -Dsurefire.failIfNoSpecifiedTests=false > .local-verification-logs/cache-codec-red.log 2>&1
red=$?
set -e
cp .local-verification-logs/cache-codec-red.log /results/cache-codec-red.log
echo "$red" > /results/cache-codec-red-exit.txt
if test "$red" -eq 0 || ! grep -q 'Tests run: 1, Failures: 1, Errors: 0' .local-verification-logs/cache-codec-red.log; then
  echo 'Intended startup-profile/codec regression not established; next steps refused.'
  exit 1
fi
for path in "$main" "$testfile" "$it"; do
  cp "/repo/backend/$path" "$path"
  sed -i 's/\r$//' "$path"
done
bash ./mvnw -B -ntp -pl services/commerce-svc -am spotless:apply > .local-verification-logs/cache-codec-green-format.log 2>&1
cp "$main" /results/formatted-cache-codec-main.java
cp "$testfile" /results/formatted-cache-codec-test.java
cp "$it" /results/formatted-cache-codec-it.java
set +e
bash ./mvnw -B -ntp -pl services/commerce-svc -am package -Dtest=RedisOpenDealOfferCacheTest -Dsurefire.failIfNoSpecifiedTests=false > .local-verification-logs/cache-codec-green.log 2>&1
green=$?
set -e
cp .local-verification-logs/cache-codec-green.log /results/cache-codec-green.log
echo "$green" > /results/cache-codec-green-exit.txt
if test "$green" -eq 0; then
  cp services/commerce-svc/target/commerce-svc.jar /results/commerce-cache-codec.jar
  sha256sum /results/commerce-cache-codec.jar | cut -d' ' -f1 > /results/cache-codec-artifact-sha256.txt
  cat /results/cache-codec-artifact-sha256.txt
fi
grep 'Tests run:.*RedisOpenDealOfferCacheTest' .local-verification-logs/cache-codec-{red,green}.log
tail -n 15 .local-verification-logs/cache-codec-green.log
exit "$green"
