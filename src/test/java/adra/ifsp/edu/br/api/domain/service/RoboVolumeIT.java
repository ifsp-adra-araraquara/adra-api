package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaRoboDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADRAbot com volume de um ano real da ADRA (10 grupos × 25 atendidos, 3
 * chamadas por semana ≈ 1.560 chamadas e 39 mil presenças esperadas): a
 * frequência de 12 meses — a consulta mais pesada do robô — precisa acertar a
 * conta e responder rápido. Dados inventados, apagados no rollback.
 */
@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified",
        "spring.jpa.properties.hibernate.jdbc.batch_size=500"
})
@Transactional
class RoboVolumeIT {

    private static final int GRUPOS = 10;
    private static final int ATENDIDOS_POR_GRUPO = 25;
    private static final List<DayOfWeek> DIAS = List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    /** Generoso para runner de CI; em produção a meta é bem menor. */
    private static final long LIMITE_MS = 2_000;

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private RoboConsultaService roboService;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Frequência de 12 meses com volume de um ano: conta certa e abaixo do limite de tempo")
    void frequenciaDe12MesesComVolumeReal() throws Exception {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
        }
        Usuario coordenador = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivelPermissaoRepository.save(NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build()))
                .nomeCompleto("Coordenação Volume")
                .email("coord-volume@teste.local")
                .build());

        YearMonth ate = YearMonth.now().minusMonths(1);
        YearMonth de = ate.minusMonths(11);
        LocalDate inicio = de.atDay(1);
        LocalDate fim = ate.atEndOfMonth();

        long esperadosNaConta = 0;
        long ausenciasNaConta = 0;
        for (int g = 0; g < GRUPOS; g++) {
            Turma turma = new Turma();
            turma.setNomeTurma("Volume " + g);
            turma.setTurno(Turno.MANHA);
            turma.setCapacidade(30);
            turma.setDiasDaSemana(new ArrayList<>(DIAS));
            turma = turmaRepository.save(turma);

            List<Assistido> atendidos = new ArrayList<>();
            for (int a = 0; a < ATENDIDOS_POR_GRUPO; a++) {
                Assistido assistido = assistidoRepository.save(Assistido.builder()
                        .nomeCompleto("Atendido " + g + "-" + a)
                        .dataNascimento(LocalDate.of(2015, 1, 1).plusDays(a))
                        .dataEntrada(inicio.minusDays(1))
                        .status(StatusGeral.ATIVO)
                        .turma(turma)
                        .build());
                TurmaAlunos vinculo = new TurmaAlunos();
                vinculo.setTurma(turma);
                vinculo.setAssistido(assistido);
                vinculo.setDataEntrada(inicio.minusDays(1));
                vinculo.setStatus(StatusGeral.ATIVO);
                turmaAlunosRepository.save(vinculo);
                atendidos.add(assistido);
            }

            List<Presenca> faltas = new ArrayList<>();
            int numeroDaChamada = 0;
            for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
                if (!DIAS.contains(dia.getDayOfWeek())) continue;
                Chamada chamada = new Chamada();
                chamada.setTurma(turma);
                chamada.setDataChamada(dia);
                chamada.setCriadoPor(coordenador);
                chamada.setAtualizadoPor(coordenador);
                chamada = chamadaRepository.save(chamada);
                for (int a = 0; a < atendidos.size(); a++) {
                    esperadosNaConta++;
                    // ~14% de ausência: 1 em cada 7, metade justificada.
                    if ((a + numeroDaChamada) % 7 == 0) {
                        ausenciasNaConta++;
                        Presenca p = new Presenca();
                        p.setChamada(chamada);
                        p.setAssistido(atendidos.get(a));
                        p.setStatusPresenca(a % 2 == 0 ? StatusPresenca.FALTA : StatusPresenca.FALTA_JUSTIFICADA);
                        p.setCriadoPor(coordenador);
                        p.setAtualizadoPor(coordenador);
                        faltas.add(p);
                    }
                }
                numeroDaChamada++;
            }
            presencaRepository.saveAll(faltas);
            entityManager.flush();
            entityManager.clear();
            coordenador = usuarioRepository.findById(coordenador.getUsuarioId()).orElseThrow();
        }
        long antes = System.nanoTime();
        FrequenciaRoboDTO frequencia = roboService.frequencia(de, ate, null);
        long ms = (System.nanoTime() - antes) / 1_000_000;
        System.out.printf("%n===== ADRAbot volume: %d esperados, %d ausências, frequência 12 meses em %d ms =====%n",
                esperadosNaConta, ausenciasNaConta, ms);

        long esperados = frequencia.linhas().stream().mapToLong(FrequenciaRoboDTO.Linha::esperados).sum();
        long ausencias = frequencia.linhas().stream().mapToLong(l -> l.faltas() + l.justificadas()).sum();
        assertThat(esperados).isEqualTo(esperadosNaConta);
        assertThat(ausencias).isEqualTo(ausenciasNaConta);
        assertThat(frequencia.linhas()).hasSize(GRUPOS * 12);
        assertThat(ms).as("frequência de 12 meses com volume de um ano (ms)").isLessThan(LIMITE_MS);
    }
}
