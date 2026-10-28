package adra.ifsp.edu.br.api.domain.dto.arquivo;

import java.time.LocalDate;
import java.util.List;

import adra.ifsp.edu.br.api.domain.enums.StatusAula;
import adra.ifsp.edu.br.api.domain.model.Arquivo;
import adra.ifsp.edu.br.api.domain.model.Aula;

/** Uma aula do oficineiro com os materiais (arquivos) vinculados a ela. */
public record AulaArquivoDTO(
        Long aulaId,
        String titulo,
        LocalDate dataAula,
        StatusAula statusAula,
        String nomeTurma,
        List<ArquivoResponseDTO> arquivos
) {

    public static AulaArquivoDTO de(Aula aula, List<Arquivo> arquivos) {
        return new AulaArquivoDTO(
                aula.getAulaId(),
                aula.getTitulo(),
                aula.getDataAula(),
                aula.getStatusAula(),
                aula.getTurma() != null ? aula.getTurma().getNomeTurma() : null,
                arquivos.stream().map(ArquivoResponseDTO::de).toList()
        );
    }
}