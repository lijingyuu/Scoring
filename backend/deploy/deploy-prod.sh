#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-scoring-backend}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/scoring/app}"
RELEASES_DIR="${DEPLOY_ROOT}/releases"
CURRENT_LINK="${DEPLOY_ROOT}/current"
JAR_SOURCE="${1:-}"

if [[ -z "${JAR_SOURCE}" || ! -f "${JAR_SOURCE}" ]]; then
  echo "Usage: deploy-prod.sh /path/to/backend-0.0.1-SNAPSHOT.jar"
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
RELEASE_DIR="${RELEASES_DIR}/${STAMP}"
mkdir -p "${RELEASE_DIR}"

cp "${JAR_SOURCE}" "${RELEASE_DIR}/backend.jar"
UPLOAD_DIR="${UPLOAD_DIR:-/opt/scoring/uploads}"
mkdir -p "${UPLOAD_DIR}/avatars"
chown -R scoring:scoring "${UPLOAD_DIR}"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

sudo systemctl restart "${APP_NAME}"
sudo systemctl status "${APP_NAME}" --no-pager
curl --fail http://127.0.0.1:8080/api/v1/tournaments
curl -sS -X POST -F "file=@/etc/hosts;type=image/png" https://api.eunomia.cc/api/v1/files/avatars | grep '"code":401'
