package adra.ifsp.edu.br.api.domain.dto.chamada;

import adra.ifsp.edu.br.api.domain.dto.presenca.AutorizadoRetiradaDTO;
import adra.ifsp.edu.br.api.domain.enums.MotivoFalta;
import adra.ifsp.edu.br.api.domain.enums.StatusPresenca;

import java.time.LocalDate;
import java.util.List;

/** Uma linha da chamada do grupo. `historicoRecente` vem das últimas chamadas do grupo, a mais recente primeiro. */
public record AtendidoChamadaDTO(
        Long assistidoId,
        String nomeCompleto,
        LocalDate dataNascimento,
        boolean podeIrEmboraSozinho,
        StatusPresenca statusPresenca,
        MotivoFalta motivoFalta,
        String observacao,
        List<HistoricoChamadaDTO> historicoRecente,
        Integer frequenciaPercentual,
        List<AutorizadoRetiradaDTO> autorizadosRetirada,
        /** FT-13: fim da suspensão que cobre o dia (a linha vem travada em FJ por Suspensão), ou null. */
        LocalDate suspensoAte
) {
}
