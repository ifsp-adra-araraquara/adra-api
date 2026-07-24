package adra.ifsp.edu.br.api.domain.dto.usuario;

import adra.ifsp.edu.br.api.domain.enums.EspecialidadeSaude;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;

import java.time.OffsetDateTime;

public record UsuarioResponseDTO(
        Long usuarioId,
        String nomeCompleto,
        String email,
        NomeNivelPermissao nivelPermissao,
        EspecialidadeSaude especialidade,
        String cargoFuncao,
        String telefone,
        boolean ativo,
        OffsetDateTime ultimoLogin,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
