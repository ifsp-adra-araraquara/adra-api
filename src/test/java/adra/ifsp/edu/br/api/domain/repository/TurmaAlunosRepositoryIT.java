package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração real (Testcontainers Postgres) do roster de chamada — CA-70.
 * Diferente do teste puro de AssistidoSpecificationTest, este exercita a
 * JPQL de verdade (findVinculosAtivosNaData) + o filtro em Java
 * (findElegiveisParaChamada) juntos, contra um Postgres real.
 * Schema gerado pelas entidades (ddl-auto), não pelas migrations do
 * Supabase — suficiente pra validar a query, não é teste de migration.
 */
@DataJpaTest
@Testcontainers
@Import(TurmaAlunosRepositoryIT.ContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "spring.flyway.enabled=false"
})
class TurmaAlunosRepositoryIT {

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            // O schema "adra" precisa existir ANTES do Hibernate criar as
            // tabelas (ddl-auto não cria schema, só tabelas dentro de um já
            // existente) — igual à migration V1 real, só que via init script.
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                    .withInitScript("create-schema-adra.sql");
        }
    }

    @Autowired
    private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired
    private TurmaRepository turmaRepository;
    @Autowired
    private OficinaRepository oficinaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired
    private AssistidoRepository assistidoRepository;
    @Autowired
    private javax.sql.DataSource dataSource;

    private Turma turma;
    private static final LocalDate DATA_AULA = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void montarTurma() throws java.sql.SQLException {
        // Usuario/Assistido usam @CreationTimestamp/@UpdateTimestamp com
        // insertable=false — na migration real do Supabase isso funciona
        // porque a coluna tem DEFAULT now() no banco; o ddl-auto do teste não
        // gera esse default sozinho, então completa aqui.
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
        }

        NivelPermissao nivel = nivelPermissaoRepository.save(
                NivelPermissao.builder().nome(NomeNivelPermissao.OFICINEIRO).build());
        Usuario oficineiro = usuarioRepository.save(
                Usuario.builder()
                        .nivelPermissao(nivel)
                        .nomeCompleto("Oficineiro Teste")
                        .email("oficineiro@teste.local")
                        .build());
        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Oficina Teste");
        oficina.setOficineiroResponsavel(oficineiro);
        oficina = oficinaRepository.save(oficina);

        turma = new Turma();
        turma.setOficina(oficina);
        turma.setOficineiroResponsavel(oficineiro);
        turma.setNomeTurma("Turma Teste");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma = turmaRepository.save(turma);
    }

    @Test
    @DisplayName("CA-70.1: desligado com data_saida antes da aula não entra no roster")
    void desligadoAntesDaAulaNaoAparece() {
        vincularAssistido("Ana Desligada", StatusGeral.INATIVO, LocalDate.of(2026, 9, 10));

        List<TurmaAlunos> roster = turmaAlunosRepository.findElegiveisParaChamada(turma, DATA_AULA);

        assertThat(roster).isEmpty();
    }

    @Test
    @DisplayName("Caso de borda: desligado no MESMO DIA da aula não aparece (decisão do CA-70.1)")
    void desligadoNoMesmoDiaNaoAparece() {
        vincularAssistido("Bia Saida Hoje", StatusGeral.INATIVO, DATA_AULA);

        List<TurmaAlunos> roster = turmaAlunosRepository.findElegiveisParaChamada(turma, DATA_AULA);

        assertThat(roster).isEmpty();
    }

    @Test
    @DisplayName("CA-70.2: desligado com data_saida depois da aula continua aparecendo (histórico)")
    void desligadoDepoisDaAulaContinuaAparecendo() {
        vincularAssistido("Caio Sai Amanha", StatusGeral.INATIVO, DATA_AULA.plusDays(1));

        List<TurmaAlunos> roster = turmaAlunosRepository.findElegiveisParaChamada(turma, DATA_AULA);

        assertThat(roster).extracting(v -> v.getAssistido().getNomeCompleto())
                .containsExactly("Caio Sai Amanha");
    }

    @Test
    @DisplayName("CA-70.4: assistido ativo aparece normalmente")
    void ativoAparece() {
        vincularAssistido("Duda Ativa", StatusGeral.ATIVO, null);

        List<TurmaAlunos> roster = turmaAlunosRepository.findElegiveisParaChamada(turma, DATA_AULA);

        assertThat(roster).extracting(v -> v.getAssistido().getNomeCompleto())
                .containsExactly("Duda Ativa");
    }

    private void vincularAssistido(String nome, StatusGeral status, LocalDate dataSaida) {
        Assistido assistido = Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(LocalDate.of(2015, 1, 1))
                .dataEntrada(LocalDate.of(2026, 1, 1))
                .status(status)
                .dataSaida(dataSaida)
                .turma(turma)
                .build();
        assistido = assistidoRepository.save(assistido);

        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(LocalDate.of(2026, 1, 1));
        vinculo.setStatus(StatusGeral.ATIVO); // vínculo com a turma continua aberto — quem fecha é o Assistido
        turmaAlunosRepository.save(vinculo);
    }
}
