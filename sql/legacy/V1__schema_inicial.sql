DROP SCHEMA IF EXISTS adra CASCADE;
CREATE SCHEMA adra;
SET search_path TO adra, public;

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE nome_nivel_permissao AS ENUM
  ('ADMINISTRADOR','COORDENADOR','OFICINEIRO','SOCIOPEDAGOGICO','PROFISSIONAL_SAUDE','FINANCEIRO');

CREATE TYPE modulo_sistema AS ENUM
  ('USUARIOS','ASSISTIDOS','RESPONSAVEIS','PROJETOS','TURMAS','OFICINAS','AULAS','SEMANARIOS',
   'PLANOS_AULA','MATERIAIS_DIDATICOS','CHAMADA','PRONTUARIOS','DISCIPLINA','DOCUMENTOS',
   'COMUNICADOS','RELATORIOS','EXPORTACOES_FINANCEIRAS','DASHBOARD','DISPONIBILIDADE_OFICINEIRO');

CREATE TYPE acao_sistema AS ENUM
  ('CRIAR','CONSULTAR','EDITAR','EXCLUIR','APROVAR','REPROVAR','EMITIR','EXPORTAR');

CREATE TYPE status_geral      AS ENUM ('ATIVO','INATIVO');
CREATE TYPE status_matricula  AS ENUM ('ATIVA','ENCERRADA','CANCELADA');
CREATE TYPE status_aula       AS ENUM ('PLANEJADA','REALIZADA','CANCELADA');
CREATE TYPE status_aprovacao  AS ENUM ('RASCUNHO','PENDENTE_APROVACAO','APROVADO','REPROVADO','CANCELADO');
CREATE TYPE status_presenca   AS ENUM ('PRESENTE','FALTA','FALTA_JUSTIFICADA');

-- ============================================================
-- FUNCAO: atualizar atualizado_em (Auditavel)
-- ============================================================

CREATE OR REPLACE FUNCTION set_atualizado_em()
RETURNS trigger AS $$
BEGIN
  NEW.atualizado_em = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- MODULO 1 — ACESSO E AUDITORIA
-- ============================================================

CREATE TABLE nivel_permissao (
  nivel_permissao_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nome      nome_nivel_permissao NOT NULL UNIQUE,
  descricao text,
  ativo     boolean NOT NULL DEFAULT true,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
  usuario_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nivel_permissao_id bigint NOT NULL REFERENCES nivel_permissao(nivel_permissao_id) ON DELETE RESTRICT,
  nome_completo varchar(180) NOT NULL,
  email         varchar(255) NOT NULL,
  senha_hash    text NOT NULL,
  cargo_funcao  varchar(120),
  telefone      varchar(30),
  ativo         boolean NOT NULL DEFAULT true,
  ultimo_login  timestamptz,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX usuario_email_lower_uk ON usuario (lower(email));
CREATE INDEX usuario_nivel_idx ON usuario(nivel_permissao_id);

CREATE TABLE log_auditoria (
  log_auditoria_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- ON DELETE SET NULL intencional: log historico nao deve ser perdido se
  -- usuario for desativado. Usuario nunca deve ser deletado fisicamente
  -- (usar ativo=false), mas SET NULL garante integridade mesmo assim.
  usuario_id bigint REFERENCES usuario(usuario_id) ON DELETE SET NULL,
  modulo            modulo_sistema NOT NULL,
  entidade_afetada  varchar(100) NOT NULL,
  entidade_id       bigint,
  acao              acao_sistema NOT NULL,
  valor_anterior    jsonb,
  valor_novo        jsonb,
  data_hora         timestamptz NOT NULL DEFAULT now(),
  ip                inet,
  dispositivo       text,
  observacao        text
);
CREATE INDEX log_auditoria_usuario_idx  ON log_auditoria(usuario_id);
CREATE INDEX log_auditoria_entidade_idx ON log_auditoria(entidade_afetada, entidade_id);
CREATE INDEX log_auditoria_data_idx     ON log_auditoria(data_hora);

-- ============================================================
-- MODULO 2 — CADASTRO (assistidos e responsaveis)
-- ============================================================

CREATE TABLE assistido (
  assistido_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nome_completo            varchar(180) NOT NULL,
  data_nascimento          date NOT NULL,
  cpf                      varchar(20),
  data_entrada             date NOT NULL DEFAULT CURRENT_DATE,
  data_saida               date,
  motivo_saida             text,
  necessidades_especificas text,
  observacoes              text,
  status                   status_geral NOT NULL DEFAULT 'ATIVO',
  -- [C] Contadores denormalizados para progressao disciplinar automatica.
  --     Atualizados pelo trigger trg_progressao_disciplinar.
  --     Fonte de verdade continua sendo registro_disciplinar.
  total_ocorrencias_ativas  integer NOT NULL DEFAULT 0,
  total_advertencias_ativas integer NOT NULL DEFAULT 0,
  total_suspensoes          integer NOT NULL DEFAULT 0,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT assistido_data_saida_ck CHECK (data_saida IS NULL OR data_saida >= data_entrada),
  CONSTRAINT assistido_contadores_ck CHECK (
    total_ocorrencias_ativas  >= 0 AND
    total_advertencias_ativas >= 0 AND
    total_suspensoes          >= 0
  )
);
CREATE INDEX assistido_nome_idx   ON assistido(nome_completo);
CREATE INDEX assistido_status_idx ON assistido(status);

CREATE TABLE responsavel (
  responsavel_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nome_completo   varchar(180) NOT NULL,
  data_nascimento date,
  cpf             varchar(20),
  telefone        varchar(30),
  email           varchar(255),
  endereco        text,
  observacoes     text,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  atualizado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX responsavel_nome_idx ON responsavel(nome_completo);

-- Classe associativa N:N (substitui a entidade "Familia").
CREATE TABLE assistido_responsavel (
  assistido_id   bigint NOT NULL REFERENCES assistido(assistido_id)   ON DELETE CASCADE,
  responsavel_id bigint NOT NULL REFERENCES responsavel(responsavel_id) ON DELETE CASCADE,
  parentesco            varchar(80),
  responsavel_principal boolean NOT NULL DEFAULT false,
  contato_emergencia    boolean NOT NULL DEFAULT false,
  autorizado_retirada   boolean NOT NULL DEFAULT false,
  observacoes           text,
  criado_em timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (assistido_id, responsavel_id)
);
-- No maximo um responsavel principal por assistido.
CREATE UNIQUE INDEX assistido_um_principal_uk
  ON assistido_responsavel(assistido_id) WHERE responsavel_principal = true;
