#!/usr/bin/env bash
# Fails unless the given file is an executable Spring Boot JAR, so a job that
# ships the backend cannot ship the plain library JAR by mistake (#455, WP-B2).
#
# blocbuy-bootstrap publishes two JARs: blocbuy-backend.jar (plain classes, a
# dependency of services/*-svc) and blocbuy-backend-exec.jar (the runnable
# application). Only the second may reach an image or a deployment artifact.
#
# Usage: assert-boot-jar.sh <jar>

set -Eeuo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <jar>" >&2
  exit 2
fi

jar=$1
launcher='org.springframework.boot.loader.launch.JarLauncher'

fail() {
  echo "assert-boot-jar: $jar: $*" >&2
  exit 1
}

[ -s "$jar" ] || fail 'missing or empty'

# Capture first: grep -q on a pipe would close it early and trip pipefail. Manifest lines wrap at
# 72 bytes with a leading-space continuation (long Start-Class names do), so join them first.
manifest=$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' |
  awk 'NR > 1 && /^ / { line = line substr($0, 2); next } NR > 1 { print line } { line = $0 } END { print line }') ||
  fail 'not a JAR or no META-INF/MANIFEST.MF'
entries=$(unzip -Z1 "$jar") || fail 'cannot list entries'

grep -qx "Main-Class: $launcher" <<<"$manifest" ||
  fail "Main-Class is not $launcher (plain JAR?)"
grep -q '^Start-Class: ' <<<"$manifest" || fail 'no Start-Class in the manifest'
grep -q '^BOOT-INF/lib/' <<<"$entries" || fail 'no BOOT-INF/lib/ entries'

echo "assert-boot-jar: $jar is an executable Spring Boot JAR ($(grep '^Start-Class: ' <<<"$manifest"))"
