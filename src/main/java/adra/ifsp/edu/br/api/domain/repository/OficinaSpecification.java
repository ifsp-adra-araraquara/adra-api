package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Oficina;
import org.springframework.data.jpa.domain.Specification;

import java.time.DayOfWeek;
import java.util.List;

public class OficinaSpecification {

    public static Specification<Oficina> filtroNome(String nome) {
        return (root, query, criteriaBuilder) -> {
            if (nome == null || nome.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("nomeOficina")),
                    "%" + nome.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Oficina> filtroAtivo(Boolean ativo) {
        return (root, query, criteriaBuilder) -> {
            if (ativo == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("ativo"), ativo);
        };
    }

    public static Specification<Oficina> filtroDias(List<DayOfWeek> dias) {
        return (root, query, criteriaBuilder) -> {
            if (dias == null || dias.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            query.distinct(true);
            return root.join("diasDaSemana").in(dias);
        };
    }

    public static Specification<Oficina> comFiltros(String nome, Boolean ativo, List<DayOfWeek> dias) {
        return Specification.where(filtroNome(nome))
                .and(filtroAtivo(ativo))
                .and(filtroDias(dias));
    }
}
