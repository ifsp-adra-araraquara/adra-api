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
     *                          consequência pendente (recomendação, passeio, retorno com responsável)
     * @param somenteRecomendacoes só recomendações de advertência/suspensão ainda não resolvidas
     */
    public static Specification<RegistroDisciplinar> comFiltros(String busca, NivelDisciplinar nivel,
                                                                boolean somentePendencias, boolean somenteRecomendacoes,
                                                                boolean incluirArquivados) {
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
                                cb.isNotNull(root.get("consequencia")),
                                cb.isNull(root.get("consequenciaResolvidaEm")))));
            }
            if (somenteRecomendacoes) {
                predicados.add(root.get("consequencia").in(ConsequenciaDisciplinar.RECOMENDA_ADVERTENCIA,
                        ConsequenciaDisciplinar.RECOMENDA_SUSPENSAO));
                predicados.add(cb.isNull(root.get("consequenciaResolvidaEm")));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
