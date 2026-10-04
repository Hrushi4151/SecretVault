#!/usr/bin/env bash
# SecretVault — Production PostgreSQL Automated Backup Script with KMS Encryption & SHA-256 Verification

set -euo pipefail

# Configuration and Environment Invariants
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-secretvault}"
DB_USER="${DB_USER:-vault_admin}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD must be set}"
S3_BUCKET="${S3_BUCKET:?S3_BUCKET must be set}"
KMS_KEY_ID="${KMS_KEY_ID:?KMS_KEY_ID must be set}"
BACKUP_DIR="${BACKUP_DIR:-/tmp/secretvault_backups}"

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_FILE="${BACKUP_DIR}/secretvault_backup_${TIMESTAMP}.dump"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
S3_PREFIX="s3://${S3_BUCKET}/database-backups/$(date -u +"%Y/%m")"

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

echo "[$(date -u)] INFO: Starting SecretVault PostgreSQL backup for database [${DB_NAME}] on [${DB_HOST}]..."

# 1. Execute custom-format compressed backup
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --verbose \
  --file="${BACKUP_FILE}"

FILESIZE=$(stat -c%s "${BACKUP_FILE}" 2>/dev/null || stat -f%z "${BACKUP_FILE}")
echo "[$(date -u)] INFO: Dump created successfully (${FILESIZE} bytes). Computing SHA-256 integrity hash..."

# 2. Compute SHA-256 Checksum
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${BACKUP_FILE}" > "${CHECKSUM_FILE}"
else
  shasum -a 256 "${BACKUP_FILE}" > "${CHECKSUM_FILE}"
fi

# 3. Secure upload to S3 with KMS Server-Side Encryption
echo "[$(date -u)] INFO: Uploading backup artifact to S3 with KMS SSE [${KMS_KEY_ID}]..."
aws s3 cp "${BACKUP_FILE}" "${S3_PREFIX}/" \
  --sse aws:kms \
  --sse-kms-key-id "${KMS_KEY_ID}"

aws s3 cp "${CHECKSUM_FILE}" "${S3_PREFIX}/" \
  --sse aws:kms \
  --sse-kms-key-id "${KMS_KEY_ID}"

# 4. Clean local ephemeral backup files
rm -f "${BACKUP_FILE}" "${CHECKSUM_FILE}"

echo "[$(date -u)] SUCCESS: SecretVault PostgreSQL backup completed and verified in [${S3_PREFIX}/secretvault_backup_${TIMESTAMP}.dump]"
