#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/scoring/backups}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-scoring_mvp}"
DB_USER="${DB_USER:-scoring_app}"
DB_PASSWORD="${DB_PASSWORD:-}"

mkdir -p "${BACKUP_DIR}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET_FILE="${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.gz"

MYSQL_PWD="${DB_PASSWORD}" mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --single-transaction \
  --quick \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" | gzip > "${TARGET_FILE}"

find "${BACKUP_DIR}" -type f -name "${DB_NAME}-*.sql.gz" -mtime +14 -delete
echo "Backup created: ${TARGET_FILE}"
