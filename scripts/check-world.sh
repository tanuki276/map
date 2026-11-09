#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$classes" \
 app/src/main/java/jp/tanuki/fablewind/World.java \
 app/src/main/java/jp/tanuki/fablewind/WarpPoint.java \
 app/src/main/java/jp/tanuki/fablewind/WarpTravel.java \
 app/src/main/java/jp/tanuki/fablewind/Item.java \
 app/src/main/java/jp/tanuki/fablewind/Recipe.java \
 app/src/main/java/jp/tanuki/fablewind/SceneCatalog.java \
 app/src/main/java/jp/tanuki/fablewind/SurvivalGame.java \
 app/src/main/java/jp/tanuki/fablewind/Terrain.java \
 app/src/main/java/jp/tanuki/fablewind/Json.java \
 app/src/main/java/jp/tanuki/fablewind/GlbModel.java tests/*.java
java -cp "$classes" jp.tanuki.fablewind.WorldTravelTest
java -cp "$classes" jp.tanuki.fablewind.SurvivalTest
java -Xmx512m -cp "$classes" jp.tanuki.fablewind.GlbTest
