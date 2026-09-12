#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" == *-SNAPSHOT ]]; then
  echo "usage: $0 <release-version>" >&2
  exit 2
fi

version="$1"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

mkdir -p "$scratch/maven-repository" "$scratch/gradle-home" "$scratch/gradle-project" "$scratch/maven-project"
cp scripts/consumer-smoke/pom.xml "$scratch/maven-project/pom.xml"
cp scripts/consumer-smoke/settings.gradle.kts "$scratch/gradle-project/settings.gradle.kts"
cp scripts/consumer-smoke/build.gradle.kts "$scratch/gradle-project/build.gradle.kts"

# A fresh Maven cache plus an explicit Central-only repository exercises the public POM/JAR graph.
./mvnw -f "$scratch/maven-project/pom.xml" -B -Dmaven.repo.local="$scratch/maven-repository" \
  -Drevision="$version" dependency:go-offline verify

GRADLE_USER_HOME="$scratch/gradle-home" ./gradlew -p "$scratch/gradle-project" \
  check -PconsumerVersion="$version" --no-daemon
