package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.EncerramentoPreviaDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.PeriodoMatriculaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Matricula;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import jakarta.persistence.EntityManager;

/**
 * Encerramento do ano (PR 3). Transacional: encerrar o ano corrente travaria
 * matrículas dos outros testes, então tudo é desfeito no fim de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
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
class EncerramentoAnoIT {

    private final int ano = LocalDate.now().getYear();
    private final LocalDate ultimoDia = LocalDate.of(ano, 12, 31);

    @Autowired private MockMvc mvc;
    @Autowired private AssistidoService assistidoService;
    @Autowired private MatriculaService matriculaService;
    @Autowired private VinculoTurmaService vinculoTurmaService;
    @Autowired private PeriodoMatriculaService periodoMatriculaService;
    @Autowired private EncerramentoAnoService encerramentoAnoService;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private adra.ifsp.edu.br.api.domain.repository.UsuarioRepository usuarioRepository;
    @Autowired private adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository nivelPermissaoRepository;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    private Turma grupoDoAno;
    private Turma grupoDoProximo;

    @BeforeEach
    void preparar() {
        jdbc.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbc.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        // Mesma regra da migration (o schema dos testes vem das entidades e não tem índices parciais).
        jdbc.execute("create unique index if not exists uq_turma_aluno_um_ativo_por_matricula"
                + " on adra.turma_aluno (assistido_id, coalesce(matricula_id, 0))"
                + " where status = 'ATIVO' and data_saida is null");
        grupoDoAno = grupo("Encerramento Atual");
        grupoDoProximo = grupo("Encerramento Próximo");
    }

    @Test
    @DisplayName("Encerrar: quem renovou segue no grupo novo, quem não renovou perde a vaga em 31/12, e o ano trava")
    void encerrarOAno() {
        Long renovou = cadastrar("Ana Renovou", grupoDoAno);
        Long naoRenovou = cadastrar("Bruno Não Renovou", grupoDoAno);
        Long desligado = cadastrar("Carla Saiu", grupoDoAno);
        assistidoService.alterarStatus(desligado,
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, LocalDate.of(ano, 6, 1), "Mudou de cidade"));
        abrirPeriodoDoProximoAno();
        rematricular(renovou, grupoDoProximo);
        sincronizar();

        EncerramentoPreviaDTO previa = encerramentoAnoService.previa(ano);
        assertThat(previa.podeEncerrar()).isTrue();
        assertThat(previa.quemNaoRenovou()).extracting(EncerramentoPreviaDTO.AtendidoNaoRenovouDTO::assistidoId)
                .contains(naoRenovou)
                .doesNotContain(renovou, desligado);

        var resultado = encerramentoAnoService.encerrar(ano);
        assertThat(resultado.totalNaoRenovaram()).isEqualTo(previa.naoRenovaram());
        assertThat(resultado.totalRenovaram()).isEqualTo(previa.renovaram());
        sincronizar();

        // Renovou: matrícula e grupo do ano seguinte passam a ser os atuais.
        Map<String, Object> ana = ficha(renovou);
        assertThat(ana.get("status")).isEqualTo("ATIVO");
        assertThat(((Number) ana.get("turma_id")).longValue()).isEqualTo(grupoDoProximo.getTurmaId());
        assertThat(anoDaMatriculaAtual(renovou)).isEqualTo(ano + 1);
        assertThat(statusDaMatricula(renovou, ano)).isEqualTo("ENCERRADA");
        assertThat(saidaDoVinculo(renovou, ano)).isEqualTo(ultimoDia);
        assertThat(saidaDoVinculo(renovou, ano + 1)).isNull();

        // Não renovou: perde a vaga no último dia do ano.
        Map<String, Object> bruno = ficha(naoRenovou);
        assertThat(bruno.get("status")).isEqualTo("INATIVO");
        assertThat(bruno.get("data_saida").toString()).isEqualTo(ultimoDia.toString());
        assertThat(bruno.get("motivo_saida")).asString().contains("Não renovou a matrícula para " + (ano + 1));
        assertThat(bruno.get("turma_id")).isNull();
        assertThat(statusDaMatricula(naoRenovou, ano)).isEqualTo("ENCERRADA");
        assertThat(saidaDoVinculo(naoRenovou, ano)).isEqualTo(ultimoDia);

        // Já tinha saído: nada muda na saída dele.
        Map<String, Object> carla = ficha(desligado);
        assertThat(carla.get("data_saida").toString()).isEqualTo(LocalDate.of(ano, 6, 1).toString());
        assertThat(statusDaMatricula(desligado, ano)).isEqualTo("DESLIGADA");

        // Ano travado: não encerra de novo nem aceita matrícula; cadastro novo vai para o ano seguinte.
        assertThatThrownBy(() -> encerramentoAnoService.encerrar(ano)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi encerrado");
        Assistido semMatricula = assistidoRepository.findById(naoRenovou).orElseThrow();
        assertThatThrownBy(() -> matriculaService.abrir(semMatricula, ano, dados("X", null), null))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(matriculaService.anoParaNovoCadastro()).isEqualTo(ano + 1);
        assertThat(encerramentoAnoService.pendente()).isEmpty();
    }

    @Test
    @DisplayName("Ano em curso só encerra depois de aberta a matrícula do ano seguinte")
    void naoEncerraSemRematricula() {
        cadastrar("Davi Sem Período", grupoDoAno);
        sincronizar();

        EncerramentoPreviaDTO previa = encerramentoAnoService.previa(ano);
        assertThat(previa.podeEncerrar()).isFalse();
        assertThat(previa.motivoBloqueio()).contains("Abra a matrícula de " + (ano + 1));
        assertThatThrownBy(() -> encerramentoAnoService.encerrar(ano)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> encerramentoAnoService.encerrar(ano + 1)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ainda não começou");
        // Durante o ano, sem a matrícula do seguinte aberta, não há aviso de encerramento.
        assertThat(encerramentoAnoService.pendente()).isEmpty();
    }

    @Test
    @DisplayName("Só o Coordenador encerra; a prévia pendente aparece para a equipe")
    void permissoes() throws Exception {
        cadastrar("Eva Permissão", grupoDoAno);
        abrirPeriodoDoProximoAno();
        sincronizar();

        mvc.perform(get("/api/matriculas/encerramento-pendente").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEncerrar").value(true));
        mvc.perform(post("/api/matriculas/" + ano + "/encerrar").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/matriculas/" + ano + "/encerrar").with(perfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(ano));
    }

    @Test
    @DisplayName("Encerrar arquiva o disciplinar do ano (sem mexer nas faltas), mantém suspensão que passa de 31/12 e cancela pedido de desligamento de quem saiu")
    void encerrarArquivaDisciplinarECancelaPedidos() {
        jdbc.execute("alter table adra.usuario alter column criado_em set default now()");
        jdbc.execute("alter table adra.usuario alter column atualizado_em set default now()");
        var nivel = nivelPermissaoRepository.findByNome(NomeNivelPermissao.COORDENADOR)
                .orElseGet(() -> nivelPermissaoRepository.save(
                        adra.ifsp.edu.br.api.domain.model.NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build()));
        var autor = usuarioRepository.save(adra.ifsp.edu.br.api.domain.model.Usuario.builder()
                .nivelPermissao(nivel).nomeCompleto("Coordenação Encerramento").email("encerramento@teste.local").build());

        Long renovou = cadastrar("Davi Renovou", grupoDoAno);
        Long naoRenovou = cadastrar("Elisa Não Renovou", grupoDoAno);
        abrirPeriodoDoProximoAno();
        rematricular(renovou, grupoDoProximo);
        sincronizar();

        // O banco dos testes é compartilhado entre classes: compara com o que já havia.
        EncerramentoPreviaDTO antes = encerramentoAnoService.previa(ano);
        long ocorrencia = registro(renovou, "OCORRENCIA", LocalDate.of(ano, 3, 10), null, null, null, autor.getUsuarioId());
        long advertencia = registro(renovou, "ADVERTENCIA", LocalDate.of(ano, 4, 2), null, null, "PENDENTE_ASSINATURA", autor.getUsuarioId());
        long suspensaoQuePassa = registro(naoRenovou, "SUSPENSAO", ultimoDia.minusDays(2), ultimoDia.minusDays(2),
                LocalDate.of(ano + 1, 1, 5), null, autor.getUsuarioId());
        jdbc.update("update adra.assistido set total_ocorrencias_ativas = 1, total_advertencias_ativas = 1 where assistido_id = ?", renovou);
        jdbc.update("""
                insert into adra.solicitacao_desligamento (assistido_id, solicitado_por_id, data_saida, motivo, status, criado_em)
                values (?, ?, ?, 'Pediu para sair', 'PENDENTE', now())""", naoRenovou, autor.getUsuarioId(), LocalDate.now());

        EncerramentoPreviaDTO previa = encerramentoAnoService.previa(ano);
        assertThat(previa.disciplinarArquivados() - antes.disciplinarArquivados()).isEqualTo(2);
        assertThat(previa.disciplinarDocumentosPendentes() - antes.disciplinarDocumentosPendentes()).isEqualTo(1);

        encerramentoAnoService.encerrar(ano);
        sincronizar();

        assertThat(arquivado(ocorrencia)).isTrue();
        assertThat(arquivado(advertencia)).isTrue();
        assertThat(jdbc.queryForObject("select arquivado_motivo from adra.registro_disciplinar where registro_id = ?",
                String.class, ocorrencia)).isEqualTo("Encerramento do ano de " + ano);
        assertThat(arquivado(suspensaoQuePassa)).isFalse();
        Map<String, Object> contadores = jdbc.queryForMap(
                "select total_ocorrencias_ativas, total_advertencias_ativas from adra.assistido where assistido_id = ?", renovou);
        assertThat(((Number) contadores.get("total_ocorrencias_ativas")).intValue()).isZero();
        assertThat(((Number) contadores.get("total_advertencias_ativas")).intValue()).isZero();
        assertThat(jdbc.queryForObject("select status from adra.solicitacao_desligamento where assistido_id = ?",
                String.class, naoRenovou)).isEqualTo("CANCELADA");
    }

    private long registro(Long assistidoId, String nivel, LocalDate data, LocalDate suspensaoInicio, LocalDate suspensaoFim,
                          String documentoStatus, Long autorId) {
        return jdbc.queryForObject("""
                insert into adra.registro_disciplinar (assistido_id, nivel, origem, sequencia, data_registro, descricao,
                    suspensao_inicio, suspensao_fim, documento_status, arquivado, registrado_por_id, criado_em)
                values (?, ?, 'MANUAL', 1, ?, 'Fato inventado', ?, ?, ?, false, ?, now())
                returning registro_id""", Long.class,
                assistidoId, nivel, data, suspensaoInicio, suspensaoFim, documentoStatus, autorId);
    }

    private boolean arquivado(long registroId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select arquivado from adra.registro_disciplinar where registro_id = ?",
                Boolean.class, registroId));
    }

    // ---------------------------------------------------------------

    private Long cadastrar(String nome, Turma turma) {
        return assistidoService.cadastrar(dados(nome, turma.getTurmaId())).assistidoId();
    }

    private void rematricular(Long assistidoId, Turma turma) {
        Assistido assistido = assistidoRepository.findById(assistidoId).orElseThrow();
        Matricula nova = matriculaService.abrir(assistido, ano + 1, dados(assistido.getNomeCompleto(), null), null);
        vinculoTurmaService.vincularNaMatricula(assistido, turma, nova);
    }

    private void abrirPeriodoDoProximoAno() {
        periodoMatriculaService.salvar(ano + 1, new PeriodoMatriculaRequestDTO(LocalDate.now(), null));
    }

    private Map<String, Object> ficha(Long assistidoId) {
        return jdbc.queryForMap(
                "select status::text as status, data_saida, motivo_saida, turma_id from adra.assistido where assistido_id = ?",
                assistidoId);
    }

    private Integer anoDaMatriculaAtual(Long assistidoId) {
        return jdbc.queryForObject("""
                select m.ano from adra.assistido a join adra.matricula m on m.matricula_id = a.matricula_atual_id
                where a.assistido_id = ?""", Integer.class, assistidoId);
    }

    private String statusDaMatricula(Long assistidoId, int anoDaMatricula) {
        return jdbc.queryForObject("select status from adra.matricula where assistido_id = ? and ano = ?",
                String.class, assistidoId, anoDaMatricula);
    }

    private LocalDate saidaDoVinculo(Long assistidoId, int anoDaMatricula) {
        return jdbc.queryForObject("""
                select ta.data_saida from adra.turma_aluno ta join adra.matricula m on m.matricula_id = ta.matricula_id
                where ta.assistido_id = ? and m.ano = ?""", LocalDate.class, assistidoId, anoDaMatricula);
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Turma grupo(String nome) {
        Turma turma = new Turma();
        turma.setNomeTurma(nome);
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma.setDiasDaSemana(new ArrayList<>());
        return turmaRepository.save(turma);
    }

    private static AssistidoRequestDTO dados(String nome, Long turmaId) {
        VinculoFamiliarComResponsavelRequestDTO mae = new VinculoFamiliarComResponsavelRequestDTO(
                "Mãe De " + nome, null, null, null, null, null, null,
                null, null, null, null, "Mãe", true, false, false, null);
        return new AssistidoRequestDTO(nome, LocalDate.of(2016, 5, 10), null, null, null, null, turmaId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                true, false, false, false, true,
                List.of(mae), null);
    }

    private static RequestPostProcessor perfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
