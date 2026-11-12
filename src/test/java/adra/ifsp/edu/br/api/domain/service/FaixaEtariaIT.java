package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.faixaetaria.FaixaEtariaResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.turma.TurmaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
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
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class FaixaEtariaIT {

    @Autowired private FaixaEtariaService faixaEtariaService;
    @Autowired private TurmaService turmaService;
    @Autowired private DataSource dataSource;

    @BeforeEach
    void prepararBanco() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }
    }

    @Test
    @DisplayName("Faixa cadastrada vai para o grupo; editar a faixa muda o rótulo do grupo")
    void cadastraAtribuiEEdita() {
        FaixaEtariaResponseDTO faixa = faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 6, (short) 10, null));
        faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 8, (short) 12, null));

        TurmaResponseDTO grupo = turmaService.cadastrar(grupo(faixa.faixaEtariaId()));
        assertThat(grupo.faixaEtariaId()).isEqualTo(faixa.faixaEtariaId());
        assertThat(grupo.faixaEtaria()).isEqualTo("6–10 anos");

        faixaEtariaService.atualizar(faixa.faixaEtariaId(), new FaixaEtariaRequestDTO((short) 6, (short) 9, null));

        assertThat(turmaService.buscarPorId(grupo.turmaId()).faixaEtaria()).isEqualTo("6–9 anos");
        assertThat(faixaEtariaService.listar(false))
                .extracting(FaixaEtariaResponseDTO::rotulo)
                .containsExactly("6–9 anos", "8–12 anos");
    }

    @Test
    @DisplayName("Intervalo invertido ou repetido é recusado")
    void intervaloInvalido() {
        faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 11, (short) 13, null));

        assertThatThrownBy(() -> faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 13, (short) 11, null)))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 11, (short) 13, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Faixa inativa continua no grupo que já a usa, mas não entra em grupo novo")
    void faixaInativa() {
        FaixaEtariaResponseDTO faixa = faixaEtariaService.criar(new FaixaEtariaRequestDTO((short) 14, (short) 17, null));
        TurmaResponseDTO grupo = turmaService.cadastrar(grupo(faixa.faixaEtariaId()));

        faixaEtariaService.atualizar(faixa.faixaEtariaId(), new FaixaEtariaRequestDTO((short) 14, (short) 17, false));

        assertThat(turmaService.atualizar(grupo.turmaId(), grupo(faixa.faixaEtariaId())).faixaEtariaId())
                .isEqualTo(faixa.faixaEtariaId());
        assertThatThrownBy(() -> turmaService.cadastrar(grupo(faixa.faixaEtariaId())))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(faixaEtariaService.listar(false)).isEmpty();
        assertThat(faixaEtariaService.listar(true)).hasSize(1);
    }

    private static TurmaRequestDTO grupo(Long faixaEtariaId) {
        return new TurmaRequestDTO("Grupo faixa", Turno.MANHA, faixaEtariaId, 20, null, new ArrayList<>(), null);
    }
}
