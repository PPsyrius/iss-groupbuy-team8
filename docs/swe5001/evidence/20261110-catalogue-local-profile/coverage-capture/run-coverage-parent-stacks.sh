#!/usr/bin/env bash
set -euo pipefail
cmp pom.xml /results/cache-channel-original-pom.xml
test ! -e /results/cache-ack-parent-exit.txt
mkdir /results/cache-ack-parent-stacks
set +e
bash ./mvnw -B -ntp -pl blocbuy-infrastructure -am test -Pci > /results/cache-ack-parent.log 2>&1 &
maven_pid=$!
echo "$maven_pid" > /results/cache-ack-parent.pid
# Fixed-cadence parent-JVM diagnostics, not a completion/progress poll. Keep stacks private.
(
  for sample in $(seq 1 18); do
    sleep 10
    timeout 10s jcmd "$maven_pid" Thread.print -l > "/results/cache-ack-parent-stacks/$sample.txt" 2>&1
  done
) &
sampler_pid=$!
wait "$maven_pid"
code=$?
kill "$sampler_pid" 2>/dev/null || true
wait "$sampler_pid" 2>/dev/null || true
set -e
echo "$code" > /results/cache-ack-parent-exit.txt
echo "Parent-stack diagnostic unit-run exit=$code; private stacks retained."
tail -n 15 /results/cache-ack-parent.log
