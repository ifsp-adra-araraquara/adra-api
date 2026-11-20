package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.oficina.OficinaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
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
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.List;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
class OficinaDiasSemanaIT {

    @Autowired private OficinaService oficinaService;
    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Dias da oficina: gravados sem repetição, em ordem, mantidos se a edição não mandar, e filtráveis por qualquer um dos dias")
    void filtraPorDiasDaSemana() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }
        Usuario oficineiro = usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivelPermissaoRepository.save(NivelPermissao.builder().nome(NomeNivelPermissao.OFICINEIRO).build()))
                .nomeCompleto("Oficineiro Dias")
                .email("oficineiro-dias@teste.local")
                .build());

        OficinaResponseDTO futebol = oficinaService.cadastrar(oficina("Dias Futebol", oficineiro, List.of(FRIDAY, MONDAY, FRIDAY)));
        OficinaResponseDTO teatro = oficinaService.cadastrar(oficina("Dias Teatro", oficineiro, List.of(TUESDAY)));
        OficinaResponseDTO xadrez = oficinaService.cadastrar(oficina("Dias Xadrez", oficineiro, null));
        assertThat(futebol.diasDaSemana()).containsExactly(MONDAY, FRIDAY);
        assertThat(xadrez.diasDaSemana()).isEmpty();

        oficinaService.editar(xadrez.oficinaId(), oficina("Dias Xadrez", oficineiro, List.of(SATURDAY, WEDNESDAY)));
        oficinaService.editar(teatro.oficinaId(), oficina("Dias Teatro", oficineiro, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(nomes(List.of(TUESDAY))).containsExactly("Dias Teatro");
        assertThat(nomes(List.of(MONDAY, TUESDAY, FRIDAY))).containsExactlyInAnyOrder("Dias Futebol", "Dias Teatro");
        assertThat(nomes(null)).containsExactlyInAnyOrder("Dias Futebol", "Dias Teatro", "Dias Xadrez");
        assertThat(oficinaService.minhasOficinas(oficineiro.getUsuarioId()))
                .extracting(OficinaResponseDTO::nomeOficina, OficinaResponseDTO::diasDaSemana)
                .containsExactlyInAnyOrder(
                        tuple("Dias Futebol", List.of(MONDAY, FRIDAY)),
                        tuple("Dias Teatro", List.of(TUESDAY)),
                        tuple("Dias Xadrez", List.of(WEDNESDAY, SATURDAY)));
    }

    private List<String> nomes(List<DayOfWeek> dias) {
        return oficinaService.listarComFiltros("dias ", true, dias).stream().map(OficinaResponseDTO::nomeOficina).toList();
    }

    private static OficinaRequestDTO oficina(String nome, Usuario oficineiro, List<DayOfWeek> dias) {
        return new OficinaRequestDTO(nome, oficineiro.getUsuarioId(), null, null, dias);
    }
}
