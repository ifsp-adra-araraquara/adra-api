package adra.ifsp.edu.br.api.domain.repository;

import adra.ifsp.edu.br.api.domain.model.Assistido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AssistidoRepository extends JpaRepository<Assistido, Long> {

    /**
     * Usado no alerta de duplicidade provavel do card "Cadastrar assistido":
     * mesmo nome completo (case-insensitive) + mesma data de nascimento.
     * Ao editar, exclua o proprio id do resultado no service.
     */
    List<Assistido> findByNomeCompletoIgnoreCaseAndDataNascimento(String nomeCompleto, LocalDate dataNascimento);
}
