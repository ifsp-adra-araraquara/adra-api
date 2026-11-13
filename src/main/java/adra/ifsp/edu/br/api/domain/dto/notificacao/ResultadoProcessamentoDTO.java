package adra.ifsp.edu.br.api.domain.dto.notificacao;

/** FT-14: o que uma passada do job fez: notificações novas e mensagens enviadas. */
public record ResultadoProcessamentoDTO(int geradas, int mensagensEnviadas) {
}
