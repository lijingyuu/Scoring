#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/prod-env.sh"
JAR_PATH="${BACKEND_DIR}/target/backend-0.0.1-SNAPSHOT.jar"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Copy prod-env.example.sh to prod-env.sh first."
  exit 1
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Missing jar: ${JAR_PATH}. Build the backend first."
  exit 1
fi

source "${ENV_FILE}"
exec java -jar "${JAR_PATH}" --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}"
