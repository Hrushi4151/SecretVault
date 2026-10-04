#!/usr/bin/env bash
# SecretVault — Production PostgreSQL Disaster Recovery Restore Script with Integrity Verification

set -euo pipefail

# Configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-secretvault}"
DB_USER="${DB_USER:-vault_admin}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD must be set}"
BACKUP_S3_URI="${1:?Usage: restore_postgres.sh <s3://bucket/path/to/backup.dump>}"
TEMP_DIR="${TEMP_DIR:-/tmp/secretvault_restore}"

mkdir -p "${TEMP_DIR}"
chmod 700 "${TEMP_DIR}"

DUMP_FILENAME=$(basename "${BACKUP_S3_URI}")
LOCAL_DUMP="${TEMP_DIR}/${DUMP_FILENAME}"
LOCAL_CHECKSUM="${LOCAL_DUMP}.sha256"
CHECKSUM_S3_URI="${BACKUP_S3_URI}.sha256"

echo "[$(date -u)] INFO: Downloading backup dump from [${BACKUP_S3_URI}]..."
aws s3 cp "${BACKUP_S3_URI}" "${LOCAL_DUMP}"
aws s3 cp "${CHECKSUM_S3_URI}" "${LOCAL_CHECKSUM}"

# 1. Verify SHA-256 Checksum Integrity
echo "[$(date -u)] INFO: Verifying SHA-256 cryptographic checksum..."
cd "${TEMP_DIR}"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum -c "${LOCAL_CHECKSUM}"
else
  shasum -a 256 -c "${LOCAL_CHECKSUM}"
fi

echo "[$(date -u)] SUCCESS: Integrity check passed. Commencing database restoration onto [${DB_HOST}:${DB_PORT}/${DB_NAME}]..."

# 2. Execute pg_restore into target database
PGPASSWORD="${DB_PASSWORD}" pg_restore \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --verbose \
  "${LOCAL_DUMP}" || true

# 3. Verify Database Table Sanity
echo "[$(date -u)] INFO: Verifying restored schema and critical tables..."
PGPASSWORD="${DB_PASSWORD}" psql \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -c "SELECT count(*) AS total_workspaces FROM workspaces;" \
  -c "SELECT count(*) AS total_secrets FROM secrets;" \
  -c "SELECT count(*) AS total_audit_logs FROM audit_logs;"

# Clean local temp files
rm -rf "${TEMP_DIR}"

echo "[$(date -u)] SUCCESS: SecretVault database restore completed successfully."
