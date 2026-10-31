package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.ContainerPostgresAdra;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ArquivarRegistroRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.DocumentoDevolvidoRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.FichaDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.RegistroDisciplinarResponseDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.ResolverConsequenciaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.disciplinar.SinalizacaoDisciplinarDTO;
import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.OrigemRegistroDisciplinar;
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
 * FT-04: progressão disciplinar (cartazes "Disciplina ADRA" e comunicado de
 * uniforme), contadores do assistido, sinalizações e permissões.
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
    @DisplayName("3ª ocorrência gera advertência escrita automaticamente")
    void terceiraOcorrenciaGeraAdvertencia() {
        registrar(palavrao);
        registrar(palavrao);
        List<RegistroDisciplinarResponseDTO> terceira = registrar(palavrao);

        assertThat(terceira).extracting(RegistroDisciplinarResponseDTO::nivel, RegistroDisciplinarResponseDTO::origem)
                .containsExactly(
                        tuple(NivelDisciplinar.OCORRENCIA, OrigemRegistroDisciplinar.MANUAL),
                        tuple(NivelDisciplinar.ADVERTENCIA, OrigemRegistroDisciplinar.PROGRESSAO));
        assertThat(terceira.get(0).consequencia()).isEqualTo(ConsequenciaDisciplinar.ADVERTENCIA_GERADA);
        assertThat(terceira.get(1).registroOrigemId()).isEqualTo(terceira.get(0).registroId());
        assertThat(terceira.get(1).documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA);

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isEqualTo(3);
        assertThat(ficha.resumo().advertenciasAtivas()).isEqualTo(1);
        assertThat(ficha.resumo().faltamParaSuspensao()).isEqualTo(2);

        Assistido atualizado = assistidoRepository.findById(assistido.getAssistidoId()).orElseThrow();
        assertThat(atualizado.getTotalOcorrenciasAtivas()).isEqualTo(3);
        assertThat(atualizado.getTotalAdvertenciasAtivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Motivo grave entra direto no nível dele; 3ª advertência gera suspensão de 3 dias")
    void terceiraAdvertenciaGeraSuspensao() {
        List<RegistroDisciplinarResponseDTO> primeira = registrar(agressao);
        assertThat(primeira).singleElement().extracting(RegistroDisciplinarResponseDTO::nivel)
                .isEqualTo(NivelDisciplinar.ADVERTENCIA);

        registrar(agressao);
        List<RegistroDisciplinarResponseDTO> terceira = registrar(agressao);

        RegistroDisciplinarResponseDTO suspensao = terceira.get(1);
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
    @DisplayName("9 ocorrências encadeiam: 3 advertências e 1 suspensão")
    void progressaoEncadeia() {
        for (int i = 0; i < 9; i++) {
            registrar(palavrao);
        }

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isEqualTo(9);
        assertThat(ficha.resumo().advertenciasAtivas()).isEqualTo(3);
        assertThat(ficha.resumo().suspensoesAtivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("3ª suspensão deixa 'perde o próximo passeio' pendente até a coordenação resolver")
    void terceiraSuspensaoPerdePasseio() {
        registrar(bullying);
        registrar(bullying);
        RegistroDisciplinarResponseDTO terceira = registrar(bullying).get(0);

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
        RegistroDisciplinarResponseDTO primeiro = registrar(uniforme).get(0);
        assertThat(primeiro.sequencia()).isEqualTo(1);
        assertThat(primeiro.documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.PENDENTE_ASSINATURA);

        registrar(uniforme);
        List<RegistroDisciplinarResponseDTO> terceiro = registrar(uniforme);
        assertThat(terceiro).hasSize(1); // não gera advertência
        assertThat(terceiro.get(0).consequencia()).isEqualTo(ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL);

        FichaDisciplinarDTO ficha = disciplinarService.buscarFicha(assistido.getAssistidoId());
        assertThat(ficha.resumo().ocorrenciasAtivas()).isZero();
        assertThat(ficha.resumo().uniformeAtivas()).isEqualTo(3);
        assertThat(ficha.resumo().retornoComResponsavelPendente()).isTrue();

        disciplinarService.resolverConsequencia(terceiro.get(0).registroId(),
                new ResolverConsequenciaRequestDTO(HOJE, "Mãe compareceu à conversa com a coordenação"));
        assertThat(disciplinarService.buscarSinalizacoes(List.of(assistido.getAssistidoId()), HOJE)).isEmpty();
    }

    @Test
    @DisplayName("Documento devolvido assinado; ocorrência simples não tem documento")
    void documentoDevolvido() {
        RegistroDisciplinarResponseDTO advertencia = registrar(agressao).get(0);
        RegistroDisciplinarResponseDTO devolvida =
                disciplinarService.marcarDocumentoDevolvido(advertencia.registroId(), new DocumentoDevolvidoRequestDTO(null));
        assertThat(devolvida.documentoStatus()).isEqualTo(StatusDocumentoDisciplinar.DEVOLVIDO_ASSINADO);

        RegistroDisciplinarResponseDTO ocorrencia = registrar(palavrao).get(0);
        assertThatThrownBy(() -> disciplinarService.marcarDocumentoDevolvido(ocorrencia.registroId(), null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Arquivar tira o registro das contagens e só o Coordenador pode")
    void arquivar() {
        autenticarComo(educadorSocial);
        RegistroDisciplinarResponseDTO ocorrencia = registrar(palavrao).get(0);

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

        assertThat(registrar(palavrao)).singleElement()
                .extracting(RegistroDisciplinarResponseDTO::nivel).isEqualTo(NivelDisciplinar.OCORRENCIA);
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

    // ------------------------------------------------------------------

    private List<RegistroDisciplinarResponseDTO> registrar(MotivoDisciplinar motivo) {
        return disciplinarService.registrar(assistido.getAssistidoId(),
                new RegistroDisciplinarRequestDTO(motivo.getMotivoId(), HOJE, "Descrição do fato", null, null, null));
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
