-- Prazo de guarda (5 anos por matrícula) e limpeza do Storage.

-- Arquivo que deveria ter saído do Storage e não saiu (falha de rede, etc.).
-- A linha em adra.arquivo já foi apagada; só sobra a chave aqui até a próxima
-- tentativa. Nada fica órfão no bucket sem que o sistema saiba.
CREATE TABLE IF NOT EXISTS adra.arquivo_remocao_pendente (
    chave        varchar(500) PRIMARY KEY,
    provedor     varchar(20)  NOT NULL,
    tentativas   integer      NOT NULL DEFAULT 0,
    ultimo_erro  text,
    criado_em    timestamp with time zone NOT NULL DEFAULT now(),
    tentado_em   timestamp with time zone
);

ALTER TABLE adra.arquivo_remocao_pendente ENABLE ROW LEVEL SECURITY;

-- Cada descarte feito pelo Coordenador: só números, nenhum dado pessoal.
CREATE TABLE IF NOT EXISTS adra.descarte_dados (
    descarte_id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    executado_por_id       bigint REFERENCES adra.usuario (usuario_id),
    executado_em           timestamp with time zone NOT NULL DEFAULT now(),
    limite                 date    NOT NULL,
    matriculas             integer NOT NULL,
    atendidos              integer NOT NULL,
    responsaveis           integer NOT NULL,
    pre_cadastros          integer NOT NULL,
    arquivos               integer NOT NULL,
    bytes_liberados        bigint  NOT NULL
);

ALTER TABLE adra.descarte_dados ENABLE ROW LEVEL SECURITY;

-- O descarte filtra matrículas pela data de criação.
CREATE INDEX IF NOT EXISTS idx_matricula_criado_em ON adra.matricula (criado_em);
CREATE INDEX IF NOT EXISTS idx_pre_cadastro_criado_em ON adra.pre_cadastro (criado_em);
