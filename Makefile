DOCKER_HOST ?= $(shell docker context inspect --format '{{.Endpoints.docker.Host}}')
export DOCKER_HOST

SUPABASE ?= supabase
DB_CONTAINER ?= supabase_db_adra-api
PSQL = docker exec -i $(DB_CONTAINER) psql -U postgres -d postgres -v ON_ERROR_STOP=1

DOCKER_VARS = API_DOMAIN=localhost \
	ADRA_CORS_ORIGENS=http://localhost:4200 \
	ADRA_FRONTEND_URL=http://localhost:4200

.PHONY: help up down reset seed migration run stage stage-direct prod prod-direct docker-stage docker-prod docker-down docker-logs test psql

help:
	@echo "up      Supabase local"
	@echo "down    derruba o Supabase local"
	@echo "reset   derruba o schema adra; o Flyway recria no proximo make run"
	@echo "run     API no perfil local"
	@echo "seed    dados ficticios e identidades, depois do make run"
	@echo "migration  cria um arquivo de migration: make migration nome=criar_tabela_x"
	@echo "stage   API contra o stage"
	@echo "prod    API contra a producao"
	@echo "stage-direct  stage sem pooler, conexao direta (precisa IPv6)"
	@echo "prod-direct   producao sem pooler, conexao direta (precisa IPv6)"
	@echo "docker-stage  sobe a imagem atras do Caddy, contra o stage"
	@echo "docker-prod   sobe a imagem atras do Caddy, contra a producao"
	@echo "docker-down   derruba os containers da API"
	@echo "docker-logs   acompanha os logs dos containers"
	@echo "test    testes"
	@echo "psql    shell no banco local"

up:
	$(SUPABASE) start

down:
	$(SUPABASE) stop

reset:
	$(PSQL) -c "DROP SCHEMA IF EXISTS adra CASCADE"

seed:
	$(PSQL) < sql/seeds/local.sql
	@SUPABASE="$(SUPABASE)" DB_CONTAINER="$(DB_CONTAINER)" ./scripts/seed-auth.sh

migration:
	@test -n "$(nome)" || { echo "uso: make migration nome=criar_tabela_oficina"; exit 1; }
	@arquivo="src/main/resources/db/migration/V$$(date +%Y%m%d%H%M%S)__$(nome).sql"; \
		touch "$$arquivo"; \
		echo "$$arquivo"

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
	SPRING_DATASOURCE_URL='jdbc:postgresql://db.zphybvsrvcsucqsbbidw.supabase.co:5432/postgres?currentSchema=adra&stringtype=unspecified' \
	SPRING_DATASOURCE_USERNAME=postgres \
	./gradlew bootRun --args='--spring.profiles.active=prod'

docker-stage:
	$(DOCKER_VARS) SPRING_PROFILES_ACTIVE=stage \
		docker compose --env-file .env.stage up -d --build

docker-prod:
	$(DOCKER_VARS) SPRING_PROFILES_ACTIVE=prod \
		docker compose --env-file .env.prod up -d --build

docker-down:
	docker compose -p adra-deploy down

docker-logs:
	docker compose -p adra-deploy logs -f

test:
	./gradlew test

psql:
	docker exec -it $(DB_CONTAINER) psql -U postgres -d postgres
