package adra.ifsp.edu.br.api.domain.dto.robo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Robozinho ADRA — perfil social dos atendidos ativos hoje, para prestação de
 * contas. SÓ CONTAGENS: nenhum campo identifica uma pessoa (LGPD).
 *
 * @param rendaInformada quantos têm renda familiar preenchida (base das faixas)
 */
public record SocialResumoDTO(long ativos, long recebemPrograma, List<ContagemDTO> programas,
                              long rendaInformada, List<ContagemDTO> faixasRenda, long pcd,
                              long necessidadesEspecificas, long problemaSaude, long comNis,
                              long estudaContraturno, List<ContagemDTO> turnoEscola,
                              LocalDateTime geradoEm) {
}
