package adra.ifsp.edu.br.api.domain.service;

import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.AulaRepository;
import adra.ifsp.edu.br.api.domain.repository.TurmaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra "o Oficineiro só acessa as turmas em que é o responsável" (turma.oficineiro_id).
 * Usada direto no @PreAuthorize, ex.:
 *   hasRole('OFICINEIRO') and @acessoTurma.podeAcessarAula(#idAula)
 * Sem permissão -> o Spring Security devolve 403 antes de entrar no método.
 * Reaproveitar nos materiais por aula.
 */
@Service("acessoTurma")
@RequiredArgsConstructor
public class AcessoTurmaService {

    private final AulaRepository aulaRepository;
    private final TurmaRepository turmaRepository;
    private final ArquivoRepository arquivoRepository;

    @Transactional(readOnly = true)
    public boolean podeAcessarAula(Long aulaId) {
        Long usuarioId = usuarioIdAtual();
        return aulaId != null && usuarioId != null
                && aulaRepository.existsByAulaIdAndTurmaOficineiroResponsavelUsuarioId(aulaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean podeAcessarTurma(Long turmaId) {
        Long usuarioId = usuarioIdAtual();
        return turmaId != null && usuarioId != null
                && turmaRepository.existsByTurmaIdAndOficineiroResponsavelUsuarioId(turmaId, usuarioId);
    }

    /** O arquivo é material de uma aula de turma deste oficineiro? */
    @Transactional(readOnly = true)
    public boolean podeAcessarArquivoDeAula(Long arquivoId) {
        if (arquivoId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .filter(a -> a.getEntidadeTipo() == EntidadeArquivo.AULA)
                .map(a -> podeAcessarAula(a.getEntidadeId()))
                .orElse(false);
    }

    /** É material de aula? (a Coordenação só vê esses, não exclui) */
    @Transactional(readOnly = true)
    public boolean ehArquivoDeAula(Long arquivoId) {
        if (arquivoId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .map(a -> a.getEntidadeTipo() == EntidadeArquivo.AULA)
                .orElse(false);
    }

    /** É material de aula ENVIADO por este usuário? (oficineiro só vê/abre/exclui os próprios) */
    @Transactional(readOnly = true)
    public boolean ehAutorDoArquivoDeAula(Long arquivoId) {
        Long usuarioId = usuarioIdAtual();
        if (arquivoId == null || usuarioId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .filter(a -> a.getEntidadeTipo() == EntidadeArquivo.AULA)
                .map(a -> a.getEnviadoPor() != null && usuarioId.equals(a.getEnviadoPor().getUsuarioId()))
                .orElse(false);
    }

    /** O usuário logado é OFICINEIRO? (pra filtrar listagens: ele só vê o que enviou) */
    public boolean usuarioAtualEhOficineiro() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OFICINEIRO"::equals);
    }

    /** O JWT da API grava o usuarioId no subject (ver UsuarioAutenticadoServiceImpl). */
    public Long usuarioIdAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return null;
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
