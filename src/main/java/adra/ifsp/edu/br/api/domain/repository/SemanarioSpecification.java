package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Semanario;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filtros da tela Semanários da coordenação (GET /api/semanarios). */
public final class SemanarioSpecification {

    private SemanarioSpecification() {}

    /**
     * @param semana qualquer dia da semana desejada (vale a segunda-feira dela); null = todas as semanas
     * @param busca  procura em conteúdos, estratégias, recursos, grupo, oficina e em quem enviou
     */
    public static Specification<Semanario> comFiltros(LocalDate semana, Long turmaId, Long oficinaId, String busca) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (semana != null) {
                predicados.add(cb.equal(root.get("semanaInicio"), semana.with(DayOfWeek.MONDAY)));
            }
            if (turmaId != null) {
                predicados.add(cb.equal(root.get("turma").get("turmaId"), turmaId));
            }
            if (oficinaId != null) {
                predicados.add(cb.equal(root.get("oficina").get("oficinaId"), oficinaId));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + busca.trim().toLowerCase() + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("conteudoPrevisto")), termo),
                        cb.like(cb.lower(root.get("objetivos")), termo),
                        cb.like(cb.lower(root.get("recursosNecessarios")), termo),
                        cb.like(cb.lower(root.get("turma").get("nomeTurma")), termo),
                        cb.like(cb.lower(root.get("oficina").get("nomeOficina")), termo),
                        cb.like(cb.lower(root.get("enviadoPor").get("nomeCompleto")), termo)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
