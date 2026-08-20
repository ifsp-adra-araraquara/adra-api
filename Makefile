DOCKER_HOST ?= $(shell docker context inspect --format '{{.Endpoints.docker.Host}}')
export DOCKER_HOST

SUPABASE ?= supabase
DB_CONTAINER ?= supabase_db_adra-api
PSQL = docker exec -i $(DB_CONTAINER) psql -U postgres -d postgres -v ON_ERROR_STOP=1

.PHONY: help up down reset run stage stage-direct prod prod-direct test psql

help:
	@echo "up      Supabase local"
	@echo "down    derruba o Supabase local"
	@echo "reset   schema, seeds e identidades no ambiente local"
	@echo "run     API no perfil local"
	@echo "stage   API contra o stage"
	@echo "prod    API contra a producao"
	@echo "stage-direct  stage sem pooler, conexao direta (precisa IPv6)"
	@echo "prod-direct   producao sem pooler, conexao direta (precisa IPv6)"
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

stage-direct:
	SPRING_DATASOURCE_URL='jdbc:postgresql://db.moorxvcnxesniwaaaksm.supabase.co:5432/postgres?currentSchema=adra&stringtype=unspecified' \
	SPRING_DATASOURCE_USERNAME=postgres \
	./gradlew bootRun --args='--spring.profiles.active=stage'

prod:
	./gradlew bootRun --args='--spring.profiles.active=prod'

prod-direct:
	SPRING_DATASOURCE_URL='jdbc:postgresql://db.kceyjdtxmxkgfhihatip.supabase.co:5432/postgres?currentSchema=adra&stringtype=unspecified' \
	SPRING_DATASOURCE_USERNAME=postgres \
	./gradlew bootRun --args='--spring.profiles.active=prod'

test:
	./gradlew test

psql:
	docker exec -it $(DB_CONTAINER) psql -U postgres -d postgres
