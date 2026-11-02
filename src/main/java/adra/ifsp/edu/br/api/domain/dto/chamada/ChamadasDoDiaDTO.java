package adra.ifsp.edu.br.api.domain.dto.chamada;

import java.time.LocalDate;
import java.util.List;

/** GET /api/chamadas/dia: grupos com chamada no dia. Em feriado ou exceção, `grupos` vem vazio e `motivoSemChamada` diz o porquê. */
public record ChamadasDoDiaDTO(
        LocalDate data,
        String motivoSemChamada,
        List<GrupoDoDiaDTO> grupos
) {
}
