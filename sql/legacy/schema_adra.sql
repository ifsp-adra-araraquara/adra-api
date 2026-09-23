--
-- PostgreSQL database dump
--

\restrict y8P3xWbAWeOv0F7Et6Vjydifa2sEbo0hZBFZXh9e44V1JhiaSyfgUYJXlMYCUyu

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: adra; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA adra;


--
-- Name: acao_sistema; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.acao_sistema AS ENUM (
    'CRIAR',
    'CONSULTAR',
    'EDITAR',
    'EXCLUIR',
    'APROVAR',
    'REPROVAR',
    'EMITIR',
    'EXPORTAR'
);


--
-- Name: especialidade_saude; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.especialidade_saude AS ENUM (
    'NEUROLOGIA',
    'PSICOPEDAGOGIA',
    'PSICOLOGIA'
);


--
-- Name: modulo_sistema; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.modulo_sistema AS ENUM (
    'USUARIOS',
    'ASSISTIDOS',
    'RESPONSAVEIS',
    'PROJETOS',
    'TURMAS',
    'OFICINAS',
    'AULAS',
    'SEMANARIOS',
    'PLANOS_AULA',
    'MATERIAIS_DIDATICOS',
    'CHAMADA',
    'PRONTUARIOS',
    'DISCIPLINA',
    'DOCUMENTOS',
    'COMUNICADOS',
    'RELATORIOS',
    'EXPORTACOES_FINANCEIRAS',
    'DASHBOARD',
    'DISPONIBILIDADE_OFICINEIRO'
);


--
-- Name: nome_nivel_permissao; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.nome_nivel_permissao AS ENUM (
    'ADMINISTRADOR',
    'COORDENADOR',
    'OFICINEIRO',
    'SOCIOPEDAGOGICO',
    'PROFISSIONAL_SAUDE',
    'FINANCEIRO'
);


--
-- Name: status_aprovacao; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_aprovacao AS ENUM (
    'RASCUNHO',
    'PENDENTE_APROVACAO',
    'APROVADO',
    'REPROVADO',
    'CANCELADO'
);


--
-- Name: status_aula; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_aula AS ENUM (
    'PLANEJADA',
    'REALIZADA',
    'CANCELADA'
);


--
-- Name: status_geral; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_geral AS ENUM (
    'ATIVO',
    'INATIVO'
);


--
-- Name: status_matricula; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_matricula AS ENUM (
    'ATIVA',
    'ENCERRADA',
    'CANCELADA'
);


--
-- Name: status_presenca; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_presenca AS ENUM (
    'PRESENTE',
    'FALTA',
    'FALTA_JUSTIFICADA'
);


--
-- Name: set_atualizado_em(); Type: FUNCTION; Schema: adra; Owner: -
--

CREATE FUNCTION adra.set_atualizado_em() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.atualizado_em = now();
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: assistido; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.assistido (
    assistido_id bigint NOT NULL,
    nome_completo character varying(180) NOT NULL,
    data_nascimento date NOT NULL,
    cpf character varying(20),
    data_entrada date DEFAULT CURRENT_DATE NOT NULL,
    data_saida date,
    motivo_saida text,
    necessidades_especificas text,
    observacoes text,
    status adra.status_geral DEFAULT 'ATIVO'::adra.status_geral NOT NULL,
    total_ocorrencias_ativas integer DEFAULT 0 NOT NULL,
    total_advertencias_ativas integer DEFAULT 0 NOT NULL,
    total_suspensoes integer DEFAULT 0 NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT assistido_contadores_ck CHECK (((total_ocorrencias_ativas >= 0) AND (total_advertencias_ativas >= 0) AND (total_suspensoes >= 0))),
    CONSTRAINT assistido_data_saida_ck CHECK (((data_saida IS NULL) OR (data_saida >= data_entrada)))
);


--
-- Name: assistido_assistido_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.assistido ALTER COLUMN assistido_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.assistido_assistido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: assistido_responsavel; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.assistido_responsavel (
    assistido_id bigint NOT NULL,
    responsavel_id bigint NOT NULL,
    parentesco character varying(80),
    responsavel_principal boolean DEFAULT false NOT NULL,
    contato_emergencia boolean DEFAULT false NOT NULL,
    autorizado_retirada boolean DEFAULT false NOT NULL,
    observacoes text,
    criado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: log_auditoria; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.log_auditoria (
    log_auditoria_id bigint NOT NULL,
    usuario_id bigint,
    modulo adra.modulo_sistema NOT NULL,
    entidade_afetada character varying(100) NOT NULL,
    entidade_id bigint,
    acao adra.acao_sistema NOT NULL,
    valor_anterior jsonb,
    valor_novo jsonb,
    data_hora timestamp with time zone DEFAULT now() NOT NULL,
    ip inet,
    dispositivo text,
    observacao text
);


--
-- Name: log_auditoria_log_auditoria_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.log_auditoria ALTER COLUMN log_auditoria_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.log_auditoria_log_auditoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: nivel_permissao; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.nivel_permissao (
    nivel_permissao_id bigint NOT NULL,
    nome adra.nome_nivel_permissao NOT NULL,
    descricao text,
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: nivel_permissao_nivel_permissao_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.nivel_permissao ALTER COLUMN nivel_permissao_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.nivel_permissao_nivel_permissao_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: responsavel; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.responsavel (
    responsavel_id bigint NOT NULL,
    nome_completo character varying(180) NOT NULL,
    data_nascimento date,
    cpf character varying(20),
    telefone character varying(30),
    email character varying(255),
    endereco text,
    observacoes text,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: responsavel_responsavel_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.responsavel ALTER COLUMN responsavel_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.responsavel_responsavel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.usuario (
    usuario_id bigint NOT NULL,
    nivel_permissao_id bigint NOT NULL,
    nome_completo character varying(180) NOT NULL,
    email character varying(255) NOT NULL,
    cargo_funcao character varying(120),
    telefone character varying(30),
    ativo boolean DEFAULT true NOT NULL,
    ultimo_login timestamp with time zone,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    especialidade adra.especialidade_saude,
    auth_uid uuid
);


--
-- Name: usuario_usuario_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.usuario ALTER COLUMN usuario_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.usuario_usuario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: assistido assistido_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido
    ADD CONSTRAINT assistido_pkey PRIMARY KEY (assistido_id);


--
-- Name: assistido_responsavel assistido_responsavel_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_responsavel
    ADD CONSTRAINT assistido_responsavel_pkey PRIMARY KEY (assistido_id, responsavel_id);


--
-- Name: log_auditoria log_auditoria_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.log_auditoria
    ADD CONSTRAINT log_auditoria_pkey PRIMARY KEY (log_auditoria_id);


--
-- Name: nivel_permissao nivel_permissao_nome_key; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.nivel_permissao
    ADD CONSTRAINT nivel_permissao_nome_key UNIQUE (nome);


--
-- Name: nivel_permissao nivel_permissao_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.nivel_permissao
    ADD CONSTRAINT nivel_permissao_pkey PRIMARY KEY (nivel_permissao_id);


--
-- Name: responsavel responsavel_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.responsavel
    ADD CONSTRAINT responsavel_pkey PRIMARY KEY (responsavel_id);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (usuario_id);


--
-- Name: assistido_nome_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_nome_idx ON adra.assistido USING btree (nome_completo);


--
-- Name: assistido_status_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_status_idx ON adra.assistido USING btree (status);


--
-- Name: assistido_um_principal_uk; Type: INDEX; Schema: adra; Owner: -
--

CREATE UNIQUE INDEX assistido_um_principal_uk ON adra.assistido_responsavel USING btree (assistido_id) WHERE (responsavel_principal = true);


--
-- Name: log_auditoria_data_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX log_auditoria_data_idx ON adra.log_auditoria USING btree (data_hora);


--
-- Name: log_auditoria_entidade_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX log_auditoria_entidade_idx ON adra.log_auditoria USING btree (entidade_afetada, entidade_id);


--
-- Name: log_auditoria_usuario_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX log_auditoria_usuario_idx ON adra.log_auditoria USING btree (usuario_id);


--
-- Name: responsavel_nome_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX responsavel_nome_idx ON adra.responsavel USING btree (nome_completo);


--
-- Name: usuario_auth_uid_uk; Type: INDEX; Schema: adra; Owner: -
--

CREATE UNIQUE INDEX usuario_auth_uid_uk ON adra.usuario USING btree (auth_uid);


--
-- Name: usuario_email_lower_uk; Type: INDEX; Schema: adra; Owner: -
--

CREATE UNIQUE INDEX usuario_email_lower_uk ON adra.usuario USING btree (lower((email)::text));


--
-- Name: usuario_nivel_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX usuario_nivel_idx ON adra.usuario USING btree (nivel_permissao_id);


--
-- Name: assistido_responsavel assistido_responsavel_assistido_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_responsavel
    ADD CONSTRAINT assistido_responsavel_assistido_id_fkey FOREIGN KEY (assistido_id) REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE;


--
-- Name: assistido_responsavel assistido_responsavel_responsavel_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_responsavel
    ADD CONSTRAINT assistido_responsavel_responsavel_id_fkey FOREIGN KEY (responsavel_id) REFERENCES adra.responsavel(responsavel_id) ON DELETE CASCADE;


--
-- Name: log_auditoria log_auditoria_usuario_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.log_auditoria
    ADD CONSTRAINT log_auditoria_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES adra.usuario(usuario_id) ON DELETE SET NULL;


--
-- Name: usuario usuario_nivel_permissao_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.usuario
    ADD CONSTRAINT usuario_nivel_permissao_id_fkey FOREIGN KEY (nivel_permissao_id) REFERENCES adra.nivel_permissao(nivel_permissao_id) ON DELETE RESTRICT;


--
-- Name: assistido; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.assistido ENABLE ROW LEVEL SECURITY;

--
-- Name: assistido_responsavel; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.assistido_responsavel ENABLE ROW LEVEL SECURITY;

--
-- Name: log_auditoria; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.log_auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: nivel_permissao; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.nivel_permissao ENABLE ROW LEVEL SECURITY;

--
-- Name: responsavel; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.responsavel ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.usuario ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--

\unrestrict y8P3xWbAWeOv0F7Et6Vjydifa2sEbo0hZBFZXh9e44V1JhiaSyfgUYJXlMYCUyu


-- Dados de referencia: perfis de acesso (necessarios em todo ambiente).
\restrict 4rVg7nM1crlLPysg0x3abbhuQEoWGXykQAVhYQpcxIVQrYKJ9IYqALimg9KXlZT
COPY adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) FROM stdin;
1	ADMINISTRADOR	Acesso total ao sistema, unico perfil que cadastra usuarios	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
2	COORDENADOR	Acesso amplo, incluindo controle disciplinar	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
3	OFICINEIRO	Acesso somente leitura	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
4	SOCIOPEDAGOGICO	Equipe sociopedagogica	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
5	PROFISSIONAL_SAUDE	Profissional de saude com login individual por especialidade	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
6	FINANCEIRO	Exportacao de dados e acompanhamento de projetos	t	2026-08-16 15:39:31.994708+00	2026-08-16 15:39:31.994708+00
\.
\unrestrict 4rVg7nM1crlLPysg0x3abbhuQEoWGXykQAVhYQpcxIVQrYKJ9IYqALimg9KXlZT
