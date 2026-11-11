package adra.ifsp.edu.br.api.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusMatricula;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Matricula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.MatriculaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import jakarta.persistence.EntityManager;

/**
 * Matrícula por ano (PR 1): o cadastro abre a matrícula do ano, os termos
 * passam a morar nela e o vínculo com o grupo fica ligado a ela.
 */
@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
@Transactional
class MatriculaIT {

    @Autowired private AssistidoService assistidoService;
    @Autowired private MatriculaRepository matriculaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    private final int anoCorrente = LocalDate.now().getYear();
    private Turma grupo;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.log_auditoria alter column data_hora set default now()");

        Turma novo = new Turma();
        novo.setNomeTurma("Grupo Matrícula");
        novo.setTurno(Turno.MANHA);
        novo.setCapacidade(20);
        novo.setDiasDaSemana(new ArrayList<>());
        grupo = turmaRepository.save(novo);
    }

    @Test
    @DisplayName("Cadastro abre a matrícula do ano com os termos e liga o vínculo do grupo a ela")
    void cadastroAbreMatriculaDoAno() {
        AssistidoResponseDTO criado = assistidoService.cadastrar(dados("Ana Matrícula", grupo.getTurmaId(), true, true));
        sincronizar();

        List<Matricula> matriculas = matriculaRepository.findByAssistido_AssistidoIdOrderByAnoDesc(criado.assistidoId());
        assertThat(matriculas).hasSize(1);
        Matricula matricula = matriculas.getFirst();
        assertThat(matricula.getAno()).isEqualTo(anoCorrente);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(matricula.getPreCadastroId()).isNull();
        assertThat(matricula.isAceiteLgpd()).isTrue();
        assertThat(matricula.getAceiteLgpdEm()).isNotNull();
        assertThat(matricula.isAutorizaUsoImagem()).isTrue();
        assertThat(matricula.isPodeIrEmboraSozinho()).isTrue();
        assertThat(matricula.isAutorizaEventosPasseios()).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "select matricula_atual_id from adra.assistido where assistido_id = ?", Long.class, criado.assistidoId()))
                .isEqualTo(matricula.getMatriculaId());
        assertThat(jdbcTemplate.queryForObject(
                "select matricula_id from adra.turma_aluno where assistido_id = ? and data_saida is null",
                Long.class, criado.assistidoId()))
                .isEqualTo(matricula.getMatriculaId());

        // A ficha continua expondo os termos (agora os da matrícula atual).
        assertThat(criado.autorizaUsoImagem()).isTrue();
        assertThat(criado.podeIrEmboraSozinho()).isTrue();
        assertThat(assistidoService.buscarPorId(criado.assistidoId()).aceiteLgpd()).isTrue();
    }

    @Test
    @DisplayName("Editar a ficha corrige os termos da matrícula atual, sem abrir outra")
    void edicaoCorrigeTermosDaMatriculaAtual() {
        AssistidoResponseDTO criado = assistidoService.cadastrar(dados("Bia Matrícula", grupo.getTurmaId(), true, true));
        sincronizar();

        AssistidoResponseDTO editado = assistidoService.atualizar(criado.assistidoId(),
                edicao("Bia Matrícula", grupo.getTurmaId(), false, true));
        sincronizar();

        List<Matricula> matriculas = matriculaRepository.findByAssistido_AssistidoIdOrderByAnoDesc(criado.assistidoId());
        assertThat(matriculas).hasSize(1);
        assertThat(matriculas.getFirst().isAutorizaUsoImagem()).isFalse();
        assertThat(matriculas.getFirst().isPodeIrEmboraSozinho()).isTrue();
        assertThat(editado.autorizaUsoImagem()).isFalse();
    }

    @Test
    @DisplayName("Desligar fecha a matrícula do ano; reativar a devolve")
    void statusAcompanhaMatricula() {
        AssistidoResponseDTO criado = assistidoService.cadastrar(dados("Caio Matrícula", null, true, false));
        sincronizar();

        assistidoService.alterarStatus(criado.assistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, LocalDate.now(), "Mudou de cidade"));
        sincronizar();
        assertThat(statusDaMatricula(criado.assistidoId())).isEqualTo(StatusMatricula.DESLIGADA);

        assistidoService.alterarStatus(criado.assistidoId(), new AssistidoStatusRequestDTO(StatusGeral.ATIVO, null, null));
        sincronizar();
        assertThat(statusDaMatricula(criado.assistidoId())).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    @DisplayName("Trocar de grupo cria o novo vínculo na mesma matrícula do ano")
    void trocaDeGrupoNaMesmaMatricula() {
        AssistidoResponseDTO criado = assistidoService.cadastrar(dados("Davi Matrícula", grupo.getTurmaId(), true, false));
        sincronizar();

        Turma outro = new Turma();
        outro.setNomeTurma("Outro Grupo");
        outro.setTurno(Turno.TARDE);
        outro.setCapacidade(20);
        outro.setDiasDaSemana(new ArrayList<>());
        outro = turmaRepository.save(outro);

        assistidoService.atualizar(criado.assistidoId(), edicao("Davi Matrícula", outro.getTurmaId(), true, false));
        sincronizar();

        Long matriculaId = matriculaRepository.findByAssistido_AssistidoIdAndAno(criado.assistidoId(), anoCorrente)
                .orElseThrow().getMatriculaId();
        assertThat(jdbcTemplate.queryForList(
                "select matricula_id from adra.turma_aluno where assistido_id = ?", Long.class, criado.assistidoId()))
                .hasSize(2)
                .containsOnly(matriculaId);
    }

    private StatusMatricula statusDaMatricula(Long assistidoId) {
        return matriculaRepository.findByAssistido_AssistidoIdAndAno(assistidoId, anoCorrente).orElseThrow().getStatus();
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AssistidoRequestDTO dados(String nome, Long turmaId, boolean usoImagem, boolean irSozinho) {
        VinculoFamiliarComResponsavelRequestDTO mae = new VinculoFamiliarComResponsavelRequestDTO(
                "Mãe De " + nome, null, null, null, null, null, null,
                null, null, null, null, "Mãe", true, false, false, null);
        return requisicao(nome, turmaId, usoImagem, irSozinho, List.of(mae));
    }

    /** Edição sem mexer nos responsáveis (listas nulas = mantém como está). */
    private static AssistidoRequestDTO edicao(String nome, Long turmaId, boolean usoImagem, boolean irSozinho) {
        return requisicao(nome, turmaId, usoImagem, irSozinho, null);
    }

    private static AssistidoRequestDTO requisicao(String nome, Long turmaId, boolean usoImagem, boolean irSozinho,
                                                  List<VinculoFamiliarComResponsavelRequestDTO> responsaveis) {
        return new AssistidoRequestDTO(nome, LocalDate.of(2016, 5, 10), null, null, null, null, turmaId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                true, usoImagem, false, irSozinho, true,
                responsaveis, null);
    }
}
