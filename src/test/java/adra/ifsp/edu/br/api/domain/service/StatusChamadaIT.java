package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadasDoDiaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.StatusChamadaDiaDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusChamada;
import adra.ifsp.edu.br.api.domain.enums.TipoExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.ExcecaoCalendario;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.ExcecaoCalendarioRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FT-15: situação das chamadas por grupo e dia (relatório por período, aba
 * Pendentes e painel). Pendente = dia de chamada do grupo, até hoje, sem
 * feriado nem exceção do calendário e sem chamada lançada.
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
class StatusChamadaIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private ExcecaoCalendarioRepository excecaoCalendarioRepository;
    @Autowired private StatusChamadaService statusChamadaService;
    @Autowired private ChamadaService chamadaService;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();
    private static final LocalDate DIA_EXCECAO = anteriorSemFeriado(HOJE.minusDays(2));
    private static final LocalDate DIA_LANCADO = anteriorSemFeriado(DIA_EXCECAO.minusDays(1));
    private static final LocalDate DIA_PENDENTE = anteriorSemFeriado(DIA_LANCADO.minusDays(1));

    private Usuario coordenador;
    private Oficina oficina;
    private Turma turma;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
        }

        NivelPermissao nivel = nivelPermissaoRepository.save(
                NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build());
        coordenador = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel)
                .nomeCompleto("Coordenador Status")
                .email("coord-status@teste.local")
                .build());

        oficina = new Oficina();
        oficina.setNomeOficina("Oficina Status");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        turma = criarGrupo("SI Manhã A");
        fundarGrupoEm(turma, DIA_PENDENTE.minusDays(1));

        Chamada chamada = new Chamada();
        chamada.setTurma(turma);
        chamada.setDataChamada(DIA_LANCADO);
        chamada.setCriadoPor(coordenador);
        chamada.setAtualizadoPor(coordenador);
        chamadaRepository.save(chamada);

        ExcecaoCalendario excecao = new ExcecaoCalendario();
        excecao.setDataExcecao(DIA_EXCECAO);
        excecao.setTipo(TipoExcecaoCalendario.EVENTO_PASSEIO);
        excecao.setDescricao("Passeio no parque");
        excecaoCalendarioRepository.save(excecao);
    }

    @Test
    @DisplayName("Dia com chamada vem como lançada, com quem lançou")
    void diaLancado() {
        Optional<StatusChamadaDiaDTO> status = statusDoDia(turma, DIA_LANCADO);

        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(StatusChamada.LANCADA);
        assertThat(status.get().lancadaPorNome()).isEqualTo("Coordenador Status");
    }

    @Test
    @DisplayName("Dia de chamada passado sem lançamento vem como pendente")
    void diaSemLancamentoPendente() {
        assertThat(statusDoDia(turma, DIA_PENDENTE)).get()
                .extracting(StatusChamadaDiaDTO::status)
                .isEqualTo(StatusChamada.PENDENTE);
        assertThat(statusChamadaService.pendentes(DIA_PENDENTE, HOJE))
                .extracting(StatusChamadaDiaDTO::data)
                .contains(DIA_PENDENTE)
                .doesNotContain(DIA_LANCADO);
    }

    @Test
    @DisplayName("Feriado nacional e exceção do calendário não ficam pendentes")
    void feriadoEExcecaoNaoPendentes() {
        assertThat(statusDoDia(turma, DIA_EXCECAO)).isEmpty();
        assertThat(statusChamadaService.noPeriodo(DIA_PENDENTE, HOJE))
                .extracting(StatusChamadaDiaDTO::data)
                .noneMatch(FeriadosNacionais::ehFeriado);
    }

    @Test
    @DisplayName("Dia futuro não fica pendente")
    void futuroNaoPendente() {
        assertThat(statusChamadaService.noPeriodo(DIA_PENDENTE, HOJE.plusDays(7)))
                .extracting(StatusChamadaDiaDTO::data)
                .allMatch(dia -> !dia.isAfter(HOJE));
    }

    @Test
    @DisplayName("Grupo criado depois só cobra chamada a partir da criação")
    void grupoNovoNaoCobraDiasAnteriores() {
        Turma novo = criarGrupo("CJ Novo");
        fundarGrupoEm(novo, HOJE);

        assertThat(statusChamadaService.noPeriodo(DIA_PENDENTE, HOJE))
                .filteredOn(s -> s.turmaId().equals(novo.getTurmaId()))
                .extracting(StatusChamadaDiaDTO::data)
                .allMatch(HOJE::equals);
    }

    @Test
    @DisplayName("Chamadas do dia: em exceção do calendário não lista grupos e diz o motivo")
    void chamadasDoDiaComExcecao() {
        ChamadasDoDiaDTO dia = chamadaService.chamadasDoDia(DIA_EXCECAO);

        assertThat(dia.motivoSemChamada()).isEqualTo("Passeio no parque");
        assertThat(dia.grupos()).isEmpty();
    }

    private Optional<StatusChamadaDiaDTO> statusDoDia(Turma grupo, LocalDate dia) {
        return statusChamadaService.noPeriodo(DIA_PENDENTE, HOJE).stream()
                .filter(s -> s.turmaId().equals(grupo.getTurmaId()) && s.data().equals(dia))
                .findFirst();
    }

    private static LocalDate anteriorSemFeriado(LocalDate dia) {
        while (FeriadosNacionais.ehFeriado(dia)) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    private Turma criarGrupo(String nome) {
        Turma grupo = new Turma();
        grupo.setNomeTurma(nome);
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>(List.of(DayOfWeek.values())));
        return turmaRepository.save(grupo);
    }

    /** O grupo só cobra chamada a partir da criação, e o @PrePersist grava "agora": volta a data pelo banco. */
    private void fundarGrupoEm(Turma grupo, LocalDate data) {
        entityManager.flush();
        entityManager.createNativeQuery("update adra.turma set criado_em = :data where turma_id = :id")
                .setParameter("data", data.atStartOfDay())
                .setParameter("id", grupo.getTurmaId())
                .executeUpdate();
        entityManager.refresh(grupo);
    }
}
