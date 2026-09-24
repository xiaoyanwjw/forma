#!/usr/bin/env bash
# Local package helper. Prefer Maven directly for compile checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
mvn -pl lippi-ai-ebus-starter -am -DskipTests package

SRC_JAR="$(ls -1 lippi-ai-ebus-starter/target/lippi-ai-ebus-starter-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
if [[ -z "${SRC_JAR}" || ! -f "${SRC_JAR}" ]]; then
  echo "error: starter jar missing after package" >&2
  exit 1
fi

BUILD_DIR="APP-META/docker-config/build"
mkdir -p "${BUILD_DIR}"
cp -f "${SRC_JAR}" "${BUILD_DIR}/app.jar"
echo "Copied ${SRC_JAR} -> ${BUILD_DIR}/app.jar"

echo "Compose tip: if Hub mirrors fail, tag a local Java 8 image as lippi-ebus-java8:local,"
echo "  export STARTER_BASE_IMAGE=lippi-ebus-java8:local DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0"
echo "  then: docker compose -f APP-META/docker-config/docker-compose.yml up -d --build"
