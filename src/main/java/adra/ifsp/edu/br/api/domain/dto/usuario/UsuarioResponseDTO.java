package adra.ifsp.edu.br.api.domain.dto.usuario;

import adra.ifsp.edu.br.api.domain.dto.modulo.ModuloDTO;
import adra.ifsp.edu.br.api.domain.enums.EspecialidadeSaude;
import adra.ifsp.edu.br.api.domain.enums.NomeNivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Modulo;

import java.time.OffsetDateTime;
import java.util.List;

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
        OffsetDateTime atualizadoEm,
        List<ModuloDTO> modulos
) {
}
