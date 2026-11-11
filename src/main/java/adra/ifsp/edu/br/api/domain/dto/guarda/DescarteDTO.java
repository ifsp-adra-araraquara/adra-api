package adra.ifsp.edu.br.api.domain.dto.guarda;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import adra.ifsp.edu.br.api.domain.model.DescarteDados;

/** Descarte já feito: quando, por quem e quanto saiu. */
public record DescarteDTO(Long descarteId, OffsetDateTime executadoEm, String executadoPorNome, LocalDate limite,
                          int matriculas, int atendidos, int responsaveis, int preCadastros, int arquivos,
                          long bytesLiberados) {

    public static DescarteDTO de(DescarteDados d) {
        return new DescarteDTO(d.getDescarteId(), d.getExecutadoEm(),
                d.getExecutadoPor() != null ? d.getExecutadoPor().getNomeCompleto() : null, d.getLimite(),
                d.getMatriculas(), d.getAtendidos(), d.getResponsaveis(), d.getPreCadastros(), d.getArquivos(),
                d.getBytesLiberados());
    }
}
