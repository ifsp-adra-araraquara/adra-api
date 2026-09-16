package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Aula;
import org.springframework.data.jpa.domain.Specification;

public class AulaSpecification {

    public static Specification<Aula> filtroTurmaId(Long turmaId) {
        return (root, query, criteriaBuilder) -> {
            if (turmaId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("turma").get("turmaId"), turmaId);
        };
    }

    public static Specification<Aula> filtroNomeTurma(String nomeTurma) {
        return (root, query, criteriaBuilder) -> {
            if (nomeTurma == null || nomeTurma.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("turma").get("nomeTurma")),
                    "%" + nomeTurma.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Aula> filtroTituloAula(String titulo) {
        return (root, query, criteriaBuilder) -> {
            if (titulo == null || titulo.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("titulo")),
                    "%" + titulo.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Aula> comFiltros(Long turmaId, String nomeTurma, String titulo) {
        return Specification.where(filtroTurmaId(turmaId))
                .and(filtroNomeTurma(nomeTurma))
                .and(filtroTituloAula(titulo));
    }
}
