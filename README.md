# adra-api

API do ADRA — assistidos, responsáveis e usuários.

O Supabase Auth só autentica e devolve o `sub`. A API valida esse token via
JWKS, acha o usuário pelo `auth_uid` e emite o próprio JWT com o perfil.
Autorização e auditoria ficam aqui; a RLS fechada impede acesso ao banco por
fora.

## Rodando

```bash
make up      # Postgres e Auth locais
make reset   # schema, seeds e identidades
make run     # API em localhost:8080
```

Local não precisa de segredo, usa as chaves de demonstração da CLI. `make down`
derruba.

O `make reset` recria o schema, insere os usuários fictícios e cria a identidade
de cada um no Auth local. Os e-mails e a senha são os mesmos do stage, para não
ter que decorar dois conjuntos de credencial. Senha `mudar123`:

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

## Banco

O `sql/migrations` é aplicado à mão. O `sql/schema_adra.sql` é o schema
consolidado, e é o que o `make reset` joga no banco local.

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
