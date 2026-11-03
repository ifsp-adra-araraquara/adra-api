package adra.ifsp.edu.br.api.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.aviso.AvisoDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.TipoAviso;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;

@SpringBootTest
@Testcontainers
@Import(ContainerPostgresAdra.class)
@TestPropertySource(properties = {
        "spring.cache.type=caffeine",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
class AssistidoCacheIT {

    @Autowired private AssistidoService assistidoService;
    @Autowired private TurmaService turmaService;
    @Autowired private AvisoService avisoService;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("alter table adra.usuario alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.usuario alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column data_nascimento drop not null");
        jdbcTemplate.execute("alter table adra.log_auditoria alter column data_hora set default now()");
    }

    @Test
    @DisplayName("Turmas e avisos saem do cache até uma escrita em atendido")
    void turmasEAvisosEmCacheAteEscritaEmAtendido() {
        Long assistidoId = assistidoRepository.save(Assistido.builder()
                .nomeCompleto("Caio Cache")
                .dataEntrada(LocalDate.now().minusMonths(1))
                .status(StatusGeral.ATIVO)
                .turma(criarTurma())
                .build()).getAssistidoId();

        assertThat(alunosNaTurma()).isEqualTo(1);
        assertThat(avisosDaAssistenteSocial()).contains(TipoAviso.CADASTRO_SEM_NASCIMENTO);

        jdbcTemplate.update("update adra.assistido set status = 'INATIVO' where assistido_id = ?", assistidoId);
        assertThat(alunosNaTurma()).as("ainda vem do cache").isEqualTo(1);
        assertThat(avisosDaAssistenteSocial()).as("ainda vem do cache").contains(TipoAviso.CADASTRO_SEM_NASCIMENTO);

        assistidoService.alterarStatus(assistidoId,
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, LocalDate.now(), "Mudou de cidade"));
        assertThat(alunosNaTurma()).as("a escrita limpou o cache").isZero();
        assertThat(avisosDaAssistenteSocial()).as("a escrita limpou o cache")
                .doesNotContain(TipoAviso.CADASTRO_SEM_NASCIMENTO);
    }

    private Integer alunosNaTurma() {
        return turmaService.listarComFiltros(null, null, null).getFirst().quantidadeAlunos();
    }

    private List<TipoAviso> avisosDaAssistenteSocial() {
        return avisoService.doPerfil(NomeNivelPermissao.ASSISTENTE_SOCIAL).stream().map(AvisoDTO::tipo).toList();
    }

    private Turma criarTurma() {
        NivelPermissao nivel = nivelPermissaoRepository.save(
                NivelPermissao.builder().nome(NomeNivelPermissao.OFICINEIRO).build());
        Usuario oficineiro = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel)
                .nomeCompleto("Oficineiro Cache")
                .email("oficineiro-cache@teste.local")
                .build());

        Oficina oficina = new Oficina();
        oficina.setNomeOficina("Oficina Cache");
        oficina.setOficineiroResponsavel(oficineiro);
        oficina.setAtivo(true);

        Turma turma = new Turma();
        oficinaRepository.save(oficina);
        turma.setNomeTurma("Turma Cache");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(10);
        turma.setAtivo(true);
        return turmaRepository.save(turma);
    }
}
