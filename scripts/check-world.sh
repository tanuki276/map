#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$classes" \
  app/src/main/java/jp/tanuki/fablewind/World.java \
  app/src/main/java/jp/tanuki/fablewind/WarpPoint.java \
  app/src/main/java/jp/tanuki/fablewind/WarpTravel.java \
  tests/WorldTravelTest.java
java -cp "$classes" jp.tanuki.fablewind.WorldTravelTest
