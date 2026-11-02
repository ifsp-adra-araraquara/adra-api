package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.AtendidoChamadaDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.ChamadaGrupoDTO;
import adra.ifsp.edu.br.api.domain.dto.chamada.PresencaChamadaRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * FT-12: chamada por grupo e dia, service a service contra Postgres real
 * (Testcontainers). Cobre os critérios CA-FT12.1, .3, .4, .5 e .6 e as regras
 * que vinham da chamada por aula: desligado some depois da saída (CA-70) e
 * correção por data conforme o perfil (US-67).
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
class ChamadaServiceIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private ChamadaService chamadaService;
    @Autowired private AssistidoService assistidoService;
    @Autowired private VinculoTurmaService vinculoTurmaService;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario educador;
    private Oficina oficina;
    private Turma turma;
    private Assistido ana;
    private Assistido bruno;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft12@teste.local");
        educador = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "educador-ft12@teste.local");
        autenticarComo(coordenador);

        oficina = new Oficina();
        oficina.setNomeOficina("Oficina FT-12");
        oficina.setOficineiroResponsavel(coordenador);
        oficina = oficinaRepository.save(oficina);

        turma = criarGrupo("SI Manhã A", DayOfWeek.values());
        LocalDate entrada = HOJE.minusMonths(3);
        ana = matricular("Ana Chamada", turma, entrada, null);
        bruno = matricular("Bruno Chamada", turma, entrada, null);
    }

    @Test
    @DisplayName("CA-FT12.1: gravar de novo o mesmo dia atualiza a chamada, sem duplicar")
    void regravarAtualizaSemDuplicar() {
        LocalDate dia = diaSemFeriado(1);

        chamadaService.gravar(turma.getTurmaId(), dia, List.of(falta(ana)));
        ChamadaGrupoDTO regravada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(presente(ana), falta(bruno)));

        assertThat(chamadaRepository.findByTurmaAndDataChamada(turma, dia)).isPresent();
        assertThat(chamadaRepository.findByTurmaIn(List.of(turma))).hasSize(1);
        assertThat(status(regravada, ana)).isEqualTo(StatusPresenca.PRESENTE);
        assertThat(status(regravada, bruno)).isEqualTo(StatusPresenca.FALTA);
    }

    @Test
    @DisplayName("CA-FT12.3: a lista do dia traz quem estava no grupo naquela data")
    void listaSegueOVinculoNaData() {
        LocalDate dia = diaSemFeriado(10);
        Assistido entrouDepois = matricular("Carla Entrou Depois", turma, dia.plusDays(2), null);
        Assistido saiuDepois = matricular("Davi Saiu Depois", turma, dia.minusMonths(1), dia.plusDays(2));

        List<Long> atendidos = chamadaService.buscar(turma.getTurmaId(), dia).atendidos().stream()
                .map(AtendidoChamadaDTO::assistidoId).toList();

        assertThat(atendidos).contains(saiuDepois.getAssistidoId()).doesNotContain(entrouDepois.getAssistidoId());
    }

    @Test
    @DisplayName("CA-70: desligado some das chamadas depois da saída e não recebe lançamento")
    void desligadoSomeDepoisDaSaida() {
        LocalDate antesDaSaida = diaSemFeriado(10);
        LocalDate depoisDaSaida = diaSemFeriado(1);
        assistidoService.alterarStatus(ana.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, depoisDaSaida.minusDays(1), "Mudou de cidade"));

        assertThat(idsNaChamada(depoisDaSaida)).doesNotContain(ana.getAssistidoId());
        assertThat(idsNaChamada(antesDaSaida)).contains(ana.getAssistidoId());
        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), depoisDaSaida, List.of(falta(ana))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("CA-FT12.4: Educador Social grava só hoje; Coordenador grava qualquer dia passado")
    void edicaoPorPerfilEData() {
        assumeFalse(FeriadosNacionais.ehFeriado(HOJE), "hoje é feriado: não há chamada");
        LocalDate ontem = diaSemFeriado(1);

        autenticarComo(educador);
        assertThat(chamadaService.buscar(turma.getTurmaId(), ontem).podeEditar()).isFalse();
        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), ontem, List.of(falta(ana))))
                .isInstanceOf(AcessoNegadoException.class);
        assertThat(chamadaService.gravar(turma.getTurmaId(), HOJE, List.of(falta(ana))).lancada()).isTrue();

        autenticarComo(coordenador);
        assertThat(chamadaService.gravar(turma.getTurmaId(), ontem, List.of(falta(ana))).lancada()).isTrue();
    }

    @Test
    @DisplayName("Educador Social não grava dia fora da grade do grupo; Coordenador grava")
    void foraDaGradeSoCoordenador() {
        assumeFalse(FeriadosNacionais.ehFeriado(HOJE), "hoje é feriado: não há chamada");
        Turma soAmanha = criarGrupo("CJ A", HOJE.plusDays(1).getDayOfWeek());
        matricular("Eva Fora da Grade", soAmanha, HOJE.minusMonths(1), null);

        autenticarComo(educador);
        assertThatThrownBy(() -> chamadaService.gravar(soAmanha.getTurmaId(), HOJE, List.of()))
                .isInstanceOf(RegraNegocioException.class);

        autenticarComo(coordenador);
        assertThat(chamadaService.gravar(soAmanha.getTurmaId(), HOJE, List.of()).lancada()).isTrue();
    }

    @Test
    @DisplayName("CA-FT12.5: se um item é inválido, nada da chamada é gravado")
    void falhaNaoGravaNada() {
        LocalDate dia = diaSemFeriado(1);
        PresencaChamadaRequestDTO justificadaSemMotivo =
                new PresencaChamadaRequestDTO(bruno.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, null, null);

        assertThatThrownBy(() -> chamadaService.gravar(turma.getTurmaId(), dia, List.of(falta(ana), justificadaSemMotivo)))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(chamadaRepository.findByTurmaAndDataChamada(turma, dia)).isEmpty();
        assertThat(presencaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Falta justificada com motivo é gravada e volta com o motivo")
    void faltaJustificadaComMotivo() {
        LocalDate dia = diaSemFeriado(1);
        PresencaChamadaRequestDTO justificada =
                new PresencaChamadaRequestDTO(ana.getAssistidoId(), StatusPresenca.FALTA_JUSTIFICADA, MotivoFalta.SAUDE, null);

        ChamadaGrupoDTO chamada = chamadaService.gravar(turma.getTurmaId(), dia, List.of(justificada));

        AtendidoChamadaDTO linha = linha(chamada, ana);
        assertThat(linha.statusPresenca()).isEqualTo(StatusPresenca.FALTA_JUSTIFICADA);
        assertThat(linha.motivoFalta()).isEqualTo(MotivoFalta.SAUDE);
    }

    @Test
    @DisplayName("CA-FT12.6: trocar de grupo deixa um vínculo ativo só")
    void trocaDeGrupoDeixaUmVinculoAtivo() {
        Turma outro = criarGrupo("SI Tarde", DayOfWeek.values());

        vinculoTurmaService.vincular(ana, outro);

        assertThat(turmaAlunosRepository.findByAssistidoAndStatusAndDataSaidaIsNull(ana, StatusGeral.ATIVO))
                .singleElement()
                .extracting(v -> v.getTurma().getTurmaId())
                .isEqualTo(outro.getTurmaId());
    }

    private List<Long> idsNaChamada(LocalDate dia) {
        return chamadaService.buscar(turma.getTurmaId(), dia).atendidos().stream()
                .map(AtendidoChamadaDTO::assistidoId).toList();
    }

    private static StatusPresenca status(ChamadaGrupoDTO chamada, Assistido assistido) {
        return linha(chamada, assistido).statusPresenca();
    }

    private static AtendidoChamadaDTO linha(ChamadaGrupoDTO chamada, Assistido assistido) {
        return chamada.atendidos().stream()
                .filter(a -> a.assistidoId().equals(assistido.getAssistidoId()))
                .findFirst().orElseThrow();
    }

    private static PresencaChamadaRequestDTO falta(Assistido assistido) {
        return new PresencaChamadaRequestDTO(assistido.getAssistidoId(), StatusPresenca.FALTA, null, null);
    }

    private static PresencaChamadaRequestDTO presente(Assistido assistido) {
        return new PresencaChamadaRequestDTO(assistido.getAssistidoId(), StatusPresenca.PRESENTE, null, null);
    }

    /** `diasAtras` dias antes de hoje, recuando mais se cair em feriado nacional (feriado não tem chamada). */
    private static LocalDate diaSemFeriado(int diasAtras) {
        LocalDate dia = HOJE.minusDays(diasAtras);
        while (FeriadosNacionais.ehFeriado(dia)) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    private Turma criarGrupo(String nome, DayOfWeek... dias) {
        Turma grupo = new Turma();
        grupo.setOficina(oficina);
        grupo.setOficineiroResponsavel(coordenador);
        grupo.setNomeTurma(nome);
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>(List.of(dias)));
        return turmaRepository.save(grupo);
    }

    private Assistido matricular(String nome, Turma grupo, LocalDate entrada, LocalDate saida) {
        Assistido assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(LocalDate.of(2015, 1, 1))
                .dataEntrada(entrada)
                .status(StatusGeral.ATIVO)
                .turma(grupo)
                .build());
        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(grupo);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(entrada);
        vinculo.setDataSaida(saida);
        vinculo.setStatus(saida == null ? StatusGeral.ATIVO : StatusGeral.INATIVO);
        turmaAlunosRepository.save(vinculo);
        return assistido;
    }

    private Usuario criarUsuario(NomeNivelPermissao perfil, String email) {
        NivelPermissao nivel = nivelPermissaoRepository.save(NivelPermissao.builder().nome(perfil).build());
        return usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel)
                .nomeCompleto("Usuário " + perfil.name())
                .email(email)
                .build());
    }

    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste-" + usuario.getUsuarioId())
                .header("alg", "none")
                .subject(usuario.getUsuarioId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("perfil", usuario.getNivelPermissao().getNome().name())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name()))));
    }
}
