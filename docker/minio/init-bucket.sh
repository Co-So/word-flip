#!/bin/sh
# Manual MinIO bucket bootstrap (alternative to the minio-init compose service).
# Requires: MinIO running, mc installed (https://min.io/docs/minio/linux/reference/minio-mc.html)
#
# Usage (from docker/):
#   export MINIO_ROOT_USER=minioadmin
#   export MINIO_ROOT_PASSWORD=change_me_minio
#   export MINIO_BUCKET=wordflip
#   sh minio/init-bucket.sh

set -e

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-change_me_minio}"
MINIO_BUCKET="${MINIO_BUCKET:-wordflip}"

mc alias set wordflip-local "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb "wordflip-local/${MINIO_BUCKET}" --ignore-existing
echo "Bucket ready: ${MINIO_BUCKET}"
