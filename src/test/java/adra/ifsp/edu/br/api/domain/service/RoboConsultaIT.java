package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.painel.PainelMensalDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.ContagemDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.DistribuicaoAtendidosDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FaltasSeguidasRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaDiaSemanaDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.FrequenciaRoboDTO;
import adra.ifsp.edu.br.api.domain.dto.robo.SocialResumoDTO;
import adra.ifsp.edu.br.api.domain.enums.DimensaoAtendidos;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.Endereco;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.OficinaTurma;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.web.controller.RoboConsultaController;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Robozinho ADRA: as consultas batem com o Painel, respeitam o escopo do
 * Oficineiro e o acesso por perfil, e o perfil social só devolve contagens.
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
class RoboConsultaIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private OficinaTurmaRepository oficinaTurmaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private MotivoDisciplinarRepository motivoRepository;
    @Autowired private DisciplinarService disciplinarService;
    @Autowired private PainelService painelService;
    @Autowired private RoboConsultaService roboService;
    @Autowired private RoboConsultaController roboController;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();
    private static final YearMonth MES = YearMonth.from(HOJE).minusMonths(1);

    private Usuario coordenador;
    private Usuario educadorSocial;
    private Usuario oficineiro;
    private Turma futebolA;
    private Turma teatroB;
    private Assistido ana;
    private Assistido bruno;
    private Assistido caio;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-robo@teste.local");
        educadorSocial = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "educ-robo@teste.local");
        oficineiro = criarUsuario(NomeNivelPermissao.OFICINEIRO, "ofic-robo@teste.local");
        autenticarComo(coordenador);

        futebolA = grupo("Grupo A - Futebol", oficina("Futebol", oficineiro));
        teatroB = grupo("Grupo B - Teatro", oficina("Teatro", coordenador));

        LocalDate entrada = MES.atDay(1).minusMonths(3);
        ana = matricular(futebolA, Assistido.builder().nomeCompleto("Ana Robo").sexo(Sexo.FEMININO)
                .dataNascimento(LocalDate.of(2015, 3, 3)).dataEntrada(entrada)
                .endereco(Endereco.builder().bairro("  jardim américa ").build())
                .rendaFamiliar(new BigDecimal("900.00")).recebeProgramaTransferencia(true)
                .programasTransferencia(Set.of(ProgramaTransferencia.BOLSA_FAMILIA)).pcd(true));
        bruno = matricular(futebolA, Assistido.builder().nomeCompleto("Bruno Robo").sexo(Sexo.MASCULINO)
                .dataNascimento(LocalDate.of(2013, 6, 6)).dataEntrada(entrada)
                .endereco(Endereco.builder().bairro("Jardim América").build())
                .rendaFamiliar(new BigDecimal("2500.00")).recebeProgramaTransferencia(false).pcd(false));
        caio = matricular(teatroB, Assistido.builder().nomeCompleto("Caio Robo").sexo(Sexo.MASCULINO)
                .dataNascimento(LocalDate.of(2012, 1, 1)).dataEntrada(entrada)
                .endereco(Endereco.builder().bairro("Centro").build()));
    }

    @Test
    @DisplayName("Frequência por grupo bate com o Painel (presença esparsa, FJ como ausência)")
    void frequenciaIgualAoPainel() {
        Chamada c1 = chamada(futebolA, MES.atDay(5));
        chamada(futebolA, MES.atDay(12));
        Chamada c3 = chamada(futebolA, MES.atDay(19));
        falta(c1, ana, StatusPresenca.FALTA);
        falta(c3, bruno, StatusPresenca.FALTA_JUSTIFICADA);
        chamada(teatroB, MES.atDay(6));
        entityManager.flush();

        FrequenciaRoboDTO robo = roboService.frequencia(MES, MES, null);
        PainelMensalDTO painel = painelService.mensal(MES, null);

        assertThat(robo.linhas())
                .extracting(FrequenciaRoboDTO.Linha::nomeTurma, FrequenciaRoboDTO.Linha::chamadas,
                        FrequenciaRoboDTO.Linha::esperados, FrequenciaRoboDTO.Linha::percentual)
                .containsExactly(tuple("Grupo A - Futebol", 3L, 6L, 67), tuple("Grupo B - Teatro", 1L, 1L, 100));
        for (FrequenciaRoboDTO.Linha linha : robo.linhas()) {
            assertThat(painel.turmas())
                    .filteredOn(t -> t.turmaId().equals(linha.turmaId()))
                    .singleElement()
                    .extracting(PainelMensalDTO.Turma::frequencia)
                    .isEqualTo(linha.percentual());
        }
        assertThat(robo.linhas().getFirst().oficinas()).isEqualTo("Futebol");
    }

    @Test
    @DisplayName("Oficineiro só vê a frequência dos grupos das oficinas dele")
    void oficineiroSoVeOsProprios() {
        chamada(futebolA, MES.atDay(5));
        chamada(teatroB, MES.atDay(6));
        entityManager.flush();
        autenticarComo(oficineiro);

        FrequenciaRoboDTO resposta = roboController.frequencia(MES, MES).getBody();

        assertThat(resposta).isNotNull();
        assertThat(resposta.linhas()).extracting(FrequenciaRoboDTO.Linha::turmaId)
                .containsExactly(futebolA.getTurmaId());
        assertThatThrownBy(() -> roboController.frequenciaPorDiaDaSemana(MES, MES, teatroB.getTurmaId()))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> roboController.social()).isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("Dia da semana: ausências (F e FJ) sobre esperados, por dia ISO")
    void frequenciaPorDiaDaSemana() {
        LocalDate dia = MES.atDay(5);
        Chamada c = chamada(futebolA, dia);
        falta(c, ana, StatusPresenca.FALTA);
        falta(c, bruno, StatusPresenca.FALTA_JUSTIFICADA);
        entityManager.flush();

        FrequenciaDiaSemanaDTO resposta = roboService.frequenciaPorDiaDaSemana(MES, MES, futebolA.getTurmaId(), null);

        assertThat(resposta.dias()).singleElement()
                .extracting(FrequenciaDiaSemanaDTO.Dia::diaSemana, FrequenciaDiaSemanaDTO.Dia::ausencias,
                        FrequenciaDiaSemanaDTO.Dia::percentual)
                .containsExactly(dia.getDayOfWeek().getValue(), 2L, 0);
    }

    @Test
    @DisplayName("Faltas seguidas trazem o grupo e respeitam o escopo do Oficineiro")
    void faltasSeguidas() {
        for (int i = 3; i >= 1; i--) {
            falta(chamada(futebolA, HOJE.minusDays(i)), ana, StatusPresenca.FALTA);
            falta(chamada(teatroB, HOJE.minusDays(i)), caio, StatusPresenca.FALTA);
        }
        entityManager.flush();

        FaltasSeguidasRoboDTO todos = roboService.faltasSeguidas(3, null);
        FaltasSeguidasRoboDTO doOficineiro = roboService.faltasSeguidas(3, oficineiro.getUsuarioId());

        assertThat(todos.itens()).extracting(FaltasSeguidasRoboDTO.Item::nome, FaltasSeguidasRoboDTO.Item::faltas)
                .containsExactlyInAnyOrder(tuple("Ana Robo", 3L), tuple("Caio Robo", 3L));
        assertThat(doOficineiro.itens()).extracting(FaltasSeguidasRoboDTO.Item::nome, FaltasSeguidasRoboDTO.Item::turma)
                .containsExactly(tuple("Ana Robo", "Grupo A - Futebol"));
        assertThat(doOficineiro.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("Distribuição: bairro normalizado, sexo e grupo contam os ativos de hoje")
    void distribuicao() {
        entityManager.flush();

        DistribuicaoAtendidosDTO bairro = roboService.distribuicao(DimensaoAtendidos.BAIRRO);
        DistribuicaoAtendidosDTO sexo = roboService.distribuicao(DimensaoAtendidos.SEXO);

        assertThat(bairro.ativos()).isEqualTo(3);
        assertThat(bairro.itens()).extracting(ContagemDTO::rotulo, ContagemDTO::total)
                .containsExactly(tuple("Jardim América", 2L), tuple("Centro", 1L));
        assertThat(sexo.itens()).extracting(ContagemDTO::rotulo, ContagemDTO::total)
                .containsExactly(tuple("MASCULINO", 2L), tuple("FEMININO", 1L));
    }

    @Test
    @DisplayName("Perfil social só devolve contagens; Educador Social não acessa")
    void perfilSocial() {
        entityManager.flush();

        SocialResumoDTO social = roboService.social();

        assertThat(social.ativos()).isEqualTo(3);
        assertThat(social.recebemPrograma()).isEqualTo(1);
        assertThat(social.programas()).extracting(ContagemDTO::rotulo).containsExactly("BOLSA_FAMILIA");
        assertThat(social.pcd()).isEqualTo(1);
        assertThat(social.rendaInformada()).isEqualTo(2);
        assertThat(social.faixasRenda()).extracting(ContagemDTO::rotulo, ContagemDTO::total)
                .containsExactly(tuple("Até R$ 1.000", 1L), tuple("De R$ 2.000,01 a R$ 3.000", 1L));

        autenticarComo(educadorSocial);
        assertThatThrownBy(() -> roboController.social()).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> roboController.distribuicao(DimensaoAtendidos.BAIRRO))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("Disciplinar do mês por grupo, com o mês anterior")
    void disciplinarDoMes() {
        MotivoDisciplinar briga = motivoRepository.save(
                MotivoDisciplinar.builder().nome("Briga").nivel(NivelDisciplinar.ADVERTENCIA).build());
        disciplinarService.registrar(bruno.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(null, null, briga.getMotivoId(), MES.atDay(10), "Fato", null, null, null));
        disciplinarService.registrar(bruno.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(null, null, briga.getMotivoId(), MES.atDay(1).minusDays(3), "Fato", null, null, null));
        entityManager.flush();

        var resumo = roboService.disciplinar(MES);

        assertThat(resumo.advertencias()).isEqualTo(1);
        assertThat(resumo.totalMesAnterior()).isEqualTo(1);
    }

    @Test
    @DisplayName("Busca por nome ignora acento e caixa e respeita o escopo do Oficineiro")
    void buscaPorNome() {
        entityManager.flush();

        assertThat(roboService.buscarAtendidos("CAIO", null))
                .extracting(adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO::nome)
                .containsExactly("Caio Robo");
        assertThat(roboService.buscarAtendidos("robo", oficineiro.getUsuarioId()))
                .extracting(adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO::nome)
                .containsExactly("Ana Robo", "Bruno Robo");
        assertThatThrownBy(() -> roboService.buscarAtendidos("a", null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Aniversariantes do mês, pelo dia, com a idade que vão fazer")
    void aniversariantes() {
        entityManager.flush();

        assertThat(roboService.aniversariantes(6, null))
                .extracting(adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO::nome,
                        adra.ifsp.edu.br.api.domain.dto.robo.AtendidoRoboDTO::detalhe)
                .containsExactly(tuple("Bruno Robo", (HOJE.getYear() - 2013) + " anos"));
    }

    @Test
    @DisplayName("Contexto: o Oficineiro só vê os grupos e as oficinas dele; oficina traz o responsável")
    void contexto() {
        entityManager.flush();

        var todos = roboService.contexto(null);
        var doOficineiro = roboService.contexto(oficineiro.getUsuarioId());

        assertThat(todos.grupos()).extracting(adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO.Opcao::nome)
                .containsExactly("Grupo A - Futebol", "Grupo B - Teatro");
        assertThat(doOficineiro.grupos()).extracting(adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO.Opcao::id)
                .containsExactly(futebolA.getTurmaId());
        assertThat(doOficineiro.oficinas()).singleElement()
                .extracting(adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO.Opcao::nome,
                        adra.ifsp.edu.br.api.domain.dto.robo.RoboContextoDTO.Opcao::detalhe)
                .containsExactly("Futebol", oficineiro.getNomeCompleto());
    }

    @Test
    @DisplayName("Movimento do ano: 12 meses, entradas, saídas e motivo de saída normalizado")
    void movimentoDoAno() {
        LocalDate saida = MES.atDay(10);
        assistidoRepository.save(Assistido.builder().nomeCompleto("Davi Saiu").dataNascimento(LocalDate.of(2014, 2, 2))
                .dataEntrada(saida.minusMonths(1)).dataSaida(saida).motivoSaida("  Mudou de Cidade ")
                .status(StatusGeral.INATIVO).build());
        entityManager.flush();

        var movimento = roboService.movimento(saida.getYear());

        assertThat(movimento.meses()).hasSize(12);
        assertThat(movimento.saidas()).isEqualTo(1);
        assertThat(movimento.motivos()).extracting(ContagemDTO::rotulo, ContagemDTO::total)
                .containsExactly(tuple("mudou de cidade", 1L));
        assertThat(movimento.ativosFim()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Toda dimensão de atendidos roda e conta os 3 ativos")
    void todasAsDimensoes() {
        entityManager.flush();

        for (DimensaoAtendidos por : DimensaoAtendidos.values()) {
            DistribuicaoAtendidosDTO d = roboService.distribuicao(por);
            assertThat(d.ativos()).as(por.name()).isEqualTo(3);
            assertThat(d.itens()).as(por.name()).isNotEmpty();
        }
        assertThat(roboService.distribuicao(DimensaoAtendidos.IDADE).itens())
                .allSatisfy(c -> assertThat(c.rotulo()).endsWith(" anos"));
        assertThat(roboService.distribuicao(DimensaoAtendidos.OFICINA).itens()).extracting(ContagemDTO::rotulo)
                .containsExactlyInAnyOrder("Futebol", "Teatro");
    }

    @Test
    @DisplayName("Um passo da próxima medida e dia da semana sem grupo rodam sem erro")
    void consultasSemFiltro() {
        entityManager.flush();

        assertThat(roboService.umPassoDaProximaMedida()).isEmpty();
        assertThat(roboService.frequenciaPorDiaDaSemana(MES, MES, null, null).dias()).isEmpty();
        assertThat(roboService.faltasSeguidas(null, null).limiar()).isEqualTo(IndicadorFrequencia.LIMIAR_FALTAS_CONSECUTIVAS);
    }

    @Test
    @DisplayName("Período acima de 12 meses ou invertido é recusado")
    void limitesDePeriodo() {
        assertThat(roboService.frequencia(MES.minusMonths(11), MES, null).linhas()).isEmpty();
        assertThatThrownBy(() -> roboService.frequencia(MES.minusMonths(12), MES, null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> roboService.frequencia(MES, MES.minusMonths(1), null))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> roboService.faltasSeguidas(1, null)).isInstanceOf(RegraNegocioException.class);
    }

    private Oficina oficina(String nome, Usuario responsavel) {
        Oficina oficina = new Oficina();
        oficina.setNomeOficina(nome);
        oficina.setOficineiroResponsavel(responsavel);
        return oficinaRepository.save(oficina);
    }

    private Turma grupo(String nome, Oficina oficina) {
        Turma turma = new Turma();
        turma.setNomeTurma(nome);
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma.setDiasDaSemana(new ArrayList<>(List.of(MES.atDay(5).getDayOfWeek())));
        turma = turmaRepository.save(turma);
        oficinaTurmaRepository.save(new OficinaTurma(oficina, turma));
        return turma;
    }

    private Assistido matricular(Turma turma, Assistido.AssistidoBuilder dados) {
        Assistido assistido = assistidoRepository.save(dados.status(StatusGeral.ATIVO).turma(turma).build());
        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(assistido.getDataEntrada());
        vinculo.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(vinculo);
        return assistido;
    }

    private Chamada chamada(Turma turma, LocalDate data) {
        Chamada chamada = new Chamada();
        chamada.setTurma(turma);
        chamada.setDataChamada(data);
        chamada.setCriadoPor(coordenador);
        chamada.setAtualizadoPor(coordenador);
        return chamadaRepository.save(chamada);
    }

    private void falta(Chamada chamada, Assistido assistido, StatusPresenca status) {
        Presenca presenca = new Presenca();
        presenca.setChamada(chamada);
        presenca.setAssistido(assistido);
        presenca.setStatusPresenca(status);
        presenca.setCriadoPor(coordenador);
        presenca.setAtualizadoPor(coordenador);
        presencaRepository.save(presenca);
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
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
