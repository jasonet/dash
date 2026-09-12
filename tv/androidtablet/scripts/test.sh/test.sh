#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
SOURCE="$ROOT/app/src/main/java/com/jasonet/dash/androidtablet"
"${JAVA_BIN}javac" -encoding UTF-8 -d "$OUT" \
  "$SOURCE/MarketData.java" "$SOURCE/MarketSnapshot.java" \
  "$SOURCE/DashboardLayout.java" "$SOURCE/NetworkStatus.java" "$ROOT/tests/DashboardTests.java"
"${JAVA_BIN}java" -cp "$OUT" com.jasonet.dash.androidtablet.DashboardTests
  
