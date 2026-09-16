package adra.ifsp.edu.br.api.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriacaoAulas {

    @NotNull(message = "Informe a turma.")
    private Long turmaId;

    @NotNull(message = "Informe a data de início.")
    private LocalDate dataInicio;

    @NotNull(message = "Informe a data de fim.")
    private LocalDate dataFim;

    @NotNull(message = "Informe os dias da semana das aulas.")
    private List<DayOfWeek> diasDaSemana;

    @NotNull(message = "Informe o horário de início.")
    private LocalTime horarioInicio;

    @NotNull(message = "Informe o horário de fim.")
    private LocalTime horarioFim;

    private String titulo;

    private String descricao;

    private String conteudoPrevisto;

    private String objetivos;

    private String recursosNecessarios;

    private String observacoes;

}
