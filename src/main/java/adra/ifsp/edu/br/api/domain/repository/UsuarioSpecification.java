package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UsuarioSpecification {

    private UsuarioSpecification() {}

    public static Specification<Usuario> comFiltros(String busca, NomeNivelPermissao perfil, Boolean ativo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (busca != null && !busca.isBlank()) {
                String pattern = "%" + busca.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nomeCompleto")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }

            if (perfil != null) {
                Join<Usuario, NivelPermissao> nivel = root.join("nivelPermissao");
                predicates.add(cb.equal(nivel.get("nome"), perfil));
            }

            if (ativo != null) {
                predicates.add(cb.equal(root.get("ativo"), ativo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
