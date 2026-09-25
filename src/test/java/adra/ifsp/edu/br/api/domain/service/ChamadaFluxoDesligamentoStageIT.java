package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.dto.assistido.AssistidoStatusRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.presenca.PresencaResponseDTO;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import adra.ifsp.edu.br.api.domain.model.Aula;
import adra.ifsp.edu.br.api.domain.model.Oficina;
import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.model.TurmaAlunos;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import adra.ifsp.edu.br.api.domain.repository.AssistidoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaAlunosRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.UsuarioRepository;
import adra.ifsp.edu.br.api.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MESMO fluxo de ChamadaFluxoDesligamentoIT, mas contra o Postgres real do
 * profile "stage" (aws-1-sa-east-1.pooler.supabase.com) em vez de
 * Testcontainers — só roda se .env.stage existir localmente (skip
 * automático em CI/outras máquinas). @Transactional no teste faz o Spring
 * dar ROLLBACK no final, com ou sem falha — NADA fica gravado no banco
 * compartilhado de stage. Não cria/edita nenhum usuário: reaproveita um
 * Coordenador já existente só pra autenticar.
 */
@SpringBootTest
@ActiveProfiles("stage")
@Transactional
@Tag("stage")
class ChamadaFluxoDesligamentoStageIT {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OficinaRepository oficinaRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private TurmaAlunosRepository turmaAlunosRepository;
    @Autowired private AulaRepository aulaRepository;
    @Autowired private AssistidoRepository assistidoRepository;
    @Autowired private AssistidoService assistidoService;
    @Autowired private PresencaService presencaService;

    private Turma turma;
    private Assistido assistido;
    private Aula aulaSemanaPassada;
    private Aula aulaAmanha;

    private static final LocalDate HOJE = LocalDate.now();
    private static final String PREFIXO_TESTE = "TESTE-CA70-AUTOMATIZADO";

    @BeforeEach
    void montarCenario() {
        Usuario coordenador = usuarioRepository.findAll().stream()
                .filter(u -> u.getNivelPermissao().getNome() == NomeNivelPermissao.COORDENADOR)
                .findFirst()
                .orElse(null);
        assumeTrue(coordenador != null,
                "Nenhum usuário COORDENADOR encontrado em stage — pulando teste (nada pra reaproveitar como autor).");
        autenticarComo(coordenador);

        Usuario oficineiroQualquer = usuarioRepository.findAll().stream().findFirst().orElseThrow();

        Oficina oficina = new Oficina();
        oficina.setNomeOficina(PREFIXO_TESTE + " Oficina");
        oficina.setOficineiroResponsavel(oficineiroQualquer);
        oficina = oficinaRepository.save(oficina);

        turma = new Turma();
        turma.setOficina(oficina);
        turma.setOficineiroResponsavel(oficineiroQualquer);
        turma.setNomeTurma(PREFIXO_TESTE + " Turma");
        turma.setTurno(Turno.MANHA);
        turma.setCapacidade(20);
        turma = turmaRepository.save(turma);

        assistido = Assistido.builder()
                .nomeCompleto(PREFIXO_TESTE + " Assistido")
                .dataNascimento(LocalDate.of(2015, 1, 1))
                .dataEntrada(HOJE.minusMonths(6))
                .status(StatusGeral.ATIVO)
                .turma(turma)
                .build();
        assistido = assistidoRepository.save(assistido);

        TurmaAlunos vinculo = new TurmaAlunos();
        vinculo.setTurma(turma);
        vinculo.setAssistido(assistido);
        vinculo.setDataEntrada(HOJE.minusMonths(6));
        vinculo.setStatus(StatusGeral.ATIVO);
        turmaAlunosRepository.save(vinculo);

        aulaSemanaPassada = criarAulaRealizada(HOJE.minusDays(7));
        aulaAmanha = criarAulaRealizada(HOJE.plusDays(1));

        presencaService.registrarChamada(aulaSemanaPassada.getAulaId(), List.of(
                new PresencaRequestDTO(aulaSemanaPassada.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)
        ));
    }

    @Test
    @DisplayName("[STAGE, rollback automático] Fluxo completo do CA-70 contra o banco real do time")
    void fluxoCompletoDesligamentoEReativacao() {
        assistidoService.alterarStatus(assistido.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.INATIVO, HOJE, "Mudou de cidade"));

        List<PresencaResponseDTO> chamadaAmanha = presencaService.buscarPorAula(aulaAmanha.getAulaId());
        assertThat(chamadaAmanha).extracting(PresencaResponseDTO::assistidoId)
                .doesNotContain(assistido.getAssistidoId());

        List<PresencaResponseDTO> chamadaSemanaPassada = presencaService.buscarPorAula(aulaSemanaPassada.getAulaId());
        assertThat(chamadaSemanaPassada)
                .filteredOn(p -> p.assistidoId().equals(assistido.getAssistidoId()))
                .hasSize(1)
                .first()
                .satisfies(p -> assertThat(p.statusPresenca()).isEqualTo(StatusPresenca.FALTA));

        assertThatThrownBy(() -> presencaService.registrarPresenca(
                new PresencaRequestDTO(aulaAmanha.getAulaId(), assistido.getAssistidoId(),
                        StatusPresenca.FALTA, null, null)))
                .isInstanceOf(RegraNegocioException.class);

        assistidoService.alterarStatus(assistido.getAssistidoId(),
                new AssistidoStatusRequestDTO(StatusGeral.ATIVO, null, null));

        List<PresencaResponseDTO> chamadaAmanhaDepoisDeReativar = presencaService.buscarPorAula(aulaAmanha.getAulaId());
        assertThat(chamadaAmanhaDepoisDeReativar).extracting(PresencaResponseDTO::assistidoId)
                .contains(assistido.getAssistidoId());
    }

    private Aula criarAulaRealizada(LocalDate data) {
        Aula aula = new Aula();
        aula.setTurma(turma);
        aula.setDataAula(data);
        aula.setStatusAula(StatusAula.REALIZADA);
        return aulaRepository.save(aula);
    }

    private static void autenticarComo(Usuario usuario) {
        Jwt jwt = Jwt.withTokenValue("token-fake-de-teste")
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
