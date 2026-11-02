package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.GerarPelaRecomendacaoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroCriadoDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroVariosRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ZerarContagemRequestDTO;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.Sexo;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.MotivoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.MotivoDisciplinarRepository;
import adra.ifsp.edu.br.api.domain.repository.NivelPermissaoRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.AcessoNegadoException;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import adra.ifsp.edu.br.api.web.controller.DisciplinarController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * FT-04/FT-22: recomendações disciplinares (cartazes "Disciplina ADRA" e
 * comunicado de uniforme), registro com vários atendidos, contadores do
 * assistido, sinalizações e permissões.
 */
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
class DisciplinarServiceIT {

    @Autowired private NivelPermissaoRepository nivelPermissaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private MotivoDisciplinarRepository motivoRepository;
    @Autowired private DisciplinarService disciplinarService;
    @Autowired private DisciplinarController disciplinarController;
    @Autowired private DataSource dataSource;

    private static final LocalDate HOJE = LocalDate.now();

    private Usuario coordenador;
    private Usuario educadorSocial;
    private Usuario oficineiro;
    private Assistido assistido;
    private MotivoDisciplinar palavrao;
    private MotivoDisciplinar agressao;
    private MotivoDisciplinar bullying;
    private MotivoDisciplinar uniforme;

    @BeforeEach
    void montarCenario() throws SQLException {
        try (var conexao = dataSource.getConnection(); var stmt = conexao.createStatement()) {
            stmt.execute("alter table adra.usuario alter column criado_em set default now()");
            stmt.execute("alter table adra.usuario alter column atualizado_em set default now()");
            stmt.execute("alter table adra.assistido alter column criado_em set default now()");
            stmt.execute("alter table adra.assistido alter column atualizado_em set default now()");
            stmt.execute("alter table adra.log_auditoria alter column data_hora set default now()");
        }

        coordenador = criarUsuario(NomeNivelPermissao.COORDENADOR, "coord-ft04@teste.local");
        educadorSocial = criarUsuario(NomeNivelPermissao.EDUCADOR_SOCIAL, "socio-ft04@teste.local");
        oficineiro = criarUsuario(NomeNivelPermissao.OFICINEIRO, "oficineiro-ft04@teste.local");
        autenticarComo(coordenador);

        assistido = assistidoRepository.save(Assistido.builder()
                .nomeCompleto("Assistido FT-04")
                .sexo(Sexo.FEMININO)
                .dataNascimento(LocalDate.of(2014, 5, 10))
                .dataEntrada(HOJE.minusMonths(6))
                .status(StatusGeral.ATIVO)
                .build());

        palavrao = criarMotivo("Falar palavrão frequentemente", NivelDisciplinar.OCORRENCIA);
        agressao = criarMotivo("Agressão", NivelDisciplinar.ADVERTENCIA);
        bullying = criarMotivo("Bullying", NivelDisciplinar.SUSPENSAO);
        uniforme = criarMotivo("Uso do uniforme", NivelDisciplinar.UNIFORME);
    }

    @Test
    @DisplayName("CA-FT22.1: a 3ª ocorrência não gera advertência, só a recomendação")
    void terceiraOcorrenciaRecomendaAdvertencia() {
        registrar(palavrao);
        registrar(palavrao);
        RegistroCriadoDTO terceira = registrar(palavrao);

        assertThat(terceira.registro().nivel()).isEqualTo(NivelDisciplinar.OCORRENCIA);
        assertThat(terceira.registro().consequencia()).isEqualTo(ConsequenciaDisciplinar.RECOMENDA_ADVERTENCIA);
        assertThat(terceira.registro().consequenciaPendente()).isTrue();
        assertThat(terceira.recomendacao().registroId()).isEqualTo(terceira.registro().registroId());

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isEqualTo(3);
        assertThat(ficha.resumo().advertenciasAtivas()).isZero();
        assertThat(disciplinarService.buscarPendencias().recomendacoesAdvertencia()).isEqualTo(1);

        Assistido atualizado = assistidoRepository.findById(assistido.getAssistidoId()).orElseThrow();
        assertThat(atualizado.getTotalOcorrenciasAtivas()).isEqualTo(3);
        assertThat(atualizado.getTotalAdvertenciasAtivas()).isZero();
    }

    @Test
    @DisplayName("CA-FT22.2: a 4ª ocorrência é aceita e a recomendação pendente continua na resposta")
    void quartaOcorrenciaAceita() {
        registrar(palavrao);
        registrar(palavrao);
        Long recomendacaoId = registrar(palavrao).registro().registroId();

        RegistroCriadoDTO quarta = registrar(palavrao);

        assertThat(quarta.registro().sequencia()).isEqualTo(4);
        assertThat(quarta.registro().consequencia()).isNull();
        assertThat(quarta.recomendacao().registroId()).isEqualTo(recomendacaoId);
    }

    @Test
    @DisplayName("CA-FT22.4: o Coordenador gera a advertência pela recomendação e ela some das pendências")
    void coordenadorGeraAdvertenciaPelaRecomendacao() {
        registrar(palavrao);
        registrar(palavrao);
        Long origemId = registrar(palavrao).registro().registroId();

        RegistroCriadoDTO gerada = disciplinarController.gerarPelaRecomendacao(origemId,
                new GerarPelaRecomendacaoRequestDTO(HOJE, "Advertência pela 3ª ocorrência", null, null)).getBody();

        assertThat(gerada.registro().nivel()).isEqualTo(NivelDisciplinar.ADVERTENCIA);
        assertThat(gerada.registro().origem()).isEqualTo(OrigemRegistroDisciplinar.RECOMENDACAO);
        assertThat(gerada.registro().registroOrigemId()).isEqualTo(origemId);
        assertThat(gerada.registro().documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA);
        assertThat(gerada.recomendacao()).isNull();

        RegistroDisciplinarResponseDTO origem = disciplinarService.buscarRegistro(origemId);
        assertThat(origem.consequenciaPendente()).isFalse();
        assertThat(origem.consequenciaResolvidaEm()).isEqualTo(HOJE);
        assertThat(origem.consequenciaResolvidaPorNome()).isEqualTo(coordenador.getNomeCompleto());
        assertThat(disciplinarService.buscarPendencias().recomendacoesAdvertencia()).isZero();
        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().advertenciasAtivas()).isEqualTo(1);

        assertThatThrownBy(() -> disciplinarService.gerarPelaRecomendacao(origemId,
                new GerarPelaRecomendacaoRequestDTO(HOJE, "De novo", null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi resolvida");
    }

    @Test
    @DisplayName("Só o Coordenador gera pela recomendação; dispensar resolve sem criar registro")
    void gerarPermissaoEDispensar() {
        registrar(palavrao);
        registrar(palavrao);
        Long origemId = registrar(palavrao).registro().registroId();
        GerarPelaRecomendacaoRequestDTO dto = new GerarPelaRecomendacaoRequestDTO(HOJE, "Advertência", null, null);

        autenticarComo(educadorSocial);
        assertThatThrownBy(() -> disciplinarController.gerarPelaRecomendacao(origemId, dto))
                .isInstanceOf(AuthorizationDeniedException.class);

        autenticarComo(coordenador);
        disciplinarController.resolverConsequencia(origemId,
                new ResolverConsequenciaRequestDTO(HOJE, "Conversado com a família; sem advertência por ora"));

        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().advertenciasAtivas()).isZero();
        assertThatThrownBy(() -> disciplinarService.gerarPelaRecomendacao(origemId, dto))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Motivo grave entra direto no nível dele; 3ª advertência recomenda suspensão de 3 dias")
    void terceiraAdvertenciaRecomendaSuspensao() {
        RegistroCriadoDTO primeira = registrar(agressao);
        assertThat(primeira.registro().nivel()).isEqualTo(NivelDisciplinar.ADVERTENCIA);
        assertThat(primeira.recomendacao()).isNull();

        registrar(agressao);
        RegistroCriadoDTO terceira = registrar(agressao);
        assertThat(terceira.registro().consequencia()).isEqualTo(ConsequenciaDisciplinar.RECOMENDA_SUSPENSAO);
        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().suspensoesAtivas()).isZero();

        RegistroDisciplinarResponseDTO suspensao = disciplinarService.gerarPelaRecomendacao(
                terceira.registro().registroId(),
                new GerarPelaRecomendacaoRequestDTO(HOJE, "Suspensão pela 3ª advertência", null, null)).registro();
        assertThat(suspensao.nivel()).isEqualTo(NivelDisciplinar.SUSPENSAO);
        assertThat(suspensao.suspensaoInicio()).isEqualTo(HOJE);
        assertThat(suspensao.suspensaoFim()).isEqualTo(HOJE.plusDays(2));

        List<SinalizacaoDisciplinarDTO> sinalizacoes =
                disciplinarService.buscarSinalizacoes(List.of(assistido.getAssistidoId()), HOJE.plusDays(1));
        assertThat(sinalizacoes).singleElement()
                .extracting(SinalizacaoDisciplinarDTO::suspensoAte).isEqualTo(HOJE.plusDays(2));

        assertThat(disciplinarService.buscarSinalizacoes(List.of(assistido.getAssistidoId()), HOJE.plusDays(3)))
                .isEmpty();
    }

    @Test
    @DisplayName("9 ocorrências não geram nada sozinhas: 3 recomendações de advertência")
    void semEscadaAutomatica() {
        for (int i = 0; i < 9; i++) {
            registrar(palavrao);
        }

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isEqualTo(9);
        assertThat(ficha.resumo().advertenciasAtivas()).isZero();
        assertThat(ficha.registros()).filteredOn(RegistroDisciplinarResponseDTO::consequenciaPendente).hasSize(3);
    }

    @Test
    @DisplayName("Uma ocorrência com 3 atendidos cria 3 registros, cada um com a sua sequência")
    void registrarVarios() {
        registrar(palavrao);
        Assistido segundo = criarAssistido("Segundo FT-22", StatusGeral.ATIVO);
        Assistido terceiro = criarAssistido("Terceiro FT-22", StatusGeral.ATIVO);

        List<RegistroCriadoDTO> criados = disciplinarController.registrarVarios(new RegistroVariosRequestDTO(
                List.of(assistido.getAssistidoId(), segundo.getAssistidoId(), terceiro.getAssistidoId()),
                palavrao.getMotivoId(), HOJE, "Briga no intervalo")).getBody();

        assertThat(criados).extracting(c -> c.registro().assistidoId(), c -> c.registro().sequencia())
                .containsExactly(
                        tuple(assistido.getAssistidoId(), 2),
                        tuple(segundo.getAssistidoId(), 1),
                        tuple(terceiro.getAssistidoId(), 1));
    }

    @Test
    @DisplayName("Se um dos atendidos não estiver ativo, nenhum registro é gravado")
    void registrarVariosTudoOuNada() {
        Assistido segundo = criarAssistido("Segundo FT-22", StatusGeral.ATIVO);
        Assistido inativo = criarAssistido("Inativo FT-22", StatusGeral.INATIVO);
        RegistroVariosRequestDTO dto = new RegistroVariosRequestDTO(
                List.of(assistido.getAssistidoId(), segundo.getAssistidoId(), inativo.getAssistidoId()),
                palavrao.getMotivoId(), HOJE, "Briga no intervalo");

        assertThatThrownBy(() -> disciplinarService.registrarVarios(dto))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Inativo FT-22");
        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).registros()).isEmpty();
        assertThat(disciplinarService.buscarFicha(segundo.getAssistidoId()).registros()).isEmpty();

        assertThatThrownBy(() -> disciplinarService.registrarVarios(new RegistroVariosRequestDTO(
                List.of(assistido.getAssistidoId(), assistido.getAssistidoId()),
                palavrao.getMotivoId(), HOJE, "Repetido")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("3ª suspensão deixa 'perde o próximo passeio' pendente até a coordenação resolver")
    void terceiraSuspensaoPerdePasseio() {
        registrar(bullying);
        registrar(bullying);
        RegistroDisciplinarResponseDTO terceira = registrar(bullying).registro();

        assertThat(terceira.consequencia()).isEqualTo(ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO);
        assertThat(terceira.consequenciaPendente()).isTrue();
        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().perdeProximoPasseio()).isTrue();

        disciplinarService.resolverConsequencia(terceira.registroId(),
                new ResolverConsequenciaRequestDTO(HOJE, "Não foi ao passeio do Parque Aquático"));

        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().perdeProximoPasseio()).isFalse();
    }

    @Test
    @DisplayName("Uniforme é trilha à parte: não conta como ocorrência e a 3ª exige retorno com o responsável")
    void uniformeTrilhaPropria() {
        RegistroDisciplinarResponseDTO primeiro = registrar(uniforme).registro();
        assertThat(primeiro.sequencia()).isEqualTo(1);
        assertThat(primeiro.documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA);

        registrar(uniforme);
        RegistroDisciplinarResponseDTO terceiro = registrar(uniforme).registro();
        assertThat(terceiro.consequencia()).isEqualTo(ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL);

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isZero();
        assertThat(ficha.resumo().uniformeAtivas()).isEqualTo(3);
        assertThat(ficha.resumo().retornoComResponsavelPendente()).isTrue();

        disciplinarService.resolverConsequencia(terceiro.registroId(),
                new ResolverConsequenciaRequestDTO(HOJE, "Mãe compareceu à conversa com a coordenação"));
        assertThat(disciplinarService.buscarSinalizacoes(List.of(assistido.getAssistidoId()), HOJE)).isEmpty();
    }

    @Test
    @DisplayName("Documento devolvido assinado; ocorrência simples não tem documento")
    void documentoDevolvido() {
        RegistroDisciplinarResponseDTO advertencia = registrar(agressao).registro();
        assertThat(advertencia.sexoAssistido()).isEqualTo(Sexo.FEMININO); // FT-25: pronome do documento
        RegistroDisciplinarResponseDTO devolvida =
                disciplinarService.marcarDocumentoDevolvido(advertencia.registroId(), new DocumentoDevolvidoRequestDTO(null));
        assertThat(devolvida.documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.DEVOLVIDO_ASSINADO);

        RegistroDisciplinarResponseDTO ocorrencia = registrar(palavrao).registro();
        assertThatThrownBy(() -> disciplinarService.marcarDocumentoDevolvido(ocorrencia.registroId(), null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Arquivar tira o registro das contagens e só o Coordenador pode")
    void arquivar() {
        autenticarComo(educadorSocial);
        RegistroDisciplinarResponseDTO ocorrencia = registrar(palavrao).registro();

        assertThatThrownBy(() -> disciplinarController.arquivar(ocorrencia.registroId(),
                new ArquivarRegistroRequestDTO("Lançado no aluno errado")))
                .isInstanceOf(AuthorizationDeniedException.class);

        autenticarComo(coordenador);
        disciplinarController.arquivar(ocorrencia.registroId(), new ArquivarRegistroRequestDTO("Lançado no aluno errado"));

        assertThat(disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().ocorrenciasAtivas()).isZero();
        assertThat(assistidoRepository.findById(assistido.getAssistidoId()).orElseThrow().getTotalOcorrenciasAtivas())
                .isZero();
        assertThatThrownBy(() -> disciplinarService.marcarDocumentoDevolvido(ocorrencia.registroId(), null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Educador Social só registra ocorrência")
    void educadorSocialSoRegistraOcorrencia() {
        autenticarComo(educadorSocial);

        assertThat(registrar(palavrao).registro().nivel()).isEqualTo(NivelDisciplinar.OCORRENCIA);
        assertThatThrownBy(() -> registrar(agressao)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> registrar(uniforme)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("Oficineiro não acessa dados disciplinares (DT-01)")
    void oficineiroNaoAcessa() {
        autenticarComo(oficineiro);
        assertThatThrownBy(() -> disciplinarController.buscarFicha(assistido.getAssistidoId()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("Não registra em assistido inativo nem com motivo inativo")
    void validacoes() {
        palavrao.setAtivo(false);
        motivoRepository.save(palavrao);
        assertThatThrownBy(() -> registrar(palavrao)).isInstanceOf(RegraNegocioException.class);

        assistido.setStatus(StatusGeral.INATIVO);
        assistidoRepository.save(assistido);
        assertThatThrownBy(() -> registrar(agressao)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Zerar tudo arquiva os registros ativos, zera os contadores e a próxima ocorrência é a 1ª")
    void zerarTudo() {
        for (int i = 0; i < 4; i++) {
            registrar(palavrao);
        }
        registrar(agressao);

        FichaDisciplinarDTO ficha = disciplinarController.zerarContagem(assistido.getAssistidoId(),
                new ZerarContagemRequestDTO("Início do ano letivo", null)).getBody();

        assertThat(ficha.resumo().ocorrenciasAtivas()).isZero();
        assertThat(ficha.resumo().advertenciasAtivas()).isZero();
        assertThat(ficha.registros()).isNotEmpty().allSatisfy(r -> {
            assertThat(r.arquivado()).isTrue();
            assertThat(r.arquivadoMotivo()).isEqualTo("Início do ano letivo");
        });

        Assistido atualizado = assistidoRepository.findById(assistido.getAssistidoId()).orElseThrow();
        assertThat(atualizado.getTotalOcorrenciasAtivas()).isZero();
        assertThat(atualizado.getTotalAdvertenciasAtivas()).isZero();

        assertThat(registrar(palavrao).registro().sequencia()).isEqualTo(1);
    }

    @Test
    @DisplayName("Zerar só ocorrências mantém os outros níveis; a próxima ocorrência é a 1ª")
    void zerarSoOcorrencias() {
        for (int i = 0; i < 4; i++) {
            registrar(palavrao);
        }
        long advertenciasAntes = disciplinarService.buscarFicha(assistido.getAssistidoId()).resumo().advertenciasAtivas();

        FichaDisciplinarDTO ficha = disciplinarService.zerarContagem(assistido.getAssistidoId(),
                new ZerarContagemRequestDTO("Acordo com a família", NivelDisciplinar.OCORRENCIA));

        assertThat(ficha.resumo().ocorrenciasAtivas()).isZero();
        assertThat(ficha.resumo().advertenciasAtivas()).isEqualTo(advertenciasAntes);
        assertThat(registrar(palavrao).registro().sequencia()).isEqualTo(1);
    }

    @Test
    @DisplayName("Só o Coordenador zera a contagem; sem registros ativos não há o que zerar")
    void zerarPermissaoEValidacao() {
        registrar(palavrao);
        Usuario assistenteSocial = criarUsuario(NomeNivelPermissao.ASSISTENTE_SOCIAL, "assistente-ft24@teste.local");
        ZerarContagemRequestDTO dto = new ZerarContagemRequestDTO("Motivo", null);

        for (Usuario usuario : List.of(educadorSocial, assistenteSocial)) {
            autenticarComo(usuario);
            assertThatThrownBy(() -> disciplinarController.zerarContagem(assistido.getAssistidoId(), dto))
                    .isInstanceOf(AuthorizationDeniedException.class);
        }

        autenticarComo(coordenador);
        assertThatThrownBy(() -> disciplinarService.zerarContagem(assistido.getAssistidoId(),
                new ZerarContagemRequestDTO("Motivo", NivelDisciplinar.SUSPENSAO)))
                .isInstanceOf(RegraNegocioException.class);
    }

    // ------------------------------------------------------------------

    private RegistroCriadoDTO registrar(MotivoDisciplinar motivo) {
        return disciplinarService.registrar(assistido.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(motivo.getMotivoId(), HOJE, "Descrição do fato", null, null, null));
    }

    private Assistido criarAssistido(String nome, StatusGeral status) {
        return assistidoRepository.save(Assistido.builder()
                .nomeCompleto(nome)
                .dataNascimento(LocalDate.of(2014, 5, 10))
                .dataEntrada(HOJE.minusMonths(6))
                .status(status)
                .build());
    }

    private MotivoDisciplinar criarMotivo(String nome, NivelDisciplinar nivel) {
        return motivoRepository.save(MotivoDisciplinar.builder().nome(nome).nivel(nivel).build());
    }

    private Usuario criarUsuario(NomeNivelPermissao perfil, String email) {
        NivelPermissao nivel = nivelPermissaoRepository.save(NivelPermissao.builder().nome(perfil).build());
        return usuarioRepository.save(Usuario.builder()
                .nivelPermissao(nivel)
                .nomeCompleto("Usuário " + perfil.name())
                .email(email)
                .build());
    }

    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste-" + usuario.getUsuarioId())
                .header("alg", "none")
                .subject(usuario.getUsuarioId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("perfil", usuario.getNivelPermissao().getNome().name())
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getNivelPermissao().getNome().name())));

        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
