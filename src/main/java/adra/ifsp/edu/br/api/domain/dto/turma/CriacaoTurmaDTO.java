package adra.ifsp.edu.br.api.domain.dto.turma;

import adra.ifsp.edu.br.api.domain.enums.Turno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriacaoTurmaDTO {

    @NotNull(message = "Informe a oficina.")
    private Long oficinaId;

    @NotNull(message = "Informe o oficineiro responsável.")
    private Long oficineiroResponsavelId;

    @NotBlank(message = "Informe o nome da turma.")
    @Size(max = 100, message = "O nome da turma deve ter no máximo 100 caracteres.")
    private String nomeTurma;

    @NotNull(message = "Selecione o turno.")
    private Turno turno;

    @Size(max = 50, message = "A faixa etária deve ter no máximo 50 caracteres.")
    private String faixaEtaria;

    @NotNull(message = "Informe a capacidade da turma.")
    @Positive(message = "A capacidade deve ser maior que zero.")
    private Integer capacidade;

    @NotNull(message = "Informe os dias da semana das aulas.")
    private List<DayOfWeek> diasDaSemana;

    @NotNull(message = "Informe o horário de início.")
    private LocalTime horarioInicio;

    @NotNull(message = "Informe o horário de fim.")
    private LocalTime horarioFim;

    private String observacoes;
}
