CREATE TABLE adra.invitacao_usuario (
    invitacao_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    validade TIMESTAMP NOT NULL,
    consumido BOOLEAN DEFAULT FALSE,
    consumido_em TIMESTAMP,
    criado_em TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT fk_invitacao_email 
        FOREIGN KEY (email) REFERENCES adra.usuario(email) ON DELETE CASCADE
);

CREATE INDEX idx_invitacao_token ON adra.invitacao_usuario(token);
CREATE INDEX idx_invitacao_email ON adra.invitacao_usuario(email);