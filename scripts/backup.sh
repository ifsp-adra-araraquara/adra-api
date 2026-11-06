#!/bin/sh
set -eu

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

cd "$(dirname "$0")"
. ./.env

: "${SUPABASE_PASSWORD:?}" "${SUPABASE_S3_ACCESS_KEY_ID:?}" "${SUPABASE_S3_SECRET_ACCESS_KEY:?}"
: "${BACKUP_ZIP_PASSWORD:?}" "${BACKUP_PING_URL:?}"

PROJECT_REF=zphybvsrvcsucqsbbidw
DB_URL="${BACKUP_DB_URL:-postgresql://postgres.$PROJECT_REF@aws-0-us-east-1.pooler.supabase.com:5432/postgres}"
S3_ENDPOINT="${BACKUP_S3_ENDPOINT:-https://$PROJECT_REF.storage.supabase.co/storage/v1/s3}"
S3_REGION="${BACKUP_S3_REGION:-us-east-1}"
REMOTE="${BACKUP_REMOTE:-gdrive:adra-backups}"
BUCKET=adra-arquivos
POSTGRES_IMAGE=postgres:17-alpine
COPIES_TO_KEEP=12
ARCHIVE="adra-backup-$(date +%F).zip"

WORK_DIR=$(mktemp -d "$PWD/.backup-XXXXXX")
trap 'rm -rf "$WORK_DIR"' EXIT
trap 'exit 1' HUP INT TERM

run_postgres_tool() {
    PGPASSWORD="$SUPABASE_PASSWORD" docker run --rm --network host -e PGPASSWORD \
        --user "$(id -u):$(id -g)" -v "$WORK_DIR:/backup:Z" "$POSTGRES_IMAGE" "$@"
}

dump_database() {
    run_postgres_tool pg_dump --format=custom --compress=0 \
        --schema=adra --schema=auth --schema=storage \
        --exclude-table=storage.buckets_vectors --exclude-table=storage.vector_indexes \
        --file=/backup/db.dump "$DB_URL"
    run_postgres_tool pg_restore --list /backup/db.dump > /dev/null
}

download_storage() {
    mkdir "$WORK_DIR/storage"
    RCLONE_CONFIG_SUPABASE_TYPE=s3 \
    RCLONE_CONFIG_SUPABASE_PROVIDER=Other \
    RCLONE_CONFIG_SUPABASE_ACCESS_KEY_ID="$SUPABASE_S3_ACCESS_KEY_ID" \
    RCLONE_CONFIG_SUPABASE_SECRET_ACCESS_KEY="$SUPABASE_S3_SECRET_ACCESS_KEY" \
    RCLONE_CONFIG_SUPABASE_ENDPOINT="$S3_ENDPOINT" \
    RCLONE_CONFIG_SUPABASE_REGION="$S3_REGION" \
    RCLONE_CONFIG_SUPABASE_FORCE_PATH_STYLE=true \
        rclone copy "supabase:$BUCKET" "$WORK_DIR/storage"
}

save_app_logs() {
    docker compose logs --no-color --since 168h app > "$WORK_DIR/app.log" ||
        echo "logs da API indisponiveis" >&2
}

create_archive() {
    (
        cd "$WORK_DIR"
        7z a -tzip -mx=7 -mem=AES256 -p"$BACKUP_ZIP_PASSWORD" -bso0 -bsp0 "$ARCHIVE" db.dump storage app.log
        7z t -p"$BACKUP_ZIP_PASSWORD" -bso0 -bsp0 "$ARCHIVE"
    )
}

upload_archive() {
    rclone copyto "$WORK_DIR/$ARCHIVE" "$REMOTE/$ARCHIVE"
}

remove_expired_copies() {
    rclone lsf --files-only --include 'adra-backup-*.zip' "$REMOTE" |
        sort -r |
        tail -n "+$((COPIES_TO_KEEP + 1))" |
        while read -r expired; do
            rclone deletefile --drive-use-trash=false "$REMOTE/$expired"
        done
}

report_success() {
    curl -fsS -m 10 --retry 3 -o /dev/null "$BACKUP_PING_URL"
}

dump_database
download_storage
save_app_logs
create_archive
upload_archive
remove_expired_copies
report_success
echo "backup concluido: $ARCHIVE"
