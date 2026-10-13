--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.6

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

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA adra;


--
-- Name: acao_sistema; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.acao_sistema AS ENUM (
    'CRIAR',
    'CONSULTAR',
    'EDITAR',
    'EXCLUIR'
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
    'AULAS'
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
-- Name: status_aula; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_aula AS ENUM (
    'PLANEJADA',
    'REALIZADA',
    'CANCELADA',
    'REMARCADA'
);


--
-- Name: status_geral; Type: TYPE; Schema: adra; Owner: -
--

CREATE TYPE adra.status_geral AS ENUM (
    'ATIVO',
    'INATIVO'
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
    necessidades_especificas text,
    observacoes text,
    status adra.status_geral DEFAULT 'ATIVO'::adra.status_geral NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL,
    data_entrada date NOT NULL,
    data_saida date,
    motivo_saida text,
    total_ocorrencias_ativas integer DEFAULT 0 NOT NULL,
    total_advertencias_ativas integer DEFAULT 0 NOT NULL,
    total_suspensoes integer DEFAULT 0 NOT NULL,
    turma_id bigint
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
-- Name: assistido_turma_historico; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.assistido_turma_historico (
    historico_id bigint NOT NULL,
    assistido_id bigint NOT NULL,
    turma_id bigint,
    data_inicio timestamp without time zone DEFAULT now() NOT NULL,
    data_fim timestamp without time zone,
    criado_em timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: assistido_turma_historico_historico_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.assistido_turma_historico ALTER COLUMN historico_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.assistido_turma_historico_historico_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: aula; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.aula (
    aula_id bigint NOT NULL,
    titulo character varying(150),
    descricao text,
    data_aula date NOT NULL,
    horario_inicio time without time zone,
    horario_fim time without time zone,
    conteudo_previsto text,
    conteudo_ministrado text,
    objetivos text,
    recursos_necessarios text,
    status_aula character varying(20) DEFAULT 'PLANEJADA'::character varying NOT NULL,
    observacoes text,
    criado_em timestamp without time zone NOT NULL,
    atualizado_em timestamp without time zone NOT NULL,
    turma_id bigint
);


--
-- Name: aula_aula_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

CREATE SEQUENCE adra.aula_aula_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aula_aula_id_seq; Type: SEQUENCE OWNED BY; Schema: adra; Owner: -
--

ALTER SEQUENCE adra.aula_aula_id_seq OWNED BY adra.aula.aula_id;


--
-- Name: excecao_calendario; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.excecao_calendario (
    excecao_id bigint NOT NULL,
    data_excecao date NOT NULL,
    tipo character varying(30) NOT NULL,
    descricao character varying(150),
    criado_em timestamp without time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: excecao_calendario_excecao_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.excecao_calendario ALTER COLUMN excecao_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.excecao_calendario_excecao_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: invitacao_usuario; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.invitacao_usuario (
    invitacao_id bigint NOT NULL,
    email character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    validade timestamp without time zone NOT NULL,
    consumido boolean DEFAULT false,
    consumido_em timestamp without time zone,
    criado_em timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: invitacao_usuario_invitacao_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

CREATE SEQUENCE adra.invitacao_usuario_invitacao_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invitacao_usuario_invitacao_id_seq; Type: SEQUENCE OWNED BY; Schema: adra; Owner: -
--

ALTER SEQUENCE adra.invitacao_usuario_invitacao_id_seq OWNED BY adra.invitacao_usuario.invitacao_id;


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
-- Name: modulo; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.modulo (
    modulo_id bigint NOT NULL,
    codigo character varying(50) NOT NULL,
    nome_exibicao character varying(100) NOT NULL,
    rota character varying(150),
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp with time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: modulo_modulo_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.modulo ALTER COLUMN modulo_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.modulo_modulo_id_seq
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
-- Name: nivel_permissao_modulo; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.nivel_permissao_modulo (
    nivel_permissao_id bigint NOT NULL,
    modulo_id bigint NOT NULL,
    ordem integer DEFAULT 0 NOT NULL,
    eh_padrao boolean DEFAULT false NOT NULL
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
-- Name: oficina; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.oficina (
    oficina_id bigint NOT NULL,
    nome_oficina character varying(100) NOT NULL,
    oficineiro_responsavel character varying(150),
    ativo boolean DEFAULT true NOT NULL,
    criado_em timestamp without time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp without time zone DEFAULT now() NOT NULL,
    turma_id bigint,
    oficineiro_id bigint
);


--
-- Name: oficina_oficina_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

ALTER TABLE adra.oficina ALTER COLUMN oficina_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME adra.oficina_oficina_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: presenca; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.presenca (
    presenca_id bigint NOT NULL,
    aula_id bigint NOT NULL,
    assistido_id bigint NOT NULL,
    status_presenca character varying(20) NOT NULL,
    justificativa_falta text,
    observacao_do_dia text,
    horario_registro timestamp without time zone NOT NULL
);


--
-- Name: presenca_presenca_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

CREATE SEQUENCE adra.presenca_presenca_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: presenca_presenca_id_seq; Type: SEQUENCE OWNED BY; Schema: adra; Owner: -
--

ALTER SEQUENCE adra.presenca_presenca_id_seq OWNED BY adra.presenca.presenca_id;


--
-- Name: responsavel; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.responsavel (
    responsavel_id bigint NOT NULL,
    nome_completo character varying(180) NOT NULL,
    data_nascimento date,
    cpf character varying(20) NOT NULL,
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
-- Name: turma; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.turma (
    turma_id integer NOT NULL,
    projeto_id integer,
    nome_turma character varying(100) NOT NULL,
    turno character varying(20) NOT NULL,
    faixa_etaria character varying(50),
    capacidade integer NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    observacoes text,
    criado_em timestamp without time zone DEFAULT now() NOT NULL,
    atualizado_em timestamp without time zone DEFAULT now() NOT NULL,
    oficina_id bigint,
    oficineiro_responsavel character varying(150),
    oficineiro_id bigint,
    horario_inicio time without time zone,
    horario_fim time without time zone,
    CONSTRAINT turmas_capacidade_check CHECK ((capacidade > 0))
);


--
-- Name: turma_dia_semana; Type: TABLE; Schema: adra; Owner: -
--

CREATE TABLE adra.turma_dia_semana (
    turma_id bigint NOT NULL,
    dia_semana character varying(20) NOT NULL
);


--
-- Name: turmas_turma_id_seq; Type: SEQUENCE; Schema: adra; Owner: -
--

CREATE SEQUENCE adra.turmas_turma_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: turmas_turma_id_seq; Type: SEQUENCE OWNED BY; Schema: adra; Owner: -
--

ALTER SEQUENCE adra.turmas_turma_id_seq OWNED BY adra.turma.turma_id;


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
    auth_uid uuid,
    especialidade adra.especialidade_saude
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
-- Name: aula aula_id; Type: DEFAULT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.aula ALTER COLUMN aula_id SET DEFAULT nextval('adra.aula_aula_id_seq'::regclass);


--
-- Name: invitacao_usuario invitacao_id; Type: DEFAULT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.invitacao_usuario ALTER COLUMN invitacao_id SET DEFAULT nextval('adra.invitacao_usuario_invitacao_id_seq'::regclass);


--
-- Name: presenca presenca_id; Type: DEFAULT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.presenca ALTER COLUMN presenca_id SET DEFAULT nextval('adra.presenca_presenca_id_seq'::regclass);


--
-- Name: turma turma_id; Type: DEFAULT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma ALTER COLUMN turma_id SET DEFAULT nextval('adra.turmas_turma_id_seq'::regclass);


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
-- Name: assistido_turma_historico assistido_turma_historico_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_turma_historico
    ADD CONSTRAINT assistido_turma_historico_pkey PRIMARY KEY (historico_id);


--
-- Name: aula aula_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.aula
    ADD CONSTRAINT aula_pkey PRIMARY KEY (aula_id);


--
-- Name: excecao_calendario excecao_calendario_data_excecao_key; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.excecao_calendario
    ADD CONSTRAINT excecao_calendario_data_excecao_key UNIQUE (data_excecao);


--
-- Name: excecao_calendario excecao_calendario_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.excecao_calendario
    ADD CONSTRAINT excecao_calendario_pkey PRIMARY KEY (excecao_id);


--
-- Name: invitacao_usuario invitacao_usuario_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.invitacao_usuario
    ADD CONSTRAINT invitacao_usuario_pkey PRIMARY KEY (invitacao_id);


--
-- Name: invitacao_usuario invitacao_usuario_token_key; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.invitacao_usuario
    ADD CONSTRAINT invitacao_usuario_token_key UNIQUE (token);


--
-- Name: log_auditoria log_auditoria_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.log_auditoria
    ADD CONSTRAINT log_auditoria_pkey PRIMARY KEY (log_auditoria_id);


--
-- Name: modulo modulo_codigo_key; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.modulo
    ADD CONSTRAINT modulo_codigo_key UNIQUE (codigo);


--
-- Name: modulo modulo_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.modulo
    ADD CONSTRAINT modulo_pkey PRIMARY KEY (modulo_id);


--
-- Name: nivel_permissao_modulo nivel_permissao_modulo_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.nivel_permissao_modulo
    ADD CONSTRAINT nivel_permissao_modulo_pkey PRIMARY KEY (nivel_permissao_id, modulo_id);


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
-- Name: oficina oficina_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.oficina
    ADD CONSTRAINT oficina_pkey PRIMARY KEY (oficina_id);


--
-- Name: presenca presenca_aula_id_assistido_id_key; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.presenca
    ADD CONSTRAINT presenca_aula_id_assistido_id_key UNIQUE (aula_id, assistido_id);


--
-- Name: presenca presenca_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.presenca
    ADD CONSTRAINT presenca_pkey PRIMARY KEY (presenca_id);


--
-- Name: responsavel responsavel_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.responsavel
    ADD CONSTRAINT responsavel_pkey PRIMARY KEY (responsavel_id);


--
-- Name: turma_dia_semana turma_dia_semana_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma_dia_semana
    ADD CONSTRAINT turma_dia_semana_pkey PRIMARY KEY (turma_id, dia_semana);


--
-- Name: turma turmas_pkey; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma
    ADD CONSTRAINT turmas_pkey PRIMARY KEY (turma_id);


--
-- Name: responsavel uq_responsavel_cpf; Type: CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.responsavel
    ADD CONSTRAINT uq_responsavel_cpf UNIQUE (cpf);


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
-- Name: assistido_nome_nascimento_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_nome_nascimento_idx ON adra.assistido USING btree (nome_completo, data_nascimento);


--
-- Name: assistido_status_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_status_idx ON adra.assistido USING btree (status);


--
-- Name: assistido_turma_historico_aberto_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_turma_historico_aberto_idx ON adra.assistido_turma_historico USING btree (assistido_id) WHERE (data_fim IS NULL);


--
-- Name: assistido_turma_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX assistido_turma_idx ON adra.assistido USING btree (turma_id);


--
-- Name: assistido_um_principal_uk; Type: INDEX; Schema: adra; Owner: -
--

CREATE UNIQUE INDEX assistido_um_principal_uk ON adra.assistido_responsavel USING btree (assistido_id) WHERE (responsavel_principal = true);


--
-- Name: excecao_calendario_data_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX excecao_calendario_data_idx ON adra.excecao_calendario USING btree (data_excecao);


--
-- Name: idx_invitacao_email; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX idx_invitacao_email ON adra.invitacao_usuario USING btree (email);


--
-- Name: idx_invitacao_token; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX idx_invitacao_token ON adra.invitacao_usuario USING btree (token);


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
-- Name: oficina_nome_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX oficina_nome_idx ON adra.oficina USING btree (lower((nome_oficina)::text));


--
-- Name: responsavel_nome_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX responsavel_nome_idx ON adra.responsavel USING btree (nome_completo);


--
-- Name: turma_dia_semana_dia_idx; Type: INDEX; Schema: adra; Owner: -
--

CREATE INDEX turma_dia_semana_dia_idx ON adra.turma_dia_semana USING btree (dia_semana);


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
-- Name: excecao_calendario excecao_calendario_atualizado_em_trigger; Type: TRIGGER; Schema: adra; Owner: -
--

CREATE TRIGGER excecao_calendario_atualizado_em_trigger BEFORE UPDATE ON adra.excecao_calendario FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();


--
-- Name: assistido trg_atualizado_em; Type: TRIGGER; Schema: adra; Owner: -
--

CREATE TRIGGER trg_atualizado_em BEFORE UPDATE ON adra.assistido FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();


--
-- Name: nivel_permissao trg_atualizado_em; Type: TRIGGER; Schema: adra; Owner: -
--

CREATE TRIGGER trg_atualizado_em BEFORE UPDATE ON adra.nivel_permissao FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();


--
-- Name: responsavel trg_atualizado_em; Type: TRIGGER; Schema: adra; Owner: -
--

CREATE TRIGGER trg_atualizado_em BEFORE UPDATE ON adra.responsavel FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();


--
-- Name: usuario trg_atualizado_em; Type: TRIGGER; Schema: adra; Owner: -
--

CREATE TRIGGER trg_atualizado_em BEFORE UPDATE ON adra.usuario FOR EACH ROW EXECUTE FUNCTION adra.set_atualizado_em();


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
-- Name: assistido_turma_historico assistido_turma_historico_assistido_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_turma_historico
    ADD CONSTRAINT assistido_turma_historico_assistido_id_fkey FOREIGN KEY (assistido_id) REFERENCES adra.assistido(assistido_id) ON DELETE CASCADE;


--
-- Name: assistido_turma_historico assistido_turma_historico_turma_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido_turma_historico
    ADD CONSTRAINT assistido_turma_historico_turma_id_fkey FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id) ON DELETE SET NULL;


--
-- Name: assistido assistido_turma_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.assistido
    ADD CONSTRAINT assistido_turma_id_fkey FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id) ON DELETE SET NULL;


--
-- Name: log_auditoria log_auditoria_usuario_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.log_auditoria
    ADD CONSTRAINT log_auditoria_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES adra.usuario(usuario_id) ON DELETE SET NULL;


--
-- Name: nivel_permissao_modulo nivel_permissao_modulo_modulo_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.nivel_permissao_modulo
    ADD CONSTRAINT nivel_permissao_modulo_modulo_id_fkey FOREIGN KEY (modulo_id) REFERENCES adra.modulo(modulo_id) ON DELETE CASCADE;


--
-- Name: nivel_permissao_modulo nivel_permissao_modulo_nivel_permissao_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.nivel_permissao_modulo
    ADD CONSTRAINT nivel_permissao_modulo_nivel_permissao_id_fkey FOREIGN KEY (nivel_permissao_id) REFERENCES adra.nivel_permissao(nivel_permissao_id) ON DELETE CASCADE;


--
-- Name: oficina oficina_oficineiro_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.oficina
    ADD CONSTRAINT oficina_oficineiro_id_fkey FOREIGN KEY (oficineiro_id) REFERENCES adra.usuario(usuario_id);


--
-- Name: oficina oficina_turma_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.oficina
    ADD CONSTRAINT oficina_turma_id_fkey FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id);


--
-- Name: presenca presenca_assistido_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.presenca
    ADD CONSTRAINT presenca_assistido_id_fkey FOREIGN KEY (assistido_id) REFERENCES adra.assistido(assistido_id);


--
-- Name: presenca presenca_aula_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.presenca
    ADD CONSTRAINT presenca_aula_id_fkey FOREIGN KEY (aula_id) REFERENCES adra.aula(aula_id);


--
-- Name: turma_dia_semana turma_dia_semana_turma_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma_dia_semana
    ADD CONSTRAINT turma_dia_semana_turma_fkey FOREIGN KEY (turma_id) REFERENCES adra.turma(turma_id) ON DELETE CASCADE;


--
-- Name: turma turma_oficina_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma
    ADD CONSTRAINT turma_oficina_id_fkey FOREIGN KEY (oficina_id) REFERENCES adra.oficina(oficina_id);


--
-- Name: turma turma_oficineiro_id_fkey; Type: FK CONSTRAINT; Schema: adra; Owner: -
--

ALTER TABLE ONLY adra.turma
    ADD CONSTRAINT turma_oficineiro_id_fkey FOREIGN KEY (oficineiro_id) REFERENCES adra.usuario(usuario_id);


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
-- Name: aula; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.aula ENABLE ROW LEVEL SECURITY;

--
-- Name: excecao_calendario; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.excecao_calendario ENABLE ROW LEVEL SECURITY;

--
-- Name: invitacao_usuario; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.invitacao_usuario ENABLE ROW LEVEL SECURITY;

--
-- Name: log_auditoria; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.log_auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: modulo; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.modulo ENABLE ROW LEVEL SECURITY;

--
-- Name: nivel_permissao; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.nivel_permissao ENABLE ROW LEVEL SECURITY;

--
-- Name: nivel_permissao_modulo; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.nivel_permissao_modulo ENABLE ROW LEVEL SECURITY;

--
-- Name: oficina; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.oficina ENABLE ROW LEVEL SECURITY;

--
-- Name: presenca; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.presenca ENABLE ROW LEVEL SECURITY;

--
-- Name: responsavel; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.responsavel ENABLE ROW LEVEL SECURITY;

--
-- Name: turma; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.turma ENABLE ROW LEVEL SECURITY;

--
-- Name: turma_dia_semana; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.turma_dia_semana ENABLE ROW LEVEL SECURITY;

--
-- Name: usuario; Type: ROW SECURITY; Schema: adra; Owner: -
--

ALTER TABLE adra.usuario ENABLE ROW LEVEL SECURITY;


--
-- Data for Name: modulo; Type: TABLE DATA; Schema: adra; Owner: -
--

INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (1, 'USUARIOS', 'Usuários', '/usuarios', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (3, 'NOTIFICACOES', 'Notificações', '/notificacoes', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (4, 'DASHBOARD', 'Dashboard', '/dashboard', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (5, 'ASSISTIDOS', 'Assistidos', '/assistidos', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (6, 'OFICINAS', 'Oficinas', '/oficinas', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (7, 'TURMAS', 'Turmas', '/turmas', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (8, 'CHAMADA', 'Chamada', '/chamada', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (9, 'DISCIPLINAR', 'Disciplinar', '/disciplinar', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (10, 'PRONTUARIOS', 'Prontuários', '/prontuarios', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (11, 'EXPORTACAO', 'Exportação', '/exportacao', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (13, 'COMUNICADOS', 'Comunicados', '/comunicados', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (2, 'ACESSO', 'Acesso', '/acesso', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (15, 'RESPONSAVEIS', 'Responsáveis', '/responsaveis', true, '2026-09-13 14:07:40.039393+00', '2026-09-13 14:07:40.039393+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (12, 'MATERIAIS', 'Materiais', '/materiais', true, '2026-08-18 20:24:01.524969+00', '2026-08-18 20:24:01.524969+00');
INSERT INTO adra.modulo (modulo_id, codigo, nome_exibicao, rota, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (17, 'AULAS', 'Aulas', '/aulas', true, '2026-09-15 03:15:03.219844+00', '2026-09-15 03:15:03.219844+00');


--
-- Data for Name: nivel_permissao; Type: TABLE DATA; Schema: adra; Owner: -
--

INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (1, 'ADMINISTRADOR', 'Acesso completo ao sistema, usuarios e permissoes.', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');
INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (2, 'COORDENADOR', 'Gestao geral, oficinas, assistidos, disciplina, relatorios e comunicados.', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');
INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (3, 'SOCIOPEDAGOGICO', 'Chamada digital, frequencia e acompanhamento sociopedagogico.', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');
INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (4, 'OFICINEIRO', 'Acesso restrito (leitura) a planos de aula, materiais e comunicados da oficina.', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');
INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (5, 'PROFISSIONAL_SAUDE', 'Prontuarios especializados (neurologia, psicopedagogia, psicologia) — acesso por especialidade do profissional logado.', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');
INSERT INTO adra.nivel_permissao (nivel_permissao_id, nome, descricao, ativo, criado_em, atualizado_em) OVERRIDING SYSTEM VALUE VALUES (6, 'FINANCEIRO', 'Indicadores, relatorios e exportacao financeira (escopo a confirmar com ADRA).', true, '2026-07-20 23:26:10.035862+00', '2026-07-20 23:26:10.035862+00');


--
-- Data for Name: nivel_permissao_modulo; Type: TABLE DATA; Schema: adra; Owner: -
--

INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (1, 1, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (1, 2, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (1, 3, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 4, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 5, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 6, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 7, 4, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 8, 5, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 9, 6, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 10, 7, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 4, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 8, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 5, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (4, 12, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (4, 13, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (5, 4, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (5, 10, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (5, 5, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (6, 4, 1, true);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (6, 5, 2, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (6, 11, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 6, 4, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 15, 8, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 15, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (4, 7, 3, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (4, 17, 4, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (3, 17, 5, false);
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao) VALUES (2, 17, 9, true);


--
-- Name: modulo_modulo_id_seq; Type: SEQUENCE SET; Schema: adra; Owner: -
--

SELECT pg_catalog.setval('adra.modulo_modulo_id_seq', 17, true);


--
-- Name: nivel_permissao_nivel_permissao_id_seq; Type: SEQUENCE SET; Schema: adra; Owner: -
--

SELECT pg_catalog.setval('adra.nivel_permissao_nivel_permissao_id_seq', 12, true);
