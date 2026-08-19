#!/usr/bin/env bash
# Cria no Supabase Auth local uma identidade para cada usuario semeado,
# reaproveitando o auth_uid que ja esta no banco.
set -euo pipefail

SUPABASE="${SUPABASE:-supabase}"
SENHA="${SEED_PASSWORD:-mudar123}"
DB_CONTAINER="${DB_CONTAINER:-supabase_db_adra-api}"

eval "$("$SUPABASE" status -o env 2>/dev/null | sed 's/^/export /')"

docker exec -i "$DB_CONTAINER" psql -U postgres -d postgres -At -F' ' \
    -c "SELECT auth_uid, email FROM adra.usuario WHERE auth_uid IS NOT NULL" |
while read -r uid email; do
    curl -sS -o /dev/null -X DELETE "$API_URL/auth/v1/admin/users/$uid" \
        -H "apikey: $SERVICE_ROLE_KEY" -H "Authorization: Bearer $SERVICE_ROLE_KEY"

    codigo=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API_URL/auth/v1/admin/users" \
        -H "apikey: $SERVICE_ROLE_KEY" -H "Authorization: Bearer $SERVICE_ROLE_KEY" \
        -H 'Content-Type: application/json' \
        -d "{\"id\":\"$uid\",\"email\":\"$email\",\"password\":\"$SENHA\",\"email_confirm\":true}")

    if [ "$codigo" = "200" ]; then
        echo "  $email"
    else
        echo "  $email FALHOU (HTTP $codigo)" >&2
        exit 1
    fi
done
