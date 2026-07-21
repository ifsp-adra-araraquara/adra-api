package adra.ifsp.edu.br.api.domain.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import adra.ifsp.edu.br.api.domain.enums.AcaoSistema;
import adra.ifsp.edu.br.api.domain.enums.ModuloSistema;
import adra.ifsp.edu.br.api.domain.model.LogAuditoria;
import adra.ifsp.edu.br.api.domain.repository.LogAuditoriaRepository;
import lombok.RequiredArgsConstructor;

/**
 * Ponto unico de gravacao em adra.log_auditoria.
 *
 * todo (quando o JWT/RBAC entrar): trocar o usuarioId=null por
 * SecurityContextHolder.getContext().getAuthentication() -> id do usuario
 * logado. E' o unico lugar que precisa mudar neste modulo.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public void registrar(ModuloSistema modulo,
                           String entidadeAfetada,
                           Long entidadeId,
                           AcaoSistema acao,
                           Map<String, Object> valorAnterior,
                           Map<String, Object> valorNovo,
                           String observacao) {

        LogAuditoria log = LogAuditoria.builder()
                .usuarioId(null) // sem RBAC ainda - ver todo acima
                .modulo(modulo)
                .entidadeAfetada(entidadeAfetada)
                .entidadeId(entidadeId)
                .acao(acao)
                .valorAnterior(valorAnterior)
                .valorNovo(valorNovo)
                .observacao(observacao)
                .build();

        logAuditoriaRepository.save(log);
    }
}
