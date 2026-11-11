#!/usr/bin/env bash
set -euo pipefail
test ! -f /results/cache-channel-original-pom.xml
test ! -f blocbuy-infrastructure/src/test/java/com/blocbuy/groupbuy/gateway/CoverageAgentDiagnosticTest.java
cp pom.xml /results/cache-channel-original-pom.xml
trap 'cp /results/cache-channel-original-pom.xml pom.xml' EXIT
for channel in pipes tcp; do
  if test "$channel" = tcp; then
    java /results/PomChannelExperiment.java pom.xml
    cp pom.xml /results/cache-channel-tcp-pom.xml
  fi
  set +e
  bash ./mvnw -B -ntp -pl blocbuy-infrastructure -am clean test -Pci > "/results/cache-channel-$channel.log" 2>&1
  code=$?
  set -e
  echo "$code" > "/results/cache-channel-$channel-exit.txt"
  java -cp /root/.m2/repository/org/jacoco/org.jacoco.core/0.8.16/org.jacoco.core-0.8.16.jar /results/ExecProbeReview.java > "/results/cache-channel-$channel-probes.txt"
  if test -f blocbuy-infrastructure/target/jacoco.exec; then
    cp blocbuy-infrastructure/target/jacoco.exec "/results/cache-channel-$channel-infra.exec"
  fi
  echo "$channel focused reactor unit-run exit=$code"
  grep -c 'Surefire is going to kill self fork JVM' "/results/cache-channel-$channel.log" || true
  cat "/results/cache-channel-$channel-probes.txt"
done
echo 'Local channel comparison finished; original build-volume POM restored by trap, checkout unchanged.'
