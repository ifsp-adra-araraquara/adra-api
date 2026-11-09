package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.responsavel.ResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CadastroSemCpfIT {

    private static final String CPF_IRMA = "28176593419";
    private static final String CPF_IRMAO = "61582034753";

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private ResponsavelRepository responsavelRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;

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
    @DisplayName("Responsaveis sem CPF sao cadastros diferentes, gravam CPF nulo e podem ser editados")
    void responsaveisSemCpf() throws Exception {
        Long primeiro = cadastrarResponsavel("Maria Sem CPF", "");
        Long segundo = cadastrarResponsavel("Maria Sem CPF", null);
        Long terceiro = cadastrarResponsavel("Maria Sem CPF", "");

        assertThat(List.of(primeiro, segundo, terceiro)).doesNotHaveDuplicates();
        assertThat(responsavelRepository.findById(primeiro).orElseThrow().getCpf()).isNull();

        mvc.perform(put("/api/responsaveis/" + segundo).with(comPerfilAssistenteSocial())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(responsavel("Maria Editada", ""))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cadastra atendido sem documento e reaproveita pelo id o responsavel sem CPF do irmao")
    void irmaosSemDocumentoComMesmoResponsavelSemCpf() throws Exception {
        Long responsavelId = cadastrarResponsavel("Mae Dos Irmaos", null);
        long responsaveisAntes = responsavelRepository.count();

        cadastrarAtendido("Irma Sem Documento", CPF_IRMA, responsavelId);
        cadastrarAtendido("Irmao Sem Documento", CPF_IRMAO, responsavelId);

        assertThat(responsavelRepository.count()).isEqualTo(responsaveisAntes);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from adra.assistido_responsavel where responsavel_id = ?", Integer.class, responsavelId))
                .isEqualTo(2);
    }

    private Long cadastrarResponsavel(String nome, String cpf) throws Exception {
        String resposta = mvc.perform(post("/api/responsaveis").with(comPerfilAssistenteSocial())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(responsavel(nome, cpf))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.responsavelId")).longValue();
    }

    private void cadastrarAtendido(String nome, String cpf, Long responsavelId) throws Exception {
        VinculoFamiliarComResponsavelRequestDTO vinculo = new VinculoFamiliarComResponsavelRequestDTO(
                "Mae Dos Irmaos", null, null, null, null, null, null,
                null, null, null, null, "Mãe", true, false, false, responsavelId);
        AssistidoRequestDTO dados = new AssistidoRequestDTO(nome, LocalDate.of(2015, 6, 1), cpf, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(vinculo), null);
        MockMultipartFile parteDados = new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                jsonMapper.writeValueAsBytes(dados));

        mvc.perform(multipart("/api/assistidos").file(parteDados).with(comPerfilAssistenteSocial()))
                .andExpect(status().isCreated());
    }

    private static ResponsavelRequestDTO responsavel(String nome, String cpf) {
        return new ResponsavelRequestDTO(nome, null, cpf, null, null, null, null, null, null, null, null);
    }

    private static RequestPostProcessor comPerfilAssistenteSocial() {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, NomeNivelPermissao.ASSISTENTE_SOCIAL.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + NomeNivelPermissao.ASSISTENTE_SOCIAL.name()));
    }
}
