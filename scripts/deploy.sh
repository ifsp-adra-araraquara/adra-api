#!/bin/sh
set -eu

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

cd /home/adra/adra

TAG="${SSH_ORIGINAL_COMMAND:-latest}"

case "$TAG" in
    latest) ;;
    sha-*)
        hexa="${TAG#sha-}"
        case "$hexa" in
            "" | *[!0-9a-f]*) echo "tag recusada: $TAG" >&2; exit 1 ;;
        esac
        ;;
    *) echo "tag recusada: $TAG" >&2; exit 1 ;;
esac

if grep -q '^IMAGE_TAG=' .env; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$TAG|" .env
else
    printf 'IMAGE_TAG=%s\n' "$TAG" >> .env
fi

docker compose pull
docker compose up -d
docker compose ps
