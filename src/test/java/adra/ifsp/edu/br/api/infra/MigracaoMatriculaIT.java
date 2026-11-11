package adra.ifsp.edu.br.api.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Roda as migrations reais (Flyway), não o schema gerado pelas entidades:
 * banco no estado de produção antes da matrícula, com dados, e depois a
 * migration da matrícula por cima — confere a carga de 2026.
 */
class MigracaoMatriculaIT {

    /** Última migration antes da matrícula anual. */
    private static final String ANTES_DA_MATRICULA = "20261108193917";

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    private static JdbcTemplate jdbc;
    private static long ativoId;
    private static long inativoId;
    private static long preCadastroId;

    @BeforeAll
    static void migrarComDados() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flyway(dataSource, ANTES_DA_MATRICULA).migrate();

        long turmaId = jdbc.queryForObject("""
                insert into adra.turma (nome_turma, turno, capacidade) values ('Grupo Legado', 'MANHA', 20)
                returning turma_id""", Long.class);
        ativoId = jdbc.queryForObject("""
                insert into adra.assistido (nome_completo, data_nascimento, data_entrada, status, turma_id,
                                            aceite_lgpd, aceite_lgpd_em, autoriza_uso_imagem, pode_ir_embora_sozinho)
                values ('Atendido Ativo', '2016-01-01', '2026-11-01', 'ATIVO', ?, true, now(), true, true)
                returning assistido_id""", Long.class, turmaId);
        inativoId = jdbc.queryForObject("""
                insert into adra.assistido (nome_completo, data_nascimento, data_entrada, status)
                values ('Atendido Desligado', '2015-01-01', '2026-11-01', 'INATIVO')
                returning assistido_id""", Long.class);
        jdbc.update("insert into adra.turma_aluno (turma_id, assistido_id, data_entrada) values (?, ?, '2026-11-01')",
                turmaId, ativoId);
        preCadastroId = jdbc.queryForObject("""
                insert into adra.pre_cadastro (nome_completo, responsavel_nome, status, assistido_id)
                values ('Atendido Ativo', 'Mãe', 'CONVERTIDO', ?)
                returning pre_cadastro_id""", Long.class, ativoId);

        for (String categoria : new String[] {"DOCUMENTO_IDENTIDADE", "FOTO_PERFIL"}) {
            jdbc.update("""
                    insert into adra.arquivo (entidade_tipo, entidade_id, categoria, provedor, chave, mime_type,
                                              tamanho_bytes, hash_sha256)
                    values ('ASSISTIDO', ?, ?, 'SUPABASE', ?, 'image/jpeg', 10, repeat('a', 64))""",
                    ativoId, categoria, "assistido/" + categoria.toLowerCase() + "/x.jpg");
        }

        flyway(dataSource, "latest").migrate();
    }

    @AfterAll
    static void parar() {
        POSTGRES.stop();
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String alvo) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("adra")
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .target(alvo)
                .load();
    }

    @Test
    @DisplayName("Todo atendido existente ganha a matrícula de 2026 com os termos que estavam na ficha")
    void cargaDe2026ComTermos() {
        Map<String, Object> ativo = jdbc.queryForMap(
                "select * from adra.matricula where assistido_id = ?", ativoId);
        assertThat(((Number) ativo.get("ano")).intValue()).isEqualTo(2026);
        assertThat(ativo.get("status")).isEqualTo("ATIVA");
        assertThat(ativo.get("aceite_lgpd")).isEqualTo(true);
        assertThat(ativo.get("aceite_lgpd_em")).isNotNull();
        assertThat(ativo.get("autoriza_uso_imagem")).isEqualTo(true);
        assertThat(ativo.get("pode_ir_embora_sozinho")).isEqualTo(true);
        assertThat(ativo.get("autoriza_eventos_passeios")).isEqualTo(false);
        assertThat(((Number) ativo.get("pre_cadastro_id")).longValue()).isEqualTo(preCadastroId);

        Map<String, Object> inativo = jdbc.queryForMap(
                "select * from adra.matricula where assistido_id = ?", inativoId);
        assertThat(inativo.get("status")).isEqualTo("DESLIGADA");
        assertThat(inativo.get("pre_cadastro_id")).isNull();
    }

    @Test
    @DisplayName("A ficha aponta para a matrícula atual e o vínculo do grupo fica na matrícula de 2026")
    void ponteirosPreenchidos() {
        Long matriculaAtiva = jdbc.queryForObject(
                "select matricula_id from adra.matricula where assistido_id = ?", Long.class, ativoId);

        assertThat(jdbc.queryForObject("select matricula_atual_id from adra.assistido where assistido_id = ?",
                Long.class, ativoId)).isEqualTo(matriculaAtiva);
        assertThat(jdbc.queryForObject("select matricula_id from adra.turma_aluno where assistido_id = ?",
                Long.class, ativoId)).isEqualTo(matriculaAtiva);
        assertThat(jdbc.queryForObject(
                "select count(*) from adra.assistido where matricula_atual_id is null", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Pré-cadastros existentes ficam no ano em que chegaram; documentos do atendido vão para a matrícula de 2026")
    void anoDoPreCadastroEDocumentos() {
        assertThat(jdbc.queryForObject("select ano_referencia from adra.pre_cadastro where pre_cadastro_id = ?",
                Integer.class, preCadastroId)).isEqualTo(java.time.LocalDate.now().getYear());

        Long matricula = jdbc.queryForObject(
                "select matricula_id from adra.matricula where assistido_id = ?", Long.class, ativoId);
        assertThat(jdbc.queryForObject(
                "select matricula_id from adra.arquivo where entidade_id = ? and categoria = 'DOCUMENTO_IDENTIDADE'",
                Long.class, ativoId)).isEqualTo(matricula);
        assertThat(jdbc.queryForObject(
                "select matricula_id from adra.arquivo where entidade_id = ? and categoria = 'FOTO_PERFIL'",
                Long.class, ativoId)).isNull();
    }

    @Test
    @DisplayName("Matrícula com documento não pode ser apagada antes dos arquivos (nada fica órfão no Storage)")
    void matriculaComArquivoNaoSomeSozinha() {
        Long matricula = jdbc.queryForObject(
                "select matricula_id from adra.matricula where assistido_id = ?", Long.class, ativoId);
        assertThatThrownBy(() -> jdbc.update("delete from adra.matricula where matricula_id = ?", matricula))
                .hasMessageContaining("arquivo");
    }

    @Test
    @DisplayName("Matrícula repetida no mesmo ano é barrada pelo banco")
    void unicaPorAno() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into adra.matricula (assistido_id, ano) values (?, 2026)", ativoId))
                .hasMessageContaining("uq_matricula_assistido_ano");
    }
}
