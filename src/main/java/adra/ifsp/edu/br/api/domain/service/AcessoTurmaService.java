package adra.ifsp.edu.br.api.domain.service;

import java.util.List;

import adra.ifsp.edu.br.api.domain.enums.CategoriaArquivo;
import adra.ifsp.edu.br.api.domain.enums.EntidadeArquivo;
import adra.ifsp.edu.br.api.domain.repository.ArquivoRepository;
import adra.ifsp.edu.br.api.domain.repository.AssistidoSpecification;
import adra.ifsp.edu.br.api.domain.repository.OficinaRepository;
import adra.ifsp.edu.br.api.domain.repository.OficinaTurmaRepository;
import adra.ifsp.edu.br.api.domain.repository.SemanarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra "o Oficineiro só acessa os grupos atendidos pelas oficinas dele" (oficina_turma).
 * Usada direto no @PreAuthorize, ex.:
 *   hasRole('OFICINEIRO') and @acessoTurma.podeAcessarSemanario(#idSemanario)
 * Sem permissão -> o Spring Security devolve 403 antes de entrar no método.
 * Reaproveitar nos materiais do semanário.
 */
@Service("acessoTurma")
@RequiredArgsConstructor
public class AcessoTurmaService {

    private final OficinaRepository oficinaRepository;
    private final SemanarioRepository semanarioRepository;
    private final OficinaTurmaRepository oficinaTurmaRepository;
    private final ArquivoRepository arquivoRepository;

    @Transactional(readOnly = true)
    public boolean podeAcessarSemanario(Long semanarioId) {
        Long usuarioId = usuarioIdAtual();
        return semanarioId != null && usuarioId != null
                && semanarioRepository.existsBySemanarioIdAndOficina_OficineiroResponsavel_UsuarioId(semanarioId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean ehOficineiroDaOficina(Long oficinaId) {
        Long usuarioId = usuarioIdAtual();
        return oficinaId != null && usuarioId != null
                && oficinaRepository.existsByOficinaIdAndOficineiroResponsavelUsuarioId(oficinaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean podeAcessarTurma(Long turmaId) {
        Long usuarioId = usuarioIdAtual();
        return turmaId != null && usuarioId != null
                && oficinaTurmaRepository.existsByTurma_TurmaIdAndOficina_OficineiroResponsavel_UsuarioIdAndAtivoTrue(turmaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public boolean oficineiroPodeListarAssistidos(Long turmaId, String busca) {
        return !AssistidoSpecification.pareceCpf(busca) && podeAcessarTurma(turmaId);
    }

    /** O arquivo é material de um semanário deste oficineiro? */
    @Transactional(readOnly = true)
    public boolean podeAcessarArquivoDeSemanario(Long arquivoId) {
        if (arquivoId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .filter(a -> a.getEntidadeTipo() == EntidadeArquivo.SEMANARIO)
                .map(a -> podeAcessarSemanario(a.getEntidadeId()))
                .orElse(false);
    }

    /** É material de semanário? (a Coordenação só vê esses, não exclui) */
    @Transactional(readOnly = true)
    public boolean ehArquivoDeSemanario(Long arquivoId) {
        if (arquivoId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .map(a -> a.getEntidadeTipo() == EntidadeArquivo.SEMANARIO)
                .orElse(false);
    }

    /** É material de semanário ENVIADO por este usuário? (oficineiro só vê/abre/exclui os próprios) */
    @Transactional(readOnly = true)
    public boolean ehAutorDoArquivoDeSemanario(Long arquivoId) {
        Long usuarioId = usuarioIdAtual();
        if (arquivoId == null || usuarioId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .filter(a -> a.getEntidadeTipo() == EntidadeArquivo.SEMANARIO)
                .map(a -> a.getEnviadoPor() != null && usuarioId.equals(a.getEnviadoPor().getUsuarioId()))
                .orElse(false);
    }

    /** O id informado na requisição é o do próprio usuário autenticado? */
    public boolean ehProprioUsuario(Long usuarioId) {
        Long atual = usuarioIdAtual();
        return usuarioId != null && usuarioId.equals(atual);
    }

    /** A lista de ids contém apenas o próprio usuário autenticado? */
    public boolean ehProprioUsuario(List<Long> usuarioIds) {
        Long atual = usuarioIdAtual();
        return usuarioIds != null && usuarioIds.size() == 1 && atual != null && atual.equals(usuarioIds.getFirst());
    }

    /** O arquivo ativo pertence a um USUARIO? */
    @Transactional(readOnly = true)
    public boolean ehArquivoDeUsuario(Long arquivoId) {
        if (arquivoId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .map(a -> a.getEntidadeTipo() == EntidadeArquivo.USUARIO)
                .orElse(false);
    }

    /** O arquivo ativo é a FOTO_PERFIL do próprio usuário autenticado? */
    @Transactional(readOnly = true)
    public boolean ehFotoPerfilDoProprioUsuario(Long arquivoId) {
        Long usuarioId = usuarioIdAtual();
        if (arquivoId == null || usuarioId == null) return false;
        return arquivoRepository.findByArquivoIdAndExcluidoEmIsNull(arquivoId)
                .map(a -> a.getEntidadeTipo() == EntidadeArquivo.USUARIO
                        && a.getCategoria() == CategoriaArquivo.FOTO_PERFIL
                        && usuarioId.equals(a.getEntidadeId()))
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
