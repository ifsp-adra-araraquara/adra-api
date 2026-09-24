package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Aula;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class AulaSpecification {

    public static Specification<Aula> filtroTurmaId(Long turmaId) {
        return (root, query, criteriaBuilder) -> {
            if (turmaId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("turma").get("turmaId"), turmaId);
        };
    }

    public static Specification<Aula> filtroOficinaId(Long oficinaId) {
        return (root, query, criteriaBuilder) -> {
            if (oficinaId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("turma").get("oficina").get("oficinaId"), oficinaId);
        };
    }

    public static Specification<Aula> filtroPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        return (root, query, criteriaBuilder) -> {
            if (dataInicio == null && dataFim == null) {
                return criteriaBuilder.conjunction();
            }
            if (dataInicio != null && dataFim != null) {
                return criteriaBuilder.between(root.get("dataAula"), dataInicio, dataFim);
            }
            if (dataInicio != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("dataAula"), dataInicio);
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("dataAula"), dataFim);
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

    /**
     * Filtra por data exata da aula (usada na tela "Aulas" do sociopedagógico:
     * por padrão mostra as aulas de hoje, com um filtro de data em cima pra
     * trocar o dia).
     */
    public static Specification<Aula> filtroDataAula(LocalDate dataAula) {
        return (root, query, criteriaBuilder) -> {
            if (dataAula == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("dataAula"), dataAula);
        };
    }

    public static Specification<Aula> comFiltros(Long turmaId, String nomeTurma, String titulo) {
        return comFiltros(turmaId, null, null, null, nomeTurma, titulo, null);
    }

    public static Specification<Aula> comFiltros(Long turmaId, String nomeTurma, String titulo, LocalDate dataAula) {
        return comFiltros(turmaId, null, null, null, nomeTurma, titulo, dataAula);
    }

    public static Specification<Aula> comFiltros(
            Long turmaId, Long oficinaId, LocalDate dataInicio, LocalDate dataFim,
            String nomeTurma, String titulo, LocalDate dataAula
    ) {
        return Specification.where(filtroTurmaId(turmaId))
                .and(filtroOficinaId(oficinaId))
                .and(filtroPeriodo(dataInicio, dataFim))
                .and(filtroNomeTurma(nomeTurma))
                .and(filtroTituloAula(titulo))
                .and(filtroDataAula(dataAula));
    }
}
