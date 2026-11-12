package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.EDUCADOR_SOCIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

import com.jayway.jsonpath.JsonPath;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import jakarta.persistence.EntityManager;

/**
 * Lista de evento/passeio (pedido da ADRA de 12/11/2026): atendidos ativos hoje nos grupos marcados,
 * em ordem alfabética, sem dados além do necessário. Só o Coordenador. Dados inventados.
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
class ListaEventoIT {

    @Autowired private MockMvc mvc;
    @Autowired private AssistidoService assistidoService;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    private Turma grupoA;
    private Turma grupoB;
    private Turma grupoC;

    @BeforeEach
    void preparar() {
        jdbc.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbc.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        grupoA = grupo("Conexão Jovem A");
        grupoB = grupo("Conexão Jovem B");
        grupoC = grupo("Conexão Jovem C");
    }

    @Test
    @DisplayName("Lista unificada: só os grupos marcados, atendidos ativos hoje, em ordem alfabética e sem responsável")
    void listaUnificada() throws Exception {
        cadastrar("Zélia Evento", grupoA);
        cadastrar("Ângela Evento", grupoA);
        cadastrar("Bruno Evento", grupoB);
        cadastrar("Carla Evento", grupoC);
        Long desligada = cadastrar("Débora Evento", grupoA);
        Long proximoAno = cadastrar("Elisa Evento", grupoB);
        sincronizar();
        jdbc.update("update adra.assistido set status = 'INATIVO' where assistido_id = ?", desligada);
        jdbc.update("update adra.turma_aluno set data_entrada = ? where assistido_id = ?",
                LocalDate.of(LocalDate.now().getYear() + 1, 1, 1), proximoAno);

        String lista = mvc.perform(get("/api/listas/evento")
                        .param("turmaIds", grupoA.getTurmaId() + "," + grupoB.getTurmaId())
                        .with(perfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].responsavelNome").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<String> nomes = JsonPath.read(lista, "$[*].nomeCompleto");
        assertThat(nomes).containsExactly("Ângela Evento", "Bruno Evento", "Zélia Evento");
        List<String> grupos = JsonPath.read(lista, "$[*].nomeTurma");
        assertThat(grupos).containsExactly("Conexão Jovem A", "Conexão Jovem B", "Conexão Jovem A");
    }

    @Test
    @DisplayName("Lista individual com responsável e telefone só quando a coordenação pede")
    void listaIndividualComResponsavel() throws Exception {
        cadastrar("Fábio Evento", grupoC);
        sincronizar();

        mvc.perform(get("/api/listas/evento")
                        .param("turmaIds", String.valueOf(grupoC.getTurmaId()))
                        .param("comResponsavel", "true")
                        .with(perfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].responsavelNome").value("Mãe De Fábio Evento"))
                .andExpect(jsonPath("$[0].responsavelTelefone").value("16999990000"));
    }

    @Test
    @DisplayName("Só o Coordenador gera a lista, e precisa escolher ao menos um grupo")
    void soCoordenador() throws Exception {
        for (NomeNivelPermissao outro : List.of(ASSISTENTE_SOCIAL, EDUCADOR_SOCIAL, NomeNivelPermissao.OFICINEIRO)) {
            mvc.perform(get("/api/listas/evento").param("turmaIds", String.valueOf(grupoA.getTurmaId())).with(perfil(outro)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/listas/evento").param("turmaIds", "").with(perfil(COORDENADOR)))
                .andExpect(status().is4xxClientError());
    }

    // ---------------------------------------------------------------

    private Long cadastrar(String nome, Turma turma) {
        return assistidoService.cadastrar(dados(nome, turma.getTurmaId())).assistidoId();
    }

    private Turma grupo(String nome) {
        Turma turma = new Turma();
        turma.setNomeTurma(nome);
        turma.setTurno(Turno.TARDE);
        turma.setCapacidade(30);
        turma.setDiasDaSemana(new ArrayList<>());
        return turmaRepository.save(turma);
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AssistidoRequestDTO dados(String nome, Long turmaId) {
        VinculoFamiliarComResponsavelRequestDTO mae = new VinculoFamiliarComResponsavelRequestDTO(
                "Mãe De " + nome, null, null, "16999990000", null, null, null,
                null, null, null, null, "Mãe", true, false, false, null);
        return new AssistidoRequestDTO(nome, LocalDate.of(2012, 5, 10), null, null, null, null, turmaId,
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
