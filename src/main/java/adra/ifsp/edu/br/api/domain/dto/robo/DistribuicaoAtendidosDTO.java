package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/** Robozinho ADRA — atendidos ativos hoje agrupados por uma dimensão (faixa etária, sexo, bairro…). */
public record DistribuicaoAtendidosDTO(String por, long ativos, LocalDateTime geradoEm, List<ContagemDTO> itens) {
}
