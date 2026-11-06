# adra-api

API do ADRA — assistidos, responsáveis e usuários.

O Supabase Auth só autentica e devolve o `sub`. A API valida esse token via
JWKS, acha o usuário pelo `auth_uid` e emite o próprio JWT com o perfil.
Autorização e auditoria ficam aqui; a RLS fechada impede acesso ao banco por
fora.

## Rodando

```bash
make up      # Postgres e Auth locais
make reset   # derruba o schema adra
make run     # API em localhost:8080; o Flyway recria o schema no boot
make seed    # usuários fictícios e identidades
```

Local não precisa de segredo, usa as chaves de demonstração da CLI. `make down`
derruba.

Quem cria o schema é o Flyway, no boot. Por isso o `make seed` vem depois do
`make run`. Ele insere os usuários fictícios e cria a identidade de cada um no
Auth local. Os e-mails e a senha são os mesmos do stage, para não ter que
decorar dois conjuntos de credencial. Senha `mudar123`:

| E-mail | Perfil |
| --- | --- |
| `administrador@adra.com` | Administrador |
| `coordenador@adra.com` | Coordenador |
| `sociopedagogico@adra.com` | Sociopedagógico |

O `supabase/config.toml` copia o que está em stage e produção: signup público
desligado, senha mínima de 8 com letra e número, JWT em ES256.

## Ambientes

| Comando | Perfil | Banco |
| --- | --- | --- |
| `make run` | `local` | Supabase local |
| `make stage` | `stage` | Supabase de stage |
| `make prod` | `prod` | Supabase de produção |

Stage e produção leem os segredos de `.env.stage` e `.env.prod`. Copie de
`.env.example`; eles não vão para o git. Variável de ambiente do sistema ganha
do arquivo, que é como CI e deploy injetam os valores.

## E-mails

O Supabase Auth envia o convite e o "esqueci a senha". A API só envia a
redefinição pedida pelo Administrador, porque precisa devolver o mesmo link
para ele copiar. Os dois saem pelo SMTP do Brevo e usam os mesmos templates,
em `src/main/resources/templates`.

Local, tudo cai no Mailpit: `http://localhost:54324`. Em stage e produção o
Supabase não lê o `config.toml`: mudou um template, cole o HTML no painel em
Authentication → Emails → Templates.

## Banco

Quem cria e atualiza o schema é o Flyway, sozinho, toda vez que a API sobe. Os
arquivos ficam em `src/main/resources/db/migration`.

### Mudei o banco, e agora?

Criou tabela nova, adicionou coluna, criou índice: pede o arquivo pro make e
escreve o SQL dentro dele.

```bash
make migration nome=criar_tabela_oficina
```

Ele cria o arquivo vazio e imprime o caminho:

```
src/main/resources/db/migration/V20260920143000__criar_tabela_oficina.sql
```

O número no nome é a data e a hora de agora. É só para duas pessoas em branches
diferentes nunca escolherem o mesmo. Por isso use o `make migration` em vez de
criar o arquivo na mão.

Dentro é SQL normal, com o schema na frente do nome da tabela:

```sql
ALTER TABLE adra.responsavel ADD COLUMN cpf varchar(20);
```

Salva e roda `make run`. O Flyway aplica. Da próxima vez ele não aplica de novo.

Se você mexeu numa `@Entity`, o arquivo tem que refletir a mesma mudança. O
Flyway não lê o código Java, e o Hibernate não cria nada.

### Duas regras

1. **Arquivo que já rodou não se edita.** Errou? Cria outro arquivo corrigindo.
   O Flyway guarda um checksum de cada um e a API não sobe se algum mudar.
2. **Não mexa no schema pelo Studio do Supabase.** O Flyway não fica sabendo, e
   o banco de cada um começa a ficar diferente do outro.

### O arquivo de baseline

O `V20260920123202__baseline.sql` é um dump do stage tirado no dia em que o
Flyway entrou. Banco novo (o seu local, o dos testes) constrói tudo a partir
dele. Stage e produção já tinham o schema, então o Flyway só marca como aplicado
e não executa nada (`spring.flyway.baseline-on-migrate`).

Ele é o único arquivo que não foi criado pelo `make migration`. Não mexa nele.

`sql/legacy/` é o que era aplicado à mão antes disso. Só histórico, não roda.

## Container Docker

Isto **não** é o jeito de desenvolver, para isso use no `make run` (ou `make stage`), que
tem hot reload e reinicia em segundos. O container serve para conferir o que vai
para o servidor no deploy: a imagem constrói, sobe atrás do [Caddy](https://caddyserver.com)
com HTTPS, e as variáveis chegam certas.

```bash
make docker-stage   # imagem rodando contra o banco de stage
make docker-prod    # imagem rodando contra o banco de producao
make docker-logs    # acompanha os logs 
make docker-down    # derruba tudo.
```

O `docker-stage` lê os segredos do seu `.env.stage`
O `docker-prod` do `.env.prod` 
Em resumo, os mesmos arquivos que o `make stage` e o `make prod` já usam.

Na sua máquina o domínio é `localhost`, então o Caddy assina com a CA local dele
e você chega na API por `https://localhost`. Precisa de `curl -k`, porque o
certificado não é de uma autoridade pública.

A aplicação não publica porta no host: só o Caddy fala com ela. E a imagem não
tem profile embutido sem `SPRING_PROFILES_ACTIVE` ela falha no boot, de
propósito, para nunca subir apontando para o banco errado.

Se faltar alguma variável, o compose recusa antes de subir e diz qual é.

Horário do container é sempre UTC. Converter para o fuso local é trabalho de quem exibe o dado.

### No servidor

Lá não tem Makefile nem `.env.stage`: existe um `.env` no diretório do projeto,
copiado do `.env.example` e preenchido, e o comando é

```bash
docker compose up -d
```

`API_DOMAIN` sai desse `.env`. Trocar de domínio depois é editar o arquivo e
rodar o comando de novo. O Caddy emite o certificado novo sozinho.

A URL do front e a origem liberada no CORS não são segredo e ficam no
`application-prod.properties`. Mudou o front de lugar, muda lá e faz merge.

## Deploy

Merge na `main` dispara o deploy sozinho. O GitHub Actions roda os testes,
constrói a imagem, publica no GHCR e atualiza o droplet. Dá para acompanhar na
aba Actions do repositório.

Em pull request só os testes rodam. Nada toca produção antes do merge.

Para subir de novo sem commitar nada, use o botão "Run workflow" na aba Actions.

### Rollback

Cada build publica duas tags: `latest` e `sha-<commit>`. O droplet guarda a tag
ativa na variável `IMAGE_TAG` do `.env`. Para voltar a uma versão anterior:

```bash
ssh -i ~/.ssh/adra-ci adra@IP_DO_DROPLET "sha-COMMIT_ANTERIOR"
```

A chave de CI é amarrada ao script de deploy no `authorized_keys` do servidor,
então esse comando não abre shell nenhum. Ele só troca a versão e sobe.

### O que o servidor precisa ter

`compose.yaml`, `Caddyfile`, um `.env` preenchido e o `scripts/deploy.sh` deste
repositório copiado para `~/adra/deploy.sh`, com permissão de execução.

## Backup

O backup do Supabase não inclui os arquivos do Storage, e o plano Free nem tem
backup automático. Por isso guardamos uma cópia completa fora do Supabase.

### Como funciona

Toda quinta às 02:17 BRT, o cron do droplet roda o `scripts/backup.sh`:

1. Gera o dump do banco e baixa o bucket.
2. Junta tudo em um zip com senha (AES-256).
3. Envia o zip para a pasta `adra-backups`, no Google Drive da conta Google da
   ADRA.
4. Apaga as cópias além das 12 mais recentes.
5. Avisa o healthchecks.io que terminou.

O zip contém:

- `db.dump`: schemas `adra`, `auth` e `storage`. Sem `auth`, ninguém faz login
  depois de restaurar.
- `storage/`: o bucket `adra-arquivos` inteiro. Os arquivos não entram no dump.
- `app.log`: logs da API dos últimos 7 dias, no máximo desde o último deploy.

Se uma etapa falhar, o script para, remove os arquivos temporários e mantém as
cópias antigas. Sem o aviso, o healthchecks.io manda um e-mail em até 8 dias.

O backup não passa pelo CI/CD. O deploy não entrega nem altera o script, as
credenciais ou o cron.

A senha do zip fica no `.env` do droplet e com o responsável da ADRA. Sem ela, os
zips não abrem. Para abrir, use o 7-Zip.

### Runbook

Os comandos rodam no droplet, salvo indicação.

| Situação | O que fazer |
| --- | --- |
| Rodar agora | `~/adra/backup.sh` |
| Ver a última execução | `journalctl -t adra-backup -n 50` |
| Listar as cópias | `rclone lsl gdrive:adra-backups` |
| Alerta do healthchecks.io | Veja a última execução, corrija a causa e rode agora |
| Erro de autenticação no Drive | Na sua máquina, rode `rclone config reconnect gdrive:` e copie o `rclone.conf` de novo |
| Erro de senha no banco ou 403 no S3 | Confira `SUPABASE_PASSWORD` e as chaves S3 no `~/adra/.env` |
| Drive sem espaço | Confira com `rclone about gdrive:`, libere espaço ou reduza `COPIES_TO_KEEP` no script |
| Mudou `scripts/backup.sh` | Faça o merge e copie o script de novo com `scp` |
| Trocou a senha do zip | Atualize o `~/adra/.env`; as cópias antigas continuam com a senha antiga |
| Perda de dados | Siga a [restauração](#restauração) |

### Instalação

1. No Google Cloud, com a conta Google da ADRA: crie um projeto, ative a Google
   Drive API, configure a tela de consentimento como External com o escopo
   `drive.file`, crie um OAuth client do tipo Desktop e publique o app. Em
   "Testing", a autorização expira em 7 dias.
2. Na sua máquina, faça login com a conta quando o navegador abrir:

   ```bash
   rclone config create gdrive drive client_id=ID client_secret=SEGREDO scope=drive.file
   rclone mkdir gdrive:adra-backups
   ```

3. No painel do Supabase de produção, gere uma chave em Storage > S3. Ela dá
   acesso total aos buckets, então fica só no servidor.
4. No healthchecks.io, crie um check com período de 7 dias e tolerância de 1 dia.
5. Prepare o droplet. A chave de CI não serve aqui, porque ela só executa o deploy.

   ```bash
   # No droplet
   curl https://rclone.org/install.sh | sudo bash
   sudo apt install p7zip-full
   timedatectl   # precisa estar em UTC
   df -h         # precisa caber cerca de 2x o bucket, mais o dump
   mkdir -p ~/.config/rclone

   # Na sua máquina
   scp scripts/backup.sh adra@IP_DO_DROPLET:~/adra/backup.sh
   scp ~/.config/rclone/rclone.conf adra@IP_DO_DROPLET:~/.config/rclone/rclone.conf

   # No droplet
   chmod 700 ~/adra/backup.sh
   chmod 600 ~/.config/rclone/rclone.conf
   ```

6. Adicione ao `~/adra/.env` as variáveis de backup do `.env.example`. Use aspas
   simples em valores com espaço ou caracteres especiais.
7. Rode `~/adra/backup.sh`, baixe o zip do Drive e abra com a senha.
8. Agende com `crontab -e`:

   ```cron
   17 5 * * 4 /home/adra/adra/backup.sh 2>&1 | logger -t adra-backup
   ```

Para testar contra outro ambiente, defina `BACKUP_DB_URL`, `BACKUP_S3_ENDPOINT`,
`BACKUP_S3_REGION` e `BACKUP_REMOTE` no `.env` de teste.

### Restauração

Em um projeto novo do Supabase, com a conexão do Session pooler em `DB_URL`:

```bash
7z x adra-backup-AAAA-MM-DD.zip

psql "$DB_URL" -c 'CREATE SCHEMA adra'
pg_restore --no-owner --no-privileges --schema=adra -d "$DB_URL" db.dump
pg_restore --data-only --schema=auth --table=users -d "$DB_URL" db.dump
pg_restore --data-only --schema=auth --table=identities -d "$DB_URL" db.dump
pg_restore --data-only --schema=storage --table=buckets -d "$DB_URL" db.dump

rclone config create supabase s3 provider=Other access_key_id=ID secret_access_key=SEGREDO \
    endpoint=https://REF.storage.supabase.co/storage/v1/s3 region=REGIAO force_path_style=true
rclone copy storage supabase:adra-arquivos
```

Depois, aponte o `application-prod.properties` e o `.env` para o projeto novo.

## Testes

`make test` roda `./gradlew test`. Os testes sobem o próprio Postgres via
Testcontainers, então não dependem do `make up`.

## Instalando as dependências

Java 21, Docker, `make` e a
[Supabase CLI](https://github.com/supabase/cli/releases) — baixe o `.rpm` ou o
`.deb` da última release, não existe pacote dela no dnf nem no apt.

**Fedora**

```bash
sudo dnf install java-21-openjdk-devel make curl
```

**Ubuntu e Mint**

```bash
sudo apt install openjdk-21-jdk make curl
```

Docker nos dois: [docs.docker.com/engine/install](https://docs.docker.com/engine/install/).
No Mint use as instruções do Ubuntu; não é oficialmente suportado, mas funciona.

**Windows 11**

O `make` e os scripts são de shell, então roda tudo dentro do WSL2 e segue as
instruções do Ubuntu acima:

```powershell
wsl --install -d Ubuntu
winget install --id Docker.DockerDesktop
```

Depois ative a integração com o WSL no Docker Desktop, em
Settings > Resources > WSL integration.
