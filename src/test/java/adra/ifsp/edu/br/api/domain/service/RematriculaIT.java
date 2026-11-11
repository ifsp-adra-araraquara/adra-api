package adra.ifsp.edu.br.api.domain.service;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Autorizacoes;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Mae;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Saude;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Termos;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Transferencia;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.infra.TurnstileClient;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rematrícula (PR 2): período de matrícula, inscrição com ano, comparação
 * com a ficha e a rematrícula sem sobrescrever o cadastro.
 * Dados inventados (CPFs válidos gerados).
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
class RematriculaIT {

    private static final String TOKEN = "token-valido";
    private static final String CPF_RENOVA = "41827365080";
    private static final String CPF_MAE_RENOVA = "52736401999";
    private static final String CPF_REPETE = "63645512896";
    private static final String CPF_MAE_REPETE = "74554623793";
    private static final String CPF_VOLTA = "85463734690";
    private static final String CPF_MAE_VOLTA = "96372845598";
    private static final String CPF_ANO_SEGUINTE = "17283956409";
    private static final String CPF_MAE_ANO_SEGUINTE = "28192067351";

    private final int ano = LocalDate.now().getYear();
    private final int proximoAno = ano + 1;

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private TransactionTemplate transacao;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;
    @MockitoBean private TurnstileClient turnstile;

    @BeforeEach
    void preparar() {
        jdbc.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbc.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbc.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbc.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        when(storage.urlTemporaria(any(), any())).thenReturn(URI.create("https://storage.test/arquivo"));
        when(turnstile.tokenValido(TOKEN)).thenReturn(true);
    }

    /** O período aberto mudaria o ano das inscrições dos outros testes (contexto compartilhado). */
    @AfterEach
    void fecharPeriodos() {
        jdbc.update("delete from adra.periodo_matricula");
    }

    @Test
    @DisplayName("Sem período aberto a inscrição é do ano corrente; com o período do ano seguinte, vai para ele")
    void periodoDefineOAnoDaInscricao() throws Exception {
        mvc.perform(get("/api/pre-cadastro/inscricao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(ano))
                .andExpect(jsonPath("$.periodoAberto").value(false));

        abrirPeriodo(proximoAno);

        mvc.perform(get("/api/pre-cadastro/inscricao"))
                .andExpect(jsonPath("$.ano").value(proximoAno))
                .andExpect(jsonPath("$.periodoAberto").value(true));

        Long id = enviar(CPF_ANO_SEGUINTE, CPF_MAE_ANO_SEGUINTE, "Rua Nova", false);
        assertThat(jdbc.queryForObject("select ano_referencia from adra.pre_cadastro where pre_cadastro_id = ?",
                Integer.class, id)).isEqualTo(proximoAno);
        assertThat(idsNaLista(proximoAno)).contains(id.intValue());
        assertThat(idsNaLista(ano)).doesNotContain(id.intValue());

        mvc.perform(get("/api/pre-cadastro/" + id).with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.anoReferencia").value(proximoAno));
    }

    @Test
    @DisplayName("Só o Coordenador abre período, e só para o ano corrente ou o seguinte")
    void regrasDoPeriodo() throws Exception {
        String corpo = "{\"inicio\":\"" + LocalDate.now() + "\"}";
        mvc.perform(put("/api/matriculas/periodos/" + proximoAno).with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/matriculas/periodos/" + (ano - 1)).with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/matriculas/periodos/" + proximoAno).with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"" + LocalDate.now() + "\",\"fim\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rematrícula para o ano seguinte: mostra o que mudou, abre a matrícula e não mexe na ficha nem no ano atual")
    void rematriculaParaOAnoSeguinte() throws Exception {
        Turma grupoAtual = grupo("Grupo Atual");
        Turma grupoNovo = grupo("Grupo Novo");
        Long assistidoId = aprovar(enviar(CPF_RENOVA, CPF_MAE_RENOVA, "Rua das Flores", true), grupoAtual);
        Long matriculaDoAno = matriculaId(assistidoId, ano);

        abrirPeriodo(proximoAno);
        Long envio = enviar(CPF_RENOVA, CPF_MAE_RENOVA, "Rua das Palmeiras", true);

        String comparacao = mvc.perform(get("/api/pre-cadastro/" + envio + "/comparacao").with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistidoId").value(assistidoId))
                .andExpect(jsonPath("$.ano").value(proximoAno))
                .andExpect(jsonPath("$.jaMatriculadoNoAno").value(false))
                .andExpect(jsonPath("$.turmaAtualId").value(grupoAtual.getTurmaId()))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> diferentes = JsonPath.read(comparacao, "$.campos[?(@.diferente == true)]");
        assertThat(diferentes).extracting(c -> c.get("rotulo")).containsExactly("Endereço");
        assertThat(diferentes.getFirst().get("naFicha")).asString().contains("Rua das Flores");
        assertThat(diferentes.getFirst().get("enviado")).asString().contains("Rua das Palmeiras");

        mvc.perform(post("/api/pre-cadastro/" + envio + "/rematricular").with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"turmaId\":" + grupoNovo.getTurmaId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTIDO"));

        Long matriculaNova = matriculaId(assistidoId, proximoAno);
        Map<String, Object> nova = jdbc.queryForMap("select * from adra.matricula where matricula_id = ?", matriculaNova);
        assertThat(((Number) nova.get("pre_cadastro_id")).longValue()).isEqualTo(envio);
        assertThat(nova.get("status")).isEqualTo("ATIVA");
        assertThat(nova.get("autoriza_eventos_passeios")).isEqualTo(true);

        // A ficha não foi sobrescrita e continua no ano em curso.
        Map<String, Object> ficha = jdbc.queryForMap(
                "select endereco_logradouro as logradouro, matricula_atual_id, turma_id from adra.assistido where assistido_id = ?", assistidoId);
        assertThat(ficha.get("logradouro")).isEqualTo("Rua das Flores");
        assertThat(((Number) ficha.get("matricula_atual_id")).longValue()).isEqualTo(matriculaDoAno);
        assertThat(((Number) ficha.get("turma_id")).longValue()).isEqualTo(grupoAtual.getTurmaId());

        // Grupo do ano seguinte começa em 1º de janeiro; o deste ano continua aberto.
        Map<String, Object> vinculoNovo = jdbc.queryForMap(
                "select turma_id, data_entrada from adra.turma_aluno where matricula_id = ?", matriculaNova);
        assertThat(((Number) vinculoNovo.get("turma_id")).longValue()).isEqualTo(grupoNovo.getTurmaId());
        assertThat(vinculoNovo.get("data_entrada").toString()).isEqualTo(proximoAno + "-01-01");
        assertThat(jdbc.queryForObject("select data_saida from adra.turma_aluno where matricula_id = ?",
                LocalDate.class, matriculaDoAno)).isNull();

        // Chamada: hoje no grupo atual; em janeiro só no grupo novo.
        LocalDate janeiro = LocalDate.of(proximoAno, 1, 10);
        assertThat(noGrupo(grupoAtual, LocalDate.now())).contains(assistidoId);
        assertThat(noGrupo(grupoAtual, janeiro)).doesNotContain(assistidoId);
        assertThat(noGrupo(grupoNovo, janeiro)).contains(assistidoId);

        // Documentos do novo ano ficam na nova matrícula; a foto substitui a anterior.
        assertThat(jdbc.queryForList("""
                select categoria from adra.arquivo
                where entidade_tipo = 'ASSISTIDO' and entidade_id = ? and matricula_id = ? and excluido_em is null
                order by categoria""", String.class, assistidoId, matriculaNova))
                .containsExactly("DECLARACAO_RESIDENCIA", "DOCUMENTO_IDENTIDADE");
        assertThat(jdbc.queryForObject("""
                select count(*) from adra.arquivo
                where entidade_tipo = 'ASSISTIDO' and entidade_id = ? and categoria = 'FOTO_PERFIL' and excluido_em is null""",
                Integer.class, assistidoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from adra.arquivo a join adra.responsavel r on r.responsavel_id = a.entidade_id
                where a.entidade_tipo = 'RESPONSAVEL' and r.cpf = ? and a.matricula_id = ?""",
                Integer.class, CPF_MAE_RENOVA, matriculaNova)).isEqualTo(1);
    }

    @Test
    @DisplayName("Segundo envio no mesmo ano não abre outra matrícula")
    void rematriculaRepetidaNoAno() throws Exception {
        Turma turma = grupo("Grupo Repete");
        aprovar(enviar(CPF_REPETE, CPF_MAE_REPETE, "Rua A", false), turma);
        abrirPeriodo(proximoAno);
        rematricular(enviar(CPF_REPETE, CPF_MAE_REPETE, "Rua A", false), turma)
                .andExpect(status().isOk());

        Long repetido = enviar(CPF_REPETE, CPF_MAE_REPETE, "Rua A", false);
        mvc.perform(get("/api/pre-cadastro/" + repetido).with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.jaMatriculadoNoAno").value(true));
        rematricular(repetido, turma)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("já está matriculado")));
    }

    @Test
    @DisplayName("Quem tinha saído volta na rematrícula: reativado e com o vínculo antigo fechado")
    void desligadoVoltaNaRematricula() throws Exception {
        Turma turma = grupo("Grupo Volta");
        Long assistidoId = aprovar(enviar(CPF_VOLTA, CPF_MAE_VOLTA, "Rua B", false), turma);
        mvc.perform(patch("/api/assistidos/" + assistidoId + "/status").with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INATIVO\",\"dataSaida\":\"" + LocalDate.now() + "\",\"motivoSaida\":\"Mudou\"}"))
                .andExpect(status().isOk());

        abrirPeriodo(proximoAno);
        rematricular(enviar(CPF_VOLTA, CPF_MAE_VOLTA, "Rua B", false), turma).andExpect(status().isOk());

        Map<String, Object> ficha = jdbc.queryForMap(
                "select status::text as status, data_saida from adra.assistido where assistido_id = ?", assistidoId);
        assertThat(ficha.get("status")).isEqualTo("ATIVO");
        assertThat(ficha.get("data_saida")).isNull();
        assertThat(jdbc.queryForObject("select data_saida from adra.turma_aluno where matricula_id = ?",
                LocalDate.class, matriculaId(assistidoId, ano))).isEqualTo(LocalDate.now());
        assertThat(matriculaStatus(assistidoId, proximoAno)).isEqualTo("ATIVA");
    }

    // ---------------------------------------------------------------

    private List<Long> noGrupo(Turma turma, LocalDate dia) {
        return transacao.execute(t -> turmaAlunosRepository.findElegiveisParaChamada(turma, dia).stream()
                .map(v -> v.getAssistido().getAssistidoId()).toList());
    }

    private Long matriculaId(Long assistidoId, int anoDaMatricula) {
        return jdbc.queryForObject("select matricula_id from adra.matricula where assistido_id = ? and ano = ?",
                Long.class, assistidoId, anoDaMatricula);
    }

    private String matriculaStatus(Long assistidoId, int anoDaMatricula) {
        return jdbc.queryForObject("select status from adra.matricula where assistido_id = ? and ano = ?",
                String.class, assistidoId, anoDaMatricula);
    }

    private void abrirPeriodo(int anoDoPeriodo) throws Exception {
        mvc.perform(put("/api/matriculas/periodos/" + anoDoPeriodo).with(perfil(COORDENADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inicio\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abertoHoje").value(true));
    }

    private List<Integer> idsNaLista(int anoDaLista) throws Exception {
        String lista = mvc.perform(get("/api/pre-cadastro").param("ano", String.valueOf(anoDaLista))
                        .with(perfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(lista, "$[*].preCadastroId");
    }

    private Long aprovar(Long preCadastroId, Turma turma) throws Exception {
        String rascunho = mvc.perform(get("/api/pre-cadastro/" + preCadastroId + "/rascunho-assistido")
                        .with(perfil(ASSISTENTE_SOCIAL)))
                .andReturn().getResponse().getContentAsString();
        rascunho = rascunho.replace("\"turmaId\":null", "\"turmaId\":" + turma.getTurmaId());
        String aprovado = mvc.perform(post("/api/pre-cadastro/" + preCadastroId + "/aprovar").with(perfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunho))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(aprovado, "$.assistidoId")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions rematricular(Long preCadastroId, Turma turma) throws Exception {
        return mvc.perform(post("/api/pre-cadastro/" + preCadastroId + "/rematricular").with(perfil(ASSISTENTE_SOCIAL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"turmaId\":" + turma.getTurmaId() + "}"));
    }

    private Long enviar(String cpf, String cpfMae, String rua, boolean comAnexos) throws Exception {
        PreCadastroRequestDTO requisicao = new PreCadastroRequestDTO("Crianca " + cpf, LocalDate.of(2016, 4, 12), cpf,
                Sexo.FEMININO, "familia@teste.local", dados(cpfMae, rua), "");
        var envio = multipart("/api/pre-cadastro")
                .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE, jsonMapper.writeValueAsBytes(requisicao)))
                .file(imagem("comprovanteEndereco", 1))
                .param("captcha", TOKEN);
        if (comAnexos) {
            envio.file(imagem("foto", 2)).file(imagem("documentosCrianca", 3)).file(imagem("documentosResponsavel", 4));
        }
        String resposta = mvc.perform(envio.header("X-Forwarded-For", "teste-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.preCadastroId")).longValue();
    }

    private static PreCadastroDadosDTO dados(String cpfMae, String rua) {
        return new PreCadastroDadosDTO(
                new Termos(true, false, true),
                new PreCadastroDadosDTO.Crianca(null),
                new Mae("Mae Teste", LocalDate.of(1990, 1, 20), cpfMae, Escolaridade.MEDIO_COMPLETO,
                        EstadoCivil.SOLTEIRO, null, "Cozinheira", new BigDecimal("1500.00"), "16991234567"),
                null,
                null,
                new EnderecoDTO(rua, "100", "Centro", null),
                new Transferencia(true, Set.of(ProgramaTransferencia.BOLSA_FAMILIA), null, "12345678901"),
                new Saude(false, null, "Tia Teste", "16998765432"),
                new Autorizacoes(true, false, "Avó Teste"));
    }

    /** PNG pequeno; {@code cor} muda o conteúdo (hash) entre um envio e outro. */
    private static MockMultipartFile imagem(String parte, int cor) throws IOException {
        BufferedImage imagem = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        imagem.setRGB(0, 0, (int) (System.nanoTime() & 0xFFFFFF) + cor);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", png);
        return new MockMultipartFile(parte, parte + ".png", "image/png", png.toByteArray());
    }

    private Turma grupo(String nome) {
        Turma turma = new Turma();
        turma.setNomeTurma(nome + " " + UUID.randomUUID().toString().substring(0, 6));
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma.setDiasDaSemana(new ArrayList<>());
        return turmaRepository.save(turma);
    }

    private static RequestPostProcessor perfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
