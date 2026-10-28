package adra.ifsp.edu.br.api.domain.dto.disciplinar;

/** Cartões do topo da tela Disciplinar — o que precisa de atenção hoje. */
public record PendenciasDisciplinaresDTO(
        long assistidosSuspensosHoje,
        long retornosComResponsavelPendentes,
        long perdemProximoPasseio,
        long documentosAAssinar
) {
}
