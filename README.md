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

Quem cria o schema é o Flyway, no boot — por isso o `make seed` vem depois do
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
diferentes nunca escolherem o mesmo — por isso use o `make migration` em vez de
criar o arquivo na mão.

Dentro é SQL normal, com o schema na frente do nome da tabela:

```sql
ALTER TABLE adra.responsavel ADD COLUMN cpf varchar(20);
```

Salva e roda `make run`. O Flyway aplica. Da próxima vez ele não aplica de novo.

Se você mexeu numa `@Entity`, o arquivo tem que refletir a mesma mudança — o
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
