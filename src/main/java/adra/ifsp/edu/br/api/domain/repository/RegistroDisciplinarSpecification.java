package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.ConsequenciaDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.NivelDisciplinar;
import adra.ifsp.edu.br.api.domain.enums.StatusDocumentoDisciplinar;
import adra.ifsp.edu.br.api.domain.model.RegistroDisciplinar;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros da lista geral da tela Disciplinar (GET /api/disciplinar/registros). */
public final class RegistroDisciplinarSpecification {

    private RegistroDisciplinarSpecification() {}

    /**
     * @param somentePendencias só o que espera ação: documento a assinar ou
     *                          consequência pendente (passeio/retorno com responsável)
     */
    public static Specification<RegistroDisciplinar> comFiltros(String busca, NivelDisciplinar nivel,
                                                                boolean somentePendencias, boolean incluirArquivados) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (!incluirArquivados) {
                predicados.add(cb.isFalse(root.get("arquivado")));
            }
            if (nivel != null) {
                predicados.add(cb.equal(root.get("nivel"), nivel));
            }
            if (busca != null && !busca.isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("assistido").get("nomeCompleto")),
                        "%" + busca.trim().toLowerCase() + "%"));
            }
            if (somentePendencias) {
                predicados.add(cb.or(
                        cb.equal(root.get("documentoStatus"), StatusDocumentoDisciplinar.PENDENTE_ASSINATURA),
                        cb.and(
                                root.get("consequencia").in(ConsequenciaDisciplinar.PERDE_PROXIMO_PASSEIO,
                                        ConsequenciaDisciplinar.RETORNO_COM_RESPONSAVEL),
                                cb.isNull(root.get("consequenciaResolvidaEm")))));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
