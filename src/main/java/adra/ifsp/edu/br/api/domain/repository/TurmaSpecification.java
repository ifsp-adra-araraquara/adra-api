package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Turma;
import adra.ifsp.edu.br.api.domain.enums.Turno;
import org.springframework.data.jpa.domain.Specification;

public class TurmaSpecification {

    public static Specification<Turma> filtroNome(String nome) {
        return (root, query, criteriaBuilder) -> {
            if (nome == null || nome.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("nomeTurma")),
                    "%" + nome.toLowerCase() + "%"
            );
        };
    }

    public static Specification<Turma> filtroTurno(Turno turno) {
        return (root, query, criteriaBuilder) -> {
            if (turno == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("turno"), turno);
        };
    }

    public static Specification<Turma> filtroAtivo(Boolean ativo) {
        return (root, query, criteriaBuilder) -> {
            if (ativo == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("ativo"), ativo);
        };
    }

    public static Specification<Turma> comFiltros(String nome, Turno turno, Boolean ativo) {
        return Specification.where(filtroNome(nome))
                .and(filtroTurno(turno))
                .and(filtroAtivo(ativo));
    }
}