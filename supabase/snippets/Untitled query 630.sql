-- migracao_cadastrar_modulo.sql

-- Catálogo de módulos, sem saber a quem pertence
CREATE TABLE adra.modulo (
    modulo_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo          VARCHAR(50) NOT NULL UNIQUE,
    nome_exibicao   VARCHAR(100) NOT NULL,
    rota            VARCHAR(150),
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tabela associativa: quais módulos cada nível de permissão vê, em que ordem, e qual é o padrão
CREATE TABLE adra.nivel_permissao_modulo (
    nivel_permissao_id BIGINT NOT NULL REFERENCES adra.nivel_permissao(nivel_permissao_id) ON DELETE CASCADE,
    modulo_id           BIGINT NOT NULL REFERENCES adra.modulo(modulo_id) ON DELETE CASCADE,
    ordem               INTEGER NOT NULL DEFAULT 0,
    eh_padrao           BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (nivel_permissao_id, modulo_id)
);

-- Seed dos módulos (cada um cadastrado só 1 vez, mesmo que use em vários perfis)
INSERT INTO adra.modulo (codigo, nome_exibicao, rota) VALUES
('USUARIOS', 'Usuários', '/usuarios'),
('ACESSO', 'Acesso', '/acesso'),
('NOTIFICACOES', 'Notificações', '/notificacoes'),
('DASHBOARD', 'Dashboard', '/dashboard'),
('ASSISTIDOS', 'Assistidos', '/assistidos'),
('OFICINAS', 'Oficinas', '/oficinas'),
('TURMAS', 'Turmas', '/turmas'),
('CHAMADA', 'Chamada', '/chamada'),
('DISCIPLINAR', 'Disciplinar', '/disciplinar'),
('PRONTUARIOS', 'Prontuários', '/prontuarios'),
('EXPORTACAO', 'Exportação', '/exportacao'),
('MATERIAIS', 'Materiais', '/materiais'),
('COMUNICADOS', 'Comunicados', '/comunicados');

-- Associações por perfil (usando os IDs reais do seu dump: 1=ADMINISTRADOR ... 6=FINANCEIRO)

-- ADMINISTRADOR (id 1)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 1, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('USUARIOS',1,true), ('ACESSO',2,false), ('NOTIFICACOES',3,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;

-- COORDENADOR (id 2)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 2, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('DASHBOARD',1,true), ('ASSISTIDOS',2,false), ('OFICINAS',3,false),
             ('TURMAS',4,false), ('CHAMADA',5,false), ('DISCIPLINAR',6,false),
             ('PRONTUARIOS',7,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;

-- SOCIOPEDAGOGICO (id 3)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 3, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('DASHBOARD',1,true), ('CHAMADA',2,false), ('ASSISTIDOS',3,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;

-- OFICINEIRO (id 4)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 4, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('MATERIAIS',1,true), ('COMUNICADOS',2,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;

-- PROFISSIONAL_SAUDE (id 5)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 5, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('DASHBOARD',1,true), ('PRONTUARIOS',2,false), ('ASSISTIDOS',3,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;

-- FINANCEIRO (id 6)
INSERT INTO adra.nivel_permissao_modulo (nivel_permissao_id, modulo_id, ordem, eh_padrao)
SELECT 6, m.modulo_id, x.ordem, x.eh_padrao
FROM adra.modulo m
JOIN (VALUES ('DASHBOARD',1,true), ('ASSISTIDOS',2,false), ('EXPORTACAO',3,false)) AS x(codigo, ordem, eh_padrao)
  ON m.codigo = x.codigo;