package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.comum.EnderecoDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.ObservacaoPreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Autorizacoes;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Mae;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Pai;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.ResponsavelLegal;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Saude;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Termos;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroDadosDTO.Transferencia;
import adra.ifsp.edu.br.api.domain.dto.precadastro.PreCadastroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.vinculo.VinculoFamiliarComResponsavelRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.Escolaridade;
import adra.ifsp.edu.br.api.domain.enums.EstadoCivil;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.ProgramaTransferencia;
import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.PreCadastroRepository;
import adra.ifsp.edu.br.api.domain.repository.ResponsavelRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.infra.armazenamento.ArmazenamentoArquivos;
import adra.ifsp.edu.br.api.infra.TurnstileClient;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.ASSISTENTE_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.COORDENADOR;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.EDUCADOR_SOCIAL;
import static adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao.OFICINEIRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Dados inventados (CPFs válidos gerados), nunca da planilha da ADRA.
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
class PreCadastroIT {

    private static final String TOKEN_VALIDO = "token-valido";
    private static final String CPF_CRIANCA = "56532228281";
    private static final String CPF_MAE = "96234168493";
    private static final String CPF_CRIANCA_REPROVADA = "98334040814";
    private static final String CPF_CRIANCA_JA_ATENDIDA = "99590633137";
    private static final String CPF_RESPONSAVEL_JA_ATENDIDA = "01462189733";
    private static final String CPF_RESPONSAVEL_LEGAL = "39577220045";
    private static final String CPF_CRIANCA_SEM_ANEXOS = "74584844208";
    private static final String CPF_CRIANCA_COM_ANEXOS = "67795615848";
    private static final String CPF_MAE_COM_ANEXOS = "18205377570";
    private static final String CPF_CRIANCA_RENDA_ALTA = "12345678062";
    private static final String CPF_MAE_RENDA_ALTA = "23456789173";
    private static final String CPF_CRIANCA_LISTA = "98697990009";
    private static final String CPF_MAE_LISTA = "32744690333";
    private static final String CPF_CRIANCA_LISTA_REPROVADA = "53438306921";
    private static final String CPF_CRIANCA_LISTA_JA_ATENDIDA = "66627433567";
    private static final String CPF_RESPONSAVEL_LISTA_JA_ATENDIDA = "60936460172";
    private static final String CPF_CRIANCA_MAIS_ANTIGA = "96132020721";
    private static final String CPF_CRIANCA_MAIS_NOVA = "99999539998";
    private static final String CPF_CRIANCA_VISTA = "31415926590";

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private PreCadastroService preCadastroService;
    @Autowired private AssistidoService assistidoService;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private ResponsavelRepository responsavelRepository;
    @MockitoSpyBean private PreCadastroRepository preCadastroRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockitoBean private ArmazenamentoArquivos storage;
    @MockitoBean private UsuarioAutenticadoService usuarioAutenticadoService;
    @MockitoBean private TurnstileClient turnstile;

    @BeforeEach
    void definirDefaultsDoBanco() {
        jdbcTemplate.execute("alter table adra.assistido alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.responsavel alter column atualizado_em set default now()");
        jdbcTemplate.execute("alter table adra.assistido_responsavel alter column criado_em set default now()");
        jdbcTemplate.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        when(storage.provedor()).thenReturn(ProvedorArmazenamento.SUPABASE);
        when(turnstile.tokenValido(TOKEN_VALIDO)).thenReturn(true);
        doAnswer(chamada -> {
            assertThat(conexoesEmUso()).isZero();
            return null;
        }).when(storage).salvar(any(), any(), any());
        when(storage.urlTemporaria(any(), any())).thenAnswer(chamada -> {
            assertThat(conexoesEmUso()).isZero();
            return URI.create("https://storage.test/arquivo");
        });
    }

    @Test
    @DisplayName("Envio publico aparece como pendente e a aprovacao cria atendido e responsaveis sem redigitar")
    void envioPublicoAprovadoSemRedigitar() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA, dados(CPF_MAE)));

        mvc.perform(get("/api/pre-cadastro").param("status", "PENDENTE").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.preCadastroId == " + id + ")].cpfJaCadastrado").value(false));

        String rascunho = mvc.perform(get("/api/pre-cadastro/" + id + "/rascunho-assistido").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String aprovado = mvc.perform(post("/api/pre-cadastro/" + id + "/aprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTIDO"))
                .andReturn().getResponse().getContentAsString();

        Assistido assistido = assistidoRepository.findByCpf(CPF_CRIANCA).orElseThrow();
        assertThat(((Number) JsonPath.read(aprovado, "$.assistidoId")).longValue()).isEqualTo(assistido.getAssistidoId());
        assertThat(assistido.getRendaFamiliar()).isEqualByComparingTo("2300.00");
        assertThat(assistido.getProblemaSaude()).isEqualTo("Asma leve");
        assertThat(assistido.getObservacoes()).isEqualTo("""
                Contato de emergência: Tia Teste — (16) 99876-5432
                Quem busca: Avó Teste
                Pai (sem CPF): Pai Teste — (16) 3333-4444""");

        Long maeId = responsavelRepository.findByCpf(CPF_MAE).orElseThrow().getResponsavelId();
        Boolean maePrincipal = jdbcTemplate.queryForObject(
                "select responsavel_principal from adra.assistido_responsavel where assistido_id = ? and responsavel_id = ?",
                Boolean.class, assistido.getAssistidoId(), maeId);
        assertThat(maePrincipal).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from adra.assistido_responsavel where assistido_id = ?",
                Integer.class, assistido.getAssistidoId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Renda alta nao barra a aprovacao: a renda e so estimativa (reuniao de 05/11)")
    void rendaAltaNaoBarraAprovacao() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA_RENDA_ALTA, dados(CPF_MAE_RENDA_ALTA)));
        String rascunho = mvc.perform(get("/api/pre-cadastro/" + id + "/rascunho-assistido").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andReturn().getResponse().getContentAsString();
        String rascunhoComRendaAlta = JsonPath.parse(rascunho).set("$.rendaFamiliar", 25000).jsonString();

        mvc.perform(post("/api/pre-cadastro/" + id + "/aprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunhoComRendaAlta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTIDO"));

        assertThat(assistidoRepository.findByCpf(CPF_CRIANCA_RENDA_ALTA).orElseThrow().getRendaFamiliar())
                .isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("Reprovar exige motivo")
    void reprovarExigeMotivo() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA_REPROVADA, dados(CPF_MAE)));

        mvc.perform(patch("/api/pre-cadastro/" + id + "/reprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/pre-cadastro/" + id + "/reprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Fora da faixa etaria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCARTADO"))
                .andExpect(jsonPath("$.motivoReprovacao").value("Fora da faixa etaria"));
    }

    @Test
    @DisplayName("Aprovar com CPF ja cadastrado devolve 409 com o atendido existente e nao grava nada")
    void aprovarComCpfJaCadastrado() throws Exception {
        Long existenteId = assistidoService.cadastrar(assistido("Crianca Ja Atendida", CPF_CRIANCA_JA_ATENDIDA,
                CPF_RESPONSAVEL_JA_ATENDIDA)).assistidoId();
        Long id = enviarPublico(requisicao(CPF_CRIANCA_JA_ATENDIDA, dadosSemMae()));
        long assistidosAntes = assistidoRepository.count();

        mvc.perform(get("/api/pre-cadastro/" + id).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.assistidoExistenteId").value(existenteId));
        String rascunho = mvc.perform(get("/api/pre-cadastro/" + id + "/rascunho-assistido").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.responsaveis[0].parentesco").value("Avó"))
                .andExpect(jsonPath("$.responsaveis[0].responsavelPrincipal").value(true))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/pre-cadastro/" + id + "/aprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunho))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.assistidoId").value(existenteId));

        assertThat(assistidoRepository.count()).isEqualTo(assistidosAntes);
        assertThat(responsavelRepository.findByCpf(CPF_RESPONSAVEL_LEGAL)).isEmpty();
        assertThat(preCadastroRepository.findById(id).orElseThrow().getStatus().name()).isEqualTo("PENDENTE");
    }

    @Test
    @DisplayName("Envio sem aceite da LGPD e bloqueado")
    void envioSemLgpdBloqueado() throws Exception {
        PreCadastroDadosDTO base = dados(CPF_MAE);
        PreCadastroDadosDTO semAceite = new PreCadastroDadosDTO(new Termos(false, true, true), base.crianca(), base.mae(),
                base.pai(), base.responsavelLegal(), base.enderecoFamilia(), base.transferencia(), base.saude(),
                base.autorizacoes());

        mvc.perform(multipart("/api/pre-cadastro").file(partDados(requisicao(CPF_CRIANCA, semAceite)))
                        .header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Respostas condicionais do formulario sao exigidas so quando se aplicam")
    void condicionaisDoFormulario() {
        PreCadastroDadosDTO base = dados(CPF_MAE);

        PreCadastroDadosDTO saudeSemDescricao = comSaude(base, new Saude(true, " ", "Tia Teste", "16998765432"));
        PreCadastroDadosDTO programaSemNome = comTransferencia(base, new Transferencia(true, Set.of(), null, null));
        PreCadastroDadosDTO outroSemNome = comTransferencia(base,
                new Transferencia(true, Set.of(ProgramaTransferencia.OUTRO), "", null));
        PreCadastroDadosDTO semQuemBusca = comAutorizacoes(base, new Autorizacoes(true, false, null));
        PreCadastroDadosDTO semMaeNemCpfDoLegal = new PreCadastroDadosDTO(base.termos(), base.crianca(), null, null,
                new ResponsavelLegal("Avo Teste", "Avó", null, "16991112222"), base.enderecoFamilia(),
                base.transferencia(), base.saude(), base.autorizacoes());

        for (PreCadastroDadosDTO invalido : List.of(saudeSemDescricao, programaSemNome, outroSemNome, semQuemBusca,
                semMaeNemCpfDoLegal)) {
            assertThatThrownBy(() -> preCadastroService.validarCondicionais(invalido))
                    .isInstanceOf(RegraNegocioException.class);
        }

        preCadastroService.validarCondicionais(comSaude(base, new Saude(false, null, "Tia Teste", "16998765432")));
        preCadastroService.validarCondicionais(comAutorizacoes(base, new Autorizacoes(true, true, null)));
        preCadastroService.validarCondicionais(dadosSemMae());
    }

    @Test
    @DisplayName("NIS, regra de frequencia e comprovante de endereco sao obrigatorios no envio publico")
    void camposObrigatoriosDoEnvioPublico() throws Exception {
        PreCadastroDadosDTO base = dados(CPF_MAE);
        long antes = preCadastroRepository.count();

        mvc.perform(multipart("/api/pre-cadastro")
                        .file(partDados(requisicao(CPF_CRIANCA, comTransferencia(base, new Transferencia(false, Set.of(), null, null)))))
                        .file(imagem("comprovanteEndereco")).param("captcha", TOKEN_VALIDO).header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isBadRequest());
        PreCadastroDadosDTO semCiencia = new PreCadastroDadosDTO(new Termos(true, false, false), base.crianca(), base.mae(),
                base.pai(), base.responsavelLegal(), base.enderecoFamilia(), base.transferencia(), base.saude(), base.autorizacoes());
        mvc.perform(multipart("/api/pre-cadastro")
                        .file(partDados(requisicao(CPF_CRIANCA, semCiencia)))
                        .file(imagem("comprovanteEndereco")).param("captcha", TOKEN_VALIDO).header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/pre-cadastro")
                        .file(partDados(requisicao(CPF_CRIANCA, base)))
                        .param("captcha", TOKEN_VALIDO).header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Envie o comprovante de endereço."));

        assertThat(preCadastroRepository.count()).isEqualTo(antes);
        verify(storage, never()).salvar(any(), any(), any());
    }

    @Test
    @DisplayName("Captcha recusado nao grava nem envia arquivos")
    void captchaRecusado() throws Exception {
        long antes = preCadastroRepository.count();

        mvc.perform(multipart("/api/pre-cadastro").file(partDados(requisicao(CPF_CRIANCA, dados(CPF_MAE))))
                        .file(imagem("comprovanteEndereco"))
                        .param("captcha", "token-falso")
                        .header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isConflict());

        assertThat(preCadastroRepository.count()).isEqualTo(antes);
        verify(storage, never()).salvar(any(), any(), any());
    }

    @Test
    @DisplayName("Limite global do dia bloqueia o envio antes de enviar arquivos")
    void limiteGlobalDoDia() throws Exception {
        doReturn(180L).when(preCadastroRepository).countByCriadoEmGreaterThanEqual(any());

        mvc.perform(multipart("/api/pre-cadastro").file(partDados(requisicao(CPF_CRIANCA, dados(CPF_MAE))))
                        .file(imagem("comprovanteEndereco"))
                        .param("captcha", TOKEN_VALIDO)
                        .header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isConflict());

        verify(storage, never()).salvar(any(), any(), any());
    }

    @Test
    @DisplayName("Mesmo IP: um envio a cada 2 minutos e no maximo 20 por dia")
    void limiteDeEnviosPorIp() {
        String ip = ipNovo();
        Instant inicio = Instant.parse("2026-11-03T11:00:00Z");

        preCadastroService.registrarEnvio(ip, inicio);
        assertThatThrownBy(() -> preCadastroService.registrarEnvio(ip, inicio.plus(Duration.ofMinutes(1))))
                .isInstanceOf(RegraNegocioException.class);
        for (int i = 1; i < 20; i++) {
            preCadastroService.registrarEnvio(ip, inicio.plus(Duration.ofMinutes(3L * i)));
        }
        assertThatThrownBy(() -> preCadastroService.registrarEnvio(ip, inicio.plus(Duration.ofMinutes(60))))
                .isInstanceOf(RegraNegocioException.class);
        preCadastroService.registrarEnvio(ip, inicio.plus(Duration.ofDays(1)));
    }

    @Test
    @DisplayName("Foto e documentos sao opcionais; o que falta vira alerta e a aprovacao leva os arquivos para o atendido")
    void anexosOpcionaisComAlertaETransferidosNaAprovacao() throws Exception {
        Long semAnexos = enviarPublico(requisicao(CPF_CRIANCA_SEM_ANEXOS, dados(CPF_MAE)));
        mvc.perform(get("/api/pre-cadastro/" + semAnexos).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.anexosFaltando.length()").value(3));

        Long id = enviarPublico(requisicao(CPF_CRIANCA_COM_ANEXOS, dados(CPF_MAE_COM_ANEXOS)),
                imagem("foto"), imagem("documentosCrianca"), imagem("documentosResponsavel"));
        mvc.perform(get("/api/pre-cadastro").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$[?(@.preCadastroId == " + id + ")].anexosFaltando.length()").value(0));
        String detalhe = mvc.perform(get("/api/pre-cadastro/" + id).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.arquivos.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        int arquivoId = JsonPath.read(detalhe, "$.arquivos[0].arquivoId");
        mvc.perform(get("/api/arquivos/" + arquivoId + "/url").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-cadastro/" + id + "/arquivos/" + arquivoId + "/url").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk());

        String rascunho = mvc.perform(get("/api/pre-cadastro/" + id + "/rascunho-assistido").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/pre-cadastro/" + id + "/aprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunho))
                .andExpect(status().isOk());

        Long assistidoId = assistidoRepository.findByCpf(CPF_CRIANCA_COM_ANEXOS).orElseThrow().getAssistidoId();
        Long maeId = responsavelRepository.findByCpf(CPF_MAE_COM_ANEXOS).orElseThrow().getResponsavelId();
        assertThat(jdbcTemplate.queryForList(
                "select categoria from adra.arquivo where entidade_tipo = 'ASSISTIDO' and entidade_id = ? order by categoria",
                String.class, assistidoId)).containsExactly("DECLARACAO_RESIDENCIA", "DOCUMENTO_IDENTIDADE", "FOTO_PERFIL");
        assertThat(jdbcTemplate.queryForList(
                "select categoria from adra.arquivo where entidade_tipo = 'RESPONSAVEL' and entidade_id = ?",
                String.class, maeId)).containsExactly("DOCUMENTO_IDENTIDADE");
        assertThat(preCadastroRepository.findById(id).orElseThrow().getStatus().name()).isEqualTo("CONVERTIDO");
        assertThat(preCadastroRepository.findById(id).orElseThrow().getAssistidoId()).isEqualTo(assistidoId);
    }

    @Test
    @DisplayName("Educador Social e Oficineiro recebem 403 ao aprovar e reprovar")
    void educadorEOficineiroNaoAprovamNemReprovam() throws Exception {
        String corpoAprovar = jsonMapper.writeValueAsString(assistido("Qualquer", CPF_CRIANCA, CPF_MAE));
        for (NomeNivelPermissao perfil : List.of(EDUCADOR_SOCIAL, OFICINEIRO)) {
            mvc.perform(post("/api/pre-cadastro/999999/aprovar").with(comPerfil(perfil))
                            .contentType(MediaType.APPLICATION_JSON).content(corpoAprovar))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/pre-cadastro/999999/reprovar").with(comPerfil(perfil))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Teste\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(colocarNaLista(999999L, "Teste").with(comPerfil(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/pre-cadastro/999999/observacao").with(comPerfil(perfil))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\": \"Teste\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Inscricao vai para a lista de espera com observacao e e aprovada a partir dela")
    void listaDeEsperaAprovada() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA_LISTA, dados(CPF_MAE_LISTA)));
        String registroNaLista = "$[?(@.preCadastroId == " + id + ")]";

        mvc.perform(colocarNaLista(id, "Ligar pela manha").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LISTA_ESPERA"))
                .andExpect(jsonPath("$.observacao").value("Ligar pela manha"));
        mvc.perform(colocarNaLista(id, "Segunda vez").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/pre-cadastro").param("status", "PENDENTE").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath(registroNaLista).isEmpty());
        mvc.perform(get("/api/pre-cadastro").param("status", "LISTA_ESPERA").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath(registroNaLista + ".rendaFamiliar").value(2300.0))
                .andExpect(jsonPath(registroNaLista + ".possuiProblemaSaude").value(true))
                .andExpect(jsonPath(registroNaLista + ".programasTransferencia[0]").value("BOLSA_FAMILIA"));

        mvc.perform(patch("/api/pre-cadastro/" + id + "/observacao").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\": \"Familia atendeu\"}"))
                .andExpect(status().isNoContent());
        String rascunho = mvc.perform(get("/api/pre-cadastro/" + id + "/rascunho-assistido").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/pre-cadastro/" + id).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$.observacao").value("Familia atendeu"));

        mvc.perform(post("/api/pre-cadastro/" + id + "/aprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content(rascunho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTIDO"));

        assertThat(jdbcTemplate.queryForObject("""
                select valor_anterior->>'status' from adra.log_auditoria
                where entidade_afetada = 'pre_cadastro' and entidade_id = ? and valor_novo->>'status' = 'CONVERTIDO'
                """, String.class, id)).isEqualTo("LISTA_ESPERA");
        mvc.perform(patch("/api/pre-cadastro/" + id + "/observacao").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observacao\": \"Tarde demais\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Inscricao da lista de espera pode ser reprovada")
    void listaDeEsperaReprovada() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA_LISTA_REPROVADA, dados(CPF_MAE)));
        mvc.perform(colocarNaLista(id, null).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/pre-cadastro/" + id + "/reprovar").with(comPerfil(ASSISTENTE_SOCIAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Mudou de cidade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCARTADO"));
    }

    @Test
    @DisplayName("Lista de espera mantem o alerta de CPF ja cadastrado")
    void listaDeEsperaAlertaCpf() throws Exception {
        Long existenteId = assistidoService.cadastrar(assistido("Crianca Da Lista", CPF_CRIANCA_LISTA_JA_ATENDIDA,
                CPF_RESPONSAVEL_LISTA_JA_ATENDIDA)).assistidoId();
        Long id = enviarPublico(requisicao(CPF_CRIANCA_LISTA_JA_ATENDIDA, dados(CPF_MAE)));

        mvc.perform(colocarNaLista(id, null).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistidoExistenteId").value(existenteId));
        mvc.perform(get("/api/pre-cadastro").param("status", "LISTA_ESPERA").with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(jsonPath("$[?(@.preCadastroId == " + id + ")].cpfJaCadastrado").value(true));
    }

    @Test
    @DisplayName("Pendentes e lista de espera vem do mais antigo para o mais novo")
    void ordemDeChegada() throws Exception {
        Long maisAntigo = enviarPublico(requisicao(CPF_CRIANCA_MAIS_ANTIGA, dados(CPF_MAE)));
        Long maisNovo = enviarPublico(requisicao(CPF_CRIANCA_MAIS_NOVA, dados(CPF_MAE)));

        assertThat(idsDaLista("PENDENTE")).containsSubsequence(maisAntigo.intValue(), maisNovo.intValue());

        mvc.perform(colocarNaLista(maisAntigo, null).with(comPerfil(ASSISTENTE_SOCIAL))).andExpect(status().isOk());
        mvc.perform(colocarNaLista(maisNovo, null).with(comPerfil(ASSISTENTE_SOCIAL))).andExpect(status().isOk());

        assertThat(idsDaLista("LISTA_ESPERA")).containsSubsequence(maisAntigo.intValue(), maisNovo.intValue());
    }

    @Test
    @DisplayName("Inscricao so fica vista quando a Assistente Social abre")
    void vistaPelaAssistenteSocial() throws Exception {
        Long id = enviarPublico(requisicao(CPF_CRIANCA_VISTA, dados(CPF_MAE)));

        mvc.perform(get("/api/pre-cadastro/" + id).with(comPerfil(COORDENADOR))).andExpect(status().isOk());
        assertThat(vistaNaLista(id)).isFalse();

        when(usuarioAutenticadoService.temPerfil(ASSISTENTE_SOCIAL)).thenReturn(true);
        mvc.perform(get("/api/pre-cadastro/" + id).with(comPerfil(ASSISTENTE_SOCIAL))).andExpect(status().isOk());
        assertThat(vistaNaLista(id)).isTrue();
    }

    private boolean vistaNaLista(Long id) throws Exception {
        String lista = mvc.perform(get("/api/pre-cadastro").param("status", "PENDENTE").with(comPerfil(COORDENADOR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Boolean> vista = JsonPath.read(lista, "$[?(@.preCadastroId == " + id + ")].vistoAssistenteSocial");
        return vista.getFirst();
    }

    private MockHttpServletRequestBuilder colocarNaLista(Long id, String observacao) throws Exception {
        return patch("/api/pre-cadastro/" + id + "/lista-espera")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new ObservacaoPreCadastroRequestDTO(observacao)));
    }

    private List<Integer> idsDaLista(String statusDaLista) throws Exception {
        String lista = mvc.perform(get("/api/pre-cadastro").param("status", statusDaLista).with(comPerfil(ASSISTENTE_SOCIAL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(lista, "$[*].preCadastroId");
    }

    private Long enviarPublico(PreCadastroRequestDTO requisicao, MockMultipartFile... anexos) throws Exception {
        var envio = multipart("/api/pre-cadastro").file(partDados(requisicao)).file(imagem("comprovanteEndereco")).param("captcha", TOKEN_VALIDO);
        for (MockMultipartFile anexo : anexos) {
            envio.file(anexo);
        }
        String resposta = mvc.perform(envio.header("X-Forwarded-For", ipNovo()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.preCadastroId")).longValue();
    }

    private MockMultipartFile partDados(PreCadastroRequestDTO requisicao) {
        return new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE, jsonMapper.writeValueAsBytes(requisicao));
    }

    private int conexoesEmUso() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    private static MockMultipartFile imagem(String parte) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        return new MockMultipartFile(parte, parte + ".png", "image/png", png.toByteArray());
    }

    private static String ipNovo() {
        return "teste-" + UUID.randomUUID();
    }

    private static PreCadastroRequestDTO requisicao(String cpfCrianca, PreCadastroDadosDTO dados) {
        return new PreCadastroRequestDTO("Crianca " + cpfCrianca, LocalDate.of(2016, 4, 12), cpfCrianca,
                Sexo.FEMININO, "familia@teste.local", dados, "");
    }

    private static PreCadastroDadosDTO dados(String cpfMae) {
        EnderecoDTO endereco = new EnderecoDTO("Rua das Flores", "100", "Centro", null);
        return new PreCadastroDadosDTO(
                new Termos(true, false, true),
                new PreCadastroDadosDTO.Crianca(null),
                new Mae("Mae Teste", LocalDate.of(1990, 1, 20), cpfMae, Escolaridade.MEDIO_COMPLETO,
                        EstadoCivil.SOLTEIRO, null, "Cozinheira", new BigDecimal("1500.00"), "16991234567"),
                new Pai("Pai Teste", null, "Pedreiro", new BigDecimal("800.00"), "1633334444"),
                null,
                endereco,
                new Transferencia(true, Set.of(ProgramaTransferencia.BOLSA_FAMILIA), null, "12345678901"),
                new Saude(true, "Asma leve", "Tia Teste", "16998765432"),
                new Autorizacoes(true, false, "Avó Teste"));
    }

    private static PreCadastroDadosDTO dadosSemMae() {
        PreCadastroDadosDTO base = dados(CPF_MAE);
        return new PreCadastroDadosDTO(base.termos(), base.crianca(), null, null,
                new ResponsavelLegal("Avo Teste", "Avó", CPF_RESPONSAVEL_LEGAL, "16991112222"),
                base.enderecoFamilia(), base.transferencia(), base.saude(), base.autorizacoes());
    }

    private static PreCadastroDadosDTO comSaude(PreCadastroDadosDTO d, Saude saude) {
        return new PreCadastroDadosDTO(d.termos(), d.crianca(), d.mae(), d.pai(), d.responsavelLegal(),
                d.enderecoFamilia(), d.transferencia(), saude, d.autorizacoes());
    }

    private static PreCadastroDadosDTO comTransferencia(PreCadastroDadosDTO d, Transferencia transferencia) {
        return new PreCadastroDadosDTO(d.termos(), d.crianca(), d.mae(), d.pai(), d.responsavelLegal(),
                d.enderecoFamilia(), transferencia, d.saude(), d.autorizacoes());
    }

    private static PreCadastroDadosDTO comAutorizacoes(PreCadastroDadosDTO d, Autorizacoes autorizacoes) {
        return new PreCadastroDadosDTO(d.termos(), d.crianca(), d.mae(), d.pai(), d.responsavelLegal(),
                d.enderecoFamilia(), d.transferencia(), d.saude(), autorizacoes);
    }

    private static AssistidoRequestDTO assistido(String nome, String cpf, String cpfResponsavel) {
        VinculoFamiliarComResponsavelRequestDTO responsavel = new VinculoFamiliarComResponsavelRequestDTO(
                "Responsavel " + nome, null, cpfResponsavel, null, null, null, null,
                null, null, null, null, "Mãe", true, false, false);
        return new AssistidoRequestDTO(nome, LocalDate.of(2015, 6, 1), cpf, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(responsavel), null);
    }

    private RequestPostProcessor comPerfil(NomeNivelPermissao perfil) {
        return jwt()
                .jwt(token -> token.subject("1").claim(TokenService.CLAIM_PERFIL, perfil.name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
