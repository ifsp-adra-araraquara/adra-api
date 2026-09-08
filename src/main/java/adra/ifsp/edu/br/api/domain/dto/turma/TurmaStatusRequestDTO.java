package adra.ifsp.edu.br.api.domain.dto.turma;

import jakarta.validation.constraints.NotNull;

public record TurmaStatusRequestDTO(
        Boolean ativo
){}