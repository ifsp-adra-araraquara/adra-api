# ADRAbot (Robozinho ADRA) — design

Data: 2026-11-19 · Repositórios: `adra-api` (consultas) e `adra-web` (robô, catálogo, PDF)

## 1. Intenção

A equipe da ADRA (Coordenador, Assistente Social, Educador Social, Oficineiro) faz todo dia
perguntas como "como está a frequência do grupo X?", "quem está faltando muito?", "quantos
atendidos recebem Bolsa Família?" ou "como corrijo a data de entrada?". Hoje a resposta exige
saber em que tela procurar, filtrar e contar. O **Robozinho ADRA** responde essas perguntas na
hora, com os números reais do sistema. Em seguida oferece **"Quer isso em PDF?"** e gera o
documento no **modelo ADRA** (folha A4 com cabeçalho e rodapé da instituição).

Ele só fala do sistema ADRA. Quando a pergunta foge do escopo, ele percebe, diz isso com
educação e devolve a pessoa a opções que sabe responder. Nenhuma conversa termina sem um
próximo passo.

**Custo zero de IA.** Não há modelo de linguagem, nem pago nem local. O entendimento usa regras
determinísticas, e cada número vem de uma consulta SQL conhecida, então o robô **nunca inventa**.

## 2. Objetivos, não objetivos e anti-objetivos

**Objetivos**
- Responder com versatilidade às perguntas do dia a dia: frequência, atendidos, grupos e
  oficinas, disciplinar e pendências.
- Responder perguntas de prestação de contas: perfil social e comparações entre períodos.
- Responder "como funciona o sistema" e mostrar na tela onde clicar.
- Oferecer o PDF no modelo ADRA para toda resposta com dados.
- Reconhecer perguntas fora do escopo e conduzir de volta, sem beco sem saída.
- Pesar pouco: zero dependência nova no front e no back, catálogo carregado só quando o robô abre
  e consultas agregadas no banco.

**Não objetivos (fora da v1)**
- O robô não escreve nada no sistema: não lança chamada, não cadastra e não apaga. Ele leva à
  tela certa.
- Não responde por voz e não é usado fora do sistema (WhatsApp, por exemplo).
- Não gera PDF no servidor.
- Não guarda histórico de conversa entre sessões.

**Anti-objetivos (falham mesmo que "funcionem")**
- Dar um número errado ou inventado. Errar a pergunta é preferível a errar o dado.
- Mostrar a um perfil um dado que a tela equivalente não mostra a esse perfil.
- Expor dado sensível individual (renda, saúde, programa social, CPF, endereço de uma criança)
  no chat. Para esses campos o robô só dá **contagens agregadas**; o dado individual continua na
  ficha.
- Responder com confiança a algo que não entendeu.
- Ficar "conversando" sobre assuntos alheios (política, religião, futebol, saúde de uma criança
  como diagnóstico).

## 3. Restrições

- Stack existente: Angular 21 (standalone, signals), Spring Boot com JPA, Flyway, Caffeine e
  Postgres. Testes de integração usam Testcontainers.
- Permissão no back por método (`@PreAuthorize`), como em `ChamadaController`. O Oficineiro é
  limitado aos próprios grupos via `@acessoTurma.podeAcessarTurma(...)`.
- PDF sem biblioteca, pelo componente `app-folha-a4` (`adra-web/src/app/shared/components/folha-a4/folha-a4.ts`)
  e "Salvar como PDF" do navegador, como já fazem o documento disciplinar e a lista anual.
- Relatórios gerados na hora; nada fica guardado (mesma regra de `/assistidos/por-ano`).
- LGPD: os dados são de crianças e adolescentes atendidos.
- Precisa funcionar no celular, que a equipe usa para fazer a chamada.

## 4. Abordagens consideradas

| | Abordagem | Troca | Decisão |
|---|---|---|---|
| **A** | Robô de regras no front, com catálogo carregado sob demanda, consultas agregadas novas no back e PDF pela folha A4 do navegador | Mais leve para o servidor e nunca inventa. O entendimento depende de um bom catálogo de sinônimos e frases. | **Escolhida** pelo usuário |
| B | Robô inteiro no back, que entende o texto em Java e gera o PDF no servidor | Lógica centralizada, mas traz biblioteca de PDF, uma ida ao servidor por mensagem e CPU para gerar PDF | Descartada: contraria "menor custo no back-end" |
| C | Modelo de linguagem local (servidor ou navegador) | Conversa mais livre, mas pesa de centenas de MB a GB, é lento no celular e pode inventar números | Descartada: risco de dado inventado sobre crianças atendidas |

## 5. Como funciona

### 5.1 Visão geral

```
[Pergunta digitada ou chip]
      │
      ▼
 Normalizar ──► Corrigir digitação ──► Expandir sinônimos
      │
      ▼
 Pontuar intenções (catálogo, filtrado pelo perfil)          ┌─ fora do escopo ─► Desvio educado + chips
      │                                                      │
      ├── confiança alta ──► Extrair lacunas ──► falta lacuna? ─► Pergunta com chips
      │                                     └─► completo ─► Consulta no back ─► Resposta + interpretação
      ├── confiança média ─► "Você quis dizer…?" (até 3 chips)                     │
      └── confiança baixa ─► Detector de fora do escopo ─────────────────────────┘
                                                                                 ▼
                                                     Chips de próximo passo: [Gerar PDF] [Comparar…] [Ver na tela] [Outra pergunta]
```

Tudo à esquerda da "Consulta no back" roda no navegador. O back só recebe chamadas GET com
parâmetros já resolvidos (ids, mês e período) e devolve números agregados.

### 5.2 Motor de entendimento (front, sem dependência nova)

1. **Normalização**: minúsculas; remove acentos, pontuação e espaços repetidos; expande
   abreviações comuns ("freq" → frequencia, "qtd/qnt/qts" → quantos, "msm" → mesmo,
   "vc" → voce); converte números por extenso até trinta ("tres faltas").
2. **Correção de digitação**: palavras com 5 letras ou mais que não estão no vocabulário do
   catálogo são trocadas pela mais próxima com distância de edição (Damerau-Levenshtein) igual a 1.
   Com 8 letras ou mais, aceita distância 2. Assim "frequecia" vira frequencia e "ocorencia" vira
   ocorrencia. A implementação é própria, com cerca de 30 linhas.
3. **Sinônimos do domínio**, num dicionário único usado por todas as intenções:
   - grupo = turma = sala = classe
   - atendido = assistido = aluno = criança = adolescente = jovem
   - frequência = presença = assiduidade; falta = ausência = faltou
   - oficina = atividade = curso
   - ocorrência = advertência = suspensão → família "disciplinar"
   - responsável = mãe = pai = família
   - desligamento = saída = desligar
   - pré-cadastro = inscrição
   - semanário = planejamento semanal
4. **Pontuação de intenções**: cada intenção tem *âncoras* (pelo menos uma precisa aparecer,
   como "frequencia" ou "falta"), *reforços* (peso menor, como "mes", "grupo" ou "comparar") e
   10 ou mais *frases-exemplo*. A nota combina âncoras e reforços (peso 3 e 1), os bigramas que
   coincidem com as frases-exemplo e a penalidade por palavras do domínio que pertencem a outra
   intenção. O resultado fica normalizado entre 0 e 1. O catálogo é filtrado pelo perfil
   **antes** de pontuar.
5. **Faixas de confiança** (os valores iniciais serão calibrados pelo banco de frases da seção 5.9):
   - **≥ 0,70**: executa.
   - **0,40 a 0,70**, ou quando as duas primeiras intenções ficam a menos de 0,10 uma da outra:
     "Você quis dizer…?", com até 3 chips.
   - **< 0,40**: vai para o detector de fora do escopo (5.4).
6. **Extração de lacunas**:
   - *Mês e período*: "outubro", "out/2026", "10/2026", "este mês", "mês passado", "últimos 3
     meses", "semestre", "este ano", "ano passado", "2025". Sem período, o padrão é o mês atual e
     isso é dito na resposta ("Considerei novembro de 2026").
   - *Grupo e oficina*: comparação aproximada com a lista de grupos e oficinas que o perfil pode
     ver. A lista é carregada uma vez ao abrir o robô e guardada em memória.
   - *Atendido*: o nome vai para a busca existente de atendidos (`GET /api/assistidos`, que já
     respeita o perfil). Se houver mais de um resultado, o robô mostra chips com nome e grupo.
   - *Comparação*: "comparar", "versus", "vs", "x", "em relação a", "com o mês anterior" ou "e em
     setembro?" ativam o modo comparação.
   - *Números*: "mais de 3 faltas", "top 5", "acima de 2 ocorrências".
7. **Memória de contexto** (só na conversa atual): guarda a última intenção e as lacunas, para
   aceitar elipses.
   - "E em setembro?" troca o período.
   - "E o grupo B?" troca o grupo.
   - "Compara com o ano passado" ativa a comparação.
   - "Só os de 6 a 10 anos" adiciona a faixa etária.
   - "E as faltas?" troca a intenção dentro do mesmo tema e mantém as lacunas.

### 5.3 Diálogo: nunca deixar pergunta solta

- **Toda** resposta do robô termina com chips de próximo passo, inclusive as de erro e de fora do
  escopo.
- Lacuna obrigatória faltando: uma pergunta curta com chips (os grupos do perfil, os 6 últimos
  meses) e campo livre.
- Duas falhas seguidas de entendimento abrem **"O que eu sei responder"**, um menu navegável por
  tema com todas as intenções do perfil. Daí a pessoa sempre chega a algo.
- Erro de rede ou do servidor: "Não consegui buscar os dados agora. [Tentar de novo] [Abrir a
  tela de …]". O robô nunca mostra número parcial.
- Resultado vazio é uma resposta válida e explicada ("Nenhuma chamada foi lançada para o Grupo X
  em outubro"), com chip para a tela de chamada.
- Toda resposta com dados mostra o **critério** em uma linha. Por exemplo: "Frequência = presenças
  ÷ atendidos esperados nas chamadas lançadas; falta justificada conta como ausência". É o mesmo
  critério do Painel (`PainelMensalDTO.Frequencia`), para os números baterem com a tela.

### 5.4 Fora do escopo

O detector roda quando a confiança é baixa, e também antes de pontuar quando há palavra de bloqueio.

| Categoria | Como detecta | Resposta |
|---|---|---|
| Saudação, agradecimento, despedida | Lista curta ("oi", "bom dia", "obrigado", "valeu", "tchau") | Responde com cordialidade e mostra 4 sugestões do perfil |
| Quem é você / o que você faz | "quem e voce", "o que voce faz", "ajuda" | Apresentação curta e o menu "O que eu sei responder" |
| Ação de escrita ("lança a chamada", "apaga o atendido", "cadastra") | Verbos de ação + objeto do domínio | "Eu só consulto, não altero nada. Posso te levar até lá." [Ir para a tela] [Me mostra como] |
| Dado sem permissão para o perfil | A intenção existe no catálogo completo mas não no filtrado | "Essa informação fica com a Coordenação / Assistente Social." Não confirma se o dado existe. |
| Dado individual sensível (renda, saúde, CPF, endereço, programa social de uma pessoa) | Campo sensível + nome de pessoa | "Por proteção de dados, eu só mostro totais. O dado de uma pessoa fica na ficha dela." [Abrir ficha] (se o perfil puder) |
| Orientação profissional sobre um caso ("o que faço com essa criança", diagnóstico, jurídico) | Termos clínicos ou jurídicos + pedido de conselho | "Isso precisa de uma pessoa da equipe. Fale com a Assistente Social ou a Coordenação." Sem conselho. |
| Assunto alheio (clima, futebol, política, religião, notícias, piadas, contas, programação) | Sem nenhuma palavra do vocabulário do domínio, ou com palavra da lista de assuntos alheios | "Eu sou o Robozinho ADRA e só sei ajudar com o sistema da ADRA." + 3 sugestões |
| Ofensa ou teste | Lista de palavrões | Resposta neutra e curta + sugestões |
| Tentativa de mudar as regras ("ignore as instruções") | Não se aplica: não há modelo de linguagem para manipular | Cai em "assunto alheio" |

### 5.5 Catálogo de intenções da v1

Legenda da fonte: **E** = endpoint existente; **N** = consulta nova (5.6).
Perfis: C = Coordenador, AS = Assistente Social, ES = Educador Social, O = Oficineiro (só os próprios grupos).

**Frequência**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| freq.grupo.mes | "Frequência do grupo X em outubro" | C AS ES O | N `frequencia/grupos` | sim |
| freq.comparar.grupos | "Compara a frequência dos grupos" / "qual grupo falta mais" | C AS ES | N `frequencia/grupos` | sim |
| freq.comparar.periodos | "Frequência de setembro x outubro" / "este ano x ano passado" | C AS ES O | N `frequencia/serie` | sim |
| freq.oficinas.ranking | "Ranking das oficinas por frequência" | C AS | N `frequencia/oficinas` | sim |
| freq.dia.semana | "Em que dia da semana falta mais gente?" | C AS ES O | N `frequencia/dia-semana` | sim |
| freq.risco.evasao | "Quem está faltando seguido?" / "risco de evasão" | C AS ES O | N `frequencia/faltas-seguidas` | sim |
| freq.atendido | "Frequência do João no ano" | C AS ES | E `/chamadas/assistido/{id}/frequencia` | sim |
| freq.chamadas.pendentes | "Quais chamadas não foram lançadas?" | C | E `/chamadas/status` | sim |

**Atendidos**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| atend.total | "Quantos atendidos temos?" | C AS | E `/assistidos/contagem` | não |
| atend.perfil | "Atendidos por faixa etária / por sexo / por grupo" | C AS | N `atendidos/distribuicao` | sim |
| atend.bairro | "Quantos atendidos por bairro?" | C AS | N `atendidos/distribuicao?por=bairro` | sim |
| atend.entradas.saidas | "Quantos entraram e saíram este ano? Por quê?" | C AS | N `atendidos/movimento` | sim |
| atend.comparar.anos | "Compara 2025 com 2026" | C AS | N `atendidos/movimento` (2 anos) | sim |
| atend.aniversario | "Aniversariantes do mês" | C AS ES O | E (cards de aniversário) | sim |
| atend.escola.turno | "Quantos estudam de manhã / à tarde?" | C AS | N `atendidos/distribuicao?por=turnoEscola` | sim |

**Perfil social (só agregados; prestação de contas)**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| social.programas | "Quantos recebem Bolsa Família / BPC?" | C AS | N `social/resumo` | sim |
| social.renda | "Faixas de renda familiar" | C AS | N `social/resumo` | sim |
| social.pcd | "Quantos PCD / com necessidades específicas?" | C AS | N `social/resumo` | sim |
| social.relatorio.completo | "Relatório para prestação de contas do ano" | C | N (junta `social/resumo` + `atendidos/movimento` + `frequencia/serie`) | sim, com linha de assinatura |

**Grupos e oficinas**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| grupo.vagas | "Quais grupos têm vaga?" | C AS | E `/painel/mensal` (ocupação) ou N `grupos/ocupacao` | sim |
| grupo.oficineiro | "Quem dá a oficina X?" | C AS | E `/oficinas/{id}` | não |
| semanario.pendentes | "Quais semanários não foram enviados?" | C | E (avisos de semanário) / N `semanarios/pendentes?periodo` | sim |

**Disciplinar**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| disc.mes | "Ocorrências do mês por grupo" + comparação com o mês anterior | C AS | N `disciplinar/resumo` | sim |
| disc.proximos.suspensao | "Quem está perto da suspensão?" | C AS ES | E `/disciplinar/sinalizacoes` | sim |
| disc.atendido | "Histórico disciplinar do João" | C AS ES | E `/assistidos/{id}/disciplinar` | sim (reaproveita o documento) |

**Pendências**

| Id | Pergunta típica | Perfis | Fonte | PDF |
|---|---|---|---|---|
| pend.resumo | "O que está pendente pra mim?" | todos (cada um vê as suas) | E `/avisos` + `/painel/hoje` | não |
| pend.precadastros | "Pré-cadastros para avaliar" | C AS | E | não |
| pend.desligamentos | "Pedidos de desligamento aguardando" | C AS | E | não |
| pend.documentos | "Fichas sem documento" | C AS | N `atendidos/sem-documento` | sim |

**Como faço (sem consulta; resposta em texto + "Me mostra" com driver.js)**

Lançar chamada · justificar falta · corrigir a data de entrada no grupo · cadastrar atendido ·
avaliar pré-cadastro · rematricular · pedir/aprovar desligamento · registrar ocorrência ·
enviar semanário · imprimir lista de frequência / lista anual / lista para evento · cadastrar
faixa etária · convidar usuário · o que significam P, F e FJ · por que um atendido aparece em
"possível rematrícula". Cada artigo aparece só para os perfis que têm acesso à tela.

### 5.6 Back-end: consultas novas

- Um controlador `RoboConsultaController` em `/api/robo/consultas/...`, com **um método por
  consulta** e `@PreAuthorize` em cada método (sem anotação na classe, como orienta o comentário
  em `ChamadaController`). O Oficineiro usa `@acessoTurma.podeAcessarTurma(...)`; consultas que
  agregam vários grupos filtram pelos grupos permitidos no próprio SQL.
- Um `RoboConsultaService` (`@Transactional(readOnly = true)`) e um `RoboConsultaRepository` com
  SQL nativo agregado (`COUNT`, `SUM`, `GROUP BY`, janelas para faltas seguidas). Nenhuma lista
  de atendidos é carregada para contar em Java.
- DTOs em `domain/dto/robo/`, sempre no formato "números prontos", como o `PainelMensalDTO`.
  Listas nominais (risco de evasão, perto da suspensão, aniversariantes) devolvem só nome, grupo e
  o número da regra; nunca campo sensível.
- **Limites**: período máximo de 24 meses por consulta; listas nominais limitadas a 50 itens, com
  "e mais N" na resposta.
- **Cache**: Caffeine (já no projeto) por 2 minutos, com chave consulta + parâmetros + escopo
  do perfil. Os números do dia a dia mudam pouco nesse intervalo e o cache evita repetição quando a
  pessoa alterna entre "comparar" e "gerar PDF".
- **Mesmo critério do Painel**: a frequência reaproveita a regra já usada em
  `PainelRepository`/`PainelService` (só P é presença; FJ é ausência; só chamadas lançadas). O
  ideal é extrair essa regra para um lugar só, em vez de duplicar o SQL.
- Nenhuma migração de tabela na v1 (a fase 3 adiciona uma, ver 5.10).

### 5.7 Respostas: formato e tom

- **Persona**: Robozinho ADRA, um mascote em SVG próprio no verde ADRA (`--color-adra-green-*`).
  Tom acolhedor e direto, tratando por "você". Frases curtas, sem gíria e sem emoji em excesso
  (no máximo 1 por mensagem, em saudações).
- **Estrutura de uma resposta com dados**:
  1. A resposta em uma frase, com o número principal em destaque: "A frequência do **Grupo
     Girassol** em **outubro de 2026** foi de **82%**."
  2. A interpretação gerada por regra: "5 pontos abaixo de setembro (87%)" · "a menor entre os 6
     grupos" · "3 atendidos com 3 faltas seguidas ou mais". Cada frase de interpretação é um
     modelo com condição (subiu/caiu/igual; acima/abaixo da média; ranking).
  3. Um detalhe compacto: tabela curta de até 6 linhas ou barras simples em CSS, sem biblioteca de
     gráfico.
  4. O critério em uma linha discreta.
  5. Os chips: **[Gerar PDF]** · [Comparar com o mês anterior] · [Ver na tela] · [Outra pergunta].
- Números no formato pt-BR, meses por extenso, nunca "null" ou "NaN". Percentual só quando o
  denominador é maior que 0; senão, "sem chamadas lançadas".

### 5.8 PDF no modelo ADRA

- Rota nova `/robo/relatorio` (fora do drawer, protegida pelos mesmos perfis da intenção) que
  renderiza `app-folha-a4` com:
  - o cabeçalho ADRA que já existe (logo, nome, núcleo, CNPJ de `INSTITUICAO`);
  - título do relatório, período e filtros aplicados;
  - o corpo: a frase-resposta, a interpretação e a tabela completa (sem o limite de 6 linhas);
  - critério e fonte: "Dados do Sistema ADRA em 19/11/2026 às 14:32";
  - "Gerado por <nome do usuário> (<perfil>) pelo Robozinho ADRA";
  - para `social.relatorio.completo` e comparações anuais, linha de assinatura do Coordenador;
  - o rodapé ADRA que já existe (endereço e contato).
- Tabelas largas usam `paisagem`. Tabelas longas quebram página com o cabeçalho repetido
  (`thead { display: table-header-group }`).
- Os dados passam do chat para a rota por um serviço em memória (signal), **nunca pela URL**. Se
  a pessoa recarregar a página do relatório, ela volta ao robô com a mensagem "Gere o relatório de
  novo".
- Nada é guardado. O botão "Imprimir / Salvar como PDF" usa o que a folha A4 já tem.

### 5.9 Interface

- Um botão flutuante com o mascote, no canto inferior direito de todas as telas internas. Ele não
  aparece no login, no pré-cadastro público nem na impressão (`no-print`). Há também um atalho no
  topbar, ao lado de "Ver tutorial".
- No desktop, o robô abre como painel lateral (reaproveita `app-drawer`). No celular, abre em tela
  cheia, com campo de texto fixo no rodapé e respeitando o teclado virtual.
- **Boas-vindas por perfil**, com 4 sugestões:
  - C: pendências, frequência dos grupos, risco de evasão, relatório do mês;
  - AS: pré-cadastros, perfil social, desligamentos, perto da suspensão;
  - ES: frequência dos meus grupos, faltas seguidas, como lançar chamada, perto da suspensão;
  - O: frequência dos meus grupos, aniversariantes, como enviar o semanário, dia da semana com
    mais faltas.
- Histórico só da sessão (`sessionStorage`), apagado no logout e no encerramento por inatividade
  (`InatividadeService`).
- Acessibilidade: `aria-live="polite"` nas respostas, foco no campo ao abrir, Esc fecha, chips
  navegáveis por teclado e contraste conforme o tema.
- "Me mostra": navega até a rota e usa driver.js (já no projeto, `OnboardingService`) para
  destacar elementos marcados com `data-robo="<id>"`.

### 5.10 Qualidade: como garantimos a excelência

- **Banco de frases** (`robo-frases.spec.ts`), versionado com o catálogo:
  - pelo menos **15 variações por intenção**: formal, informal, com erro de digitação, sem acento,
    com elipse, com período e sem período;
  - pelo menos **150 frases fora do escopo**: assuntos alheios, ações de escrita, dados
    sensíveis individuais, orientação profissional.
- **Metas medidas no teste**, que falha abaixo delas:
  - ≥ 95% de acerto da intenção no topo;
  - ≥ 98% das frases de fora do escopo **não** executam consulta;
  - 100% das lacunas de período e grupo extraídas corretamente nas frases que as têm.
- Testes de integração no back (padrão `*IT` com Testcontainers), um por consulta: números
  conferidos com dados montados, mês vazio, limite de período, e **acesso por perfil** no padrão
  de `AcessoPorPerfilIT`, incluindo o Oficineiro tentando o grupo de outro.
- **Consistência com o Painel**: um teste compara `frequencia/grupos` com `PainelMensalDTO` no
  mesmo mês e o mesmo banco.
- **Desempenho (metas)**:
  - catálogo e motor ≤ 60 KB comprimidos, num *chunk* separado carregado só ao abrir o robô;
  - entendimento em menos de 10 ms;
  - cada consulta em menos de 300 ms (p95) com o banco de produção.
- **Ciclo de melhoria (fase 3)**: uma tabela `robo_pergunta_nao_entendida` guarda o texto
  normalizado (no máximo 200 caracteres), a intenção sugerida, o perfil e a data, por 90 dias.
  Nomes próprios encontrados na lista de atendidos são trocados por "[nome]" antes de enviar. Uma
  aba simples para o Coordenador lista as perguntas mais frequentes que o robô não soube
  responder, e com isso a equipe acrescenta sinônimos e frases ao catálogo.

### 5.11 Fases

1. **Fase 1, o robô e o dia a dia**: motor de entendimento, interface, fora do escopo, artigos
   "Como faço", intenções que usam endpoints existentes, PDF no modelo ADRA e banco de frases.
2. **Fase 2, consultas novas**: frequência (grupos, séries, oficinas, dia da semana, faltas
   seguidas), atendidos (distribuição, bairro, movimento, turno escolar), disciplinar (resumo),
   pendência de documentos.
3. **Fase 3, prestação de contas e melhoria contínua**: perfil social, relatório anual completo
   com assinatura e registro das perguntas não entendidas.

Cada fase pode ir para produção sozinha.

## 6. Decisões tomadas

**Pelo usuário**
- Abordagem A.
- O robô responde com dados reais e pergunta se a pessoa quer o PDF.
- Foco em perguntas do dia a dia, prestação de contas e funcionamento do sistema.
- O robô reconhece fora do escopo.
- Relatórios no modelo ADRA.
- Custo zero de IA e baixo custo no back.

**Pelo autor do design (podem ser revistas)**
- O robô é só leitura.
- Dado sensível só em agregados.
- Faixas de confiança 0,70 / 0,40.
- Cache de 2 minutos.
- Limite de 24 meses e de 50 itens.
- Risco de evasão = 3 faltas seguidas (F ou FJ) nas chamadas lançadas do grupo, valor ajustável
  numa constante.
- Histórico só na sessão.
- Fase 3 com registro anonimizado das perguntas não entendidas.

## 7. Fica para quem constrói

- A forma exata do arquivo de catálogo (TS tipado ou JSON) e a organização das pastas em
  `adra-web/src/app/features/robo/`.
- Os pesos exatos da pontuação, calibrados pelo banco de frases até bater as metas.
- O SQL de cada consulta e os índices, se o `EXPLAIN` pedir.
- O desenho do mascote (SVG simples no verde ADRA).
- Os textos finais de cada resposta e de cada artigo "Como faço", revisados com alguém da equipe
  ADRA.
