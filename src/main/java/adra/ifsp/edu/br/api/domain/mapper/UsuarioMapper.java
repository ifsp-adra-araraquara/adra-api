package adra.ifsp.edu.br.api.domain.mapper;

import adra.ifsp.edu.br.api.domain.dto.modulo.ModuloDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioRequestDTO;
import adra.ifsp.edu.br.api.domain.dto.usuario.UsuarioResponseDTO;
import adra.ifsp.edu.br.api.domain.model.NivelPermissao;
import adra.ifsp.edu.br.api.domain.model.Usuario;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UsuarioMapper {

    public Usuario paraNovaEntidade(UsuarioRequestDTO dto, NivelPermissao nivelPermissao, String senhaHash) {
        return Usuario.builder()
                .nivelPermissao(nivelPermissao)
                .nomeCompleto(dto.nomeCompleto())
                .email(dto.email())
                .senhaHash(senhaHash)
                .cargoFuncao(dto.cargoFuncao())
                .telefone(dto.telefone())
                .especialidade(dto.especialidade())
                .ativo(true)
                .build();
    }

    /** Nao mexe em senha nem em ativo - fluxos separados (ver UsuarioService). */
    public void atualizarEntidade(Usuario entidade, UsuarioRequestDTO dto, NivelPermissao nivelPermissao) {
        entidade.setNivelPermissao(nivelPermissao);
        entidade.setNomeCompleto(dto.nomeCompleto());
        entidade.setEmail(dto.email());
        entidade.setCargoFuncao(dto.cargoFuncao());
        entidade.setTelefone(dto.telefone());
        entidade.setEspecialidade(dto.especialidade());
    }

    public UsuarioResponseDTO paraDTO(Usuario entidade) {
        return paraDTO(entidade, List.of());
    }

    public UsuarioResponseDTO paraDTO(Usuario entidade, List<ModuloDTO> listamodulos) {
        return new UsuarioResponseDTO(
                entidade.getUsuarioId(),
                entidade.getNomeCompleto(),
                entidade.getEmail(),
                entidade.getNivelPermissao().getNome(),
                entidade.getEspecialidade(),
                entidade.getCargoFuncao(),
                entidade.getTelefone(),
                entidade.isAtivo(),
                entidade.getUltimoLogin(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm(),
                listamodulos
        );
    }
}
