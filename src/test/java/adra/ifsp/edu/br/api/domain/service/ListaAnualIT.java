package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.EDUCADOR_SOCIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.ListaAnualDTO;
import adra.ifsp.edu.br.api.domain.dto.matricula.ListaAnualDTO.LinhaDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusMatricula;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Chamada;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Presenca;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ChamadaRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.PresencaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import jakarta.persistence.EntityManager;

/** Lista de atendidos por ano (PR 4): grupo e frequência só das chamadas daquele ano. */
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
class ListaAnualIT {

    private final int ano = LocalDate.now().getYear();
    /** Dias já passados em qualquer data de execução do ano (exceto 1º de janeiro). */
    private final LocalDate dia1 = LocalDate.of(ano, 1, 2);
    private final LocalDate dia2 = LocalDate.of(ano, 1, 3);
    private final LocalDate dia3 = LocalDate.of(ano, 1, 4);

    @Autowired private MockMvc mvc;
    @Autowired private AssistidoService assistidoService;
    @Autowired private MatriculaService matriculaService;
    @Autowired private ListaAnualService listaAnualService;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private ChamadaRepository chamadaRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

    private Usuario coordenador;
    private Turma grupo;

    @BeforeEach
    void preparar() {
        jdbc.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbc.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        jdbc.execute("alter table adra.usuario alter column criado_em set default now()");
        jdbc.execute("alter table adra.usuario alter column atualizado_em set default now()");

        NivelPermissao nivel = nivelPermissaoRepository.findByNome(NomeNivelPermissao.COORDENADOR)
                .orElseGet(() -> nivelPermissaoRepository.save(NivelPermissao.builder().nome(NomeNivelPermissao.COORDENADOR).build()));
        coordenador = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel).nomeCompleto("Coordenação Lista Anual").email("lista-anual@teste.local").build());

        grupo = new Turma();
        grupo.setNomeTurma("Grupo Lista Anual");
        grupo.setTurno(Turno.MANHA);
        grupo.setCapacidade(20);
        grupo.setDiasDaSemana(new ArrayList<>());
        grupo = turmaRepository.save(grupo);
    }

    @Test
    @DisplayName("Lista do ano: grupo, entrada, saída e frequência só das chamadas do ano e enquanto estava na ADRA")
    void listaDoAno() {
        Long ana = cadastrar("Ana Lista");
        Long bruno = cadastrar("Bruno Lista");
        Long soNoProximo = cadastrarNoAnoSeguinte("Caio Só Próximo Ano");
        // Os dois entraram no grupo em 1º de janeiro.
        jdbc.update("update adra.turma_aluno set data_entrada = ? where assistido_id in (?, ?)",
                LocalDate.of(ano, 1, 1), ana, bruno);

        Chamada c1 = chamada(dia1);
        Chamada c2 = chamada(dia2);
        chamada(dia3);
        falta(c2, ana, StatusPresenca.FALTA);
        falta(c1, bruno, StatusPresenca.FALTA_JUSTIFICADA);
        assistidoService.alterarStatus(bruno, new AssistidoStatusRequestDTO(StatusGeral.INATIVO, dia2, "Mudou"));
        sincronizar();

        ListaAnualDTO lista = listaAnualService.doAno(ano);
        LinhaDTO linhaAna = linha(lista, ana);
        LinhaDTO linhaBruno = linha(lista, bruno);

        assertThat(lista.atendidos()).extracting(LinhaDTO::assistidoId).doesNotContain(soNoProximo);
        assertThat(lista.encerrado()).isFalse();

        assertThat(linhaAna.grupo()).isEqualTo("Grupo Lista Anual");
        assertThat(linhaAna.entrada()).isEqualTo(LocalDate.of(ano, 1, 1));
        assertThat(linhaAna.statusMatricula()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(linhaAna.totalChamadas()).isEqualTo(3);
        assertThat(linhaAna.presencas()).isEqualTo(2);
        assertThat(linhaAna.faltas()).isEqualTo(1);
        assertThat(linhaAna.frequenciaPercentual()).isEqualTo(67);

        // Saiu no dia 2: a partir dele as chamadas não contam (mesma regra da chamada).
        assertThat(linhaBruno.statusMatricula()).isEqualTo(StatusMatricula.DESLIGADA);
        assertThat(linhaBruno.saida()).isEqualTo(dia2);
        assertThat(linhaBruno.totalChamadas()).isEqualTo(1);
        assertThat(linhaBruno.faltasJustificadas()).isEqualTo(1);
        assertThat(linhaBruno.frequenciaPercentual()).isZero();

        assertThat(listaAnualService.anos()).contains(ano, ano + 1);
    }

    @Test
    @DisplayName("Educador Social e Oficineiro não exportam a lista anual")
    void permissao() throws Exception {
        mvc.perform(get("/api/matriculas/" + ano + "/atendidos").with(perfil(EDUCADOR_SOCIAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/matriculas/" + ano + "/atendidos").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------

    private Long cadastrar(String nome) {
        return assistidoService.cadastrar(dados(nome, grupo.getTurmaId())).assistidoId();
    }

    private Long cadastrarNoAnoSeguinte(String nome) {
        Long id = assistidoService.cadastrar(dados(nome, null)).assistidoId();
        sincronizar();
        jdbc.update("update adra.assistido set matricula_atual_id = null where assistido_id = ?", id);
        jdbc.update("delete from adra.matricula where assistido_id = ?", id);
        Assistido assistido = assistidoRepository.findById(id).orElseThrow();
        matriculaService.abrir(assistido, ano + 1, dados(nome, null), null);
        return id;
    }

    private LinhaDTO linha(ListaAnualDTO lista, Long assistidoId) {
        return lista.atendidos().stream().filter(l -> l.assistidoId().equals(assistidoId)).findFirst().orElseThrow();
    }

    private Chamada chamada(LocalDate data) {
        Chamada chamada = new Chamada();
        chamada.setTurma(grupo);
        chamada.setDataChamada(data);
        chamada.setCriadoPor(coordenador);
        chamada.setAtualizadoPor(coordenador);
        return chamadaRepository.save(chamada);
    }

    private void falta(Chamada chamada, Long assistidoId, StatusPresenca status) {
        Presenca presenca = new Presenca();
        presenca.setChamada(chamada);
        presenca.setAssistido(assistidoRepository.getReferenceById(assistidoId));
        presenca.setStatusPresenca(status);
        presenca.setCriadoPor(coordenador);
        presenca.setAtualizadoPor(coordenador);
        presencaRepository.save(presenca);
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
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
