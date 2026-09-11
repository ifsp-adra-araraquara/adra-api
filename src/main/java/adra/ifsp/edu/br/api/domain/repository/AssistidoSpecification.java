package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.StatusGeral;
import adra.ifsp.edu.br.api.domain.model.Assistido;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AssistidoSpecification {

    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private AssistidoSpecification() {}

    public static String removerAcentos(String texto) {
        if (texto == null) {
            return "";
        }
        String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return DIACRITICAL_MARKS.matcher(nfd).replaceAll("").toLowerCase().trim();
    }

    public static Specification<Assistido> comFiltros(String busca, Long turmaId, StatusGeral status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // CA-A03.1: Busca por parte do nome ignorando acentos e caixa
            if (busca != null && !busca.isBlank()) {
                String termoNormalizado = "%" + removerAcentos(busca) + "%";
                Expression<String> nomeSemAcento = cb.function(
                        "unaccent",
                        String.class,
                        cb.lower(root.get("nomeCompleto"))
                );
                predicates.add(cb.like(nomeSemAcento, termoNormalizado));
            }

            // CA-A03.2: Filtro por turma
            if (turmaId != null) {
                predicates.add(cb.equal(root.get("turma").get("turmaId"), turmaId));
            }

            // CA-A03.2: Filtro por status
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
