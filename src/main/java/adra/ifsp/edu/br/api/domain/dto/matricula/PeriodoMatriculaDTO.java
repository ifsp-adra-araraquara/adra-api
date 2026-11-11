package adra.ifsp.edu.br.api.domain.dto.matricula;

import java.time.LocalDate;

import adra.ifsp.edu.br.api.domain.model.PeriodoMatricula;

/** Período de matrícula de um ano, com a situação de hoje. */
public record PeriodoMatriculaDTO(Integer ano, LocalDate inicio, LocalDate fim, boolean abertoHoje) {

    public static PeriodoMatriculaDTO de(PeriodoMatricula periodo, LocalDate hoje) {
        return new PeriodoMatriculaDTO(periodo.getAno(), periodo.getInicio(), periodo.getFim(), periodo.abertoEm(hoje));
    }
}
