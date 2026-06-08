#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-scoring-backend}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/scoring/app}"
RELEASES_DIR="${DEPLOY_ROOT}/releases"
CURRENT_LINK="${DEPLOY_ROOT}/current"
TARGET_RELEASE="${1:-}"

if [[ -z "${TARGET_RELEASE}" || ! -d "${RELEASES_DIR}/${TARGET_RELEASE}" ]]; then
  echo "Usage: rollback-prod.sh <release-directory-name>"
  exit 1
fi

ln -sfn "${RELEASES_DIR}/${TARGET_RELEASE}" "${CURRENT_LINK}"
sudo systemctl restart "${APP_NAME}"
sudo systemctl status "${APP_NAME}" --no-pager
curl --fail http://127.0.0.1:8080/health
