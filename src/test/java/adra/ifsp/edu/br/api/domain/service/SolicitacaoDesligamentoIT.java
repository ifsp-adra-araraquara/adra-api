package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import jakarta.persistence.EntityManager;

/**
 * Pedido de desligamento (pedido da ADRA de 13/11/2026): a Assistente Social pede,
 * só o Coordenador aprova (inativa) ou recusa; desligar/reativar direto é só dele.
 * Dados inventados.
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
class SolicitacaoDesligamentoIT {

    @Autowired private MockMvc mvc;
    @Autowired private AssistidoService assistidoService;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    private Usuario assistente;
    private Usuario coordenador;
    private Turma grupo;

    @BeforeEach
    void preparar() {
        for (String tabela : List.of("assistido", "responsavel", "usuario")) {
            jdbc.execute("alter table adra." + tabela + " alter column criado_em set default now()");
            jdbc.execute("alter table adra." + tabela + " alter column atualizado_em set default now()");
        }
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        jdbc.execute("create unique index if not exists uq_solicitacao_desligamento_pendente"
                + " on adra.solicitacao_desligamento (assistido_id) where status = 'PENDENTE'");
        assistente = usuario(ASSISTENTE_SOCIAL, "Maria Assistente", "assistente-desligamento@teste.local");
        coordenador = usuario(COORDENADOR, "Carlos Coordenador", "coord-desligamento@teste.local");
        grupo = new Turma();
        grupo.setNomeTurma("Grupo Desligamento");
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>());
        grupo = turmaRepository.save(grupo);
    }

    @Test
    @DisplayName("Assistente pede, Coordenador é avisado sem o motivo, recusa com resposta e o atendido segue ativo")
    void pedidoRecusado() throws Exception {
        Long id = cadastrar("Ana Pedido");

        Long solicitacao = solicitar(id, "Família mudou de cidade").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.solicitadoPorNome").value("Maria Assistente"))
                .andReturn().getResponse().getContentAsString().transform(this::idDoPedido);

        // Um pedido em aberto por atendido; a Assistente não desliga direto nem decide.
        solicitar(id, "De novo").andExpect(status().isConflict());
        mvc.perform(patch("/api/assistidos/" + id + "/status").with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INATIVO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/assistidos/desligamentos/" + solicitacao + "/aprovar").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isForbidden());

        // Coordenador avisado (sino e e-mail), sem o texto do motivo.
        Map<String, Object> notificacao = jdbc.queryForMap(
                "select titulo, mensagem from adra.notificacao where tipo = 'DESLIGAMENTO_SOLICITADO' and destinatario_id = ?",
                coordenador.getUsuarioId());
        assertThat(notificacao.get("titulo")).isEqualTo("Desligamento solicitado: Ana Pedido");
        assertThat(notificacao.get("mensagem")).asString().doesNotContain("mudou de cidade");
        mvc.perform(get("/api/avisos").with(perfil(COORDENADOR)))
                .andExpect(jsonPath("$[?(@.tipo == 'DESLIGAMENTO_SOLICITADO')]").exists());

        comoUsuario(coordenador);
        mvc.perform(patch("/api/assistidos/desligamentos/" + solicitacao + "/recusar").with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/assistidos/desligamentos/" + solicitacao + "/recusar").with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"Vamos conversar com a família antes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.decididoPorNome").value("Carlos Coordenador"));

        assertThat(statusDoAtendido(id)).isEqualTo("ATIVO");
        mvc.perform(get("/api/assistidos/desligamentos").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$[0].status").value("RECUSADA"))
                .andExpect(jsonPath("$[0].resposta").value("Vamos conversar com a família antes"));
    }

    @Test
    @DisplayName("Aprovar desliga o atendido com a data e o motivo do pedido")
    void pedidoAprovado() throws Exception {
        Long id = cadastrar("Bruno Pedido");
        LocalDate saida = LocalDate.now().minusDays(2);
        comoUsuario(assistente);
        Long solicitacao = mvc.perform(post("/api/assistidos/" + id + "/desligamento").with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataSaida\":\"" + saida + "\",\"motivo\":\"Começou a trabalhar\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::idDoPedido);

        comoUsuario(coordenador);
        mvc.perform(patch("/api/assistidos/desligamentos/" + solicitacao + "/aprovar").with(perfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
        entityManager.flush();

        Map<String, Object> ficha = jdbc.queryForMap(
                "select status::text as status, data_saida, motivo_saida from adra.assistido where assistido_id = ?", id);
        assertThat(ficha.get("status")).isEqualTo("INATIVO");
        assertThat(ficha.get("data_saida").toString()).isEqualTo(saida.toString());
        assertThat(ficha.get("motivo_saida")).isEqualTo("Começou a trabalhar");
        mvc.perform(patch("/api/assistidos/desligamentos/" + solicitacao + "/aprovar").with(perfil(COORDENADOR)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A Assistente retira o pedido; o Coordenador desligando direto fecha o pedido em aberto")
    void cancelarEDesligarDireto() throws Exception {
        Long carla = cadastrar("Carla Pedido");
        Long pedidoCarla = solicitar(carla, "Engano").andReturn().getResponse().getContentAsString().transform(this::idDoPedido);
        mvc.perform(patch("/api/assistidos/desligamentos/" + pedidoCarla + "/cancelar").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        Long davi = cadastrar("Davi Pedido");
        solicitar(davi, "Mudou de escola").andExpect(status().isCreated());
        comoUsuario(coordenador);
        mvc.perform(patch("/api/assistidos/" + davi + "/status").with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INATIVO\",\"dataSaida\":\"" + LocalDate.now() + "\",\"motivoSaida\":\"Mudou de escola\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select status from adra.solicitacao_desligamento where assistido_id = ?",
                String.class, davi)).isEqualTo("APROVADA");
        // Reativar também é só do Coordenador.
        mvc.perform(patch("/api/assistidos/" + davi + "/status").with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ATIVO\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------

    private ResultActions solicitar(Long assistidoId, String motivo) throws Exception {
        comoUsuario(assistente);
        return mvc.perform(post("/api/assistidos/" + assistidoId + "/desligamento").with(perfil(ASSISTENTE_SOCIAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataSaida\":\"" + LocalDate.now() + "\",\"motivo\":\"" + motivo + "\"}"));
    }

    private Long idDoPedido(String json) {
        return ((Number) JsonPath.read(json, "$.solicitacaoId")).longValue();
    }

    private void comoUsuario(Usuario usuario) {
        when(usuarioAutenticadoService.getUsuarioAtual()).thenReturn(usuario);
    }

    private String statusDoAtendido(Long id) {
        return jdbc.queryForObject("select status::text from adra.assistido where assistido_id = ?", String.class, id);
    }

    private Long cadastrar(String nome) {
        Long id = assistidoService.cadastrar(dados(nome, grupo.getTurmaId())).assistidoId();
        entityManager.flush();
        return id;
    }

    private Usuario usuario(NomeNivelPermissao perfil, String nome, String email) {
        NivelPermissao nivel = nivelPermissaoRepository.findByNome(perfil)
                .orElseGet(() -> nivelPermissaoRepository.save(NivelPermissao.builder().nome(perfil).build()));
        return usuarioRepository.save(Usuario.builder().nivelPermissao(nivel).nomeCompleto(nome).email(email).build());
    }

    private static AssistidoRequestDTO dados(String nome, Long turmaId) {
        VinculoFamiliarComResponsavelRequestDTO mae = new VinculoFamiliarComResponsavelRequestDTO(
                "Mãe De " + nome, null, null, "16999990000", null, null, null,
                null, null, null, null, "Mãe", true, false, false, null);
        return new AssistidoRequestDTO(nome, LocalDate.of(2014, 5, 10), null, null, null, null, turmaId,
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
