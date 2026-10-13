package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.assistido.ResultadoLinhaLoteDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.StatusLinhaLote;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Sem @Transactional: o lote depende de uma transacao por assistido, e o teste precisa exercitar isso.
@SpringBootTest
@Testcontainers
@Import(AssistidoLoteIT.ContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.default_schema=adra",
        "adra.brevo.api-key=dummy-teste",
        "adra.brevo.remetente-email=teste@teste.local",
        "adra.brevo.remetente-nome=Teste",
        "adra.frontend.url=http://localhost:4200",
        "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
})
class AssistidoLoteIT {

    private static final String CPF_MAE_IRMAOS = "52998224725";
    private static final String CPF_RESPONSAVEL_INVALIDA = "11144477735";
    private static final String CPF_RESPONSAVEL_EXISTENTE = "39053344705";
    private static final String CPF_ASSISTIDO_EXISTENTE = "12345678909";
    private static final String CPF_ASSISTIDO_HOMONIMO = "98765432100";

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                    .withInitScript("create-schema-adra.sql");
        }
    }

    @Autowired private AssistidoLoteService assistidoLoteService;
    @Autowired private AssistidoService assistidoService;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private ResponsavelRepository responsavelRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void definirDefaultsDoBanco() {
        jdbcTemplate.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.log_auditoria alter column data_hora set default now()");
    }

    @Test
    @DisplayName("Cadastra as linhas validas, devolve a invalida e cria uma vez o responsavel comum aos irmaos")
    void cadastraParcialmenteReaproveitandoResponsavel() {
        VinculoFamiliarComResponsavelRequestDTO mae = responsavel("Maria Irmaos", CPF_MAE_IRMAOS);

        List<ResultadoLinhaLoteDTO> resultados = assistidoLoteService.cadastrar(List.of(
                assistido("Ana Irmaos", LocalDate.of(2015, 5, 3), mae),
                assistido("Bia Irmaos", LocalDate.of(2017, 8, 10), mae),
                assistido("", LocalDate.of(2016, 1, 1), responsavel("Joao Invalida", CPF_RESPONSAVEL_INVALIDA))));

        assertThat(resultados).extracting(ResultadoLinhaLoteDTO::status)
                .containsExactly(StatusLinhaLote.CRIADA, StatusLinhaLote.CRIADA, StatusLinhaLote.INVALIDA);
        assertThat(resultados.get(2).erros()).anyMatch(erro -> erro.startsWith("nomeCompleto:"));

        Long maeId = responsavelRepository.findByCpf(CPF_MAE_IRMAOS).orElseThrow().getResponsavelId();
        Integer vinculosDaMae = jdbcTemplate.queryForObject(
                "select count(*) from adra.assistido_responsavel where responsavel_id = ?", Integer.class, maeId);
        assertThat(vinculosDaMae).isEqualTo(2);
        assertThat(responsavelRepository.findByCpf(CPF_RESPONSAVEL_INVALIDA)).isEmpty();
    }

    @Test
    @DisplayName("Validar bloqueia CPF repetido e mesmo nome e nascimento, libera CPFs diferentes e nao grava nada")
    void validarBloqueiaDuplicidadeSemGravar() {
        VinculoFamiliarComResponsavelRequestDTO paula = responsavel("Paula Existente", CPF_RESPONSAVEL_EXISTENTE);
        assistidoService.cadastrar(assistido("Caio Existente", LocalDate.of(2014, 2, 2), CPF_ASSISTIDO_EXISTENTE, paula));
        assistidoService.cadastrar(assistido("Eva Existente", LocalDate.of(2013, 3, 3), paula));
        long assistidosAntes = assistidoRepository.count();

        List<ResultadoLinhaLoteDTO> resultados = assistidoLoteService.validar(List.of(
                assistido("Caio Existente", LocalDate.of(2014, 2, 2), CPF_ASSISTIDO_HOMONIMO,
                        responsavel("Paula Com Outro Nome", CPF_RESPONSAVEL_EXISTENTE)),
                assistido("Davi Novo", LocalDate.of(2016, 6, 6), CPF_ASSISTIDO_EXISTENTE, paula),
                assistido("eva existente", LocalDate.of(2013, 3, 3), paula)));

        assertThat(resultados).extracting(ResultadoLinhaLoteDTO::status)
                .containsExactly(StatusLinhaLote.VALIDA, StatusLinhaLote.INVALIDA, StatusLinhaLote.INVALIDA);
        assertThat(resultados.get(0).responsaveisExistentes()).containsEntry(CPF_RESPONSAVEL_EXISTENTE, "Paula Existente");
        assertThat(resultados.get(1).erros()).containsExactly("CPF ja cadastrado para o assistido Caio Existente");
        assertThat(resultados.get(2).erros()).containsExactly("Ja existe assistido com mesmo nome e data de nascimento");
        assertThat(assistidoRepository.count()).isEqualTo(assistidosAntes);
    }

    private AssistidoRequestDTO assistido(String nome, LocalDate nascimento, VinculoFamiliarComResponsavelRequestDTO responsavel) {
        return assistido(nome, nascimento, null, responsavel);
    }

    private AssistidoRequestDTO assistido(String nome, LocalDate nascimento, String cpf,
                                          VinculoFamiliarComResponsavelRequestDTO responsavel) {
        return new AssistidoRequestDTO(nome, nascimento, cpf, null, null, null, null, List.of(responsavel), null);
    }

    private VinculoFamiliarComResponsavelRequestDTO responsavel(String nome, String cpf) {
        return new VinculoFamiliarComResponsavelRequestDTO(nome, null, cpf, null, null, null, null, "Mae", true, false, false);
    }
}
