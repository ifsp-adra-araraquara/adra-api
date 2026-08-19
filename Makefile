SUPABASE ?= supabase
DB_CONTAINER ?= supabase_db_adra-api
PSQL = docker exec -i $(DB_CONTAINER) psql -U postgres -d postgres -v ON_ERROR_STOP=1

.PHONY: help up down reset run stage prod test psql

help:
	@echo "up      Supabase local"
	@echo "down    derruba o Supabase local"
	@echo "reset   schema, seeds e identidades no ambiente local"
	@echo "run     API no perfil local"
	@echo "stage   API contra o stage"
	@echo "prod    API contra a producao"
	@echo "test    testes"
	@echo "psql    shell no banco local"

up:
	$(SUPABASE) start

down:
	$(SUPABASE) stop

reset:
	$(PSQL) -c "DROP SCHEMA IF EXISTS adra CASCADE"
	$(PSQL) < sql/schema_adra.sql
	$(PSQL) < sql/seeds/local.sql
	@SUPABASE="$(SUPABASE)" DB_CONTAINER="$(DB_CONTAINER)" ./scripts/seed-auth.sh

run:
	./gradlew bootRun --args='--spring.profiles.active=local'

stage:
	./gradlew bootRun --args='--spring.profiles.active=stage'

prod:
	./gradlew bootRun --args='--spring.profiles.active=prod'

test:
	./gradlew test

psql:
	docker exec -it $(DB_CONTAINER) psql -U postgres -d postgres
